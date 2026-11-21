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
      <div class="heading"><div><p class="breadcrumb">{{ $t("Administration / Failures") }}</p><h1 id="failures-title">{{ $t("Failed Jobs & Errors") }}</h1><p>{{ $t("Inspect failed background tasks and safe technical metadata.") }}</p></div><div class="actions"><ElButton :loading="loading" :disabled="loading" @click="refresh">{{ $t("Refresh") }}</ElButton><ElButton @click="router.push({ name: 'AdminHealth' })">{{ $t("Service Health") }}</ElButton><ElButton @click="router.push({ name: 'AdminAudit' })">{{ $t("Audit") }}</ElButton></div></div>
      <ElAlert type="info" :closable="false" :title="$t(&quot;Frontend demo only. These tasks and errors are synthetic and independent of existing cases. Manual Admin retry is not connected; no task is executed or repaired.&quot;)" />
      <form class="filters panel" @submit.prevent="page = 1">
        <label class="search">{{ $t("Search") }}<ElInput v-model="filters.keyword" :aria-label="$t(&quot;Search failed tasks&quot;)" :placeholder="$t(&quot;Task ID, resource ID or safe error&quot;)" clearable /></label>
        <label>{{ $t("Stage") }}<ElSelect v-model="filters.stage" :aria-label="$t(&quot;Filter by stage&quot;)" :placeholder="$t(&quot;All stages&quot;)" clearable><ElOption v-for="stage in failureStages" :key="stage" :label="$t(stage)" :value="stage" /></ElSelect></label>
        <label>{{ $t("Status") }}<ElSelect v-model="filters.status" :aria-label="$t(&quot;Filter by status&quot;)" :placeholder="$t(&quot;All statuses&quot;)" clearable><ElOption v-for="status in failureStatuses" :key="status" :label="$t(status)" :value="status" /></ElSelect></label>
        <label>{{ $t("From (UTC)") }}<ElInput v-model="filters.from" :aria-label="$t(&quot;From date&quot;)" :placeholder="$t(&quot;YYYY-MM-DD&quot;)" maxlength="10" clearable /></label><label>{{ $t("To (UTC)") }}<ElInput v-model="filters.to" :aria-label="$t(&quot;To date&quot;)" :placeholder="$t(&quot;YYYY-MM-DD&quot;)" maxlength="10" clearable /></label>
        <ElButton native-type="button" @click="resetFilters">{{ $t("Reset Filters") }}</ElButton>
      </form>
      <ElAlert v-if="dateError" class="notice" type="error" :closable="false" :title="$t(dateError)" />
      <div class="panel">
        <div class="list-heading"><h2>{{ $t("Failed Tasks") }}</h2><span>{{ filtered.length }} {{ $t(filtered.length === 1 ? 'task' : 'tasks') }}</span></div>
        <ElSkeleton v-if="loading" :rows="7" animated />
        <ElAlert v-else-if="error" type="error" :closable="false" :title="$t(error)" />
        <ElEmpty v-else-if="dateError" :description="$t(&quot;Correct the date range to view tasks.&quot;)" />
        <ElEmpty v-else-if="!filtered.length" :description="$t(rows.length ? 'No failed tasks match your filters.' : 'No failed tasks are available.')" />
        <template v-else>
          <table class="failure-table"><thead><tr><th>{{ $t("Task / Resource") }}</th><th>{{ $t("Stage") }}</th><th>{{ $t("Error Code") }}</th><th>{{ $t("Retryable") }}</th><th>{{ $t("Cleanup") }}</th><th>{{ $t("Occurred At (UTC)") }}</th><th>{{ $t("Actions") }}</th></tr></thead><tbody><tr v-for="row in visible" :key="row.taskId"><th scope="row">{{ row.taskId }}<p>{{ row.resourceId }}</p></th><td>{{ row.stage }}<p><ElTag :type="row.retryable ? 'warning' : 'danger'">{{ $t(row.status) }}</ElTag></p></td><td>{{ $t(row.errorCode) }}<p>{{ $t(row.message) }}</p></td><td>{{ $t(row.retryable ? 'Yes' : 'No') }}</td><td>{{ $t(row.cleanupStatus) }}</td><td>{{ date(row.occurredAt) }}</td><td><div class="row-actions"><ElButton type="primary" plain @click="view(row.taskId)">{{ $t("View") }}</ElButton><ElButton disabled :title="$t(retryExplanation(row))">{{ $t("Retry") }}</ElButton></div></td></tr></tbody></table>
          <div class="failure-cards"><article v-for="row in visible" :key="row.taskId"><h3>{{ row.taskId }}</h3><ElTag :type="row.retryable ? 'warning' : 'danger'">{{ $t(row.status) }}</ElTag><dl><dt>{{ $t("Resource") }}</dt><dd>{{ row.resourceId }}</dd><dt>{{ $t("Stage") }}</dt><dd>{{ row.stage }}</dd><dt>{{ $t("Error Code") }}</dt><dd>{{ $t(row.errorCode) }}</dd><dt>{{ $t("Retryable") }}</dt><dd>{{ $t(row.retryable ? 'Yes' : 'No') }}</dd><dt>{{ $t("Cleanup") }}</dt><dd>{{ $t(row.cleanupStatus) }}</dd><dt>{{ $t("Occurred At (UTC)") }}</dt><dd>{{ date(row.occurredAt) }}</dd></dl><p>{{ $t(row.message) }}</p><div class="row-actions"><ElButton type="primary" plain @click="view(row.taskId)">{{ $t("View") }}</ElButton><ElButton disabled :title="$t(retryExplanation(row))">{{ $t("Retry") }}</ElButton></div></article></div>
          <div class="pagination"><label>{{ $t("Tasks per page") }}<ElSelect v-model="pageSize" :aria-label="$t(&quot;Tasks per page&quot;)"><ElOption :value="5" :label="$t(&quot;5 / page&quot;)" /><ElOption :value="10" :label="$t(&quot;10 / page&quot;)" /></ElSelect></label><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" /></div>
        </template>
        <p class="retry-note">{{ $t("Retry is unavailable until a supported Admin operation is implemented. A future accepted retry means Queued, not repaired or completed.") }}</p>
      </div>
    </section>
    <ElDrawer v-model="drawer" :title="$t(&quot;Task Technical Details&quot;)" size="min(560px, 100vw)" @closed="detailSequence += 1">
      <ElSkeleton v-if="detailLoading" :rows="8" animated />
      <div v-else-if="detailError"><ElAlert type="error" :closable="false" :title="$t(detailError)" /><ElButton class="notice" @click="view(selectedId)">{{ $t("Refresh") }}</ElButton></div>
      <div v-else-if="detail" class="details"><h2>{{ detail.taskId }}</h2><ElTag :type="detail.retryable ? 'warning' : 'danger'">{{ $t(detail.status) }}</ElTag><dl><dt>{{ $t("Resource Type") }}</dt><dd>{{ detail.resourceType }}</dd><dt>{{ $t("Resource ID") }}</dt><dd>{{ detail.resourceId }}</dd><dt>{{ $t("Failed Stage") }}</dt><dd>{{ detail.stage }}</dd><dt>{{ $t("Error Code") }}</dt><dd>{{ $t(detail.errorCode) }}</dd><dt>{{ $t("Retryable") }}</dt><dd>{{ $t(detail.retryable ? 'Yes' : 'No') }}</dd><dt>{{ $t("Cleanup Status") }}</dt><dd>{{ $t(detail.cleanupStatus) }}</dd><dt>{{ $t("Occurred At (UTC)") }}</dt><dd>{{ date(detail.occurredAt) }}</dd></dl><h3>{{ $t("Safe Error Summary") }}</h3><p>{{ $t(detail.message) }}</p><h3>{{ $t("Retry Availability") }}</h3><p>{{ retryExplanation(detail) }}</p><ElButton disabled :title="$t(retryExplanation(detail))">{{ $t("Retry") }}</ElButton><p>{{ $t("A future HTTP 202 response indicates Queued. Completion must be verified separately; successful stages and fixed versions must be retained.") }}</p><ElAlert type="info" :closable="false" :title="$t(&quot;Synthetic metadata only. No raw logs, CV text, human reasons, secrets or stack traces are displayed.&quot;)" /></div>
    </ElDrawer>
  </AdminLayout>
