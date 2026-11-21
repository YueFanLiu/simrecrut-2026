// CV library names, library reuse in simulations and job work locations use the Java contracts.
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

test('library CV, rename and work-location adapters send only the documented fields', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const jobs = await server.ssrLoadModule('/src/api/jobs.js')
    const cases = await server.ssrLoadModule('/src/api/cases.js')
    const resumes = await server.ssrLoadModule('/src/api/resumes.js')
    const seen = []
    request.defaults.adapter = async config => {
      const { pathname } = new URL(config.url, 'http://local')
      const body = config.data ? JSON.parse(config.data) : undefined
      seen.push({ method: config.method, pathname, body, key: config.headers.get('Idempotency-Key') })
      const reply = data => ({ config, status: 200, data: { code: 0, data } })
      if (pathname === '/api/v1/candidate/jobs') return reply({ items: [{ jobId: 'JOB', workLocation: 'Paris, France' }, { jobId: 'OTHER' }], total: 2 })
      if (pathname === '/api/v1/candidate/jobs/JOB') return reply({ jobId: 'JOB', workLocation: 'Remote' })
      return reply({ caseId: 'CASE', processingStatus: 'REVIEW_REQUIRED', ...body })
    }
    const listed = await jobs.publishedJobs()
    assert.deepEqual(listed.map(job => job.location), ['Paris, France', ''])
    assert.equal((await jobs.jobDetail('JOB')).location, 'Remote')

    await jobs.createJob({ title: ' Engineer ', description: 'Build Java services.', location: ' Lyon, France ' }, 'job-key-0123456789')
    assert.deepEqual(seen.at(-1).body, { title: 'Engineer', description: 'Build Java services.', workLocation: 'Lyon, France' })
    await jobs.createJob({ title: 'Engineer', description: 'Build Java services.', location: '  ' }, 'job-key-9876543210')
    assert.deepEqual(seen.at(-1).body, { title: 'Engineer', description: 'Build Java services.' })

    const resumeId = 'a'.repeat(32)
    await cases.useLibraryResume('CASE', resumeId, 'library-key-0123456789')
    assert.deepEqual(seen.at(-1), { method: 'post', pathname: '/api/v1/candidate/cases/CASE/resume',
      body: { resumeId }, key: 'library-key-0123456789' })

    await resumes.resumeAction(resumeId, 'rename', { displayName: 'CV for IT' }, { operationKey: 'rename-key-0123456789', silent: true })
    assert.deepEqual(seen.at(-1), { method: 'post', pathname: `/api/v1/resumes/${resumeId}/rename`,
      body: { displayName: 'CV for IT' }, key: 'rename-key-0123456789' })
  } finally { await server.close() }
})
