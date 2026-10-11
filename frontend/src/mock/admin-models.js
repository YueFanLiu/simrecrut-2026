import { requireAdmin } from './admin-users.js'
export const modelTypes = ['Neutral ML', 'Biased ML']
export const modelStatuses = ['TESTED', 'APPROVED', 'ACTIVE', 'RETIRED']
const compatibility = { schema: 'FS-32-v1', preprocessing: 'PP-2026.01', protocol: 'P-2026.01' }
function metadata(id, type, version, status, day) {
  return { id, type, version, status, description: `${type} synthetic offline package ${version}.`,
    architecture: '32 → 16 → 8 → 1', parameters: 673, schema: compatibility.schema,
    preprocessing: compatibility.preprocessing, protocol: compatibility.protocol, cutoff: .5,
    inputMode: type === 'Neutral ML' ? '16 professional inputs + 16 zero research values' : '16 professional inputs + 16 permitted encoded research values',
    createdAt: `2026-10-0${day}T10:00:00.000Z`, createdBy: 'Demo Admin User', revision: 0,
    manifest: `DEMO-${id}`, checksum: 'a'.repeat(64), trainingRun: `DEMO-RUN-${id}`,
    evidence: { integrity: true, validation: true, finalTest: true, validationReport: `DEMO-VAL-${id}`, finalTestReport: `DEMO-TEST-${id}` },
    demoReady: true, approvedAt: ['APPROVED', 'ACTIVE', 'RETIRED'].includes(status) ? '2026-10-08T10:00:00.000Z' : null,
    activatedAt: ['ACTIVE', 'RETIRED'].includes(status) ? '2026-10-08T11:00:00.000Z' : null,
    retiredAt: status === 'RETIRED' ? '2026-10-08T12:00:00.000Z' : null }
}
export function createAdminModels() {
  return { models: [metadata('N-2026.01', 'Neutral ML', '1.0.0', 'ACTIVE', 1), metadata('N-2026.02', 'Neutral ML', '1.1.0', 'APPROVED', 2),
    metadata('N-2026.03', 'Neutral ML', '1.2.0', 'TESTED', 3), metadata('B-2026.01', 'Biased ML', '1.0.0', 'ACTIVE', 1),
    metadata('B-2026.02', 'Biased ML', '1.1.0', 'APPROVED', 2), metadata('B-2026.03', 'Biased ML', '1.2.0', 'TESTED', 3),
    metadata('N-2025.12', 'Neutral ML', '0.9.0', 'RETIRED', 1)], slots: { 'Neutral ML': 'N-2026.01', 'Biased ML': 'B-2026.01' } }
}
export function offlinePackages() {
  return [metadata('N-2026.04', 'Neutral ML', '1.3.0', 'TESTED', 8), metadata('B-2026.04', 'Biased ML', '1.3.0', 'TESTED', 8)]
}
export function checkModelPackage(row) {
  if (!modelTypes.includes(row.type) || row.schema !== compatibility.schema || row.preprocessing !== compatibility.preprocessing || row.protocol !== compatibility.protocol
    || row.architecture !== '32 → 16 → 8 → 1' || row.parameters !== 673 || !Number.isFinite(row.cutoff) || row.cutoff <= 0 || row.cutoff >= 1) return 'The package is incompatible with the frozen demo schema, protocol or cutoff.'
  if (!row.manifest || !/^[a-f0-9]{64}$/.test(row.checksum || '') || !row.trainingRun || !row.evidence?.integrity || !row.evidence.validation || !row.evidence.finalTest || !row.evidence.validationReport || !row.evidence.finalTestReport) return 'Recorded package checks or evaluation report references are incomplete.'
  return ''
}
export function modelProjection(row) {
  const { id, type, version, status, description, architecture, parameters, schema, preprocessing, protocol, cutoff, inputMode, createdAt, createdBy, revision, manifest, checksum, trainingRun, approvedAt, activatedAt, retiredAt } = row
  return { id, type, version, status, description, architecture, parameters, schema, preprocessing, protocol, cutoff, inputMode, createdAt, createdBy, revision, manifest, checksum, trainingRun, approvedAt, activatedAt, retiredAt,
    checks: { integrity: row.evidence?.integrity === true, validation: row.evidence?.validation === true, finalTest: row.evidence?.finalTest === true,
      validationReport: row.evidence?.validationReport || 'Unavailable', finalTestReport: row.evidence?.finalTestReport || 'Unavailable' },
    readiness: row.demoReady ? 'Ready in the synthetic fixture' : 'Not ready in the synthetic fixture',
    evaluation: 'Unavailable. No measured evaluation values or sample denominators are retained in this frontend demo.' }
}
export function filterAdminModels(rows, { keyword = '', type = '', status = '' } = {}) {
  const query = keyword.trim().toLowerCase()
  return rows.filter(row => (!query || [row.id, row.type, row.version, row.description].some(value => value.toLowerCase().includes(query))) && (!type || row.type === type) && (!status || row.status === status))
}
export function checkRegistry(models, slots) {
  for (const type of modelTypes) {
    const active = models.filter(row => row.type === type && row.status === 'ACTIVE')
    if (active.length !== 1 || active[0].id !== slots[type]) throw new Error('The active slot configuration is inconsistent. Refresh the demonstration before releasing a model.')
  }
}
export function projectModels(models, user) { requireAdmin(user); return models.map(modelProjection) }
