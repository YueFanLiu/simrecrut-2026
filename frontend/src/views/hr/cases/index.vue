<script setup>
import { dateLocale } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useHrJobsStore } from '../../../store/modules/hr-jobs.js'
import { useHrCasesStore } from '../../../store/modules/hr-cases.js'
import { filterHrCases } from '../../../mock/hr-cases.js'
import { hrCasesScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const jobs = useHrJobsStore()
const cases = useHrCasesStore()
const loading = ref(true)
const loaded = ref(false)
const error = ref('')
const clock = ref(Date.now())
const filters = reactive({ keyword: '', jobId: typeof route.query.jobId === 'string' ? route.query.jobId : '', sort: 'latest' })
const page = ref(1)
const pageSize = ref(5)
const rows = computed(() => loaded.value && auth.hasDemoRole('HR') && hrCasesScenario !== 'empty'
  ? cases.listCases(auth.demoUser, jobs.jobs, clock.value) : [])
const jobOptions = computed(() => [...new Map(rows.value.map((row) => [row.jobId, { jobId: row.jobId, title: row.jobTitle }])).values()]
  .sort((a, b) => a.title.localeCompare(b.title, 'en')))
const filtered = computed(() => filterHrCases(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
let active = true
let sequence = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => filtered.value.length, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })
watch(() => route.query.jobId, (value) => { filters.jobId = typeof value === 'string' ? value : '' })
const formatDate = (value) => value ? new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC',
}).format(new Date(value)) : 'Not set yet'

function openCase(row) {
  if (!cases.listCases(auth.demoUser, jobs.jobs).some(entry => entry.caseId === row.caseId)) { loadCases(); return }
  router.push({ name: 'HrCaseDetail', params: { caseId: row.caseId }, query: { jobId: filters.jobId || undefined } })
}
function setJobFilter(value) {
  router.replace({ name: 'HrCases', query: { ...route.query, jobId: value || undefined } })
}
function resetFilters() {
  Object.assign(filters, { keyword: '', jobId: '', sort: 'latest' })
  setJobFilter('')
}
async function loadCases() {
  const request = ++sequence
  const username = auth.demoUser?.username
  loading.value = true
  loaded.value = false
  error.value = ''
  try {
    await cases.loadCases(auth.demoUser, jobs.jobs, hrCasesScenario)
    if (active && sequence === request && auth.hasDemoRole('HR') && auth.demoUser.username === username) {
      clock.value = Date.now()
      loaded.value = true
      page.value = 1
    }
  } catch (failure) { if (active && sequence === request) error.value = failure.message }
  finally { if (active && sequence === request) loading.value = false }
}
loadCases()
onBeforeUnmount(() => { active = false; sequence += 1; loaded.value = false; clearInterval(timer) })
</script>

