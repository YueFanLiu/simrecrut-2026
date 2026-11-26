import test from 'node:test'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'
import { createServer } from 'vite'
import { originalPdfStatus, mayRetryLibraryPdfCleanup, withOriginalPdfMetadata } from '../src/utils/original-pdf-status.js'

test('only an explicit library absence flag confirms no original is stored, including unconfirmed copies', () => {
  for (const confirmed of [0, 1, false, true]) {
    assert.equal(originalPdfStatus({ confirmed, originalAvailable: false }).code, 'absent')
  }
  for (const originalAvailable of [undefined, null, '', 0, 'false', 'true']) {
    const row = { confirmed: 1, status: 'CONFIRMED', revision: 2, cloud_revision: 2, originalAvailable }
    assert.equal(originalPdfStatus(row).code, 'unknown')
  }
  assert.equal(originalPdfStatus(null).code, 'unknown')
})

test('temporary files distinguish pre-review storage from pending cleanup; legacy originals never imply automatic deletion', () => {
  const row = { originalAvailable: true, temporary_original: 1, confirmed: 0 }
  assert.equal(originalPdfStatus(row).code, 'temporary')
  assert.equal(originalPdfStatus({ ...row, confirmed: 1 }).pending, true)
  for (const temporary_original of [0, undefined, null, '1']) {
    assert.equal(originalPdfStatus({ ...row, confirmed: 1, temporary_original }).code, 'stored')
  }
})

test('case cleanup depends on an explicit cleanup status, never on confirmation or completed predictions', () => {
  const row = { confirmedAt: '2026-11-25T15:00:00Z', processingStatus: 'AUTOMATED_COMPLETE' }
  for (const cleanupStatus of [undefined, null, '', 'NONE', 'UNRECOGNIZED']) {
    assert.equal(originalPdfStatus({ ...row, cleanupStatus }, 'case').code, 'unknown')
  }
  assert.equal(originalPdfStatus({ ...row, cleanupStatus: 'PENDING' }, 'case').pending, true)
  assert.equal(originalPdfStatus({ ...row, cleanupStatus: 'FAILED' }, 'case').code, 'failed')
  assert.equal(originalPdfStatus({ ...row, cleanupStatus: 'COMPLETE' }, 'case').code, 'absent')
  assert.equal(originalPdfStatus({ cleanupStatus: 'NONE', confirmedAt: null }, 'case').code, 'planned')
  assert.equal(originalPdfStatus(row, 'demo').code, 'demo')
})

test('manual library cleanup is offered only for a confirmed temporary original with its current revision published', () => {
  const row = { originalAvailable: true, temporary_original: 1, confirmed: 1, revision: 3, cloud_revision: 3 }
  assert.equal(mayRetryLibraryPdfCleanup(row), true)
  for (const change of [{ confirmed: 0 }, { originalAvailable: false }, { originalAvailable: undefined },
    { temporary_original: 0 }, { temporary_original: undefined }, { cloud_revision: 2 }, { revision: 0, cloud_revision: 0 },
    { revision: '3', cloud_revision: '3' }]) assert.equal(mayRetryLibraryPdfCleanup({ ...row, ...change }), false)
  assert.equal(mayRetryLibraryPdfCleanup(null), false)
})

test('metadata refresh preserves unsaved facts and review state and does not retain absent fields as cached proof', () => {
  const current = { id: 'CV', confirmed: 0, revision: 3, originalAvailable: true, temporary_original: 1,
    draft: { professional: { skills: [{ skillCode: 'JAVA' }] } } }
  const before = structuredClone(current)
  const refreshed = withOriginalPdfMetadata(current, { originalAvailable: false, confirmed: 1, revision: 4,
    draft: { professional: {} } })
  assert.deepEqual(current, before)
  assert.equal(refreshed.draft, current.draft)
  assert.equal(refreshed.confirmed, 0)
  assert.equal(refreshed.revision, 3)
  assert.equal(refreshed.originalAvailable, false)
  assert.equal(Object.hasOwn(refreshed, 'temporary_original'), false)
  assert.equal(originalPdfStatus(withOriginalPdfMetadata(refreshed, {})).code, 'unknown')
})

test('the shared component renders consistent pending and unknown states in English, Chinese and French', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const i18n = await server.ssrLoadModule('/src/i18n/index.js')
    const component = (await server.ssrLoadModule('/src/components/simrecrut/OriginalPdfStatus.vue')).default
    for (const locale of ['en', 'zh', 'fr']) {
      i18n.setLanguage(locale)
      for (const props of [{ record: { originalAvailable: true, confirmed: 1, temporary_original: 1 } },
        { record: {}, source: 'case' }, { source: 'demo' }]) {
        const app = createSSRApp(component, props)
        app.config.globalProperties.$t = i18n.t
        const html = await renderToString(app)
        const expected = originalPdfStatus(props.record, props.source)
        assert.ok(html.includes(i18n.t(expected.label)))
        assert.ok(html.includes('role="status"'))
      }
    }
  } finally { await server.close() }
})

test('a newly missing case cleanup status replaces cached COMPLETE with unknown', async () => {
  const server = await createServer({ root: fileURLToPath(new URL('../', import.meta.url)),
    server: { middlewareMode: true, hmr: false }, appType: 'custom' })
  try {
    const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
    const { useCasesStore } = await server.ssrLoadModule('/src/store/modules/cases.js')
    setActivePinia(createPinia())
    const cases = useCasesStore()
    cases.cases.CASE = { caseId: 'CASE', cleanupStatus: 'COMPLETE', jobTitle: 'Retained title' }
    request.defaults.adapter = async config => ({ config, status: 200, data: { code: 0, data: {
      caseId: 'CASE', processingStatus: 'CONFIRMED', confirmedAt: '2026-11-25T15:00:00Z',
    } } })
    const row = await cases.loadConnectedCase('CASE')
    assert.equal(row.jobTitle, 'Retained title')
    assert.equal(row.cleanupStatus, null)
    assert.equal(originalPdfStatus(row, 'case').code, 'unknown')
  } finally { await server.close() }
})
