import { requireAdmin } from './admin-users.js'

const protocol = Object.freeze({
  version: 'P-2026.01', name: 'Frozen assessment protocol', status: 'Frozen',
  schema: 'FS-32-v1', preprocessing: 'PP-2026.01', cutoff: 0.5,
  description: 'Synthetic protocol metadata shared by the Neutral ML and Biased ML demo packages.',
  modelTypes: Object.freeze(['Neutral ML', 'Biased ML']),
  matching: 'Quantise matches to seven decimal places. Multiply by the fixed weights, round each contribution to seven places using HALF_UP, then sum and compare with the stored rule threshold. Display rounding is never used for scoring.',
  labels: 'Unavailable. The complete frozen offline label-generation configuration is not supplied in this frontend fixture.',
  inputs: '32 inputs: 16 professional values and 16 research values. Neutral ML uses a zero research mask; Biased ML uses permitted encoded research categories.',
  encoding: 'Unavailable. Complete feature ordering, category mappings and preprocessing parameters are not supplied in this frontend fixture.',
  threshold: '0.5 is the synthetic ML cutoff recorded in both demo model packages. It does not specify the Objective rule threshold or an offline label rule.',
  frozenAt: null,
})

export function protocolProjection(row) {
  const { version, name, status, schema, preprocessing, cutoff, description, matching, labels, inputs, encoding, threshold, frozenAt } = row
  return { version, name, status, schema, preprocessing, cutoff, description, matching, labels, inputs, encoding, threshold, frozenAt, modelTypes: [...row.modelTypes] }
}
export function listFrozenProtocols(user) { requireAdmin(user); return [protocolProjection(protocol)] }
export function readFrozenProtocol(version, user) {
  requireAdmin(user)
  if (version !== protocol.version) throw new Error('The frozen protocol was not found.')
  return protocolProjection(protocol)
}
export function filterFrozenProtocols(rows, { keyword = '', type = '', status = '' } = {}) {
  const query = keyword.trim().toLowerCase()
  return rows.filter(row => (!query || [row.version, row.name, row.description, row.schema].some(value => value.toLowerCase().includes(query)))
    && (!type || row.modelTypes.includes(type)) && (!status || row.status === status))
}