<template>
  <HrLayout>
    <section aria-labelledby="cases-title" :aria-busy="loading">
      <div class="page-heading"><div><h1 id="cases-title">{{ $t("Authorized Cases") }}</h1><p>{{ $t("View cases for jobs you manage and cases explicitly shared with your HR account.") }}</p></div><ElButton :loading="loading" :disabled="loading" @click="loadCases">{{ $t("Refresh") }}</ElButton></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demo only. This list uses separate authorised mock cases and local access checks. It does not read real Candidate data or call an API.&quot;)" />
      <p class="availability">{{ $t("Open Case shows a read-only professional snapshot and permitted results. Access is checked again before details are displayed.") }}</p>
      <div class="filters"><ElInput v-model="filters.keyword" :aria-label="$t(&quot;Search authorised cases&quot;)" clearable :disabled="loading" :placeholder="$t(&quot;Search loaded cases by ID or job title&quot;)" /><ElSelect v-model="filters.jobId" :aria-label="$t(&quot;Filter cases by job&quot;)" :placeholder="$t(&quot;All Jobs&quot;)" :disabled="loading" @change="setJobFilter"><ElOption :label="$t(&quot;All Jobs&quot;)" value="" /><ElOption v-if="filters.jobId && !jobOptions.some(job => job.jobId === filters.jobId)" :label="$t('Selected job has no accessible cases')" :value="filters.jobId" /><ElOption v-for="job in jobOptions" :key="job.jobId" :label="$t(job.title)" :value="job.jobId" /></ElSelect><ElSelect v-model="filters.sort" :aria-label="$t(&quot;Sort cases&quot;)" :disabled="loading"><ElOption :label="$t(&quot;Newest First&quot;)" value="latest" /><ElOption :label="$t(&quot;Oldest First&quot;)" value="oldest" /></ElSelect></div>
      <p class="filter-note">{{ $t("Search and filtering apply to all loaded, accessible cases in this demo. Pending human reviews have no decision. Processing failures are technical states.") }}</p>
      <div v-if="loading" class="panel state-panel" role="status"><p>{{ $t("Loading authorised cases...") }}</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>{{ $t("Cases Could Not Be Loaded") }}</h2><p>{{ $t(error) }}</p><ElButton type="primary" @click="loadCases">{{ $t("Try Again") }}</ElButton></div>
      <div v-else-if="!rows.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No authorised cases available&quot;)" /><p>{{ $t("No retained cases are currently accessible to this HR account.") }}</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No cases match your filters&quot;)" /><ElButton @click="resetFilters">{{ $t("Reset Filters") }}</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" :aria-label="$t(&quot;Authorised cases table&quot;)"><table><caption>{{ filtered.length }} {{ $t("accessible cases · Times shown in UTC") }}</caption><thead><tr><th scope="col">{{ $t("Case ID") }}</th><th scope="col">{{ $t("Job / Version") }}</th><th v-if="demoMode" scope="col">{{ $t("Created Date") }}</th><th scope="col">{{ $t("Processing") }}</th><th scope="col">{{ $t("Human Reviews") }}</th><th scope="col">{{ $t("Access / Retention") }}</th><th scope="col">{{ $t("Actions") }}</th></tr></thead><tbody><tr v-for="row in visible" :key="row.caseId"><th scope="row">{{ row.caseId }}</th><td>{{ row.jobTitle }}<span class="secondary">{{ $t(demoMode ? 'Version ' : 'Version ID ') }}{{ row.version }}</span></td><td v-if="demoMode">{{ formatDate(row.createdAt) }}</td><td><ElTag :type="row.statusType">{{ $t(row.processingLabel) }}</ElTag></td><td><template v-if="demoMode"><span>{{ $t("Standard:") }} {{ $t(row.humanStatus.standard) }}</span><span class="secondary">{{ $t("Instructed Bias:") }} {{ $t(row.humanStatus.instructedBias) }}</span></template><span v-else>{{ $t(row.humanLabel) }}</span></td><td>{{ $t(row.accessLabel) }}<span class="secondary">{{ $t("Retention deadline:") }} {{ formatDate(row.purgeAt) }}</span></td><td><ElButton size="small" :disabled="row.resultsRestricted" @click="openCase(row)">{{ $t("Open Case") }}</ElButton></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="row in visible" :key="row.caseId" class="panel"><div class="card-heading"><span>{{ row.caseId }}</span><ElTag :type="row.statusType">{{ $t(row.processingLabel) }}</ElTag></div><h2>{{ row.jobTitle }}</h2><p>{{ $t(demoMode ? 'Version ' : 'Version ID ') }}{{ row.version }}<template v-if="demoMode"> · {{ formatDate(row.createdAt) }}</template></p><p v-if="demoMode">{{ $t("Standard Human:") }} {{ $t(row.humanStatus.standard) }}<br />{{ $t("Instructed-Bias Human:") }} {{ $t(row.humanStatus.instructedBias) }}</p><p v-else>{{ $t("Human reviews:") }} {{ $t(row.humanLabel) }}</p><p>{{ $t(row.accessLabel) }}<br />{{ $t("Retention deadline:") }} {{ formatDate(row.purgeAt) }}</p><ElButton :disabled="row.resultsRestricted" @click="openCase(row)">{{ $t("Open Case") }}</ElButton></article></div>
        <div class="pagination"><span>{{ $t("Showing") }} {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} {{ $t("of") }} {{ filtered.length }} {{ $t("cases") }}</span><ElSelect v-model="pageSize" :aria-label="$t(&quot;Cases per page&quot;)"><ElOption v-for="size in [5, 10, 20]" :key="size" :label="$t('{size} / page', { size })" :value="size" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </HrLayout>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -.7px; } h2 { color: var(--sr-ink); font-size: 18px; } p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; }
.page-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; } .availability, .filter-note { font-size: 12px; } .filters { display: grid; grid-template-columns: minmax(0, 1fr) 240px 160px; gap: 14px; margin: 24px 0 12px; }
.panel { background: white; border: 1px solid var(--sr-border); border-radius: 12px; padding: 20px; } .state-panel { text-align: center; padding: 36px 20px; } .el-button { margin: 0; } .table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid var(--sr-primary); }
table { width: 100%; min-width: 1050px; border-collapse: collapse; color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; } caption { text-align: left; font-size: 12px; padding-bottom: 16px; } th, td { padding: 14px 10px; text-align: left; border-bottom: 1px solid var(--sr-border); } thead th { background: var(--sr-primary-50); color: var(--sr-ink); } tbody th { color: var(--sr-primary); white-space: nowrap; font-weight: 500; } .secondary { display: block; font-size: 12px; }
.mobile-list { display: none; } .card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--sr-primary); font-size: 12px; flex-wrap: wrap; } .pagination { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 16px; margin: 24px 0; color: var(--sr-text-muted); font-size: 12px; } .pagination .el-select { width: 120px; }
@media (max-width: 1100px) { .filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } h1 { font-size: 25px; } }
@media (max-width: 600px) { .filters { grid-template-columns: minmax(0, 1fr); } .pagination { justify-content: center; } }
</style>
