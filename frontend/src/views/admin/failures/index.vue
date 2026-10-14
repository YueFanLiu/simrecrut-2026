<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDrawer, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { failureStages, failureStatuses, filterFailures, loadFailures, readFailure, validateFailureDates } from '../../../mock/admin-failures.js'
import { adminFailuresScenario } from '../../../config/demo.js'
const auth = useAuthStore(), router = useRouter()
const rows = ref([]), loading = ref(false), error = ref(''), drawer = ref(false), detail = ref(null), detailLoading = ref(false), detailError = ref(''), selectedId = ref('')
const page = ref(1), pageSize = ref(5)
const filters = reactive({ keyword: '', stage: '', status: '', from: '', to: '' })
const dateError = computed(() => validateFailureDates(filters))
const filtered = computed(() => filterFailures(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const date = value => new Date(value).toUTCString()
const retryExplanation = row => row.retryable ? 'Manual retry is unavailable until a supported Admin operation is implemented.' : 'This task is not retryable. Manual retry is unavailable.'
let active = true, listSequence = 0, detailSequence = 0
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
function resetFilters() { Object.assign(filters, { keyword: '', stage: '', status: '', from: '', to: '' }) }
async function refresh() {
  const request = ++listSequence, user = auth.demoUser
  const current = () => active && request === listSequence && auth.demoUser === user
  detailSequence += 1; drawer.value = false; detail.value = null; rows.value = []; error.value = ''; loading.value = true
  try { const result = await loadFailures(user, adminFailuresScenario); if (current()) { rows.value = result; page.value = 1 } }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
async function view(taskId) {
  const request = ++detailSequence, user = auth.demoUser
  const current = () => active && request === detailSequence && drawer.value && auth.demoUser === user
  selectedId.value = taskId; drawer.value = true; detail.value = null; detailError.value = ''; detailLoading.value = true
  try { const result = await readFailure(taskId, user, adminFailuresScenario); if (current()) detail.value = result }
  catch (failure) { if (current()) detailError.value = failure.message }
  finally { if (current()) detailLoading.value = false }
}
onBeforeUnmount(() => { active = false; listSequence += 1; detailSequence += 1 })
refresh()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="failures-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Failures</p><h1 id="failures-title">Failed Jobs &amp; Errors</h1><p>Inspect failed background tasks and safe technical metadata.</p></div><div class="actions"><ElButton :loading="loading" :disabled="loading" @click="refresh">Refresh</ElButton><ElButton @click="router.push({ name: 'AdminHealth' })">Service Health</ElButton><ElButton @click="router.push({ name: 'AdminAudit' })">Audit</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only. These tasks and errors are synthetic and independent of existing cases. Manual Admin retry is not connected; no task is executed or repaired." />
      <form class="filters panel" @submit.prevent="page = 1">
        <label class="search">Search<ElInput v-model="filters.keyword" aria-label="Search failed tasks" placeholder="Task ID, resource ID or safe error" clearable /></label>
        <label>Stage<ElSelect v-model="filters.stage" aria-label="Filter by stage" placeholder="All stages" clearable><ElOption v-for="stage in failureStages" :key="stage" :label="stage" :value="stage" /></ElSelect></label>
        <label>Status<ElSelect v-model="filters.status" aria-label="Filter by status" placeholder="All statuses" clearable><ElOption v-for="status in failureStatuses" :key="status" :label="status" :value="status" /></ElSelect></label>
        <label>From (UTC)<ElInput v-model="filters.from" aria-label="From date" placeholder="YYYY-MM-DD" maxlength="10" clearable /></label><label>To (UTC)<ElInput v-model="filters.to" aria-label="To date" placeholder="YYYY-MM-DD" maxlength="10" clearable /></label>
        <ElButton native-type="button" @click="resetFilters">Reset Filters</ElButton>
      </form>
      <ElAlert v-if="dateError" class="notice" type="error" :closable="false" :title="dateError" />
      <div class="panel">
        <div class="list-heading"><h2>Failed Tasks</h2><span>{{ filtered.length }} {{ filtered.length === 1 ? 'task' : 'tasks' }}</span></div>
        <ElSkeleton v-if="loading" :rows="7" animated />
        <ElAlert v-else-if="error" type="error" :closable="false" :title="error" />
        <ElEmpty v-else-if="dateError" description="Correct the date range to view tasks." />
        <ElEmpty v-else-if="!filtered.length" :description="rows.length ? 'No failed tasks match your filters.' : 'No failed tasks are available.'" />
        <template v-else>
          <table class="failure-table"><thead><tr><th>Task / Resource</th><th>Stage</th><th>Error Code</th><th>Retryable</th><th>Cleanup</th><th>Occurred At (UTC)</th><th>Actions</th></tr></thead><tbody><tr v-for="row in visible" :key="row.taskId"><th scope="row">{{ row.taskId }}<p>{{ row.resourceId }}</p></th><td>{{ row.stage }}<p><ElTag :type="row.retryable ? 'warning' : 'danger'">{{ row.status }}</ElTag></p></td><td>{{ row.errorCode }}<p>{{ row.message }}</p></td><td>{{ row.retryable ? 'Yes' : 'No' }}</td><td>{{ row.cleanupStatus }}</td><td>{{ date(row.occurredAt) }}</td><td><div class="row-actions"><ElButton type="primary" plain @click="view(row.taskId)">View</ElButton><ElButton disabled :title="retryExplanation(row)">Retry</ElButton></div></td></tr></tbody></table>
          <div class="failure-cards"><article v-for="row in visible" :key="row.taskId"><h3>{{ row.taskId }}</h3><ElTag :type="row.retryable ? 'warning' : 'danger'">{{ row.status }}</ElTag><dl><dt>Resource</dt><dd>{{ row.resourceId }}</dd><dt>Stage</dt><dd>{{ row.stage }}</dd><dt>Error Code</dt><dd>{{ row.errorCode }}</dd><dt>Retryable</dt><dd>{{ row.retryable ? 'Yes' : 'No' }}</dd><dt>Cleanup</dt><dd>{{ row.cleanupStatus }}</dd><dt>Occurred At (UTC)</dt><dd>{{ date(row.occurredAt) }}</dd></dl><p>{{ row.message }}</p><div class="row-actions"><ElButton type="primary" plain @click="view(row.taskId)">View</ElButton><ElButton disabled :title="retryExplanation(row)">Retry</ElButton></div></article></div>
          <div class="pagination"><label>Tasks per page<ElSelect v-model="pageSize" aria-label="Tasks per page"><ElOption :value="5" label="5 / page" /><ElOption :value="10" label="10 / page" /></ElSelect></label><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" /></div>
        </template>
        <p class="retry-note">Retry is unavailable until a supported Admin operation is implemented. A future accepted retry means Queued, not repaired or completed.</p>
      </div>
    </section>
    <ElDrawer v-model="drawer" title="Task Technical Details" size="min(560px, 100vw)" @closed="detailSequence += 1">
      <ElSkeleton v-if="detailLoading" :rows="8" animated />
      <div v-else-if="detailError"><ElAlert type="error" :closable="false" :title="detailError" /><ElButton class="notice" @click="view(selectedId)">Refresh</ElButton></div>
      <div v-else-if="detail" class="details"><h2>{{ detail.taskId }}</h2><ElTag :type="detail.retryable ? 'warning' : 'danger'">{{ detail.status }}</ElTag><dl><dt>Resource Type</dt><dd>{{ detail.resourceType }}</dd><dt>Resource ID</dt><dd>{{ detail.resourceId }}</dd><dt>Failed Stage</dt><dd>{{ detail.stage }}</dd><dt>Error Code</dt><dd>{{ detail.errorCode }}</dd><dt>Retryable</dt><dd>{{ detail.retryable ? 'Yes' : 'No' }}</dd><dt>Cleanup Status</dt><dd>{{ detail.cleanupStatus }}</dd><dt>Occurred At (UTC)</dt><dd>{{ date(detail.occurredAt) }}</dd></dl><h3>Safe Error Summary</h3><p>{{ detail.message }}</p><h3>Retry Availability</h3><p>{{ retryExplanation(detail) }}</p><ElButton disabled :title="retryExplanation(detail)">Retry</ElButton><p>A future HTTP 202 response indicates Queued. Completion must be verified separately; successful stages and fixed versions must be retained.</p><ElAlert type="info" :closable="false" title="Synthetic metadata only. No raw logs, CV text, human reasons, secrets or stack traces are displayed." /></div>
    </ElDrawer>
  </AdminLayout>
</template>

<style scoped>
.heading, .actions, .list-heading, .row-actions, .pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }.heading, .list-heading, .pagination { justify-content: space-between; }.heading { margin-bottom: 24px; }.actions .el-button, .row-actions .el-button { margin-left: 0; }
h1 { font-size: 28px; margin: 8px 0; color: #213957; }h2 { font-size: 18px; }h3 { font-size: 15px; }p, dd { color: #586b83; line-height: 1.7; }.breadcrumb { font-size: 12px; }
.panel { border: 1px solid #e1eaf5; border-radius: 8px; padding: 22px; background: white; margin-top: 22px; min-width: 0; }.filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: end; }label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; min-width: 0; }label .el-select { width: 100%; }.filters .el-button { justify-self: start; }.notice { margin-top: 16px; }.list-heading { margin-bottom: 16px; }.list-heading h2 { margin: 0; }.list-heading span { font-size: 12px; color: #586b83; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }th, td { text-align: left; padding: 14px 8px; border-bottom: 1px solid #e1eaf5; overflow-wrap: anywhere; }thead { background: #f8fbff; color: #586b83; }tbody th { font-weight: 600; }td p, th p { margin: 6px 0; }th:first-child { width: 16%; }th:nth-child(3) { width: 22%; }th:last-child { width: 12%; }.row-actions { gap: 6px; }.row-actions .el-button { padding: 8px 10px; }.failure-cards { display: none; }.pagination { margin-top: 20px; }.pagination label { width: 120px; }.retry-note { font-size: 12px; margin-top: 20px; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 12px; font-size: 13px; }dt { font-weight: 600; color: #314866; }dd { margin: 0; overflow-wrap: anywhere; }.details { overflow-wrap: anywhere; }
@media (max-width: 1100px) { .failure-table { display: none; }.failure-cards { display: grid; gap: 20px; }.failure-cards article { padding-bottom: 18px; border-bottom: 1px solid #e1eaf5; } }
@media (max-width: 760px) { h1 { font-size: 24px; }.filters { grid-template-columns: minmax(0, 1fr); }.panel { padding: 16px; } }
@media (max-width: 480px) { dl { grid-template-columns: minmax(0, 1fr); gap: 6px; }dd { margin-bottom: 8px; }.pagination { justify-content: start; } }
</style>
