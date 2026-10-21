<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElOption, ElPagination, ElSelect, ElSkeleton, ElTabPane, ElTabs, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useHrJobsStore } from '../../../store/modules/hr-jobs.js'
import { criteria } from '../../../mock/hr-job-wizard.js'
import { waitForVersionDemo } from '../../../mock/hr-job-versions.js'
import { hrVersionsScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useHrJobsStore()
const data = ref(null)
const detail = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const detailError = ref('')
const cloneError = ref('')
const draftConflict = ref(false)
const selectedVersion = ref('')
const tab = ref('basic')
const page = ref(1)
const pageSize = ref(5)
const visible = computed(() => data.value?.versions.slice((page.value - 1) * pageSize.value, page.value * pageSize.value) || [])
const latestPublished = computed(() => data.value?.versions.find((entry) => entry.current && entry.status === 'Published'))
let active = true
let sequence = 0
watch(pageSize, () => { page.value = 1 })

function formatDate(value) {
  return value ? new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) : 'Not recorded in this demo'
}
function stillCurrent(request, username) {
  return active && sequence === request && auth.hasDemoRole('HR') && auth.demoUser.username === username
}
async function loadVersions() {
  const request = ++sequence
  const jobId = route.params.jobId
  const username = auth.demoUser?.username
  loading.value = true
  busy.value = false
  error.value = ''
  detailError.value = ''
  cloneError.value = ''
  draftConflict.value = false
  selectedVersion.value = ''
  detail.value = null
  data.value = null
  try {
    await waitForVersionDemo(hrVersionsScenario, 'load')
    if (!stillCurrent(request, username)) return
    data.value = await store.readVersions(auth.demoUser, jobId)
    if (hrVersionsScenario === 'empty') data.value.versions = []
    page.value = 1
  } catch (failure) { if (stillCurrent(request, username)) error.value = failure.message }
  finally { if (stillCurrent(request, username)) loading.value = false }
}
async function viewVersion(versionId) {
  if (busy.value) return
  const request = ++sequence
  const jobId = route.params.jobId
  const username = auth.demoUser?.username
  busy.value = true
  selectedVersion.value = versionId
  detail.value = null
  detailError.value = ''
  cloneError.value = ''
  draftConflict.value = false
  tab.value = 'basic'
  try {
    await waitForVersionDemo(hrVersionsScenario, 'detail')
    if (stillCurrent(request, username)) detail.value = await store.readVersion(auth.demoUser, jobId, versionId)
  } catch (failure) { if (stillCurrent(request, username)) detailError.value = failure.message }
  finally { if (stillCurrent(request, username)) busy.value = false }
}
async function newDraft(versionId) {
  if (busy.value) return
  const request = ++sequence
  const jobId = route.params.jobId
  const username = auth.demoUser?.username
  busy.value = true
  cloneError.value = ''
  draftConflict.value = false
  try {
    await waitForVersionDemo(hrVersionsScenario, 'clone')
    if (!stillCurrent(request, username)) return
    await store.cloneVersion(auth.demoUser, jobId, versionId)
    await router.push({ name: 'HrJobEdit', params: { jobId } })
  } catch (failure) {
    if (stillCurrent(request, username)) {
      cloneError.value = failure.message
      draftConflict.value = failure.code === 'DRAFT_EXISTS'
      if (draftConflict.value) data.value = await store.readVersions(auth.demoUser, jobId)
    }
  } finally { if (stillCurrent(request, username)) busy.value = false }
}
function editDraft() { router.push({ name: 'HrJobEdit', params: { jobId: route.params.jobId } }) }
function backToVersions() { sequence += 1; busy.value = false; detail.value = null; detailError.value = ''; selectedVersion.value = ''; cloneError.value = ''; draftConflict.value = false }
watch(() => route.params.jobId, loadVersions, { immediate: true })
onBeforeUnmount(() => { active = false; sequence += 1; data.value = null; detail.value = null })
</script>

