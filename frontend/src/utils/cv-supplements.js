// Human-readable compatibility records for the current `professional.other` API.
// Keep free CV content out of the closed assessment-code catalogues.
const SKILL = 'Additional skill: '
const SECTION = 'CV section: '
export const cvGroups = ['personal', 'skills', 'experience', 'education', 'languages', 'projects', 'other']
export const cvGroupLabels = { personal: 'Personal information', skills: 'Skills', experience: 'Experience',
  education: 'Education', languages: 'Languages', projects: 'Projects', other: 'Other information' }
export const userEvidence = () => ({ text: '', page: null, source: 'USER_SUPPLIED' })
export const readableCode = value => String(value ?? '').includes('_') || /^[A-Z\d]+$/.test(String(value))
  ? String(value).split('_').map(word => word.length <= 3 ? word : word[0] + word.slice(1).toLowerCase()).join(' ')
  : String(value ?? '')

export function readSupplement(row) {
  if (row?.categoryCode !== 'OTHER' || row?.evidence?.source !== 'USER_SUPPLIED') return null
  const value = row.value || ''
  if (value.startsWith(SKILL) && !value.includes('\n')) return { kind: 'skill', name: value.slice(SKILL.length) }
  if (value.startsWith(SECTION) && value.includes('\n')) {
    const split = value.indexOf('\n')
    return { kind: 'section', name: value.slice(SECTION.length, split), text: value.slice(split + 1) }
  }
  return null
}

export function makeSkill(name) {
  const clean = name.trim()
  if (!clean || clean.length > 100 || /[\r\n]/.test(clean)) throw new Error('Enter a skill of 1–100 characters.')
  return { categoryCode: 'OTHER', value: SKILL + clean, evidence: userEvidence() }
}

export function makeSection(name, text = '') {
  const clean = name.trim()
  if (!clean || clean.length > 60 || /[\r\n]/.test(clean)) throw new Error('Enter a section name of 1–60 characters.')
  const value = SECTION + clean + '\n' + text
  if (value.length > 2000) throw new Error('Keep this section within 2,000 characters, including its heading.')
  return { categoryCode: 'OTHER', value, evidence: userEvidence() }
}

export const sectionTextLimit = name => 2000 - SECTION.length - name.length - 1
export function skillItems(professional = {}) {
  const extracted = (professional.skills || []).map((row, index) => ({
    id: `skills:${index}`, name: readableCode(row.skillCode), evidence: row.evidence,
    source: row.evidence?.source === 'USER_SUPPLIED' ? 'Your addition or correction' : 'From CV',
  }))
  const extra = (professional.other || []).flatMap((row, index) => {
    const item = readSupplement(row)
    return item?.kind === 'skill' ? [{ id: `other:${index}`, name: item.name, evidence: row.evidence, source: 'Your addition or correction' }] : []
  })
  return [...extracted, ...extra]
}

export function putSkill(professional, name, id) {
  const next = structuredClone(professional)
  next.other ||= []
  const skill = makeSkill(name)
  const key = skill.value.slice(SKILL.length).normalize('NFKC').toLowerCase()
  const current = skillItems(next).find(item => item.id === id)
  if (current && current.name.normalize('NFKC').toLowerCase() === key) return next
  if (skillItems(next).some(item => item.id !== id && item.name.normalize('NFKC').toLowerCase() === key)) {
    throw new Error('This skill is already in your CV.')
  }
  if (id) {
    const [group, index] = id.split(':')
    if (!current || !['skills', 'other'].includes(group) || !next[group]?.[Number(index)]) throw new Error('Reload the latest CV facts before editing.')
    next[group].splice(Number(index), 1)
  }
  if (next.other.length >= 100) throw new Error('You can keep up to 100 additional CV items.')
  next.other.push(skill)
  return next
}

export function removeSkill(professional, id) {
  const next = structuredClone(professional)
  const [group, index] = id.split(':')
  if (skillItems(next).some(item => item.id === id)) next[group]?.splice(Number(index), 1)
  return next
}
