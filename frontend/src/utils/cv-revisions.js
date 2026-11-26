import { cvGroups } from './cv-supplements.js'

const FORMAT = 'simrecrut-cv-revision-draft-v1'
const MAX_BYTES = 2 * 1024 * 1024
const idPattern = /^[a-f0-9]{32}$/
const copy = value => JSON.parse(JSON.stringify(value))
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const requireValue = (condition, message) => { if (!condition) throw new Error(message) }

export function createRevisionDraft(source, displayName) {
  requireValue(source?.status === 'CONFIRMED' && [true, 1].includes(source.confirmed)
    && typeof source.id === 'string' && idPattern.test(source.id) && Number.isSafeInteger(source.revision) && source.revision > 0,
  'Choose a confirmed CV before creating a revised copy.')
  requireValue(object(source.draft?.professional), 'This draft file has an unsupported structure.')
  const value = { sourceId: source.id, expectedRevision: source.revision, displayName,
    draft: { professional: Object.fromEntries(cvGroups.map(group => [group, copy(source.draft?.professional?.[group] || [])])),
      warnings: copy(source.draft?.warnings || []) } }
  validateDraft(value.draft)
  return value
}

function validateDraft(draft) {
  requireValue(object(draft) && object(draft.professional) && Array.isArray(draft.warnings)
    && draft.warnings.length <= 100 && draft.warnings.every(value => typeof value === 'string' && value.length <= 1000),
  'This draft file has an unsupported structure.')
  const fields = {
    personal: ['fieldCode', 'value', 'imageBase64'], skills: ['skillCode'],
    experience: ['roleFamilyCode', 'startMonth', 'endMonth', 'isCurrent', 'supportedSkillCodes', 'reviewedRelevantYears'],
    education: ['degreeLevelCode', 'subjectCode'], languages: ['languageCode', 'levelCode'],
    projects: ['projectId', 'supportedConditionCodes', 'supportedSkillCodes'], other: ['categoryCode', 'value'],
  }
  const required = { personal: ['fieldCode'], skills: ['skillCode'], experience: ['roleFamilyCode'],
    education: ['degreeLevelCode', 'subjectCode'], languages: ['languageCode', 'levelCode'], projects: ['projectId'], other: ['categoryCode'] }
  requireValue(Object.keys(draft.professional).every(group => cvGroups.includes(group)), 'This draft file has an unsupported structure.')
  for (const group of cvGroups) {
    const rows = draft.professional[group]
    requireValue(Array.isArray(rows) && rows.length <= 100, 'This draft file has an unsupported structure.')
    for (const row of rows) {
      requireValue(object(row) && Object.keys(row).every(key => key === 'evidence' || fields[group].includes(key)), 'This draft file has an unsupported structure.')
      requireValue(required[group].every(key => typeof row[key] === 'string' && row[key].trim().length > 0), 'This draft file has an unsupported structure.')
      for (const [key, value] of Object.entries(row)) {
        if (key === 'evidence') {
          requireValue(object(value) && ['EXTRACTED', 'USER_SUPPLIED'].includes(value.source)
            && typeof value.text === 'string' && value.text.length <= 300
            && (value.page === null || Number.isInteger(value.page) && value.page >= 1 && value.page <= 20),
          'This draft file has an unsupported structure.')
        } else if (key === 'imageBase64' && value === null) {
          // Existing confirmed records may explicitly omit their photo.
        } else if (key.endsWith('Codes')) {
          requireValue(Array.isArray(value) && value.length <= 100 && value.every(item => typeof item === 'string' && item.length <= 1000), 'This draft file has an unsupported structure.')
        } else if (key === 'isCurrent') {
          requireValue(typeof value === 'boolean', 'This draft file has an unsupported structure.')
        } else if (key === 'reviewedRelevantYears') {
          requireValue(value === null || typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 80, 'This draft file has an unsupported structure.')
        } else if (key.endsWith('Month')) {
          requireValue(value === null || typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value), 'This draft file has an unsupported structure.')
        } else {
          const limit = key === 'imageBase64' ? 400000 : key === 'value' ? 2000 : 100
          requireValue(typeof value === 'string' && value.length <= limit, 'This draft file has an unsupported structure.')
        }
      }
    }
  }
}

export function revisionPayload(value) {
  requireValue(typeof value?.displayName === 'string', 'Enter a CV name of 1–120 characters.')
  const displayName = value.displayName.trim()
  requireValue(displayName.length > 0 && displayName.length <= 120 && !/[\p{Cc}]/u.test(displayName), 'Enter a CV name of 1–120 characters.')
  requireValue(typeof value.sourceId === 'string' && idPattern.test(value.sourceId) && Number.isSafeInteger(value.expectedRevision) && value.expectedRevision > 0,
    'Reload the source CV before creating a revised copy.')
  validateDraft(value.draft)
  // No confirmation, publication, filename, PDF, retention or case state is
  // copied. The server must create a new, unconfirmed library record.
  return { displayName, expectedRevision: value.expectedRevision,
    draft: { professional: copy(value.draft.professional), warnings: copy(value.draft.warnings) } }
}

export function exportRevisionDraft(value) {
  const text = JSON.stringify({ format: FORMAT, sourceId: value.sourceId, ...revisionPayload(value) }, null, 2)
  requireValue(new TextEncoder().encode(text).length <= MAX_BYTES, 'Choose a revision draft file smaller than 2 MB.')
  return text
}

export function restoreRevisionDraft(text, source) {
  requireValue(new TextEncoder().encode(text).length <= MAX_BYTES, 'Choose a revision draft file smaller than 2 MB.')
  let value
  try { value = JSON.parse(text) } catch { throw new Error('Choose a valid revision draft file.') }
  requireValue(value?.format === FORMAT && value.sourceId === source.id && value.expectedRevision === source.revision,
    'This draft belongs to a different CV or source revision.')
  const payload = revisionPayload(value)
  return { sourceId: value.sourceId, ...payload }
}

export function createRevisionSaver(send) {
  let pending
  return {
    get hasPending() { return Boolean(pending) },
    async save(value) {
      const body = revisionPayload(value)
      const signature = JSON.stringify([value.sourceId, body])
      requireValue(!pending || pending.signature === signature,
        'The previous save result is unknown. Retry that save before changing the copy.')
      pending ||= { signature, sourceId: value.sourceId, body, key: crypto.randomUUID() }
      try {
        const result = await send(pending.sourceId, copy(pending.body), pending.key)
        requireValue(typeof result?.id === 'string' && idPattern.test(result.id) && result.id !== pending.sourceId
          && result.status === 'REVIEW_REQUIRED' && [false, 0].includes(result.confirmed),
        'The service did not return a new editable CV. Your source CV has not been replaced in this page.')
        pending = undefined
        return result
      } catch (error) {
        const status = error.status ?? error.response?.status
        if (status >= 400 && status < 500) pending = undefined
        throw error
      }
    },
  }
}
