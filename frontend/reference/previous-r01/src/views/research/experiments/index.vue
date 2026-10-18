<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import ResearchLayout from '../../../layouts/ResearchLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useResearchExperimentsStore } from '../../../store/modules/research-experiments.js'
import { experimentStatuses, filterResearchExperiments } from '../../../mock/research-experiments.js'
import { researchExperimentsScenario } from '../../../config/demo.js'

const auth = useAuthStore()
const experiments = useResearchExperimentsStore()
const loading = ref(true)
const loaded = ref(false)
const error = ref('')
const clock = ref(Date.now())
const filters = reactive({ keyword: '', status: '', jobId: '', sort: 'latest' })
const page = ref(1)
const pageSize = ref(5)
const rows = computed(() => loaded.value && auth.hasDemoRole('Researcher') && researchExperimentsScenario !== 'empty'
  ? experiments.listExperiments(auth.demoUser, clock.value) : [])
const jobOptions = computed(() => [...new Map(rows.value.map((row) => [row.jobId, { jobId: row.jobId, title: row.jobTitle }])).values()])
const filtered = computed(() => filterResearchExperiments(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const statusType = (value) => ({ Completed: 'success', Running: 'primary', Draft: 'warning' })[value] || 'info'
const formatDate = (value) => new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value))
let active = true
let sequence = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => filtered.value.length, () => { page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))) })

function resetFilters() { Object.assign(filters, { keyword: '', status: '', jobId: '', sort: 'latest' }) }
async function loadExperiments() {
  const request = ++sequence
  const username = auth.demoUser?.username
  const current = () => active && request === sequence && auth.hasDemoRole('Researcher') && auth.demoUser.username === username
  loading.value = true
  loaded.value = false
  error.value = ''
  try {
    await experiments.loadExperiments(auth.demoUser, researchExperimentsScenario)
    if (current()) { loaded.value = true; clock.value = Date.now() }
  } catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1; loaded.value = false; clearInterval(timer) })
loadExperiments()
</script>

