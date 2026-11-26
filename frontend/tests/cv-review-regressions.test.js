import test from 'node:test'
import assert from 'node:assert/strict'
import { createRenderer, h, nextTick, ssrContextKey } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { createPinia } from 'pinia'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'
import { exportRevisionDraft } from '../src/utils/cv-revisions.js'
import { ElMessage, ElMessageBox } from 'element-plus'

// Run the real SFC setup/watch/action code without requiring a browser DOM.
// Browser checks additionally exercise the rendered parent/child event wiring.
const renderer = createRenderer({ createComment: () => ({}), createText: () => ({}), createElement: () => ({}),
  insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null, setText() {}, setElementText() {}, patchProp() {} })
const headless = component => ({ ...component, render: () => null })
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
const sourceId = 'a'.repeat(32)
const professional = { personal: [], skills: [{ skillCode: 'JAVA' }], experience: [], education: [], languages: [], projects: [], other: [] }
const source = { id: sourceId, revision: 1, confirmed: 1, status: 'CONFIRMED', display_name: 'Source', draft: { professional, warnings: [] } }

async function settle(condition) {
  for (let i = 0; i < 100 && !condition(); i++) await new Promise(resolve => setImmediate(resolve))
  assert.ok(condition(), 'component did not finish loading')
}

