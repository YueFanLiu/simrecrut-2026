<script setup>
import { dateLocale } from '../../i18n/index.js'
import { t } from '../../i18n/index.js'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import CandidateLayout from '../../layouts/CandidateLayout.vue'
import CvUploadCard from '../../components/simrecrut/CvUploadCard.vue'
import OriginalPdfStatus from '../../components/simrecrut/OriginalPdfStatus.vue'
import { originalPdfStatus } from '../../utils/original-pdf-status.js'
import { listResumes, importResume, resumeAction, getResume, deleteResume } from '../../api/resumes.js'
import { demoMode } from '../../config/runtime.js'
import useUserStore from '../../store/modules/user.js'
import { cvContext, cvReviewRoute, cvRevisionRoute, cvSelectionRoute, jobsForResume, reusableCv } from '../../utils/cv-workflow.js'

const router = useRouter()
const route = useRoute()
const context = computed(() => cvContext(route.query))
const user = useUserStore()
const records = ref([])
const selected = ref(null)
const loading = ref(false)
const refreshing = ref(false)
const busy = ref(false)
const listError = ref('')
const uploadError = ref('')
const stage = ref('')
const displayName = ref('')
const editingId = ref('')
const editingName = ref('')
const rowBusy = ref('')
const canEdit = computed(() => !demoMode && user.permissions.includes('simrecrut:resume:edit'))
const canDelete = computed(() => !demoMode && user.permissions.includes('simrecrut:resume:delete'))
// The library label defaults to the file name without its .pdf extension.
const defaultName = computed(() => selected.value?.name?.replace(/\.pdf$/i, '') || '')
const nameOf = row => row.display_name || row.filename
const canUpload = computed(() => !demoMode && user.permissions.includes('simrecrut:resume:upload'))
const controller = new AbortController()
let active = true
let importKey
let importPayload
let cleanKey
const statusLabels = { UPLOADED: 'Ready to extract', EXTRACTING: 'Extracting facts',
  REVIEW_REQUIRED: 'Ready for review', CONFIRMED: 'Confirmed', FAILED: 'Extraction failed' }
async function refresh({ quiet = false } = {}) {
  if (demoMode || refreshing.value) return
  refreshing.value = true
  if (!quiet) loading.value = true
  listError.value = ''
  try {
    const result = await listResumes(controller.signal)
    if (active) records.value = result.data
  } catch (error) {
    if (active) listError.value = error.message || 'Unable to load your CVs. Please retry.'
  } finally { loading.value = false; refreshing.value = false }
}
watch(displayName, () => { importKey = undefined; importPayload = undefined })
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
      const label = displayName.value.trim()
      importPayload = { filename: selected.value.name, content, ...(label ? { displayName: label } : {}) }
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
    if (active) await router.push(cvReviewRoute(data.id, context.value.caseId))
  } catch (error) {
    if (!active) return
    uploadError.value = error.message || 'Upload could not finish. Please retry.'
    await refresh()
  } finally { busy.value = false; stage.value = '' }
}
function startRename(row) {
  editingId.value = row.id
  editingName.value = nameOf(row)
}
function useForSimulation(row) {
  if (!reusableCv(row)) return
  router.push(context.value.caseId ? cvSelectionRoute(context.value.caseId, row.id) : jobsForResume(row.id))
}
async function saveRename(row) {
  if (rowBusy.value) return
  rowBusy.value = row.id
  try {
    await resumeAction(row.id, 'rename', { displayName: editingName.value.trim() }, { signal: controller.signal, silent: true })
    if (!active) return
    editingId.value = ''
    await refresh()
  } catch (error) {
    if (active) ElMessage.error(t(error.message || 'The CV name was not saved. Please retry.'))
  } finally { rowBusy.value = '' }
}
async function removeRow(row) {
  if (rowBusy.value) return
  try {
    await ElMessageBox.confirm(t('Delete "{name}"? Its extracted facts are removed and this cannot be undone.', { name: nameOf(row) }),
      t('Delete CV'), { type: 'warning', confirmButtonText: t('Delete CV'), cancelButtonText: t('Cancel'),
        confirmButtonClass: 'el-button--danger' })
  } catch { return }
  rowBusy.value = row.id
  try {
    const result = await deleteResume(row.id)
    if (!active) return
    ElMessage.success(t(result.data.deleted ? 'CV deleted.' : 'Access blocked. Deletion will retry automatically.'))
    await refresh()
  } catch (error) {
    if (active) ElMessage.error(t(error.message || 'Deletion could not finish. Reload its current status before retrying.'))
  } finally { rowBusy.value = '' }
}
let pdfTimer
onMounted(() => {
  refresh()
  pdfTimer = setInterval(() => {
    if (!busy.value && !rowBusy.value && !editingId.value && !listError.value
      && records.value.some(row => originalPdfStatus(row).pending)) refresh({ quiet: true })
  }, 10000)
})
onUnmounted(() => {
  active = false
  clearInterval(pdfTimer)
  controller.abort()
  records.value = []
  selected.value = null
  importPayload = undefined
})
</script>

