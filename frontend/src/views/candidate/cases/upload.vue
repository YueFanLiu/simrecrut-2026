<script setup>
import { dateLocale, t } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDialog, ElEmpty } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CvUploadCard from '../../../components/simrecrut/CvUploadCard.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import CvSourcePicker from '../../../components/simrecrut/CvSourcePicker.vue'
import { isCaseProcessing, useCasesStore } from '../../../store/modules/cases.js'
import { extractionScenario } from '../../../config/demo.js'
import { formatCvSize } from '../../../utils/cv-validation.js'
import { listResumes } from '../../../api/resumes.js'
import { useLibraryResume } from '../../../api/cases.js'
import useUserStore from '../../../store/modules/user.js'
import { cvContext, cvReviewRoute, createCvAttachment, reusableCv } from '../../../utils/cv-workflow.js'
import { getCaseDestination } from '../../../utils/case-history.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
let connectedRequest = 0, connectedActive = true, loadingCaseId
const statusLoading = ref(false), statusError = ref('')
const selected = ref(null)
const uploading = ref(false)
const confirmDelete = ref(false)
const clock = ref(Date.now())
const expiryTimer = setInterval(() => { clock.value = Date.now() }, 5000)
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const processing = computed(() => isCaseProcessing(entry.value))
const ready = computed(() => entry.value?.processingStatus === 'REVIEW_REQUIRED')
const failed = computed(() => ['FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.value?.processingStatus))
const canChooseFile = computed(() => entry.value && !entry.value.confirmedAt && !processing.value && !ready.value && (demoMode || entry.value.processingStatus === 'AWAITING_UPLOAD'))
const expiresAt = computed(() => entry.value?.expiresAt ? new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(entry.value.expiresAt)) : '—')

// A confirmed library CV can replace a new upload: its reviewed facts open directly in the review step.
const user = useUserStore()
const library = ref([])
const libraryChoice = ref('')
const usingLibrary = ref(false)
const libraryLoading = ref(false)
const libraryError = ref('')
const selectionError = ref('')
const sourceMode = ref('library')
let libraryRequest = 0
let appliedResumeQuery = ''
const canUseLibrary = computed(() => !demoMode && canChooseFile.value
  && user.permissions.includes('simrecrut:resume:list'))
async function loadLibrary() {
  const request = ++libraryRequest, caseId = route.params.caseId
  libraryError.value = ''; selectionError.value = ''
  if (!canUseLibrary.value) {
    if (!demoMode && canChooseFile.value) libraryError.value = 'Your account cannot access the CV library. You can still upload a PDF for this simulation.'
    return
  }
  libraryLoading.value = true
  try {
    const rows = (await listResumes()).data || []
    if (!connectedActive || request !== libraryRequest || route.params.caseId !== caseId) return
    library.value = rows
    const requested = cvContext(route.query).resumeId
    if (requested && requested !== appliedResumeQuery) {
      appliedResumeQuery = requested
      if (rows.some(row => row.id === requested && reusableCv(row))) libraryChoice.value = requested
      else selectionError.value = 'The selected CV is unavailable, expired or not confirmed. Choose another CV or review it first.'
    }
    if (libraryChoice.value && !rows.some(row => row.id === libraryChoice.value && reusableCv(row))) libraryChoice.value = ''
  } catch (error) {
    if (connectedActive && request === libraryRequest) { library.value = []; libraryError.value = error.message || 'Unable to load your CVs. Please retry.' }
  } finally { if (request === libraryRequest) libraryLoading.value = false }
}
const attachFromLibrary = createCvAttachment({
  readCase: id => cases.loadConnectedCase(id),
  attach: useLibraryResume,
})
async function useLibrary() {
  if (usingLibrary.value || !canChooseFile.value || !library.value.some(row => row.id === libraryChoice.value && reusableCv(row))) return
  usingLibrary.value = true
  selectionError.value = ''
  const id = String(route.params.caseId)
  try {
    const result = await attachFromLibrary(id, libraryChoice.value)
    if (!connectedActive || route.params.caseId !== id) return
    cases.cases[id] = { ...cases.cases[id], ...result.row }
    if (result.alreadyStarted) ElMessage.info(t('This simulation already has CV information. Continuing from its saved step.'))
    await router.push(getCaseDestination({ ...result.row, caseId: id }) || { name: 'CvReview', params: { caseId: id } })
  } catch (error) {
    if (connectedActive && route.params.caseId === id) selectionError.value = error.message || 'This CV could not be used. Choose another CV or retry.'
  } finally { usingLibrary.value = false }
}
watch(canChooseFile, value => { if (value && !demoMode) loadLibrary() }, { immediate: true })
watch(() => route.query.resumeId, () => { if (!demoMode && canChooseFile.value) loadLibrary() })
function chooseSource(mode) { sourceMode.value = mode; selected.value = null; selectionError.value = '' }

async function upload() {
  if (!selected.value || !canChooseFile.value || uploading.value) return
  uploading.value = true
  const metadata = selected.value
  try { await cases.uploadCv(route.params.caseId, metadata, extractionScenario); selected.value = null } catch (error) { ElMessage.error(t(error.message)) } finally { uploading.value = false }
}
async function retryCase(caseId, scenario) {
  try { await cases.retryCase(caseId, scenario) } catch (error) { ElMessage.error(t(error.message)) }
}

async function cancelAndDelete() {
  try {
    await cases.deleteCase(route.params.caseId)
    confirmDelete.value = false
    router.replace({ name: 'MySimulations' })
  } catch (error) { ElMessage.error(t(error.message)) }
}

watch(() => route.params.caseId, () => { selected.value = null; confirmDelete.value = false; libraryChoice.value = ''; appliedResumeQuery = ''; library.value = []; libraryRequest++; libraryLoading.value = false; sourceMode.value = 'library'; loadLibrary() })
onBeforeUnmount(() => clearInterval(expiryTimer))

// Direct navigation must load the authorized case instead of requiring demo memory.
async function refreshConnectedCase() {
  if (demoMode) return
  const id = String(route.params.caseId)
  if (statusLoading.value && loadingCaseId === id) return
  const request = ++connectedRequest
  loadingCaseId = id
  statusLoading.value = true; statusError.value = ''
  try {
    const row = await cases.loadConnectedCase(id)
    if (connectedActive && request === connectedRequest && route.params.caseId === id) cases.cases[id] = row
  } catch (error) {
    if (connectedActive && request === connectedRequest && route.params.caseId === id) {
      if ([401, 403, 404, 410].includes(error.status ?? error.response?.status)) {
        delete cases.cases[id]; ElMessage.error(t(error.message))
      } else {
        // A failed status read says nothing about whether extraction stopped.
        // Keep the known case so polling can resume after a temporary outage.
        statusError.value = error.message || 'The service is unavailable. Please try again.'
      }
    }
  } finally { if (request === connectedRequest) statusLoading.value = false }
}
watch(() => route.params.caseId, refreshConnectedCase)
refreshConnectedCase()
// After an upload, open the review as soon as extraction finishes instead of waiting for another click.
watch(ready, (value, previous) => {
  if (!demoMode && value && previous === false && processingSeen && entry.value) {
    router.push({ name: 'CvReview', params: { caseId: entry.value.caseId } })
  }
})
let processingSeen = false
watch(processing, value => { if (value) processingSeen = true }, { immediate: true })
const connectedTimer = setInterval(() => { if (!demoMode && processing.value && !statusLoading.value) refreshConnectedCase() }, 2000)
onBeforeUnmount(() => { connectedActive = false; connectedRequest++; libraryRequest++; clearInterval(connectedTimer) })
</script>

<template>
  <CandidateLayout>
    <section v-if="!entry && statusLoading" class="unavailable-panel" role="status">
      <p>{{ $t('Loading case…') }}</p>
    </section>
    <section v-else-if="!entry && statusError" class="unavailable-panel">
      <h1>{{ $t('Could not load the case status') }}</h1>
      <ElAlert :title="$t(statusError)" type="error" :closable="false" />
      <ElButton type="primary" :loading="statusLoading" @click="refreshConnectedCase">{{ $t('Try Again') }}</ElButton>
      <ElButton @click="router.replace({ name: 'PublishedJobs' })">{{ $t('Back to Jobs') }}</ElButton>
    </section>
    <section v-else-if="!entry" class="unavailable-panel">
      <ElEmpty :description="$t(&quot;Case not available&quot;)" />
      <h1>{{ $t("Case Not Available") }}</h1>
      <p>{{ $t("This case does not exist, has expired or is not available to your account.") }}</p>
      <p>{{ $t("Start a simulation from a published job to create a new case.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
    </section>
    <section v-else-if="entry.confirmedAt" class="unavailable-panel">
      <h1>{{ $t("Facts Already Confirmed") }}</h1>
      <p>{{ $t("This case is locked. Start a new case from Jobs if you need to change the facts.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'CvReview', params: { caseId: entry.caseId } })">{{ $t("View Confirmation") }}</ElButton>
    </section>
    <section v-else aria-labelledby="upload-title">
      <nav class="breadcrumb" :aria-label="$t(&quot;Breadcrumb&quot;)">
        <RouterLink :to="{ name: 'PublishedJobs' }">{{ $t("Jobs") }}</RouterLink><span aria-hidden="true">/</span>
        <RouterLink v-if="entry.jobId" :to="{ name: 'JobDetail', params: { jobId: entry.jobId } }">{{ entry.jobTitle }}</RouterLink>
        <span aria-hidden="true">/</span><span aria-current="page">{{ $t("Upload CV") }}</span>
      </nav>
      <h1 id="upload-title">{{ $t(!demoMode && canChooseFile && sourceMode === 'library' ? 'Choose a CV for this simulation' : 'Upload Your CV') }}</h1>
      <p class="subtitle">{{ $t(demoMode ? "Choose a PDF and preview the recruitment simulation's extraction workflow." : 'Choose a saved CV or upload a new PDF for this simulation.') }}</p>
      <div v-if="statusError" class="status-error" role="alert">
        <ElAlert :title="$t('The case status could not be refreshed. Your upload may still be processing.')"
          :description="$t(statusError)" type="error" :closable="false" />
        <ElButton :loading="statusLoading" @click="refreshConnectedCase">{{ $t('Try Again') }}</ElButton>
      </div>
      <div class="case-context"><span v-if="entry.jobTitle">{{ entry.jobTitle }}<template v-if="entry.version"> {{ $t("· Version") }} {{ entry.version }}</template></span>
        <span>{{ $t("Case:") }} {{ entry.caseId }}</span><span>{{ $t("Expires:") }} {{ expiresAt }} {{ $t("(Paris time)") }}</span></div>
      <ElAlert v-if="demoMode" class="demo-notice" type="info" :closable="false"
        :title="$t(&quot;Frontend demo only: PDF checks run locally. No file is uploaded and no OCR or AI extraction occurs. Sample facts are unrelated to your PDF. Reload before confirmation requires a new upload; confirmed checkpoints survive reload in this tab.&quot;)" />
      <p v-else-if="sourceMode === 'upload'">{{ $t("The PDF is processed on the server. When DeepSeek extraction is enabled, it receives selected professional text with personal details removed.") }}</p>
      <p v-if="!demoMode && sourceMode === 'upload'">{{ $t("After confirmation, the server requests deletion of any original PDF. Check its status on simulation progress.") }}</p>
      <CaseProgressStepper :ready-for-review="ready" />
      <CvSourcePicker v-if="!demoMode && canChooseFile && sourceMode === 'library'" :resumes="library" :loading="libraryLoading" :busy="usingLibrary"
        :error="libraryError" :selection-error="selectionError" :selected-id="libraryChoice"
        @select="libraryChoice = $event; selectionError = ''" @use="useLibrary" @refresh="loadLibrary"
        @review="router.push(cvReviewRoute($event, entry.caseId))" @upload="chooseSource('upload')" />
      <ElButton v-if="!demoMode && canChooseFile && sourceMode === 'upload'" class="source-back" text :disabled="uploading" @click="chooseSource('library')">← {{ $t('Choose a saved CV instead') }}</ElButton>
      <div v-if="demoMode || !canChooseFile || sourceMode === 'upload'" class="upload-grid">
        <div class="upload-column">
          <CvUploadCard v-if="canChooseFile" :selected="selected" @select="selected = $event" />
          <div v-if="entry.file" class="file-panel">
            <h2>{{ $t(ready ? 'PDF Accepted for the Demo' : 'Selected PDF') }}</h2>
            <strong>{{ entry.file.name }}</strong>
            <p>{{ formatCvSize(entry.file.size) }} · {{ entry.file.pageCount }} {{ $t(entry.file.pageCount === 1 ? 'page' : 'pages') }}</p>
            <p>{{ $t("Only file metadata is retained in memory. File contents are not stored.") }}</p>
          </div>
          <aside class="tips-panel" aria-labelledby="tips-title">
            <h2 id="tips-title">{{ $t("Tips") }}</h2>
            <ul><li>{{ $t("Use a clear, up-to-date CV.") }}</li><li>{{ $t("Choose a readable PDF with no password or encryption.") }}</li>
              <li v-if="demoMode">{{ $t("Use a sample CV when exploring this frontend demonstration.") }}</li></ul>
          </aside>
          <ElButton v-if="canChooseFile" class="upload-button" type="primary" :disabled="!selected"
            :loading="uploading" @click="upload">{{ $t("Upload and Extract") }}</ElButton>
        </div>
        <section class="extraction-panel" aria-labelledby="extraction-title" :aria-busy="processing">
          <h2 id="extraction-title">{{ $t("Extraction Status") }}</h2>
          <div v-if="processing" class="processing-state" role="status" aria-live="polite">
            <span class="spinner" aria-hidden="true"></span>
            <h3>{{ $t(entry.processingStatus === 'UPLOADED' ? 'Preparing the demo upload...' : 'Extracting information...') }}</h3>
            <p>{{ $t(entry.processingStatus === 'UPLOADED' ? 'Your PDF checks passed. Preparing the simulated extraction.' : 'Professional facts are being extracted. Please wait.') }}</p>
            <p>{{ $t("Stage:") }} {{ entry.stage }}</p>
            <p>{{ $t("Duplicate upload is unavailable while extraction is running.") }}</p>
          </div>
          <div v-else-if="failed" class="failure-state" role="alert">
            <h3>{{ $t(entry.stage === 'Upload' ? 'Demo upload failed' : 'Extraction failed') }}</h3>
            <p>{{ entry.failure }}</p>
            <ElButton v-if="entry.processingStatus === 'FAILED_RETRYABLE' && (demoMode ? entry.retryCount < 1 : entry.retryable)"
              type="primary" @click="retryCase(entry.caseId, extractionScenario)">
              {{ $t(entry.stage === 'Upload' ? 'Retry Upload' : 'Retry Extraction') }}
            </ElButton>
            <p v-else-if="demoMode">{{ $t("Retry is unavailable for this failure. Choose a different PDF.") }}</p>
            <ElButton v-else @click="router.push({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
          </div>
          <div v-else-if="ready" class="ready-state" role="status">
            <h3>{{ $t("Ready for Fact Review") }}</h3>
            <p v-if="demoMode">{{ $t("Demo extraction completed. These sample values were not read from your PDF.") }}</p>
            <dl v-if="entry.preview"><div><dt>{{ $t("Education") }}</dt><dd>{{ entry.preview.education }}</dd></div>
              <div><dt>{{ $t("Work Experience") }}</dt><dd>{{ entry.preview.experience }}</dd></div>
              <div><dt>{{ $t("Skills") }}</dt><dd>{{ $t(entry.preview.skills.join(', ')) }}</dd></div></dl>
            <p id="review-notice">{{ $t("Review the professional facts and their supporting evidence in the next step.") }}</p>
          </div>
          <div v-else class="empty-state">
            <ElEmpty :description="$t(&quot;No file uploaded yet&quot;)" />
            <p>{{ $t("Select a valid PDF and choose Upload and Extract to begin extraction.") }}</p>
          </div>
        </section>
      </div>
      <div class="page-actions">
        <ElButton type="danger" plain @click="confirmDelete = true">{{ $t('Delete simulation') }}</ElButton>
        <ElButton v-if="demoMode || !canChooseFile || sourceMode === 'upload'" type="primary" :disabled="!ready" :aria-describedby="ready ? 'review-notice' : undefined"
          @click="router.push({ name: 'CvReview', params: { caseId: entry.caseId } })">{{ $t('Review CV') }}</ElButton>
      </div>
    </section>
    <ElDialog v-model="confirmDelete" :title="$t('Delete this simulation?')" width="min(460px, calc(100% - 32px))"
      :close-on-click-modal="false">
      <p v-if="demoMode">{{ $t("The case, file metadata and mock preview will be removed from this session.") }}</p>
      <p v-else>{{ $t("Access ends immediately. The server will remove the case and its data.") }}</p>
      <p>{{ $t("You will return to My simulations.") }}</p>
      <template #footer><ElButton @click="confirmDelete = false">{{ $t('Keep simulation') }}</ElButton>
        <ElButton type="danger" @click="cancelAndDelete">{{ $t('Delete simulation') }}</ElButton></template>
    </ElDialog>
  </CandidateLayout>
</template>

<style scoped>
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: var(--sr-text-muted); font-size: 13px; line-height: 1.7; }
.breadcrumb a { color: var(--sr-primary); text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.breadcrumb a:focus-visible { outline: 2px solid var(--sr-primary); outline-offset: 3px; }
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; line-height: 1.4; letter-spacing: -0.7px; }
.subtitle { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; margin: 10px 0 16px; }
.case-context { display: flex; flex-wrap: wrap; gap: 8px 22px; color: var(--sr-text-muted); font-size: 12px; line-height: 1.7; margin-bottom: 20px; overflow-wrap: anywhere; }
.upload-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }
.upload-column { min-width: 0; }
.source-back { margin-bottom:16px; }
.status-error { display:grid; gap:12px; justify-items:start; margin-bottom:16px; }
.extraction-panel, .file-panel { padding: 24px; background: white; border: 1px solid var(--sr-border); border-radius: 10px; min-width: 0; }
h2 { margin: 0 0 16px; font-size: 17px; color: var(--sr-text); }
h3 { font-size: 18px; line-height: 1.5; color: var(--sr-text); }
.extraction-panel p, .file-panel p, .unavailable-panel p { color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; }
.file-panel { margin-top: 16px; }
.file-panel strong { font-size: 14px; overflow-wrap: anywhere; }
.library-panel { padding: 20px; margin-bottom: 16px; background: white; border: 1px solid var(--sr-border); border-radius: 10px; }
.library-panel p { color: var(--sr-text-muted); font-size: 13px; line-height: 1.7; }
.library-panel .el-select { width: 100%; margin-bottom: 12px; }
.library-panel .divider { margin: 16px 0 0; padding-top: 12px; border-top: 1px solid var(--sr-surface-sunken); text-align: center; }
.tips-panel { margin-top: 16px; padding: 20px; border-radius: 8px; background: var(--sr-primary-50); }
.tips-panel h2 { font-size: 14px; margin-bottom: 8px; }
.tips-panel ul { margin: 0; padding-left: 18px; color: var(--sr-text-muted); font-size: 13px; line-height: 1.9; }
.upload-button { margin-top: 18px; width: 100%; }
.processing-state, .empty-state { padding: 25px 0; text-align: center; }
.spinner { display: inline-block; width: 40px; height: 40px; border: 4px solid var(--sr-primary-100); border-top-color: var(--sr-primary); border-radius: 50%; animation: spin 1s linear infinite; }
.failure-state { padding: 18px; border-radius: 8px; background: #fff3f3; }
.failure-state h3 { margin-top: 0; color: #b42318; }
.ready-state h3 { color: #218458; }
dl { font-size: 13px; line-height: 1.8; }
dl > div { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 10px 0; border-bottom: 1px solid var(--sr-surface-sunken); }
dt { color: var(--sr-text-muted); }
dd { margin: 0; color: var(--sr-text-secondary); }
.page-actions { display: flex; justify-content: space-between; gap: 12px; margin-top: 26px; }
.page-actions .el-button { margin: 0; }
.unavailable-panel { padding: 32px 20px; text-align: center; border: 1px solid var(--sr-border); border-radius: 10px; background: white; }
.unavailable-panel :deep(.el-empty__description) { display: none; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinner { animation: none; } }
@media (max-width: 700px) {
  .upload-grid { grid-template-columns: 1fr; }
  h1 { font-size: 25px; }
  .extraction-panel, .file-panel { padding: 20px; }
  .page-actions { flex-direction: column-reverse; }
  .page-actions .el-button { width: 100%; }
}
</style>
