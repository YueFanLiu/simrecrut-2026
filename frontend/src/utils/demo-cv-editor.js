// The shared editor uses the connected shape. Preserve the demo's per-field
// provenance and validation status instead of turning its fixtures into API data.
export function demoCvFacts(draft) {
  return {
    skills: draft.professional.skills.items.map(item => ({ skillCode: item.name.value, demoId: item.id,
      evidence: { text: item.name.evidenceExcerpt, page: null, source: item.name.status === 'USER_SUPPLIED' ? 'USER_SUPPLIED' : 'EXTRACTED' } })),
    other: draft.supplements || [],
  }
}

export function applyDemoCvFacts(draft, professional) {
  const previous = draft.professional.skills.items
  draft.professional.skills.items = professional.skills.map(row => {
    const original = previous.find(item => item.id === row.demoId)
    if (original) return original
    return { id: crypto.randomUUID(), name: { value: row.skillCode, status: 'USER_SUPPLIED', evidenceExcerpt: '', evidenceLocation: '' } }
  })
  draft.supplements = professional.other
}
