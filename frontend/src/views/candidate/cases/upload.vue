<script setup>
import { demoMode } from '../../../config/runtime.js'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDialog, ElEmpty } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CvUploadCard from '../../../components/simrecrut/CvUploadCard.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import { isCaseProcessing, useCasesStore } from '../../../store/modules/cases.js'
import { extractionScenario } from '../../../config/demo.js'
import { formatCvSize } from '../../../utils/cv-validation.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const selected = ref(null)
const confirmDelete = ref(false)
const clock = ref(Date.now())
const expiryTimer = setInterval(() => { clock.value = Date.now() }, 5000)
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const processing = computed(() => isCaseProcessing(entry.value))
const ready = computed(() => entry.value?.processingStatus === 'REVIEW_REQUIRED')
const failed = computed(() => ['FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.value?.processingStatus))
const canChooseFile = computed(() => entry.value && !entry.value.confirmedAt && !processing.value && !ready.value)
const expiresAt = computed(() => entry.value ? new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(entry.value.expiresAt)) : '')

async function upload() {
  if (!selected.value || !canChooseFile.value) return
  const metadata = selected.value
  try { await cases.uploadCv(route.params.caseId, metadata, extractionScenario); selected.value = null } catch (error) { ElMessage.error(error.message) }
}
async function retryCase(caseId, scenario) {
  try { await cases.retryCase(caseId, scenario) } catch (error) { ElMessage.error(error.message) }
}

async function cancelAndDelete() {
  try {
    await cases.deleteCase(route.params.caseId)
    confirmDelete.value = false
    router.replace({ name: 'MySimulations' })
  } catch (error) { ElMessage.error(error.message) }
}

watch(() => route.params.caseId, () => { selected.value = null; confirmDelete.value = false })
onBeforeUnmount(() => clearInterval(expiryTimer))

// Direct navigation must load the authorized case instead of requiring demo memory.
let connectedRequest = 0, connectedActive = true
async function refreshConnectedCase() {
  if (demoMode) return
  const request = ++connectedRequest, id = String(route.params.caseId)
  try {
    const row = await (await import('../../../api/cases.js')).caseDetail(id)
    if (connectedActive && request === connectedRequest && route.params.caseId === id) cases.cases[id] = row
  } catch (error) {
    if (connectedActive && request === connectedRequest) { delete cases.cases[id]; ElMessage.error(error.message) }
  }
}
watch(() => route.params.caseId, refreshConnectedCase)
refreshConnectedCase()
const connectedTimer = null
onBeforeUnmount(() => { connectedActive = false; connectedRequest++; clearInterval(connectedTimer) })
</script>

<template>
  <CandidateLayout>
    <section v-if="!entry" class="unavailable-panel">
      <ElEmpty description="Demo case not available" />
      <h1>Demo Case Not Available</h1>
      <p>This case does not exist, has expired or is not available in your demo session.</p>
      <p>Start a simulation from a published job to create a new demo case.</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">Back to Jobs</ElButton>
    </section>
    <section v-else-if="entry.confirmedAt" class="unavailable-panel">
      <h1>Facts Already Confirmed</h1>
      <p>This case is locked. Start a new case from Jobs if you need to change the facts.</p>
      <ElButton type="primary" @click="router.replace({ name: 'CvReview', params: { caseId: entry.caseId } })">View Confirmation</ElButton>
    </section>
    <section v-else aria-labelledby="upload-title">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <RouterLink :to="{ name: 'PublishedJobs' }">Jobs</RouterLink><span aria-hidden="true">/</span>
        <RouterLink :to="{ name: 'JobDetail', params: { jobId: entry.jobId } }">{{ entry.jobTitle }}</RouterLink>
        <span aria-hidden="true">/</span><span aria-current="page">Upload CV</span>
      </nav>
      <h1 id="upload-title">Upload Your CV</h1>
      <p class="subtitle">Choose a PDF and preview the recruitment simulation's extraction workflow.</p>
      <div class="case-context"><span>{{ entry.jobTitle }} · Version {{ entry.version }}</span>
        <span>Demo case: {{ entry.caseId }}</span><span>Expires: {{ expiresAt }} (Paris time)</span></div>
      <ElAlert class="demo-notice" type="info" :closable="false"
        title="Frontend demo only: PDF checks run locally. No file is uploaded and no OCR or AI extraction occurs. Sample facts are unrelated to your PDF. Reload before confirmation requires a new upload; confirmed checkpoints survive reload in this tab." />
      <CaseProgressStepper :ready-for-review="ready" />
      <div class="upload-grid">
        <div class="upload-column">
          <CvUploadCard v-if="canChooseFile" :selected="selected" @select="selected = $event" />
          <div v-if="entry.file" class="file-panel">
            <h2>{{ ready ? 'PDF Accepted for the Demo' : 'Selected PDF' }}</h2>
            <strong>{{ entry.file.name }}</strong>
            <p>{{ formatCvSize(entry.file.size) }} · {{ entry.file.pageCount }} {{ entry.file.pageCount === 1 ? 'page' : 'pages' }}</p>
            <p>Only file metadata is retained in memory. File contents are not stored.</p>
          </div>
          <aside class="tips-panel" aria-labelledby="tips-title">
            <h2 id="tips-title">Tips</h2>
            <ul><li>Use a clear, up-to-date CV.</li><li>Choose a readable PDF with no password or encryption.</li>
              <li>Use a sample CV when exploring this frontend demonstration.</li></ul>
          </aside>
          <ElButton v-if="canChooseFile" class="upload-button" type="primary" :disabled="!selected"
            @click="upload">Upload and Extract</ElButton>
        </div>
        <section class="extraction-panel" aria-labelledby="extraction-title" :aria-busy="processing">
          <h2 id="extraction-title">Extraction Status</h2>
          <div v-if="processing" class="processing-state" role="status" aria-live="polite">
            <span class="spinner" aria-hidden="true"></span>
            <h3>{{ entry.processingStatus === 'UPLOADED' ? 'Preparing the demo upload...' : 'Extracting information...' }}</h3>
            <p>{{ entry.processingStatus === 'UPLOADED' ? 'Your PDF checks passed. Preparing the simulated extraction.' : 'The frontend is simulating extraction. Please wait.' }}</p>
            <p>Stage: {{ entry.stage }}</p>
            <p>Duplicate upload is unavailable while this demo is processing.</p>
          </div>
          <div v-else-if="failed" class="failure-state" role="alert">
            <h3>{{ entry.stage === 'Upload' ? 'Demo upload failed' : 'Demo extraction failed' }}</h3>
            <p>{{ entry.failure }}</p>
            <ElButton v-if="entry.processingStatus === 'FAILED_RETRYABLE' && entry.retryCount < 1"
              type="primary" @click="retryCase(entry.caseId, extractionScenario)">
              {{ entry.stage === 'Upload' ? 'Retry Upload' : 'Retry Extraction' }}
            </ElButton>
            <p v-else>Retry is unavailable for this failure. Choose a different PDF.</p>
          </div>
          <div v-else-if="ready" class="ready-state" role="status">
            <h3>Ready for Fact Review</h3>
            <p>Demo extraction completed. These sample values were not read from your PDF.</p>
            <dl><div><dt>Education</dt><dd>{{ entry.preview.education }}</dd></div>
              <div><dt>Work Experience</dt><dd>{{ entry.preview.experience }}</dd></div>
              <div><dt>Skills</dt><dd>{{ entry.preview.skills.join(', ') }}</dd></div></dl>
            <p id="review-notice">Review the professional facts and short mock evidence in the next step.</p>
          </div>
          <div v-else class="empty-state">
            <ElEmpty description="No file uploaded yet" />
            <p>Select a valid PDF and choose Upload and Extract to begin the frontend demo.</p>
          </div>
        </section>
      </div>
      <div class="page-actions">
        <ElButton @click="confirmDelete = true">Cancel and Delete</ElButton>
        <ElButton type="primary" :disabled="!ready" :aria-describedby="ready ? 'review-notice' : undefined"
          @click="router.push({ name: 'CvReview', params: { caseId: entry.caseId } })">Review Extracted Facts</ElButton>
      </div>
    </section>
    <ElDialog v-model="confirmDelete" title="Cancel and delete this demo case?" width="min(460px, calc(100% - 32px))"
      :close-on-click-modal="false">
      <p>The case, file metadata and mock preview will be removed from this session. No file was uploaded to a server.</p>
      <p>You will return to My simulations.</p>
      <template #footer><ElButton @click="confirmDelete = false">Keep Case</ElButton>
        <ElButton type="danger" @click="cancelAndDelete">Delete Demo Case</ElButton></template>
    </ElDialog>
  </CandidateLayout>
</template>

<style scoped>
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: #586b83; font-size: 13px; line-height: 1.7; }
.breadcrumb a { color: #176bdf; text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.breadcrumb a:focus-visible { outline: 2px solid #176bdf; outline-offset: 3px; }
h1 { margin: 0; color: #132941; font-size: 28px; line-height: 1.4; letter-spacing: -0.7px; }
.subtitle { color: #586b83; font-size: 14px; line-height: 1.8; margin: 10px 0 16px; }
.case-context { display: flex; flex-wrap: wrap; gap: 8px 22px; color: #586b83; font-size: 12px; line-height: 1.7; margin-bottom: 20px; overflow-wrap: anywhere; }
.upload-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 22px; }
.upload-column { min-width: 0; }
.extraction-panel, .file-panel { padding: 24px; background: white; border: 1px solid #e1eaf5; border-radius: 10px; min-width: 0; }
h2 { margin: 0 0 16px; font-size: 17px; color: #20354e; }
h3 { font-size: 18px; line-height: 1.5; color: #20354e; }
.extraction-panel p, .file-panel p, .unavailable-panel p { color: #586b83; font-size: 13px; line-height: 1.8; }
.file-panel { margin-top: 16px; }
.file-panel strong { font-size: 14px; overflow-wrap: anywhere; }
.tips-panel { margin-top: 16px; padding: 20px; border-radius: 8px; background: #eaf3ff; }
.tips-panel h2 { font-size: 14px; margin-bottom: 8px; }
.tips-panel ul { margin: 0; padding-left: 18px; color: #586b83; font-size: 13px; line-height: 1.9; }
.upload-button { margin-top: 18px; width: 100%; }
.processing-state, .empty-state { padding: 25px 0; text-align: center; }
.spinner { display: inline-block; width: 40px; height: 40px; border: 4px solid #d6e8ff; border-top-color: #176bdf; border-radius: 50%; animation: spin 1s linear infinite; }
.failure-state { padding: 18px; border-radius: 8px; background: #fff3f3; }
.failure-state h3 { margin-top: 0; color: #b42318; }
.ready-state h3 { color: #218458; }
dl { font-size: 13px; line-height: 1.8; }
dl > div { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 10px 0; border-bottom: 1px solid #edf1f7; }
dt { color: #68798c; }
dd { margin: 0; color: #425269; }
.page-actions { display: flex; justify-content: space-between; gap: 12px; margin-top: 26px; }
.page-actions .el-button { margin: 0; }
.unavailable-panel { padding: 32px 20px; text-align: center; border: 1px solid #e1eaf5; border-radius: 10px; background: white; }
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
