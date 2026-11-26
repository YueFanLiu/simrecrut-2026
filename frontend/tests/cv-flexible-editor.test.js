import { test } from 'node:test'
import assert from 'node:assert/strict'
import { makeSection, makeSkill, readSupplement, putSkill, removeSkill, skillItems, sectionTextLimit } from '../src/utils/cv-supplements.js'
import { demoCvFacts, applyDemoCvFacts } from '../src/utils/demo-cv-editor.js'
import { copyProfessionalDraft, validateReviewDraft } from '../src/utils/review-validation.js'
import { createReviewDraft } from '../src/mock/review-draft.js'

const original = () => ({ personal: [{ fieldCode: 'EMAIL', value: 'candidate@example.test' }],
  skills: [{ skillCode: 'JAVA', evidence: { source: 'EXTRACTED', page: 1, text: 'Java developer' } }],
  experience: [], education: [], languages: [], projects: [],
  other: [{ categoryCode: 'AWARD', value: 'Student award', evidence: { source: 'EXTRACTED' } }] })

test('free skills survive a JSON save/reload without becoming unsupported assessment codes', () => {
  const before = original()
  const next = putSkill(before, ' \u7528\u6237\u8bbf\u8c08 / Figma ')
  assert.deepEqual(next.skills, before.skills)
  assert.equal(before.other.length, 1)
  const reloaded = JSON.parse(JSON.stringify(next))
  assert.equal(skillItems(reloaded)[1].name, '\u7528\u6237\u8bbf\u8c08 / Figma')
  assert.equal(skillItems(reloaded)[1].source, 'Your addition or correction')
  assert.deepEqual(Object.keys(reloaded.other[1]).sort(), ['categoryCode', 'evidence', 'value'])
  assert.equal(reloaded.other[1].evidence.source, 'USER_SUPPLIED')
  assert.deepEqual(reloaded.personal, before.personal)
  assert.deepEqual(reloaded.other[0], before.other[0])
})

test('editing an assessed skill removes its old claim and retains the replacement as free CV information', () => {
  const next = putSkill(original(), 'Product discovery', 'skills:0')
  assert.deepEqual(next.skills, [])
  assert.deepEqual(skillItems(next).map(item => item.name), ['Product discovery'])
  assert.equal(next.other[0].categoryCode, 'AWARD')
  assert.equal(removeSkill(next, 'other:1').other.length, 1)
  assert.throws(() => putSkill(original(), 'java'), /already/)
  assert.deepEqual(putSkill(original(), 'Java', 'skills:0'), original())
  assert.throws(() => putSkill(original(), 'Changed award', 'other:0'), /Reload/)
  assert.deepEqual(removeSkill(original(), 'other:0'), original())
  assert.throws(() => makeSkill('  '), /1–100/)
  assert.throws(() => makeSkill('one\ntwo'), /1–100/)
})

test('custom headings, multiline content and extracted other facts remain distinct after reload', () => {
  const row = makeSection('\u5fd7\u613f\u7ecf\u5386', '\u8f85\u5bfc\u5b66\u751f\n\u7ec4\u7ec7\u8fce\u65b0\u6d3b\u52a8')
  const decoded = readSupplement(JSON.parse(JSON.stringify(row)))
  assert.deepEqual(decoded, { kind: 'section', name: '\u5fd7\u613f\u7ecf\u5386', text: '\u8f85\u5bfc\u5b66\u751f\n\u7ec4\u7ec7\u8fce\u65b0\u6d3b\u52a8' })
  assert.equal(readSupplement({ ...row, evidence: { source: 'EXTRACTED' } }), null)
  assert.equal(readSupplement({ ...row, categoryCode: 'VOLUNTEERING' }), null)
  assert.equal(makeSection('Portfolio', 'x'.repeat(sectionTextLimit('Portfolio'))).value.length, 2000)
  assert.throws(() => makeSection('Portfolio', 'x'.repeat(sectionTextLimit('Portfolio') + 1)), /2,000/)
  const full = original(); full.other = Array.from({ length: 100 }, () => makeSection('Example'))
  assert.throws(() => putSkill(full, 'New skill'), /100/)
})

test('demo edits preserve per-field validation, provenance and supplemental content through its save projection', () => {
  const draft = createReviewDraft('missing')
  const oldSkills = structuredClone(draft.professional.skills)
  const oldErrors = validateReviewDraft(draft, true)
  const edited = putSkill(demoCvFacts(draft), 'Event planning')
  edited.other.push(makeSection('Portfolio', 'A public design portfolio'))
  applyDemoCvFacts(draft, edited)
  const saved = copyProfessionalDraft(draft)
  assert.deepEqual(saved.professional.skills, oldSkills)
  assert.deepEqual(validateReviewDraft(saved, true), oldErrors)
  assert.equal(skillItems(demoCvFacts(saved)).at(-1).name, 'Event planning')
  assert.equal(readSupplement(saved.supplements[1]).text, 'A public design portfolio')
  saved.supplements[0].value = 'Changed copy'
  assert.notEqual(saved.supplements[0].value, draft.supplements[0].value)
})