test('CV review regressions: unfinished edits and asynchronous draft restoration', async t => {
  const originalFlag = process.env.VITE_APP_CV_REVISIONS_ENABLED
  const originalWindow = globalThis.window
  process.env.VITE_APP_CV_REVISIONS_ENABLED = 'true'
  const server = await createServer({ mode: 'connected', root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const { default: useUserStore } = await server.ssrLoadModule('/src/store/modules/user.js')
    const writes = [], windowEvents = new Map()
    let librarySource = source
    let statusFailure = 0
    let caseRow = { caseId: 'CASE', processingStatus: 'REVIEW_REQUIRED', revision: 1 }
    request.defaults.adapter = async config => {
      if (config.method !== 'get') writes.push(config.url)
      let data = librarySource
      if (config.method === 'delete') data = { deleted: true }
      if (config.url.endsWith('/save')) {
        librarySource = { ...librarySource, revision: librarySource.revision + 1, draft: JSON.parse(config.data).draft }
        data = librarySource
      }
      if (config.url.endsWith('/status')) {
        if (statusFailure) {
          const status = statusFailure; statusFailure = 0
          throw Object.assign(new Error('Synthetic status failure'), { config, response: { status, data: {} } })
        }
        data = caseRow
      }
      if (config.url.endsWith('/draft')) data = { professional, confirmedAbsent: [], unresolvedRequiredFields: [], revision: 1 }
      if (config.url.endsWith('/research-attributes')) data = { allowedValues: {}, attributes: [], revision: 1 }
      return { config, status: 200, data: { code: 0, data } }
    }
    globalThis.window = { addEventListener(type, listener) { windowEvents.set(type, listener) },
      removeEventListener(type, listener) { if (windowEvents.get(type) === listener) windowEvents.delete(type) } }

    async function page(path, url) {
      const component = headless((await server.ssrLoadModule(path)).default)
      const pinia = createPinia()
      useUserStore(pinia).permissions = ['simrecrut:resume:upload', 'simrecrut:resume:edit']
      const router = createRouter({ history: createMemoryHistory(), routes: [
        { path: url.split('?')[0].replace(sourceId, ':id').replace('/CASE/', '/:caseId/'), component },
        { path: '/elsewhere', component: { render: () => null } },
        { path: '/candidate/resumes', name: 'ConnectedResumes', component: { render: () => null } },
        { path: '/done/:caseId', name: 'CvReview', component: { render: () => null } },
      ] })
      await router.push(url)
      const app = renderer.createApp({ render: () => h(RouterView) })
      app.use(pinia); app.use(router); app.provide(ssrContextKey, { modules: new Set() }); app.mount({})
      let vnode = app._instance.subTree
      while (vnode?.component && vnode.type !== component) vnode = vnode.component.subTree
      const state = vnode.component.setupState
      await settle(() => !state.loading)
      return { state, router, close: () => app.unmount() }
    }

    await t.test('skill edits report pending immediately and survive a temporary lock', async () => {
      const component = headless((await server.ssrLoadModule('/src/components/simrecrut/CvSkillEditor.vue')).default)
      const pending = [], edits = []
      const app = renderer.createApp(component, { items: [{ id: 'skills:0', name: 'Java' }], locked: false,
        onPendingChange: value => pending.push(value), onEdit: value => edits.push(value) })
      app.provide(ssrContextKey, { modules: new Set() }); app.mount({})
      try {
        const state = app._instance.setupState
        state.edit({ id: 'skills:0', name: 'Java' }); state.name = 'Python'
        assert.equal(pending.at(-1), true)
        app._instance.props.locked = true; await nextTick()
        assert.equal(state.name, 'Python')
        assert.equal(state.editing, 'skills:0')
        app._instance.props.locked = false; await state.submit()
        assert.deepEqual(edits, [{ name: 'Python', id: 'skills:0' }])
        assert.equal(pending.at(-1), false)
      } finally { app.unmount() }
    })

    await t.test('the shared editor forwards pending skills and section creation until both are finished', async () => {
      const component = headless((await server.ssrLoadModule('/src/components/simrecrut/CvFactsEditor.vue')).default)
      const events = []
      const app = renderer.createApp(component, { modelValue: professional, onPendingChange: value => events.push(value) })
      app.provide(ssrContextKey, { modules: new Set() }); app.mount({})
      try {
        const state = app._instance.setupState
        state.skillPending = true
        assert.equal(events.at(-1), true)
        state.adding = true; state.sectionName = 'Portfolio'; state.skillPending = false
        assert.equal(events.at(-1), true)
        app._instance.props.locked = true; await nextTick()
        assert.equal(state.sectionName, 'Portfolio')
        assert.equal(events.at(-1), true)
        state.adding = false
        assert.equal(events.at(-1), false)
      } finally { app.unmount() }
    })

    await t.test('unsubmitted editor input invalidates case acknowledgement and blocks even direct confirmation calls', async () => {
      const { state, close } = await page('/src/views/candidate/cases/ConnectedReview.vue', '/candidate/cases/CASE/review')
      try {
        assert.ok(state.draft)
        state.reviewed = true; state.editorPending = true
        assert.equal(state.reviewed, false)
        const before = writes.length
        state.reviewed = true
        await state.perform('confirm'); await state.perform('save')
        assert.equal(writes.length, before)
      } finally { close() }
    })

    await t.test('library confirmation and save cannot bypass unfinished local edits', async () => {
      const { state, close } = await page('/src/views/candidate/ResumeReview.vue', `/candidate/resumes/${sourceId}`)
      try {
        assert.ok(state.record)
        state.confirmed = true; state.editorPending = true
        assert.equal(state.confirmed, false)
        const before = writes.length
        await state.publish(); await state.saveDraft()
        assert.equal(writes.length, before)
      } finally { close() }
    })

    await t.test('library edits guard navigation, resource changes and refresh; saving releases the guard', async () => {
      librarySource = { ...source, confirmed: 0, status: 'REVIEW_REQUIRED' }
      const { state, router, close } = await page('/src/views/candidate/ResumeReview.vue', `/candidate/resumes/${sourceId}`)
      const originalConfirm = ElMessageBox.confirm, originalInfo = ElMessage.info
      let prompts = 0, notices = 0
      ElMessageBox.confirm = async () => { prompts++; throw 'cancel' }
      ElMessage.info = () => { notices++ }
      try {
        state.editorPending = true
        await router.push('/elsewhere')
        assert.equal(router.currentRoute.value.params.id, sourceId)
        await router.push(`/candidate/resumes/${'f'.repeat(32)}`)
        assert.equal(router.currentRoute.value.params.id, sourceId)
        assert.equal(prompts, 2)
        const refreshEvent = { preventDefault() { this.prevented = true } }
        windowEvents.get('beforeunload')(refreshEvent)
        assert.equal(refreshEvent.prevented, true)
        state.editorPending = false
        state.editors.skills[0].skillCode = 'PYTHON'
        await router.push('/elsewhere')
        assert.equal(prompts, 3)
        state.busy = true
        await router.push('/elsewhere')
        assert.equal(notices, 1)
        assert.equal(router.currentRoute.value.params.id, sourceId)
        state.busy = false
        await state.saveDraft()
        assert.equal(librarySource.draft.professional.skills[0].skillCode, 'PYTHON')
        assert.equal(state.dirty, false)
        const cleanEvent = { preventDefault() { this.prevented = true } }
        windowEvents.get('beforeunload')(cleanEvent)
        assert.equal(cleanEvent.prevented, undefined)
        await router.push('/elsewhere')
        assert.equal(router.currentRoute.value.path, '/elsewhere')
        assert.equal(prompts, 3)
      } finally { close(); ElMessageBox.confirm = originalConfirm; ElMessage.info = originalInfo; librarySource = source }
      assert.equal(windowEvents.has('beforeunload'), false)
    })

    await t.test('library edits can be deliberately discarded after confirmation', async () => {
      librarySource = { ...source, confirmed: 0, status: 'REVIEW_REQUIRED' }
      const { state, router, close } = await page('/src/views/candidate/ResumeReview.vue', `/candidate/resumes/${sourceId}`)
      const original = ElMessageBox.confirm
      ElMessageBox.confirm = async () => 'confirm'
      try {
        state.editorPending = true
        await router.push('/elsewhere')
        assert.equal(router.currentRoute.value.path, '/elsewhere')
      } finally { close(); ElMessageBox.confirm = original; librarySource = source }
    })

    await t.test('successful CV deletion and case attachment can leave while their action is finishing', async () => {
      const originalConfirm = ElMessageBox.confirm, originalSuccess = ElMessage.success, originalInfo = ElMessage.info
      ElMessageBox.confirm = async () => 'confirm'; ElMessage.success = () => {}; ElMessage.info = () => {}
      try {
        const deletion = await page('/src/views/candidate/ResumeReview.vue', `/candidate/resumes/${sourceId}`)
        try {
          deletion.state.editorPending = true
          await deletion.state.removeResume()
          assert.equal(deletion.router.currentRoute.value.name, 'ConnectedResumes')
        } finally { deletion.close() }
        const attachment = await page('/src/views/candidate/ResumeReview.vue', `/candidate/resumes/${sourceId}?caseId=CASE`)
        try {
          await attachment.state.useForCase()
          assert.equal(attachment.router.currentRoute.value.name, 'CvReview')
        } finally { attachment.close() }
      } finally { ElMessageBox.confirm = originalConfirm; ElMessage.success = originalSuccess; ElMessage.info = originalInfo }
    })

    await t.test('temporary extraction status failures preserve the case and a subsequent poll opens review', async () => {
      caseRow = { caseId: 'CASE', processingStatus: 'EXTRACTING', stage: 'EXTRACTION', revision: 1 }
      const { state, router, close } = await page('/src/views/candidate/cases/upload.vue', '/candidate/cases/CASE/upload')
      try {
        await settle(() => !state.statusLoading)
        assert.equal(state.processing, true)
        const known = state.entry
        statusFailure = 503
        await state.refreshConnectedCase()
        assert.equal(state.entry, known)
        assert.equal(state.processing, true)
        assert.ok(state.statusError)
        caseRow = { ...caseRow, processingStatus: 'REVIEW_REQUIRED' }
        // Exercise the same callback used by the automatic polling timer.
        await state.refreshConnectedCase()
        await settle(() => router.currentRoute.value.name === 'CvReview')
      } finally { close(); caseRow = { caseId: 'CASE', processingStatus: 'REVIEW_REQUIRED', revision: 1 } }
    })

    await t.test('an initial status outage can be retried; definite access errors clear the case', async () => {
      statusFailure = 503
      const { state, close } = await page('/src/views/candidate/cases/upload.vue', '/candidate/cases/CASE/upload')
      const originalError = ElMessage.error
      ElMessage.error = () => {}
      try {
        await settle(() => !state.statusLoading)
        assert.equal(state.entry, null)
        assert.ok(state.statusError)
        await state.refreshConnectedCase()
        assert.equal(state.entry.caseId, 'CASE')
        assert.equal(state.statusError, '')
        statusFailure = 403
        await state.refreshConnectedCase()
        assert.equal(state.entry, null)
        assert.equal(state.statusError, '')
      } finally { close(); ElMessage.error = originalError }
    })

    await t.test('a delayed restore locks saving until the file finishes and then restores its contents', async () => {
      const { state, close } = await page('/src/views/candidate/ResumeRevision.vue', `/candidate/resumes/${sourceId}/revise`)
      try {
        assert.equal(state.canSave, true)
        assert.ok(state.revision)
        const file = deferred()
        const restored = { ...state.revision, displayName: 'Restored draft' }
        const operation = state.restore({ target: { files: [{ size: 100, text: () => file.promise }], value: 'file' } })
        assert.equal(state.restoring, true)
        assert.equal(state.locked, true)
        const before = writes.length
        await state.save()
        assert.equal(writes.length, before)
        file.resolve(exportRevisionDraft(restored)); await operation
        assert.equal(state.revision.displayName, 'Restored draft')
        assert.equal(state.restoring, false)
      } finally { close() }
    })

    await t.test('unfinished revision input blocks save and a failed restore unlocks the unchanged draft', async () => {
      const { state, close } = await page('/src/views/candidate/ResumeRevision.vue', `/candidate/resumes/${sourceId}/revise`)
      try {
        const original = state.revision.displayName, before = writes.length
        state.editorPending = true
        await state.save()
        assert.equal(writes.length, before)
        state.editorPending = false
        await state.restore({ target: { files: [{ size: 10, text: async () => { throw new Error('File read failed') } }], value: 'file' } })
        assert.equal(state.restoring, false)
        assert.equal(state.locked, false)
        assert.equal(state.revision.displayName, original)
        assert.equal(state.error, 'File read failed')
      } finally { close() }
    })

    await t.test('a restore cannot overwrite an uncertain save or a newly reloaded source', async () => {
      const { state, close } = await page('/src/views/candidate/ResumeRevision.vue', `/candidate/resumes/${sourceId}/revise`)
      try {
        assert.ok(state.revision)
        const original = state.revision.displayName
        const text = exportRevisionDraft({ ...state.revision, displayName: 'Must not replace pending data' })
        const file = deferred()
        const operation = state.restore({ target: { files: [{ size: 100, text: () => file.promise }], value: 'file' } })
        state.uncertain = true
        file.resolve(text); await operation
        assert.equal(state.revision.displayName, original)
        assert.equal(state.restoring, false)
        state.uncertain = false
        const secondFile = deferred()
        const second = state.restore({ target: { files: [{ size: 100, text: () => secondFile.promise }], value: 'file' } })
        await state.load()
        secondFile.resolve(text); await second
        assert.equal(state.revision.displayName, original)
      } finally { close() }
    })
  } finally {
    await server.close()
    if (originalFlag === undefined) delete process.env.VITE_APP_CV_REVISIONS_ENABLED
    else process.env.VITE_APP_CV_REVISIONS_ENABLED = originalFlag
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
  }
})
