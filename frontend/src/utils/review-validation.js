import { factGroups, researchFields } from '../mock/review-draft.js'

/** Drafts may be incomplete when saved; confirmation requires resolved demo criteria. */
export function validateReviewDraft(draft, forConfirmation = false) {
  const errors = {}
  for (const group of factGroups) {
    const data = draft.professional[group.key]
    if (forConfirmation && draft.requiredGroups.includes(group.key) && !data.items.length && !data.confirmedAbsent) {
      errors[group.key] = `Add ${group.label.toLowerCase()} or confirm that there are none to report.`
    }
    data.items.forEach((item) => group.fields.forEach((field) => {
      const evidence = item[field.key]
      const value = String(evidence.value ?? '').trim()
      const key = `${group.key}.${item.id}.${field.key}`
      if (!value) {
        if (forConfirmation) errors[key] = `${field.label} is required for this item.`
      } else if (field.type === 'number' && (!Number.isInteger(Number(value)) || Number(value) < 0 || Number(value) > field.max)) {
        errors[key] = `${field.label} must be a whole number between 0 and ${field.max}.`
      } else if (field.options && !field.options.includes(value)) {
        errors[key] = `Choose a valid ${field.label.toLowerCase()}.`
      } else if (!field.options && field.type !== 'number' && value.length > field.max) {
        errors[key] = `${field.label} must be ${field.max} characters or fewer.`
      } else if (forConfirmation && draft.requiredGroups.includes(group.key) && ['UNKNOWN', 'CONFLICT'].includes(evidence.status)) {
        errors[key] = `Resolve ${field.label.toLowerCase()} before confirming.`
      }
    }))
  }
  return errors
}

/** Whitelist professional fields and retain original mock evidence on user corrections. */
export function copyProfessionalDraft(draft) {
  return {
    extractionVersion: draft.extractionVersion,
    requiredGroups: [...draft.requiredGroups],
    professional: Object.fromEntries(factGroups.map((group) => [group.key, {
      confirmedAbsent: Boolean(draft.professional[group.key].confirmedAbsent),
      items: draft.professional[group.key].items.map((item) => Object.fromEntries([
        ['id', item.id], ...group.fields.map((field) => [field.key, {
          value: typeof item[field.key].value === 'string' ? item[field.key].value.trim() : item[field.key].value,
          status: item[field.key].status,
          evidenceExcerpt: item[field.key].evidenceExcerpt,
          evidenceLocation: item[field.key].evidenceLocation,
        }]),
      ])),
    }])),
  }
}

export function emptyResearchAttributes() {
  return Object.fromEntries(researchFields.map((field) => [field.key, 'UNKNOWN']))
}

export function normalizeResearchAttributes(attributes) {
  return Object.fromEntries(researchFields.map((field) => [field.key, String(attributes[field.key] || '').trim() || 'UNKNOWN']))
}
