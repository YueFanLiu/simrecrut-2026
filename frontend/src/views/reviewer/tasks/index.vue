<script setup>
import { useRouter } from 'vue-router'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import ResearchLayout from '../../../layouts/ResearchLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useResearchModuleStore } from '../../../store/modules/research-module.js'
import { useReviewTasksStore } from '../../../store/modules/review-tasks.js'
import { filterReviewTasks, reviewConditions, reviewStatuses } from '../../../mock/review-tasks.js'
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
const pending = computed(() => rows.value.filter(row => row.status === 'Pending').length)
const submitted = computed(() => rows.value.filter(row => row.status === 'Submitted').length)
const formatDate = value => new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value))
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
      <div class="page-heading"><div><h1 id="tasks-title">My Review Tasks</h1><p>Review responsibilities assigned to your current demo account.</p></div><ElButton :loading="loading" :disabled="loading" @click="loadTasks">Refresh</ElButton></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration only. Reviewer is an assigned responsibility of an HR or Researcher account. This list contains no assessment scores, decisions or other reviewers' reasons." />
      <p class="availability">Open a task to review its anonymised professional facts. Submitted tasks are read-only.</p>
      <div v-if="!loading && !error" class="stats"><article><span>Total Assigned Tasks</span><strong>{{ rows.length }}</strong></article><article><span>Pending</span><strong>{{ pending }}</strong></article><article><span>Submitted</span><strong>{{ submitted }}</strong></article></div>
      <div class="filters"><ElInput v-model="filters.keyword" aria-label="Search review tasks" placeholder="Search task ID, experiment ID or job" clearable /><ElSelect v-model="filters.status" aria-label="Filter by status" placeholder="All Statuses"><ElOption label="All Statuses" value="" /><ElOption v-for="value in reviewStatuses" :key="value" :value="value" :label="value" /></ElSelect><ElSelect v-model="filters.condition" aria-label="Filter by condition" placeholder="All Conditions"><ElOption label="All Conditions" value="" /><ElOption v-for="value in reviewConditions" :key="value" :value="value" :label="value" /></ElSelect><ElSelect v-model="filters.purpose" aria-label="Filter by purpose" placeholder="All Purposes"><ElOption label="All Purposes" value="" /><ElOption label="Primary" value="Primary" /><ElOption label="Research" value="Research" /></ElSelect><ElSelect v-model="filters.sort" aria-label="Sort review tasks"><ElOption label="Newest first" value="latest" /><ElOption label="Oldest first" value="oldest" /></ElSelect><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <p class="filter-note">Filters apply to your loaded assigned mock tasks. Pending is a review status, not a hiring decision. Dates use UTC.</p>
      <div v-if="loading" class="panel state-panel" role="status"><p>Loading assigned review tasks...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>Review Tasks Unavailable</h2><p>{{ error }}</p><ElButton type="primary" @click="loadTasks">Try Again</ElButton></div>
      <div v-else-if="!rows.length" class="panel state-panel"><ElEmpty description="No assigned review tasks" /><p>No retained authorised tasks are currently assigned to this account.</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty description="No review tasks match your filters" /><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" aria-label="Assigned review tasks table"><table><caption>{{ filtered.length }} accessible assigned tasks</caption><thead><tr><th scope="col">Task ID</th><th scope="col">Job / Experiment</th><th scope="col">Condition</th><th scope="col">Purpose</th><th scope="col">Assigned Date</th><th scope="col">Status</th><th scope="col">Action</th></tr></thead><tbody><tr v-for="row in visible" :key="row.reviewId"><th scope="row">{{ row.reviewId }}</th><td>{{ row.job }}<span class="secondary">{{ row.experimentId || 'No research experiment' }}</span></td><td>{{ row.condition }}</td><td>{{ row.purpose }}</td><td>{{ formatDate(row.assignedAt) }}</td><td><ElTag :type="row.status === 'Submitted' ? 'success' : 'warning'">{{ row.status }}</ElTag></td><td><ElButton size="small" @click="router.push({ name: 'ReviewTaskDetail', params: { reviewId: row.reviewId } })">Open Task</ElButton></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="row in visible" :key="row.reviewId" class="panel"><div class="card-heading"><span>{{ row.reviewId }}</span><ElTag :type="row.status === 'Submitted' ? 'success' : 'warning'">{{ row.status }}</ElTag></div><h2>{{ row.job }}</h2><p>{{ row.condition }} · {{ row.purpose }}<br />{{ row.experimentId || 'No research experiment' }}<br />Assigned: {{ formatDate(row.assignedAt) }} UTC</p><ElButton @click="router.push({ name: 'ReviewTaskDetail', params: { reviewId: row.reviewId } })">Open Task</ElButton></article></div>
        <div class="pagination"><span>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} of {{ filtered.length }} tasks</span><ElSelect v-model="pageSize" aria-label="Tasks per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="`${size} / page`" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </component>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { font-size: 18px; color: #132941; } p { color: #586b83; font-size: 14px; line-height: 1.8; } .page-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 20px; } .availability, .filter-note { font-size: 12px; }
.stats { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 16px; margin: 24px 0; } .stats article, .panel { padding: 20px; border-radius: 12px; border: 1px solid #e1eaf5; background: white; min-width: 0; } .stats span { color: #586b83; font-size: 12px; } .stats strong { display: block; color: #132941; font-size: 28px; margin-top: 12px; } .filters { display: grid; grid-template-columns: minmax(180px,1fr) repeat(4,140px) auto; gap: 12px; margin-top: 24px; } .el-button { margin: 0; } .state-panel { text-align: center; padding: 36px 20px; }
.table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; } table { width: 100%; min-width: 960px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } caption { text-align: left; font-size: 12px; padding-bottom: 16px; } th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; overflow-wrap: anywhere; } thead th { background: #f3f7fd; color: #132941; } tbody th { color: #176bdf; font-weight: 500; } .secondary { display: block; font-size: 12px; } .mobile-list { display: none; } .card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; color: #176bdf; font-size: 12px; } .pagination { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 16px; color: #586b83; font-size: 12px; margin: 24px 0; } .pagination .el-select { width: 120px; }
@media (max-width: 1250px) { .filters { grid-template-columns: repeat(3,minmax(0,1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } .filters { grid-template-columns: repeat(2,minmax(0,1fr)); } h1 { font-size: 25px; } }
@media (max-width: 420px) { .filters { grid-template-columns: minmax(0,1fr); } .stats { gap: 8px; } .stats article { padding: 14px 10px; } .pagination { justify-content: center; } }
</style>