</template>

<style scoped>
.heading, .actions, .list-heading, .row-actions, .pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }.heading, .list-heading, .pagination { justify-content: space-between; }.heading { margin-bottom: 24px; }.actions .el-button, .row-actions .el-button { margin-left: 0; }
h1 { font-size: 28px; margin: 8px 0; color: var(--sr-text); }h2 { font-size: 18px; }h3 { font-size: 15px; }p, dd { color: var(--sr-text-muted); line-height: 1.7; }.breadcrumb { font-size: 12px; }
.panel { border: 1px solid var(--sr-border); border-radius: 8px; padding: 22px; background: white; margin-top: 22px; min-width: 0; }.filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: end; }label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; min-width: 0; }label .el-select { width: 100%; }.filters .el-button { justify-self: start; }.notice { margin-top: 16px; }.list-heading { margin-bottom: 16px; }.list-heading h2 { margin: 0; }.list-heading span { font-size: 12px; color: var(--sr-text-muted); }
table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }th, td { text-align: left; padding: 14px 8px; border-bottom: 1px solid var(--sr-border); overflow-wrap: anywhere; }thead { background: var(--sr-surface-tint); color: var(--sr-text-muted); }tbody th { font-weight: 600; }td p, th p { margin: 6px 0; }th:first-child { width: 16%; }th:nth-child(3) { width: 22%; }th:last-child { width: 12%; }.row-actions { gap: 6px; }.row-actions .el-button { padding: 8px 10px; }.failure-cards { display: none; }.pagination { margin-top: 20px; }.pagination label { width: 120px; }.retry-note { font-size: 12px; margin-top: 20px; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 12px; font-size: 13px; }dt { font-weight: 600; color: var(--sr-text-secondary); }dd { margin: 0; overflow-wrap: anywhere; }.details { overflow-wrap: anywhere; }
@media (max-width: 1100px) { .failure-table { display: none; }.failure-cards { display: grid; gap: 20px; }.failure-cards article { padding-bottom: 18px; border-bottom: 1px solid var(--sr-border); } }
@media (max-width: 760px) { h1 { font-size: 24px; }.filters { grid-template-columns: minmax(0, 1fr); }.panel { padding: 16px; } }
@media (max-width: 480px) { dl { grid-template-columns: minmax(0, 1fr); gap: 6px; }dd { margin-bottom: 8px; }.pagination { justify-content: start; } }
</style>