<template>
  <HrLayout>
    <section aria-labelledby="versions-title" :aria-busy="loading || busy">
      <div class="page-heading"><div><h1 id="versions-title">Job Versions</h1><p>View published history and the current draft of a job you manage.</p></div><ElButton @click="router.push({ name: 'HrJobs' })">Back to Jobs</ElButton></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only. Version history and new drafts use local mock data. Published configurations are read-only. Saved version metadata survives sign out and reload in this browser tab." />
      <div v-if="loading" class="panel state-panel" role="status"><p>Loading job versions...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>Versions Unavailable</h2><p>{{ error }}</p><ElButton type="primary" @click="loadVersions">Try Again</ElButton></div>
      <template v-else-if="data">
        <div class="panel job-summary"><div><h2>{{ data.title }}</h2><p>{{ data.department }} · {{ data.jobId }}</p><ElTag v-if="data.publishedVersion" type="success">Current published: {{ data.publishedVersion }}</ElTag><ElTag v-if="data.draftVersion" type="info">Draft: {{ data.draftVersion }}</ElTag></div>
          <div class="summary-actions"><ElButton v-if="data.draftVersion" type="primary" :disabled="busy" @click="editDraft">Edit Current Draft</ElButton><ElButton v-if="latestPublished" :disabled="busy || Boolean(data.draftVersion)" :loading="busy && !selectedVersion" :title="data.draftVersion ? 'An editable draft already exists. Open it instead.' : 'Copy the current published configuration into a new draft.'" @click="newDraft(latestPublished.versionId)">New Draft from {{ data.publishedVersion }}</ElButton><ElButton :disabled="busy" @click="loadVersions">Refresh</ElButton></div>
          <p v-if="data.draftVersion" class="draft-notice">Only one editable draft is allowed. Open the current draft to continue.</p>
        </div>
        <div v-if="cloneError" class="panel error-panel" role="alert"><p>{{ cloneError }}</p><ElButton v-if="draftConflict" :disabled="busy" @click="editDraft">Open Existing Draft</ElButton></div>
        <template v-if="selectedVersion">
          <div class="detail-toolbar"><ElButton @click="backToVersions">Back to Versions</ElButton></div>
          <div v-if="busy && !detail" class="panel" role="status"><p>Loading version details...</p><ElSkeleton :rows="6" animated /></div>
          <div v-else-if="detailError" class="panel error-panel" role="alert"><h2>Version Could Not Be Loaded</h2><p>{{ detailError }}</p><ElButton type="primary" @click="viewVersion(selectedVersion)">Retry Version</ElButton></div>
          <article v-else-if="detail" class="panel detail-panel" aria-label="Read-only version details">
            <div class="page-heading"><div><h2>{{ detail.title }} — Version {{ detail.version }}</h2><p>{{ formatDate(detail.createdAt) }} · {{ detail.createdBy }} · Revision {{ detail.revision }}</p></div><ElTag :type="detail.status === 'Published' ? 'success' : 'info'">{{ detail.status }} · Read-only view</ElTag></div>
            <p>{{ detail.changeSummary }}</p><p v-if="detail.status === 'Draft'">This panel previews saved draft data. Continue editing and publish through the job wizard.</p>
            <ElTabs v-model="tab">
              <ElTabPane label="Basic Information" name="basic"><template #label><span class="desktop-tab-label">Basic Information</span><span class="mobile-tab-label">Basic</span></template><dl><dt>Job Title</dt><dd>{{ detail.title }}</dd><dt>Department</dt><dd>{{ detail.department }}</dd><dt>Employment Type</dt><dd>{{ detail.employmentType }}</dd><dt>Experience Level</dt><dd>{{ detail.experienceLevel }}</dd><dt>Location</dt><dd>{{ detail.location || 'Not specified' }}</dd><dt>Positions</dt><dd>{{ detail.positions }}</dd></dl><h3>Job Description</h3><blockquote>{{ detail.description }}</blockquote></ElTabPane>
              <ElTabPane label="Requirements" name="requirements"><section v-for="category in criteria" :key="category" :aria-label="`${category} version requirements`"><h3>{{ category }}</h3><p v-if="!detail.requirements.some(row => row.category === category)">No requirements in this group.</p><article v-for="row in detail.requirements.filter(item => item.category === category)" :key="row.id" class="requirement"><p>{{ row.text || 'No requirement text saved' }}</p><p v-if="row.equivalents">Accepted equivalents: {{ row.equivalents }}</p><div class="flags"><ElTag :type="row.mandatory ? 'warning' : 'info'">{{ row.mandatory ? 'Mandatory' : 'Optional' }}</ElTag><ElTag>{{ row.assessmentIncluded ? 'Assessed' : 'Excluded from assessment' }}</ElTag><ElTag>{{ row.publicVisible ? 'Visible to Candidates' : 'Hidden from Candidates' }}</ElTag><ElTag :type="row.reviewed ? 'success' : 'warning'">{{ row.reviewed ? 'Reviewed' : 'Needs review' }}</ElTag></div><details v-if="row.evidenceExcerpt"><summary>Source Evidence</summary><p>{{ row.evidenceLocation }}</p><blockquote>{{ row.evidenceExcerpt }}</blockquote></details></article></section></ElTabPane>
              <ElTabPane label="Weights" name="weights"><p>Values are saved fractions displayed as percentages. Inactive categories are not assessed.</p><dl><template v-for="(category, index) in criteria" :key="category"><dt>{{ category }}</dt><dd>{{ detail.percentages[index] }}% · {{ detail.activeCriteria.includes(category) ? 'Active' : 'Not assessed' }}</dd></template></dl><p v-if="detail.status === 'Draft'">Draft weights may be incomplete. The wizard validates them before publication.</p></ElTabPane>
            </ElTabs>
            <div class="detail-actions"><ElButton v-if="detail.status === 'Draft'" type="primary" :disabled="busy" @click="editDraft">Edit Current Draft</ElButton><ElButton v-else :disabled="busy || Boolean(data.draftVersion)" :loading="busy" @click="newDraft(detail.versionId)">New Draft from Version {{ detail.version }}</ElButton></div>
          </article>
        </template>
        <div v-else-if="!data.versions.length" class="panel state-panel"><ElEmpty description="No versions available" /><p>No version history is available in this mock view.</p></div>
        <template v-else>
          <div class="panel desktop-list"><div class="table-scroll" tabindex="0" role="region" aria-label="Job versions table"><table><caption>Published history is immutable. Drafts are published through the wizard.</caption><thead><tr><th scope="col">Version</th><th scope="col">Status</th><th scope="col">Created Date (UTC)</th><th scope="col">Created By</th><th scope="col">Changes Summary</th><th scope="col">Actions</th></tr></thead><tbody><tr v-for="version in visible" :key="version.versionId"><th scope="row">{{ version.version }}<span v-if="version.current" class="current-label">Current published</span></th><td><ElTag :type="version.status === 'Published' ? 'success' : 'info'">{{ version.status }}</ElTag></td><td>{{ formatDate(version.createdAt) }}</td><td>{{ version.createdBy }}</td><td>{{ version.changeSummary }}</td><td><div class="row-actions"><ElButton size="small" :disabled="busy" @click="viewVersion(version.versionId)">View Version {{ version.version }}</ElButton><ElButton v-if="version.status === 'Draft'" size="small" :disabled="busy" @click="editDraft">Edit Draft</ElButton></div></td></tr></tbody></table></div></div>
          <div class="mobile-list"><article v-for="version in visible" :key="version.versionId" class="panel"><div class="card-heading"><h3>Version {{ version.version }}</h3><ElTag :type="version.status === 'Published' ? 'success' : 'info'">{{ version.status }}</ElTag></div><p v-if="version.current" class="current-label">Current published</p><p>{{ formatDate(version.createdAt) }} · {{ version.createdBy }}</p><p>{{ version.changeSummary }}</p><div class="row-actions"><ElButton :disabled="busy" @click="viewVersion(version.versionId)">View Version {{ version.version }}</ElButton><ElButton v-if="version.status === 'Draft'" :disabled="busy" @click="editDraft">Edit Draft</ElButton></div></article></div>
          <div class="pagination"><span>{{ data.versions.length }} {{ data.versions.length === 1 ? 'version' : 'versions' }}</span><ElSelect v-model="pageSize" aria-label="Versions per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :label="`${size} / page`" :value="size" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="data.versions.length" layout="prev, pager, next" :pager-count="5" background /></div>
        </template>
      </template>
    </section>
  </HrLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { color: #132941; font-size: 20px; } h3 { color: #132941; font-size: 16px; } p, dd { color: #586b83; font-size: 14px; line-height: 1.8; }
.page-heading, .job-summary, .card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; } .page-heading { margin-bottom: 20px; }
.panel { background: white; border: 1px solid #e1eaf5; border-radius: 12px; padding: 24px; margin-top: 20px; } .state-panel { text-align: center; padding: 36px 20px; } .summary-actions, .row-actions, .flags { display: flex; gap: 8px; flex-wrap: wrap; } .el-button { margin: 0; max-width: 100%; } .job-summary .el-tag { margin-right: 8px; margin-bottom: 6px; } .draft-notice { width: 100%; margin: 0; font-size: 12px; }
.table-scroll { overflow-x: auto; } table { width: 100%; min-width: 780px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } caption { text-align: left; padding-bottom: 16px; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; } thead th { background: #f3f7fd; color: #132941; } tbody th { color: #176bdf; } .current-label { display: block; font-size: 12px; font-weight: 500; color: #176bdf; }
.mobile-tab-label { display: none; } .mobile-list { display: none; } .pagination { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 16px; margin: 24px 0; font-size: 12px; color: #586b83; } .pagination .el-select { width: 120px; }
.detail-toolbar { margin-top: 20px; } .detail-actions { border-top: 1px solid #e1eaf5; margin-top: 20px; padding-top: 20px; } dl { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 12px; font-size: 14px; } dt { color: #132941; } dd { margin: 0; overflow-wrap: anywhere; } blockquote { background: #f3f7fd; padding: 14px; margin: 14px 0; color: #586b83; line-height: 1.8; font-size: 13px; white-space: pre-wrap; overflow-wrap: anywhere; }
.requirement { background: #f8fbff; border: 1px solid #e1eaf5; padding: 16px; border-radius: 8px; margin: 12px 0; } .requirement p { overflow-wrap: anywhere; } summary { color: #176bdf; cursor: pointer; margin-top: 16px; font-size: 13px; } .error-panel p { color: #b83232; } .table-scroll:focus-visible { outline: 2px solid #176bdf; }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; } .panel { padding: 20px 16px; } h1 { font-size: 25px; } }
@media (max-width: 600px) { .desktop-tab-label { display: none; } .mobile-tab-label { display: inline; } :deep(.el-tabs__item) { font-size: 12px; padding: 0 10px; } dl { grid-template-columns: minmax(0, 1fr); gap: 6px; } dd { margin-bottom: 12px; } .pagination { justify-content: center; } }
</style>
