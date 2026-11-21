<script setup>
import { dateLocale } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { useRouter } from 'vue-router'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import ResearchLayout from '../../../layouts/ResearchLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useResearchModuleStore } from '../../../store/modules/research-module.js'
import { useReviewTasksStore } from '../../../store/modules/review-tasks.js'
import { filterReviewTasks, reviewConditions, reviewStatuses as demoStatuses } from '../../../mock/review-tasks.js'
import { reviewTasksScenario } from '../../../config/demo.js'

const router = useRouter()
const auth = useAuthStore(), research = useResearchModuleStore(), tasks = useReviewTasksStore()
const loading = ref(true), loaded = ref(false), error = ref(''), clock = ref(Date.now())
const page = ref(1), pageSize = ref(5)
const filters = reactive({ keyword: '', status: '', condition: '', purpose: '', sort: 'latest' })
const rows = computed(() => loaded.value && (auth.hasDemoRole('HR') || auth.hasDemoRole('Researcher')) && reviewTasksScenario !== 'empty'
  ? tasks.listTasks(auth.demoUser, research, clock.value) : [])
const filtered = computed(() => filterReviewTasks(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const reviewStatuses = demoMode ? demoStatuses : ['Pending', 'Opened', 'Submitted', 'Cancelled', 'Expired']
const pending = computed(() => rows.value.filter(row => ['Pending', 'Opened'].includes(row.status)).length)
const submitted = computed(() => rows.value.filter(row => row.status === 'Submitted').length)
const formatDate = value => new Intl.DateTimeFormat(dateLocale.value, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value))
let active = true, sequence = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => filtered.value.length, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })
function resetFilters() { Object.assign(filters, { keyword: '', status: '', condition: '', purpose: '', sort: 'latest' }) }
async function loadTasks() {
  const request = ++sequence, username = auth.demoUser?.username
  const current = () => active && request === sequence && auth.demoUser?.username === username && (auth.hasDemoRole('HR') || auth.hasDemoRole('Researcher'))
  loading.value = true; loaded.value = false; error.value = ''
  try { await tasks.loadTasks(auth.demoUser, research, reviewTasksScenario); if (current()) { loaded.value = true; clock.value = Date.now() } }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1; loaded.value = false; clearInterval(timer) })
loadTasks()
</script>