<template>
  <ResearchLayout>
    <section aria-labelledby="experiments-title" :aria-busy="loading">
      <div class="page-heading"><div><h1 id="experiments-title">Research Experiments</h1><p>Browse authorised experiments, statuses and frozen configuration versions.</p></div><div class="actions"><ElButton :loading="loading" :disabled="loading" @click="loadExperiments">Refresh</ElButton><ElButton type="primary" disabled>Create New Experiment</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration only. These fixed experiment summaries do not run models, access Candidate uploads or train models." />
      <p class="availability">Create New Experiment will open R02; View will open R03. These pages are not implemented yet.</p>
      <div class="filters"><ElInput v-model="filters.keyword" aria-label="Search experiments" placeholder="Search name, description or experiment ID" clearable /><ElSelect v-model="filters.status" aria-label="Filter by status" placeholder="All Statuses"><ElOption label="All Statuses" value="" /><ElOption v-for="status in experimentStatuses" :key="status" :label="status" :value="status" /></ElSelect><ElSelect v-model="filters.jobId" aria-label="Filter by job" placeholder="All Jobs"><ElOption label="All Jobs" value="" /><ElOption v-if="filters.jobId && !jobOptions.some(job => job.jobId === filters.jobId)" :value="filters.jobId" label="No accessible experiments for this job" /><ElOption v-for="job in jobOptions" :key="job.jobId" :label="job.title" :value="job.jobId" /></ElSelect><ElSelect v-model="filters.sort" aria-label="Sort experiments"><ElOption value="latest" label="Newest first" /><ElOption value="oldest" label="Oldest first" /></ElSelect><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <p class="filter-note">Search and filters apply only to the authorised mock summaries loaded in this tab.</p>
      <div v-if="loading" class="panel state-panel" role="status"><p>Loading authorised experiments...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>Experiments Could Not Be Loaded</h2><p>{{ error }}</p><ElButton type="primary" @click="loadExperiments">Try Again</ElButton></div>
      <div v-else-if="!rows.length" class="panel state-panel"><ElEmpty description="No authorised experiments available" /><p>No experiments are currently accessible to this Researcher demo account.</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty description="No experiments match your filters" /><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" tabindex="0" role="region" aria-label="Authorised experiments table"><table><caption>{{ filtered.length }} accessible experiments · Times shown in UTC</caption><thead><tr><th scope="col">Experiment ID / Name</th><th scope="col">Description</th><th scope="col">Job / Fixed Version</th><th scope="col">Frozen Configuration</th><th scope="col">Subjects</th><th scope="col">Created Date</th><th scope="col">Status</th><th scope="col">Actions</th></tr></thead><tbody><tr v-for="row in visible" :key="row.experimentId"><th scope="row">{{ row.experimentId }}<span class="secondary">{{ row.name }}</span></th><td>{{ row.description }}</td><td>{{ row.jobTitle }}<span class="secondary">Version {{ row.versions.job }}</span></td><td><span>Weights: {{ row.versions.weights }}</span><span class="secondary">Protocol: {{ row.versions.protocol }}</span><span class="secondary">Neutral: {{ row.versions.neutral }}</span><span class="secondary">Biased: {{ row.versions.biased }}</span></td><td>{{ row.subjectCount }}</td><td>{{ formatDate(row.createdAt) }}</td><td><ElTag :type="statusType(row.status)">{{ row.status }}</ElTag></td><td><ElButton size="small" disabled>View</ElButton></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="row in visible" :key="row.experimentId" class="panel"><div class="card-heading"><span>{{ row.experimentId }}</span><ElTag :type="statusType(row.status)">{{ row.status }}</ElTag></div><h2>{{ row.name }}</h2><p>{{ row.description }}</p><p>{{ row.jobTitle }} · Version {{ row.versions.job }}<br />{{ row.subjectCount }} subjects · {{ formatDate(row.createdAt) }} UTC</p><dl><dt>Weights</dt><dd>{{ row.versions.weights }}</dd><dt>Protocol</dt><dd>{{ row.versions.protocol }}</dd><dt>Neutral model</dt><dd>{{ row.versions.neutral }}</dd><dt>Biased model</dt><dd>{{ row.versions.biased }}</dd></dl><ElButton disabled>View</ElButton><p class="availability">R03 not implemented</p></article></div>
        <div class="pagination"><span>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} of {{ filtered.length }} experiments</span><ElSelect v-model="pageSize" aria-label="Experiments per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :label="`${size} / page`" :value="size" /></ElSelect><ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </ResearchLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -.7px; } h2 { color: #132941; font-size: 18px; } p { color: #586b83; font-size: 14px; line-height: 1.8; }
.page-heading, .actions { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 14px; } .page-heading { margin-bottom: 20px; } .el-button { margin: 0; } .availability, .filter-note { font-size: 12px; } .filters { display: grid; grid-template-columns: minmax(180px, 1fr) 140px 160px 140px auto; gap: 12px; margin-top: 24px; }
.panel { background: white; padding: 20px; border: 1px solid #e1eaf5; border-radius: 12px; min-width: 0; } .state-panel { text-align: center; padding: 36px 20px; } .table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; } table { width: 100%; min-width: 1100px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; } caption { text-align: left; padding-bottom: 16px; font-size: 12px; } th, td { padding: 14px 10px; text-align: left; vertical-align: top; border-bottom: 1px solid #e1eaf5; } thead th { background: #f3f7fd; color: #132941; } tbody th { color: #176bdf; font-weight: 500; min-width: 170px; } .secondary { display: block; font-size: 12px; } td:nth-child(4) { white-space: nowrap; }
.mobile-list { display: none; } .card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; color: #176bdf; font-size: 12px; } dl { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 10px; font-size: 12px; color: #586b83; } dd { margin: 0; overflow-wrap: anywhere; } .pagination { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 16px; margin: 24px 0; color: #586b83; font-size: 12px; } .pagination .el-select { width: 120px; }
@media (max-width: 1250px) { .filters { grid-template-columns: repeat(4, minmax(0, 1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } h1 { font-size: 25px; } .filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 420px) { .filters { grid-template-columns: minmax(0, 1fr); } .pagination { justify-content: center; } }
</style>
