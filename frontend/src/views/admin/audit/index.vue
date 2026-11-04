<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDrawer, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { auditActions, auditOutcomes, auditResourceTypes, loadAuditEvents, validateAuditDates } from '../../../mock/admin-audit.js'
import { adminAuditScenario } from '../../../config/demo.js'
const auth = useAuthStore()
const blankFilters = () => ({ actionCode: '', actor: '', resourceType: '', outcome: '', from: '', to: '' })
const filters = reactive(blankFilters()), applied = ref(blankFilters())
const items = ref([]), total = ref(0), page = ref(1), pageSize = ref(5), loading = ref(false), error = ref(''), validationError = ref(''), selected = ref(null)
const date = value => new Date(value).toUTCString()
let active = true, sequence = 0
async function load() {
  const request = ++sequence, user = auth.demoUser, query = { ...applied.value }, currentPage = page.value, size = pageSize.value
  const current = () => active && request === sequence && auth.demoUser === user
  loading.value = true; error.value = ''; items.value = []; total.value = 0; selected.value = null
  try { const result = await loadAuditEvents(user, query, currentPage, size, adminAuditScenario); if (current()) { items.value = result.items; total.value = result.total; page.value = result.page } }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
function search() {
  validationError.value = validateAuditDates(filters)
  if (validationError.value) return
  applied.value = { ...filters }; page.value = 1; load()
}
function clearFilters() { Object.assign(filters, blankFilters()); validationError.value = ''; applied.value = blankFilters(); page.value = 1; load() }
function changePage(value) { page.value = value; load() }
function changeSize() { page.value = 1; load() }
onBeforeUnmount(() => { active = false; sequence += 1 })
load()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="audit-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Audit</p><h1 id="audit-title">Audit Logs</h1><p>Inspect read-only operational events and safe metadata.</p></div><ElButton :loading="loading" :disabled="loading" @click="load">Refresh</ElButton></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration. Job publications, model approvals/releases and review submissions in this tab create safe local events alongside bundled examples. No CV facts, scores or review reasons are logged. This is not a real audit trail." />
      <form class="filters panel" @submit.prevent="search">
        <label>Action Code<ElSelect v-model="filters.actionCode" aria-label="Action Code" placeholder="All actions" clearable><ElOption v-for="action in auditActions" :key="action" :value="action" :label="action" /></ElSelect></label>
        <label>Actor<ElInput v-model="filters.actor" aria-label="Actor reference" placeholder="Actor reference, e.g. DEMO-ACTOR-001" clearable /></label>
        <label>Resource Type<ElSelect v-model="filters.resourceType" aria-label="Resource Type" placeholder="All resource types" clearable><ElOption v-for="type in auditResourceTypes" :key="type" :value="type" :label="type" /></ElSelect></label>
        <label>From (UTC)<ElInput v-model="filters.from" aria-label="From date" placeholder="YYYY-MM-DD" maxlength="10" clearable /></label>
        <label>To (UTC)<ElInput v-model="filters.to" aria-label="To date" placeholder="YYYY-MM-DD" maxlength="10" clearable /></label>
        <label>Outcome<ElSelect v-model="filters.outcome" aria-label="Outcome" placeholder="All outcomes" clearable><ElOption v-for="outcome in auditOutcomes" :key="outcome" :value="outcome" :label="outcome" /></ElSelect></label>
        <div class="actions"><ElButton type="primary" native-type="submit" :disabled="loading">Search</ElButton><ElButton :disabled="loading" @click="clearFilters">Clear Filters</ElButton></div><p class="filter-note">Search applies the filters. Refresh and pagination use the last applied query.</p>
      </form>
      <ElAlert v-if="validationError" class="notice" type="error" :closable="false" :title="validationError" />
      <div class="panel"><div class="list-heading"><h2>Operational Events</h2><span>{{ total }} {{ total === 1 ? 'event' : 'events' }}</span></div>
        <ElSkeleton v-if="loading" :rows="7" animated />
        <ElAlert v-else-if="error" type="error" :closable="false" :title="error" />
        <ElEmpty v-else-if="!items.length" :description="adminAuditScenario === 'empty' ? 'No audit events are available.' : 'No audit events match the applied filters.'" />
        <template v-else>
          <table class="audit-table"><thead><tr><th>Time (UTC)</th><th>Actor</th><th>Action Code</th><th>Resource</th><th>Outcome</th><th>Action</th></tr></thead><tbody><tr v-for="event in items" :key="event.eventId"><td>{{ date(event.occurredAt) }}</td><td>{{ event.actor || 'Unavailable / removed actor' }}</td><td>{{ event.actionCode }}<p>{{ event.summary }}</p></td><td>{{ event.resourceType }}<p>{{ event.resourceId || 'Unavailable' }}</p></td><td><ElTag :type="event.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ event.outcome }}</ElTag></td><td><ElButton type="primary" plain @click="selected = event">View</ElButton></td></tr></tbody></table>
          <div class="audit-cards"><article v-for="event in items" :key="event.eventId"><h3>{{ event.actionCode }}</h3><ElTag :type="event.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ event.outcome }}</ElTag><dl><dt>Time (UTC)</dt><dd>{{ date(event.occurredAt) }}</dd><dt>Actor</dt><dd>{{ event.actor || 'Unavailable / removed actor' }}</dd><dt>Resource Type</dt><dd>{{ event.resourceType }}</dd><dt>Resource ID</dt><dd>{{ event.resourceId || 'Unavailable' }}</dd></dl><p>{{ event.summary }}</p><ElButton type="primary" plain @click="selected = event">View</ElButton></article></div>
          <div class="pagination"><label>Events per page<ElSelect v-model="pageSize" aria-label="Events per page" :disabled="loading" @change="changeSize"><ElOption :value="5" label="5 / page" /><ElOption :value="10" label="10 / page" /></ElSelect></label><ElPagination :current-page="page" :page-size="pageSize" :total="total" :disabled="loading" layout="prev, pager, next" @current-change="changePage" /></div>
        </template>
      </div>
    </section>
    <ElDrawer :model-value="!!selected" title="Audit Event Details" size="min(560px, 100vw)" @close="selected = null">
      <div v-if="selected" class="details"><h2>{{ selected.actionCode }}</h2><ElTag :type="selected.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ selected.outcome }}</ElTag><dl><template v-for="(label, key) in { eventId: 'Event ID', actor: 'Actor', actionCode: 'Action Code', resourceType: 'Resource Type', resourceId: 'Resource ID', fromState: 'From State', toState: 'To State', requestId: 'Request ID' }" :key="key"><dt>{{ label }}</dt><dd>{{ selected[key] || 'Unavailable' }}</dd></template><dt>Time (UTC)</dt><dd>{{ date(selected.occurredAt) }}</dd></dl><h3>Safe Event Summary</h3><p>{{ selected.summary }}</p><p>From / To describe operational state only. Missing actor references and resource identifiers are preserved as unavailable.</p><ElAlert type="info" :closable="false" title="Read-only synthetic metadata. No content snapshots, review decisions/reasons, IP addresses, session identifiers or raw logs are included." /></div>
    </ElDrawer>
  </AdminLayout>
</template>

<style scoped>
.heading, .actions, .list-heading, .pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }.heading, .list-heading, .pagination { justify-content: space-between; }.heading { margin-bottom: 24px; }.actions .el-button { margin-left: 0; }
h1 { font-size: 28px; margin: 8px 0; color: #213957; }h2 { font-size: 18px; }h3 { font-size: 15px; }p, dd { color: #586b83; line-height: 1.7; }.breadcrumb, .filter-note { font-size: 12px; }
.panel { border: 1px solid #e1eaf5; border-radius: 8px; padding: 22px; background: white; margin-top: 22px; min-width: 0; }.filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: end; }label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; min-width: 0; }label .el-select { width: 100%; }.filter-note { grid-column: span 2; margin: 0; }.notice { margin-top: 16px; }.list-heading { margin-bottom: 16px; }.list-heading h2 { margin: 0; }.list-heading span { font-size: 12px; color: #586b83; }
table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }th, td { text-align: left; padding: 14px 8px; border-bottom: 1px solid #e1eaf5; overflow-wrap: anywhere; }thead { background: #f8fbff; color: #586b83; }td p { margin: 6px 0; }th:nth-child(3) { width: 27%; }th:last-child { width: 10%; }.audit-cards { display: none; }.pagination { margin-top: 20px; }.pagination label { width: 130px; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 12px; font-size: 13px; }dt { font-weight: 600; color: #314866; }dd { margin: 0; overflow-wrap: anywhere; }.details, h3 { overflow-wrap: anywhere; }
@media (max-width: 1100px) { .audit-table { display: none; }.audit-cards { display: grid; gap: 20px; }.audit-cards article { padding-bottom: 18px; border-bottom: 1px solid #e1eaf5; } }
@media (max-width: 760px) { h1 { font-size: 24px; }.filters { grid-template-columns: minmax(0, 1fr); }.filter-note { grid-column: auto; }.panel { padding: 16px; } }
@media (max-width: 480px) { dl { grid-template-columns: minmax(0, 1fr); gap: 6px; }dd { margin-bottom: 8px; }.pagination { justify-content: start; } }
</style>
