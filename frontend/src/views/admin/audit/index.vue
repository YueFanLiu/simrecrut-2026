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
      <div class="heading"><div><p class="breadcrumb">{{ $t("Administration / Audit") }}</p><h1 id="audit-title">{{ $t("Audit Logs") }}</h1><p>{{ $t("Inspect read-only operational events and safe metadata.") }}</p></div><ElButton :loading="loading" :disabled="loading" @click="load">{{ $t("Refresh") }}</ElButton></div>
      <ElAlert type="info" :closable="false" :title="$t(&quot;Frontend demonstration. Job publications, model approvals/releases and review submissions in this tab create safe local events alongside bundled examples. No CV facts, scores or review reasons are logged. This is not a real audit trail.&quot;)" />
      <form class="filters panel" @submit.prevent="search">
        <label>{{ $t("Action Code") }}<ElSelect v-model="filters.actionCode" :aria-label="$t(&quot;Action Code&quot;)" :placeholder="$t(&quot;All actions&quot;)" clearable><ElOption v-for="action in auditActions" :key="action" :value="action" :label="$t(action)" /></ElSelect></label>
        <label>{{ $t("Actor") }}<ElInput v-model="filters.actor" :aria-label="$t(&quot;Actor reference&quot;)" :placeholder="$t(&quot;Actor reference, e.g. DEMO-ACTOR-001&quot;)" clearable /></label>
        <label>{{ $t("Resource Type") }}<ElSelect v-model="filters.resourceType" :aria-label="$t(&quot;Resource Type&quot;)" :placeholder="$t(&quot;All resource types&quot;)" clearable><ElOption v-for="type in auditResourceTypes" :key="type" :value="type" :label="$t(type)" /></ElSelect></label>
        <label>{{ $t("From (UTC)") }}<ElInput v-model="filters.from" :aria-label="$t(&quot;From date&quot;)" :placeholder="$t(&quot;YYYY-MM-DD&quot;)" maxlength="10" clearable /></label>
        <label>{{ $t("To (UTC)") }}<ElInput v-model="filters.to" :aria-label="$t(&quot;To date&quot;)" :placeholder="$t(&quot;YYYY-MM-DD&quot;)" maxlength="10" clearable /></label>
        <label>{{ $t("Outcome") }}<ElSelect v-model="filters.outcome" :aria-label="$t(&quot;Outcome&quot;)" :placeholder="$t(&quot;All outcomes&quot;)" clearable><ElOption v-for="outcome in auditOutcomes" :key="outcome" :value="outcome" :label="$t(outcome)" /></ElSelect></label>
        <div class="actions"><ElButton type="primary" native-type="submit" :disabled="loading">{{ $t("Search") }}</ElButton><ElButton :disabled="loading" @click="clearFilters">{{ $t("Clear Filters") }}</ElButton></div><p class="filter-note">{{ $t("Search applies the filters. Refresh and pagination use the last applied query.") }}</p>
      </form>
      <ElAlert v-if="validationError" class="notice" type="error" :closable="false" :title="$t(validationError)" />
      <div class="panel"><div class="list-heading"><h2>{{ $t("Operational Events") }}</h2><span>{{ total }} {{ $t(total === 1 ? 'event' : 'events') }}</span></div>
        <ElSkeleton v-if="loading" :rows="7" animated />
        <ElAlert v-else-if="error" type="error" :closable="false" :title="$t(error)" />
        <ElEmpty v-else-if="!items.length" :description="$t(adminAuditScenario === 'empty' ? 'No audit events are available.' : 'No audit events match the applied filters.')" />
        <template v-else>
          <table class="audit-table"><thead><tr><th>{{ $t("Time (UTC)") }}</th><th>{{ $t("Actor") }}</th><th>{{ $t("Action Code") }}</th><th>{{ $t("Resource") }}</th><th>{{ $t("Outcome") }}</th><th>{{ $t("Action") }}</th></tr></thead><tbody><tr v-for="event in items" :key="event.eventId"><td>{{ date(event.occurredAt) }}</td><td>{{ $t(event.actor || 'Unavailable / removed actor') }}</td><td>{{ event.actionCode }}<p>{{ event.summary }}</p></td><td>{{ event.resourceType }}<p>{{ $t(event.resourceId || 'Unavailable') }}</p></td><td><ElTag :type="event.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ $t(event.outcome) }}</ElTag></td><td><ElButton type="primary" plain @click="selected = event">{{ $t("View") }}</ElButton></td></tr></tbody></table>
          <div class="audit-cards"><article v-for="event in items" :key="event.eventId"><h3>{{ event.actionCode }}</h3><ElTag :type="event.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ $t(event.outcome) }}</ElTag><dl><dt>{{ $t("Time (UTC)") }}</dt><dd>{{ date(event.occurredAt) }}</dd><dt>{{ $t("Actor") }}</dt><dd>{{ $t(event.actor || 'Unavailable / removed actor') }}</dd><dt>{{ $t("Resource Type") }}</dt><dd>{{ event.resourceType }}</dd><dt>{{ $t("Resource ID") }}</dt><dd>{{ $t(event.resourceId || 'Unavailable') }}</dd></dl><p>{{ event.summary }}</p><ElButton type="primary" plain @click="selected = event">{{ $t("View") }}</ElButton></article></div>
          <div class="pagination"><label>{{ $t("Events per page") }}<ElSelect v-model="pageSize" :aria-label="$t(&quot;Events per page&quot;)" :disabled="loading" @change="changeSize"><ElOption :value="5" :label="$t(&quot;5 / page&quot;)" /><ElOption :value="10" :label="$t(&quot;10 / page&quot;)" /></ElSelect></label><ElPagination :current-page="page" :page-size="pageSize" :total="total" :disabled="loading" layout="prev, pager, next" @current-change="changePage" /></div>
        </template>
      </div>
    </section>
    <ElDrawer :model-value="!!selected" :title="$t(&quot;Audit Event Details&quot;)" size="min(560px, 100vw)" @close="selected = null">
      <div v-if="selected" class="details"><h2>{{ selected.actionCode }}</h2><ElTag :type="selected.outcome === 'SUCCESS' ? 'success' : 'danger'">{{ $t(selected.outcome) }}</ElTag><dl><template v-for="(label, key) in { eventId: 'Event ID', actor: 'Actor', actionCode: 'Action Code', resourceType: 'Resource Type', resourceId: 'Resource ID', fromState: 'From State', toState: 'To State', requestId: 'Request ID' }" :key="key"><dt>{{ $t(label) }}</dt><dd>{{ $t(selected[key] || 'Unavailable') }}</dd></template><dt>{{ $t("Time (UTC)") }}</dt><dd>{{ date(selected.occurredAt) }}</dd></dl><h3>{{ $t("Safe Event Summary") }}</h3><p>{{ selected.summary }}</p><p>{{ $t("From / To describe operational state only. Missing actor references and resource identifiers are preserved as unavailable.") }}</p><ElAlert type="info" :closable="false" :title="$t(&quot;Read-only synthetic metadata. No content snapshots, review decisions/reasons, IP addresses, session identifiers or raw logs are included.&quot;)" /></div>
    </ElDrawer>
  </AdminLayout>
