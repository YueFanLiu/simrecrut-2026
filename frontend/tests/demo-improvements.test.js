import test from 'node:test'
import assert from 'node:assert/strict'
import { publishDemoJob, readDemoPublications } from '../src/mock/shared-demo.js'
import { loadJobDetail } from '../src/mock/job-details.js'
import { projectCaseCheckpoints } from '../src/utils/demo-session.js'
import { createResearchResults, summarizeResearchResults } from '../src/mock/research-results.js'
import { createApp } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { installDemoCheckpoints } from '../src/store/demo-checkpoints.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { useCasesStore } from '../src/store/modules/cases.js'
import { readDemoSession, saveDemoSession } from '../src/utils/demo-session.js'
import { shareDemoModels } from '../src/mock/shared-demo.js'
import { useResearchModuleStore } from '../src/store/modules/research-module.js'

test('HR publications reach the public detail without assessment weights or private requirements', async () => {
  publishDemoJob({ jobId: 'demo-job-public-test' }, { title: 'Shared Test Job', description: 'Public description',
    department: 'Engineering', location: 'Remote', employmentType: 'Full-time', experienceLevel: 'Junior',
    version: '0.1', createdAt: new Date().toISOString(), percentages: ['100'],
    requirements: [{ publicVisible: true, text: 'Public skill' }, { publicVisible: false, text: 'Hidden requirement' }] })
  const result = await loadJobDetail('demo-job-public-test')
  assert.equal(result.title, 'Shared Test Job')
  assert.deepEqual(result.requirements.qualifications, ['Public skill'])
  assert.doesNotMatch(JSON.stringify(readDemoPublications()), /Hidden requirement|percentages/)
})

test('reload checkpoints exclude files, facts, evidence, research, reasons and unconfirmed cases', () => {
  const entry = { caseId: 'demo-case-1', owner: 'demo', confirmedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 86400000).toISOString(), processingStatus: 'CONFIRMED',
    jobSummary: { title: 'Public Job', privateWeight: 'SECRET' }, file: 'SECRET',
    reviewDraft: { evidence: 'SECRET' }, confirmedProfessional: 'SECRET', researchAttributes: 'SECRET', results: 'SECRET' }
  const output = projectCaseCheckpoints({ cases: { confirmed: entry, draft: { ...entry, caseId: 'draft', confirmedAt: null } }, nextCaseNumber: 2 })
  assert.equal(Object.keys(output.cases).length, 1)
  assert.doesNotMatch(JSON.stringify(output), /SECRET|privateWeight|reviewDraft|confirmedProfessional|researchAttributes/)
  assert.equal(projectCaseCheckpoints({ cases: { removed: { ...entry, processingStatus: 'PURGING' } }, nextCaseNumber: 2 }).cases['demo-case-1'], undefined)
})

test('synthetic comparison counts paired flips within each method and reports undefined ratios', () => {
  const rows = createResearchResults({ type: 'Single-factor paired', subjectIds: ['one', 'two'] })
  const neutral = summarizeResearchResults(rows, 'neutral'), biased = summarizeResearchResults(rows, 'biased')
  assert.equal(neutral.sampleCount, 4)
  assert.equal(neutral.pairs, 2)
  assert.equal(neutral.flips, 0)
  assert.equal(biased.flips, 1)
  assert.equal(biased.flipRate, .5)
  assert.equal(biased.tp + biased.tn + biased.fp + biased.fn, 4)
  assert.equal(summarizeResearchResults([], 'neutral').accuracy, null)
  assert.equal(summarizeResearchResults([], 'neutral').flipRate, null)
})

test('built-in demo reload resumes unfinished stages without persisting private facts; logout removes checkpoints', async () => {
  const data = new Map()
  globalThis.sessionStorage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
  const setup = () => {
    const pinia = createPinia(); pinia.use(installDemoCheckpoints); createApp({}).use(pinia); setActivePinia(pinia)
    return { auth: useAuthStore(), cases: useCasesStore() }
  }
  try {
    saveDemoSession({ username: 'demo', password: 'SECRET' })
    let { auth, cases } = setup()
    assert.equal(auth.demoUser.role, 'Candidate')
    cases.cases['demo-case-1'] = { caseId: 'demo-case-1', owner: 'demo', jobId: 'software-engineer',
      jobVersionId: 'software-engineer-v1.2', jobTitle: 'Software Engineer', version: '1.2',
      createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 86400000).toISOString(),
      confirmedAt: new Date().toISOString(), processingStatus: 'AUTOMATED_RUNNING', operation: 0,
      jobSummary: { jobId: 'software-engineer', version: '1.2' }, confirmedProfessional: 'SECRET',
      assessment: { methods: [{ name: 'Objective', status: 'Complete' }, { name: 'Neutral ML', status: 'Running' },
        { name: 'Biased ML', status: 'Waiting' }], retryCount: 0, updatedAt: new Date().toISOString(), events: [] } }
    assert.doesNotMatch([...data.values()].join(''), /SECRET/)
    ;({ auth, cases } = setup())
    const restored = cases.getOwnedCase('demo-case-1')
    assert.equal(restored.processingStatus, 'FAILED_RETRYABLE')
    assert.equal(restored.confirmedProfessional, null)
    await cases.runSimulation('demo-case-1', 'normal', true)
    assert.equal(restored.processingStatus, 'AUTOMATED_COMPLETE')
    assert.equal(restored.assessment.methods[0].status, 'Complete')
    ;({ auth, cases } = setup())
    assert.equal((await cases.loadResults('demo-case-1')).objective.displayScore, 78)
    auth.logout()
    assert.equal(readDemoSession(), null)
    assert.equal(Object.keys(JSON.parse(data.get('simrecrut-case-checkpoints-v1')).cases).length, 0)
  } finally { delete globalThis.sessionStorage }
})

test('released model metadata supplies compatible active Research choices without package internals', () => {
  setActivePinia(createPinia())
  shareDemoModels([{ id: 'N-new', type: 'Neutral ML', version: '2.0', status: 'ACTIVE', schema: 'FS-32-v1', protocol: 'P-2026.01', inputMode: 'masked', cutoff: .5, checksum: 'SECRET' }])
  const models = useResearchModuleStore().availableModels
  assert.equal(models[0].id, 'N-new')
  assert.equal(models[0].status, 'Active')
  assert.doesNotMatch(JSON.stringify(models), /SECRET|checksum/)
})