<template>
  <component :is="auth.hasDemoRole('HR') ? HrLayout : ResearchLayout">
    <section aria-labelledby="tasks-title" :aria-busy="loading">
      <div class="page-heading"><div><h1 id="tasks-title">{{ $t("My Review Tasks") }}</h1><p>{{ $t("Review responsibilities assigned to your account.") }}</p></div><ElButton :loading="loading" :disabled="loading" @click="loadTasks">{{ $t("Refresh") }}</ElButton></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demonstration only. Reviewer is an assigned responsibility of an HR or Researcher account. This list contains no assessment scores, decisions or other reviewers' reasons.&quot;)" />
      <p class="availability">{{ $t("Open a task to review its anonymised professional facts. Submitted tasks are read-only.") }}</p>
      <div v-if="!loading && !error" class="stats"><article><span>{{ $t("Total Assigned Tasks") }}</span><strong>{{ rows.length }}</strong></article><article><span>{{ $t("Pending") }}</span><strong>{{ pending }}</strong></article><article><span>{{ $t("Submitted") }}</span><strong>{{ submitted }}</strong></article></div>
      <div class="filters"><ElInput v-model="filters.keyword" :aria-label="$t(&quot;Search review tasks&quot;)" :placeholder="$t(&quot;Search task ID, experiment ID or job&quot;)" clearable /><ElSelect v-model="filters.status" :aria-label="$t(&quot;Filter by status&quot;)" :placeholder="$t(&quot;All Statuses&quot;)"><ElOption :label="$t(&quot;All Statuses&quot;)" value="" /><ElOption v-for="value in reviewStatuses" :key="value" :value="value" :label="$t(value)" /></ElSelect><ElSelect v-model="filters.condition" :aria-label="$t(&quot;Filter by condition&quot;)" :placeholder="$t(&quot;All Conditions&quot;)"><ElOption :label="$t(&quot;All Conditions&quot;)" value="" /><ElOption v-for="value in reviewConditions" :key="value" :value="value" :label="$t(value)" /></ElSelect><ElSelect v-model="filters.purpose" :aria-label="$t(&quot;Filter by purpose&quot;)" :placeholder="$t(&quot;All Purposes&quot;)"><ElOption :label="$t(&quot;All Purposes&quot;)" value="" /><ElOption :label="$t(&quot;Primary&quot;)" value="Primary" /><ElOption :label="$t(&quot;Research&quot;)" value="Research" /></ElSelect><ElSelect v-model="filters.sort" :aria-label="$t(&quot;Sort review tasks&quot;)"><ElOption :label="$t(&quot;Newest first&quot;)" value="latest" /><ElOption :label="$t(&quot;Oldest first&quot;)" value="oldest" /></ElSelect><ElButton @click="resetFilters">{{ $t("Reset Filters") }}</ElButton></div>
      <p class="filter-note">{{ $t("Filters apply to your loaded assigned mock tasks. Pending is a review status, not a hiring decision. Dates use UTC.") }}</p>
      <div v-if="loading" class="panel state-panel" role="status"><p>{{ $t("Loading assigned review tasks...") }}</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>{{ $t("Review Tasks Unavailable") }}</h2><p>{{ $t(error) }}</p><ElButton type="primary" @click="loadTasks">{{ $t("Try Again") }}</ElButton></div>
      <div v-else-if="!rows.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No assigned review tasks&quot;)" /><p>{{ $t("No retained authorised tasks are currently assigned to this account.") }}</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No review tasks match your filters&quot;)" /><ElButton @click="resetFilters">{{ $t("Reset Filters") }}</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" :aria-label="$t(&quot;Assigned review tasks table&quot;)"><table><caption>{{ filtered.length }} {{ $t("accessible assigned tasks") }}</caption><thead><tr><th scope="col">{{ $t("Task ID") }}</th><th scope="col">{{ $t("Job / Experiment") }}</th><th scope="col">{{ $t("Condition") }}</th><th scope="col">{{ $t("Purpose") }}</th><th scope="col">{{ $t("Assigned Date") }}</th><th scope="col">{{ $t("Status") }}</th><th scope="col">{{ $t("Action") }}</th></tr></thead><tbody><tr v-for="row in visible" :key="row.reviewId"><th scope="row">{{ row.reviewId }}</th><td>{{ row.job }}<span class="secondary">{{ $t(row.experimentId || 'No research experiment') }}</span></td><td>{{ $t(row.condition) }}</td><td>{{ $t(row.purpose) }}</td><td>{{ formatDate(row.assignedAt) }}</td><td><ElTag :type="row.status === 'Submitted' ? 'success' : 'warning'">{{ $t(row.status) }}</ElTag></td><td><ElButton size="small" @click="router.push({ name: 'ReviewTaskDetail', params: { reviewId: row.reviewId } })">{{ $t("Open Task") }}</ElButton></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="row in visible" :key="row.reviewId" class="panel"><div class="card-heading"><span>{{ row.reviewId }}</span><ElTag :type="row.status === 'Submitted' ? 'success' : 'warning'">{{ $t(row.status) }}</ElTag></div><h2>{{ row.job }}</h2><p>{{ $t(row.condition) }} · {{ $t(row.purpose) }}<br />{{ $t(row.experimentId || 'No research experiment') }}<br />{{ $t("Assigned:") }} {{ formatDate(row.assignedAt) }} {{ $t("UTC") }}</p><ElButton @click="router.push({ name: 'ReviewTaskDetail', params: { reviewId: row.reviewId } })">{{ $t("Open Task") }}</ElButton></article></div>
        <div class="pagination"><span>{{ $t("Showing") }} {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} {{ $t("of") }} {{ filtered.length }} {{ $t("tasks") }}</span><ElSelect v-model="pageSize" :aria-label="$t(&quot;Tasks per page&quot;)"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="$t('{size} / page', { size })" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </component>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -.7px; } h2 { font-size: 18px; color: var(--sr-ink); } p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; } .page-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; } .availability, .filter-note { font-size: 12px; }
.stats { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 16px; margin: 24px 0; } .stats article, .panel { padding: 20px; border-radius: 12px; border: 1px solid var(--sr-border); background: white; min-width: 0; } .stats span { color: var(--sr-text-muted); font-size: 12px; } .stats strong { display: block; color: var(--sr-ink); font-size: 28px; margin-top: 12px; } .filters { display: grid; grid-template-columns: minmax(180px,1fr) repeat(4,140px) auto; gap: 12px; margin-top: 24px; } .el-button { margin: 0; } .state-panel { text-align: center; padding: 36px 20px; }
.table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid var(--sr-primary); } table { width: 100%; min-width: 960px; border-collapse: collapse; color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; } caption { text-align: left; font-size: 12px; padding-bottom: 16px; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid var(--sr-border); overflow-wrap: anywhere; } thead th { background: var(--sr-primary-50); color: var(--sr-ink); } tbody th { color: var(--sr-primary); font-weight: 500; } .secondary { display: block; font-size: 12px; } .mobile-list { display: none; } .card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; color: var(--sr-primary); font-size: 12px; } .pagination { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 16px; color: var(--sr-text-muted); font-size: 12px; margin: 24px 0; } .pagination .el-select { width: 120px; }
@media (max-width: 1250px) { .filters { grid-template-columns: repeat(3,minmax(0,1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } .filters { grid-template-columns: repeat(2,minmax(0,1fr)); } h1 { font-size: 25px; } }
@media (max-width: 420px) { .filters { grid-template-columns: minmax(0,1fr); } .stats { gap: 8px; } .stats article { padding: 14px 10px; } .pagination { justify-content: center; } }
</style>
