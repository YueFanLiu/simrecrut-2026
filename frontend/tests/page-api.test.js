// Exercise the real transport adapter without writing to a shared database.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('connected page adapters use the Java contracts and preserve access errors', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const admin = await server.ssrLoadModule('/src/api/administration.js')
    const jobs = await server.ssrLoadModule('/src/api/jobs.js')
    const cases = await server.ssrLoadModule('/src/api/cases.js')
    const reviews = await server.ssrLoadModule('/src/api/reviews.js')
    const seen = []
    const professional = { skills: [{ skillCode: 'JAVA' }], experience: [], education: [], languages: [], projects: [] }
    const user = { userId: 'USER', username: 'candidate', roles: ['CANDIDATE', 'HR'], revision: 3 }
    const professionalCase = { caseId: 'CASE', professional, job: { title: 'Engineer', requirements: [] } }
    request.defaults.adapter = async config => {
      seen.push(config)
      const url = new URL(config.url, 'http://local')
      const reply = data => ({ config, status: 200, data: { code: 0, data } })
      const { pathname } = url
      if (pathname === '/api/v1/admin/users' && config.method === 'get') {
        const page = Number(url.searchParams.get('page'))
        return reply({ items: [{ ...user, userId: String(page) }], total: 2, page, pageSize: 100 })
      }
      if (pathname.startsWith('/api/v1/admin/')) return reply(user)
      if (pathname === '/api/v1/candidate/jobs') return reply({ items: [{ jobVersionId: 'VERSION' }], total: 1 })
      if (pathname === '/api/v1/candidate/cases/CASE/status') return reply({ processingStatus: 'EXTRACTING' })
      if (pathname === '/api/v1/candidate/cases/CASE/results') return reply({
        job: { title: 'Engineer' }, objective: { displayScore: 0 }, neutralMl: null, biasedMl: null,
        human: { standard: { status: 'Pending' } }, privacy: { purgeAt: '2026-11-10T14:00:00', canDeleteNow: true },
      })
      if (pathname.startsWith('/api/v1/candidate/cases')) return reply({ caseId: 'CASE', revision: 4 })
      if (pathname === '/api/v1/hr/cases/CASE') return reply({
        ...professionalCase, matching: { values: null }, weights: [1, 0, 0, 0, 0], contributions: null,
        objective: null, neutralMl: null, biasedMl: null, human: [], versions: {},
      })
      if (pathname === '/api/v1/reviewer/tasks/REVIEW') return reply({
        ...professionalCase, status: 'SUBMITTED', condition: 'STANDARD', purpose: 'PRIMARY',
        rubric: { guidance: 'Review the public requirements.' }, submissionVisible: false,
      })
      if (pathname.startsWith('/api/v1/reviewer/') || pathname.startsWith('/api/v1/research/cases/')) return reply({})
      if (pathname === '/monitor/operlog/list') return { config, status: 200,
        data: { code: 200, total: 1, rows: [{ operId: 7, operName: 'admin', status: 0,
          title: 'Users', operIp: 'private', operParam: 'private' }] } }
      const error = new Error('Missing resource')
      error.config = config
      error.response = { status: 404, data: pathname === '/api/v1/hr/cases/UNKNOWN' ? { message: 'Case is unavailable.' } : {} }
      throw error
    }

    const last = () => seen.at(-1)
    const payload = () => JSON.parse(last().data)
    assert.deepEqual((await admin.users()).map(row => row.userId), ['1', '2'])
    assert.deepEqual(seen.map(config => new URL(config.url, 'http://local').searchParams.get('page')), ['1', '2'])
    await admin.createUser({ ...user, email: 'test@example.test' })
    assert.deepEqual(payload(), { username: 'candidate', email: 'test@example.test', roles: ['CANDIDATE', 'HR'] })
    await admin.assignRole(user.userId, user.revision, ['HR', 'RESEARCHER'])
    assert.equal(last().url, '/api/v1/admin/users/USER/roles')
    assert.deepEqual(payload(), { roles: ['HR', 'RESEARCHER'], revision: 3 })
    await admin.changeUserStatus('USER', 'DISABLED', 4)
    assert.deepEqual(payload(), { status: 'DISABLED', revision: 4 })
    await admin.rolePermissions({ roleCode: 'HR', revision: 2, permissions: ['simrecrut:review:list'] })
    assert.equal(last().url, '/api/v1/admin/roles/HR/permissions')
    assert.deepEqual(payload(), { permissions: ['simrecrut:review:list'], revision: 2 })

    assert.equal((await jobs.publishedJobs())[0].jobVersionId, 'VERSION')
    const key = 'one-logical-operation'
    await cases.createCase('VERSION', key)
    assert.equal(last().headers.get('Idempotency-Key'), key)
    assert.deepEqual(payload(), { jobVersionId: 'VERSION' })
    await cases.uploadCaseCv('CASE', new File(['test PDF'], 'cv.pdf'))
    assert.equal(last().url, '/api/v1/candidate/cases/CASE/cv')
    assert.equal(last().data.get('file').name, 'cv.pdf')
    assert.equal((await cases.caseDetail('CASE')).processingStatus, 'EXTRACTING')
    await cases.saveCaseDraft('CASE', { professional, revision: 3, confirmedAbsent: ['PROJECTS'] })
    assert.equal(last().method, 'put')
    assert.deepEqual(payload(), { professional, revision: 3, confirmedAbsent: ['PROJECTS'] })
    await cases.saveCaseResearch('CASE', { attributes: [{ type: 'GENDER', value: 'UNKNOWN' }], revision: 4 })
    assert.equal(last().url, '/api/v1/candidate/cases/CASE/research-attributes')
    assert.equal(payload().revision, 4)
    const partial = await cases.caseResults('CASE')
    assert.equal(partial.objective.displayScore, 0)
    assert.equal(partial.neutral, null)
    assert.equal(partial.expiresAt, '2026-11-10T14:00:00Z')
    const hrCase = await cases.hrCase('CASE')
    assert.equal(hrCase.matching, null)
    assert.equal(hrCase.objective.status, 'Unavailable')
    await assert.rejects(cases.hrCase('UNKNOWN'), /Case is unavailable/)

    const review = await reviews.reviewTask('REVIEW')
    assert.equal(review.status, 'Submitted')
    assert.equal(review.submission, null)
    assert.equal(review.controlled, null)
    await reviews.submitReview('REVIEW', { decision: 'Accept', reason: ' Relevant facts ' }, key)
    assert.equal(last().url, '/api/v1/reviewer/tasks/REVIEW/submit')
    assert.deepEqual(payload(), { decision: 'Accept', reason: 'Relevant facts' })
    await reviews.assignPrimaryReviews('CASE', [{ condition: 'STANDARD', reviewerId: 'USER' }], key)
    assert.equal(last().url, '/api/v1/research/cases/CASE/reviews')
    assert.deepEqual(payload(), { assignments: [{ condition: 'STANDARD', reviewerId: 'USER' }] })

    const events = await admin.audit({ actor: 'admin', outcome: 'SUCCESS' }, 2, 5)
    assert.equal(events.items[0].outcome, 'SUCCESS')
    assert.equal(events.items[0].operIp, undefined)
    assert.equal(events.items[0].operParam, undefined)
    await assert.rejects(admin.audit({ actionCode: 'JOB_PUBLISHED' }, 1, 5), /business audit API/)
    assert.ok(seen.every(config => config.silent === true))
    request.defaults.adapter = async config => ({ config, status: 200,
      data: { code: 500, msg: 'No static resource api/v1/hr/jobs.' } })
    await assert.rejects(jobs.managedJobs(), /has not implemented/)
  } finally { await server.close() }
})
