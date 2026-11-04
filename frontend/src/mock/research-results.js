// Deterministic teaching examples, unrelated to real CVs or trained models.
export function createResearchResults(experiment) {
  return experiment.subjectIds.flatMap((subjectId, index) => {
    const variants = experiment.type === 'Single-factor paired' ? ['baseline', 'variant'] : ['baseline']
    return variants.map(variant => ({ subjectId, variant, label: index % 2 === 0 ? 'Accept' : 'Reject',
      objective: index % 2 === 0 ? 80 : 40, neutral: index % 2 === 0 ? .8 : .4,
      biased: variant === 'variant' ? .3 : index % 2 === 0 ? .8 : .4 }))
  })
}

export function summarizeResearchResults(rows, method) {
  const cutoff = method === 'objective' ? 50 : .5
  const available = rows.filter(row => Number.isFinite(row[method]) && ['Accept', 'Reject'].includes(row.label))
  let tp = 0, tn = 0, fp = 0, fn = 0, flips = 0, pairs = 0
  for (const row of available) {
    const accepted = row[method] >= cutoff, positive = row.label === 'Accept'
    if (accepted && positive) tp++
    else if (accepted) fp++
    else if (positive) fn++
    else tn++
    if (row.variant === 'baseline') {
      const variant = available.find(item => item.subjectId === row.subjectId && item.variant === 'variant')
      if (variant) { pairs++; if (accepted !== (variant[method] >= cutoff)) flips++ }
    }
  }
  const ratio = (a, b) => b ? a / b : null
  return { sampleCount: available.length, tp, tn, fp, fn, pairs, flips, flipRate: ratio(flips, pairs),
    accuracy: ratio(tp + tn, available.length), precision: ratio(tp, tp + fp), recall: ratio(tp, tp + fn),
    f1: ratio(2 * tp, 2 * tp + fp + fn) }
}
