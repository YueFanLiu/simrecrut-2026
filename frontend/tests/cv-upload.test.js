import test from 'node:test'
import assert from 'node:assert/strict'
import { PDFDocument, PDFName } from 'pdf-lib'
import { createPinia, setActivePinia } from 'pinia'
import { validateCvFile, maximumCvBytes } from '../src/utils/cv-validation.js'
import { useAuthStore } from '../src/store/modules/auth.js'
import { isCaseProcessing, useCasesStore } from '../src/store/modules/cases.js'
import { loadJobDetail } from '../src/mock/job-details.js'

async function pdfFile(pages = 1, encrypted = false) {
  const document = await PDFDocument.create()
  for (let page = 0; page < pages; page += 1) document.addPage()
  if (encrypted) {
    // Encryption is rejected on the trailer flag, before any encrypted content is read.
    document.context.trailerInfo.Encrypt = document.context.register(document.context.obj({
      Filter: PDFName.of('Standard'), V: 1, R: 2, P: -4,
    }))
  }
  return new File([await document.save({ addDefaultPage: false })], 'sample-cv.pdf', { type: 'application/pdf' })
}

function signedInStores() {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.demoUser = { username: 'demo', displayName: 'Demo User', role: 'Candidate' }
  return { auth, cases: useCasesStore() }
}

test('PDF validation reads compressed page trees and enforces the 20-page limit', async () => {
  assert.equal((await validateCvFile(await pdfFile(20))).pageCount, 20)
  await assert.rejects(validateCvFile(await pdfFile(21)), /no more than 20 pages/)
  await assert.rejects(validateCvFile(await pdfFile(0)), /at least one page/)
})

test('PDF validation rejects wrong formats, oversized, empty, corrupt and encrypted files', async () => {
  await assert.rejects(validateCvFile(new File(['image'], 'cv.png', { type: 'image/png' })), /Choose one PDF/)
  await assert.rejects(validateCvFile(new File([], 'cv.pdf')), /empty/)
  await assert.rejects(validateCvFile({ name: 'cv.pdf', type: 'application/pdf', size: maximumCvBytes + 1 }), /10 MB or smaller/)
  await assert.rejects(validateCvFile(new File(['not a PDF'], 'cv.pdf', { type: 'application/pdf' })), /valid PDF/)
  await assert.rejects(validateCvFile(await pdfFile(1, true)), /Encrypted or password-protected/)
})

test('case creation pins the displayed published version, rejects stale versions and limits ownership', async () => {
  const { auth, cases } = signedInStores()
  const job = await loadJobDetail('software-engineer')
  await assert.rejects(cases.createCase({ ...job, jobVersionId: 'software-engineer-v1.3' }), /no longer available/)
  const caseId = await cases.createCase(job)
  const entry = cases.getOwnedCase(caseId)
  assert.equal(entry.jobVersionId, job.jobVersionId)
  assert.equal(entry.processingStatus, 'AWAITING_UPLOAD')
  assert.ok(Date.parse(entry.expiresAt) > Date.now() + 23 * 60 * 60 * 1000)
  auth.demoUser = { username: 'another-candidate' }
  assert.equal(cases.getOwnedCase(caseId), null)
  auth.demoUser = { username: 'demo' }
  entry.expiresAt = new Date(Date.now() - 1).toISOString()
  assert.equal(cases.getOwnedCase(caseId), null)
})

test('upload prevents duplicates, exposes stage states and allows one controlled extraction retry', async () => {
  const { cases } = signedInStores()
  const caseId = await cases.createCase(await loadJobDetail('data-analyst'))
  const metadata = { name: 'sample-cv.pdf', size: 1024, pageCount: 1 }
  const pending = cases.uploadCv(caseId, metadata, 'retryable')
  const entry = cases.getOwnedCase(caseId)
  const expiry = entry.expiresAt
  assert.equal(entry.processingStatus, 'UPLOADED')
  assert.equal(isCaseProcessing(entry), true)
  await cases.uploadCv(caseId, { ...metadata, name: 'duplicate.pdf' })
  assert.equal(entry.file.name, 'sample-cv.pdf')
  await pending
  assert.equal(entry.processingStatus, 'FAILED_RETRYABLE')
  await cases.retryCase(caseId, 'retryable')
  assert.equal(entry.processingStatus, 'REVIEW_REQUIRED')
  assert.equal(entry.retryCount, 1)
  assert.ok(entry.preview.skills.length)
  assert.equal(entry.expiresAt, expiry)
  assert.deepEqual(Object.keys(entry.file).sort(), ['name', 'pageCount', 'size'])
  await cases.retryCase(caseId)
  assert.equal(entry.retryCount, 1)
})

test('final failure requires replacement and cancellation prevents late work from restoring data', async () => {
  const { auth, cases } = signedInStores()
  const job = await loadJobDetail('software-engineer')
  const caseId = await cases.createCase(job)
  const metadata = { name: 'sample.pdf', size: 1024, pageCount: 1 }
  await cases.uploadCv(caseId, metadata, 'final')
  assert.equal(cases.getOwnedCase(caseId).processingStatus, 'FAILED_FINAL')
  await cases.retryCase(caseId)
  assert.equal(cases.getOwnedCase(caseId).processingStatus, 'FAILED_FINAL')
  const pending = cases.uploadCv(caseId, metadata)
  cases.deleteCase(caseId)
  await pending
  assert.equal(cases.getOwnedCase(caseId), null)
  const secondId = await cases.createCase(job)
  const secondPending = cases.uploadCv(secondId, metadata)
  auth.logout()
  await secondPending
  assert.deepEqual(cases.cases, {})
})