</template>

<style scoped>
.heading, .actions, .list-heading, .pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }.heading, .list-heading, .pagination { justify-content: space-between; }.heading { margin-bottom: 24px; }.actions .el-button { margin-left: 0; }
h1 { font-size: 28px; margin: 8px 0; color: var(--sr-text); }h2 { font-size: 18px; }h3 { font-size: 15px; }p, dd { color: var(--sr-text-muted); line-height: 1.7; }.breadcrumb, .filter-note { font-size: 12px; }
.panel { border: 1px solid var(--sr-border); border-radius: 8px; padding: 22px; background: white; margin-top: 22px; min-width: 0; }.filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: end; }label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; min-width: 0; }label .el-select { width: 100%; }.filter-note { grid-column: span 2; margin: 0; }.notice { margin-top: 16px; }.list-heading { margin-bottom: 16px; }.list-heading h2 { margin: 0; }.list-heading span { font-size: 12px; color: var(--sr-text-muted); }
table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 12px; }th, td { text-align: left; padding: 14px 8px; border-bottom: 1px solid var(--sr-border); overflow-wrap: anywhere; }thead { background: var(--sr-surface-tint); color: var(--sr-text-muted); }td p { margin: 6px 0; }th:nth-child(3) { width: 27%; }th:last-child { width: 10%; }.audit-cards { display: none; }.pagination { margin-top: 20px; }.pagination label { width: 130px; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 12px; font-size: 13px; }dt { font-weight: 600; color: var(--sr-text-secondary); }dd { margin: 0; overflow-wrap: anywhere; }.details, h3 { overflow-wrap: anywhere; }
@media (max-width: 1100px) { .audit-table { display: none; }.audit-cards { display: grid; gap: 20px; }.audit-cards article { padding-bottom: 18px; border-bottom: 1px solid var(--sr-border); } }
@media (max-width: 760px) { h1 { font-size: 24px; }.filters { grid-template-columns: minmax(0, 1fr); }.filter-note { grid-column: auto; }.panel { padding: 16px; } }
@media (max-width: 480px) { dl { grid-template-columns: minmax(0, 1fr); gap: 6px; }dd { margin-bottom: 8px; }.pagination { justify-content: start; } }
</style>
