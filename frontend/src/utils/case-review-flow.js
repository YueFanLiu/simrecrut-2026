const copy = value => JSON.parse(JSON.stringify(value))
const conflict = () => Object.assign(new Error('The case changed while loading. Reload the draft.'), { status: 409 })

// Each successful stage is checkpointed. A lost response retries the exact body
// and idempotency key, including when confirmation has already locked the case.
export function createCaseReviewFlow(api, makeKey = () => crypto.randomUUID()) {
  let pending = null
  return {
    get hasPending() { return pending !== null },
    async run(input, onUpdate = () => {}) {
      if (!pending) {
        const state = copy(input)
        const steps = []
        const write = (action, body) => {
          let request
          return async () => {
            request ??= { body: copy(body()), key: makeKey() }
            const result = await api[action](request.body, request.key)
            state.revision = result.revision
            onUpdate(copy(state), action)
          }
        }
        if (state.factsChanged) {
          steps.push(write('saveDraft', () => ({ ...state.facts, revision: state.revision })))
          steps.push(async () => {
            const fresh = await api.readDraft()
            if (fresh.revision !== state.revision) throw conflict()
            state.draft = fresh
            state.facts = { professional: fresh.professional, confirmedAbsent: fresh.confirmedAbsent }
            onUpdate(copy(state), 'readDraft')
          })
          if (state.model && !state.modelTextEdited) steps.push(async () => {
            const fresh = await api.readModel()
            if (fresh.revision !== state.revision) throw conflict()
            if (typeof fresh.biography !== 'string') throw new Error('The model text could not be loaded. Retry this save.')
            const changed = fresh.biography !== state.model.biography
            if (changed && state.model.useText) state.previousModelText = state.model.biography
            state.model.biography = fresh.biography
            state.requiresModelReview = changed && state.model.useText
            onUpdate(copy(state), 'readModel')
          })
        }
        if (state.attributesChanged) steps.push(write('saveAttributes', () => ({ attributes: state.attributes, revision: state.revision })))
        // Retain manual text and portrait choices; changed automatic text must
        // be shown and reviewed before it is saved or used for confirmation.
        if (state.model) steps.push(write('saveModel', () => {
          const { portraitBase64, ...model } = state.model
          return {
            ...model,
            // Omitted text/portraits must stay omitted, including dormant local data.
            name: model.useText ? model.name : '',
            biography: model.useText ? model.biography : '',
            portraitIndex: model.usePortrait ? model.portraitIndex : -1,
            ...(model.usePortrait && portraitBase64 ? { portraitBase64 } : {}),
            revision: state.revision,
          }
        }))
        if (state.confirm) steps.push(write('confirm', () => ({ revision: state.revision, confirmed: true })))
        pending = { state, steps, index: 0 }
      }
      const operation = pending
      try {
        while (operation.index < operation.steps.length) {
          await operation.steps[operation.index]()
          operation.index++
          if (operation.state.requiresModelReview) {
            pending = null
            return copy(operation.state)
          }
        }
        pending = null
        return copy(operation.state)
      } catch (error) {
        // A definitive rejection allows corrections. Network/server failures keep
        // the frozen operation available for an exact retry.
        if (error.status >= 400 && error.status < 500 && error.status !== 408) pending = null
        throw error
      }
    },
  }
}