<template>
  <CandidateLayout>
    <div class="page-heading"><div><p class="eyebrow">{{ $t("CANDIDATE WORKSPACE") }}</p>
      <h1>{{ $t("My CVs") }}</h1><p>{{ $t("Upload a CV, review the extracted facts and save your confirmed profile.") }}</p>
    </div></div>
    <el-alert v-if="context.caseId" class="context-notice" type="info" :closable="false" :title="$t('Review or choose a confirmed CV, then return to your simulation.')" />
    <el-button v-if="context.caseId" class="return-to-case" @click="router.push(cvSelectionRoute(context.caseId))">{{ $t('Back to CV selection') }}</el-button>
    <el-alert v-if="demoMode" :title="$t(&quot;Sign in to the connected service to upload a real CV.&quot;)"
      type="info" :closable="false" />
    <section class="upload-section" aria-labelledby="upload-title">
      <h2 id="upload-title">{{ $t("Upload your CV") }}</h2>
      <p>{{ $t("Your original PDF stays temporary. Review the extracted facts before confirming them.") }}</p>
      <CvUploadCard :selected="selected" :disabled="busy || !canUpload" @select="selectFile" />
      <label class="name-field"><span>{{ $t("CV name (optional)") }}</span>
        <el-input v-model="displayName" maxlength="120" show-word-limit :disabled="busy || !canUpload"
          :placeholder="defaultName || $t('For example: CV for IT, CV for product manager')" />
        <small>{{ $t("Give each CV a name to find it in your library. Leave empty to use the file name.") }}</small>
      </label>
      <el-alert v-if="uploadError" :title="$t(uploadError)" type="error" show-icon :closable="false" />
      <div class="upload-actions">
        <el-button type="primary" size="large" :loading="busy"
          :disabled="!selected || !canUpload || busy" @click="upload">
          {{ $t(busy ? stage : uploadError ? 'Retry upload' : 'Upload and extract facts') }}
        </el-button>
        <span role="status" aria-live="polite">{{ $t(busy ? 'Keep this page open while your PDF uploads.' : 'PDF only · Up to 10 MB · 1–20 pages') }}</span>
      </div>
      <p class="retention">{{ $t("Unconfirmed CVs expire after 24 hours. Confirmed CVs expire after 30 days. Confirmation requests cleanup of the original PDF; check each CV's PDF status for completion.") }}</p>
    </section>
    <section class="library" aria-labelledby="library-title" :aria-busy="loading">
      <div class="library-heading"><h2 id="library-title">{{ $t("Your CV library") }}</h2>
        <el-button :loading="refreshing" :disabled="busy || demoMode" @click="refresh">{{ $t("Refresh list") }}</el-button>
      </div>
      <el-alert v-if="listError" :title="$t(listError)" type="error" show-icon :closable="false" />
      <p v-if="loading" role="status">{{ $t("Loading your CVs...") }}</p>
      <el-empty v-else-if="!records.length && !listError" :description="$t(&quot;No CVs yet. Upload your first PDF above.&quot;)" />
      <el-table v-if="records.length" :data="records" class="cv-table">
        <el-table-column :label="$t(&quot;Name&quot;)" min-width="220"><template #default="{ row }">
          <div v-if="editingId === row.id" class="rename">
            <el-input v-model="editingName" size="small" maxlength="120" :aria-label="$t('CV name')"
              @keyup.enter="saveRename(row)" @keyup.esc="editingId = ''" />
            <el-button size="small" type="primary" :loading="rowBusy === row.id" @click="saveRename(row)">{{ $t("Save") }}</el-button>
            <el-button size="small" :disabled="rowBusy === row.id" @click="editingId = ''">{{ $t("Cancel") }}</el-button>
          </div>
          <div v-else class="cv-name"><strong>{{ nameOf(row) }}</strong>
            <el-button v-if="canEdit" link type="primary" size="small" :disabled="busy || !!rowBusy" @click="startRename(row)">{{ $t("Rename") }}</el-button>
          </div>
        </template></el-table-column>
        <el-table-column prop="filename" :label="$t(&quot;File&quot;)" min-width="180" />
        <el-table-column :label="$t(&quot;Status&quot;)" min-width="160"><template #default="{ row }">
          <el-tag :type="row.status === 'FAILED' ? 'danger' : row.confirmed ? 'success' : 'info'">
            {{ $t(row.cloud_revision === row.revision ? 'Published' : statusLabels[row.status] || row.status) }}
          </el-tag>
          <OriginalPdfStatus :record="row" compact />
        </template></el-table-column>
        <el-table-column :label="$t(&quot;Expires&quot;)" min-width="160"><template #default="{ row }">
          {{ $t(row.expires_at ? new Date(row.expires_at).toLocaleString(dateLocale) : '—') }}
        </template></el-table-column>
        <el-table-column :label="$t(&quot;Action&quot;)" min-width="300"><template #default="{ row }">
          <div class="row-actions">
            <el-button v-if="reusableCv(row)" size="small" type="primary" :disabled="busy || !!rowBusy" @click="useForSimulation(row)">{{ $t(context.caseId ? 'Use for this simulation' : 'Use for a simulation') }}</el-button>
            <el-button size="small" :disabled="busy" @click="router.push(cvReviewRoute(row.id, context.caseId))">
              {{ $t(row.confirmed ? 'View facts' : 'Review facts') }}</el-button>
            <el-button v-if="canEdit && reusableCv(row)" size="small" :disabled="busy || !!rowBusy" @click="router.push(cvRevisionRoute(row.id, context.caseId))">{{ $t('Edit a revised copy') }}</el-button>
            <el-button v-if="canDelete" size="small" type="danger" plain :loading="rowBusy === row.id"
              :disabled="busy || (!!rowBusy && rowBusy !== row.id)" @click="removeRow(row)">{{ $t("Delete") }}</el-button>
          </div>
        </template></el-table-column>
      </el-table>
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 30px; color: var(--sr-ink); letter-spacing: -0.7px; }
h2 { margin: 0 0 12px; font-size: 20px; color: var(--sr-text); }
p { color: var(--sr-text-muted); line-height: 1.7; font-size: 14px; }
.eyebrow { color: var(--sr-primary); font-size: 11px; font-weight: 700; letter-spacing: 1.4px; }
.page-heading { margin-bottom: 26px; }
.return-to-case { margin-top:14px; }
.upload-section, .library { padding: 28px; margin-top: 24px; background: white;
  border: 1px solid var(--sr-border); border-radius: 12px; min-width: 0; }
.upload-actions, .library-heading { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
.upload-actions { margin-top: 20px; }
.upload-actions span, .retention { font-size: 12px; color: var(--sr-text-muted); }
.library-heading { justify-content: space-between; margin-bottom: 18px; }
.library-heading h2 { margin: 0; }
.el-alert { margin-top: 18px; }
.name-field { display: flex; flex-direction: column; gap: 6px; margin-top: 18px; max-width: 520px; color: var(--sr-text-secondary); font-size: 14px; }
.name-field small, .pdf-note { color: var(--sr-text-muted); font-size: 12px; }
.pdf-note { display: block; margin-top: 4px; }
.cv-name, .rename, .row-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cv-name strong { color: var(--sr-ink); overflow-wrap: anywhere; }
.rename .el-input { flex: 1 1 140px; }
.row-actions .el-button { margin: 0; }
@media (max-width: 600px) {
  .upload-section, .library { padding: 18px; }
  .upload-actions .el-button { width: 100%; font-size: 13px; }
}
</style>
