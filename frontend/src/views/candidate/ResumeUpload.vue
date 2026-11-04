<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CandidateLayout from '../../layouts/CandidateLayout.vue'
import CvUploadCard from '../../components/simrecrut/CvUploadCard.vue'
import { listResumes, importResume, resumeAction, getResume } from '../../api/resumes.js'
import { demoMode } from '../../config/runtime.js'
import useUserStore from '../../store/modules/user.js'

const router = useRouter()
const user = useUserStore()
const records = ref([])
const selected = ref(null)
const loading = ref(false)
const busy = ref(false)
const listError = ref('')
const uploadError = ref('')
const stage = ref('')
const canUpload = computed(() => !demoMode && user.permissions.includes('simrecrut:resume:upload'))
const controller = new AbortController()
let active = true
let importKey
let importPayload
let cleanKey
const statusLabels = { UPLOADED: 'Ready to extract', EXTRACTING: 'Extracting facts',
  REVIEW_REQUIRED: 'Ready for review', CONFIRMED: 'Confirmed', FAILED: 'Extraction failed' }
async function refresh() {
  if (demoMode || loading.value) return
  loading.value = true
  listError.value = ''
  try {
    const result = await listResumes(controller.signal)
    if (active) records.value = result.data
  } catch (error) {
    if (active) listError.value = error.message || 'Unable to load your CVs. Please retry.'
  } finally { loading.value = false }
}
function selectFile(value) {
  selected.value = value
  importKey = undefined
  importPayload = undefined
  cleanKey = undefined
  uploadError.value = ''
}
async function upload() {
  if (busy.value || !selected.value?.file || !canUpload.value) return
  busy.value = true
  uploadError.value = ''
  try {
    stage.value = 'Preparing PDF...'
    if (!importPayload) {
      const content = await new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result.split(',')[1])
        reader.onerror = () => reject(new Error('Unable to read this PDF. Please select it again.'))
        reader.readAsDataURL(selected.value.file)
      })
      importPayload = { filename: selected.value.name, content }
      importKey = crypto.randomUUID()
    }
    if (!active) return
    stage.value = 'Uploading PDF...'
    // Retain the same payload and key after an ambiguous network failure.
    const { data } = await importResume(importPayload, controller.signal, importKey)
    if (!active) return
    const existing = (await getResume(data.id, controller.signal)).data
    if (!active) return
    if (existing.status === 'UPLOADED' && user.permissions.includes('simrecrut:resume:edit')) {
      stage.value = 'Starting extraction...'
      cleanKey ||= crypto.randomUUID()
      await resumeAction(data.id, 'clean', {}, { signal: controller.signal, operationKey: cleanKey })
    }
    if (active) await router.push({ name: 'ConnectedResumeReview', params: { id: data.id } })
  } catch (error) {
    if (!active) return
    uploadError.value = error.message || 'Upload could not finish. Please retry.'
    await refresh()
  } finally { busy.value = false; stage.value = '' }
}
onMounted(refresh)
onUnmounted(() => {
  active = false
  controller.abort()
  records.value = []
  selected.value = null
  importPayload = undefined
})
</script>

<template>
  <CandidateLayout>
    <div class="page-heading"><div><p class="eyebrow">CANDIDATE WORKSPACE</p>
      <h1>My CVs</h1><p>Upload a CV, review the extracted facts and save your confirmed profile.</p>
    </div></div>
    <el-alert v-if="demoMode" title="Sign in to the connected service to upload a real CV."
      type="info" :closable="false" />
    <section class="upload-section" aria-labelledby="upload-title">
      <h2 id="upload-title">Upload your CV</h2>
      <p>Your original PDF stays temporary. Review the extracted facts before confirming them.</p>
      <CvUploadCard :selected="selected" :disabled="busy || !canUpload" @select="selectFile" />
      <el-alert v-if="uploadError" :title="uploadError" type="error" show-icon :closable="false" />
      <div class="upload-actions">
        <el-button type="primary" size="large" :loading="busy"
          :disabled="!selected || !canUpload || busy" @click="upload">
          {{ busy ? stage : uploadError ? 'Retry upload' : 'Upload and extract facts' }}
        </el-button>
        <span role="status" aria-live="polite">{{ busy ? 'Keep this page open while your PDF uploads.' : 'PDF only · Up to 10 MB · 1–20 pages' }}</span>
      </div>
      <p class="retention">Unconfirmed CVs expire after 24 hours. Confirmed CVs expire after 30 days.
        The temporary original is removed after successful publication of your confirmed facts.</p>
    </section>
    <section class="library" aria-labelledby="library-title" :aria-busy="loading">
      <div class="library-heading"><h2 id="library-title">Your CV library</h2>
        <el-button :loading="loading" :disabled="busy || demoMode" @click="refresh">Refresh list</el-button>
      </div>
      <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false" />
      <p v-if="loading" role="status">Loading your CVs...</p>
      <el-empty v-else-if="!records.length && !listError" description="No CVs yet. Upload your first PDF above." />
      <el-table v-if="records.length" :data="records" class="cv-table">
        <el-table-column prop="filename" label="CV" min-width="180" />
        <el-table-column label="Status" min-width="160"><template #default="{ row }">
          <el-tag :type="row.status === 'FAILED' ? 'danger' : row.confirmed ? 'success' : 'info'">
            {{ row.cloud_revision === row.revision ? 'Published' : statusLabels[row.status] || row.status }}
          </el-tag>
        </template></el-table-column>
        <el-table-column label="Expires" min-width="160"><template #default="{ row }">
          {{ row.expires_at ? new Date(row.expires_at).toLocaleString('en-GB') : '—' }}
        </template></el-table-column>
        <el-table-column label="Action" width="140"><template #default="{ row }">
          <el-button :disabled="busy" @click="router.push({ name: 'ConnectedResumeReview', params: { id: row.id } })">
            {{ row.confirmed ? 'View facts' : 'Review facts' }}</el-button>
        </template></el-table-column>
      </el-table>
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 30px; color: #132941; letter-spacing: -0.7px; }
h2 { margin: 0 0 12px; font-size: 20px; color: #20354e; }
p { color: #586b83; line-height: 1.7; font-size: 14px; }
.eyebrow { color: #176bdf; font-size: 11px; font-weight: 700; letter-spacing: 1.4px; }
.page-heading { margin-bottom: 26px; }
.upload-section, .library { padding: 28px; margin-top: 24px; background: white;
  border: 1px solid #e1eaf5; border-radius: 12px; min-width: 0; }
.upload-actions, .library-heading { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
.upload-actions { margin-top: 20px; }
.upload-actions span, .retention { font-size: 12px; color: #60728a; }
.library-heading { justify-content: space-between; margin-bottom: 18px; }
.library-heading h2 { margin: 0; }
.el-alert { margin-top: 18px; }
@media (max-width: 600px) {
  .upload-section, .library { padding: 18px; }
  .upload-actions .el-button { width: 100%; font-size: 13px; }
}
</style>
