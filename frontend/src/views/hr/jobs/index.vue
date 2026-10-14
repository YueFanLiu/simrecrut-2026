<script setup>
import { useRouter } from 'vue-router'
import { useHrJobsStore } from '../../../store/modules/hr-jobs.js'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { filterHrJobs, loadHrJobs } from '../../../mock/hr-jobs.js'
import { hrJobsScenario } from '../../../config/demo.js'

const auth = useAuthStore()
const router = useRouter()
const store = useHrJobsStore()
const jobs = ref([])
const loading = ref(true)
const error = ref('')
const filters = reactive({ keyword: '', status: '', sort: 'latest' })
const page = ref(1)
const pageSize = ref(5)
const filtered = computed(() => filterHrJobs(jobs.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
let active = true
let sequence = 0
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
const formatDate = (date) => new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium', timeZone: 'UTC',
}).format(new Date(`${date}T00:00:00Z`))

async function loadJobs() {
  const request = ++sequence
  const username = auth.demoUser?.username
  loading.value = true
  error.value = ''
  jobs.value = []
  try {
    const data = await loadHrJobs(auth.demoUser, hrJobsScenario, store.jobs)
    if (active && request === sequence && auth.hasDemoRole('HR') && auth.demoUser.username === username) {
      jobs.value = data
      page.value = 1
    }
  } catch (failure) { if (active && request === sequence) error.value = failure.message }
  finally { if (active && request === sequence) loading.value = false }
}
function resetFilters() { Object.assign(filters, { keyword: '', status: '', sort: 'latest' }) }
onMounted(loadJobs)
onBeforeUnmount(() => { active = false; sequence += 1; jobs.value = [] })
</script>

<template>
  <HrLayout>
    <section aria-labelledby="jobs-title" :aria-busy="loading">
      <div class="page-heading"><div><h1 id="jobs-title">Job Management</h1><p>View the drafts and published versions of jobs you manage.</p></div>
        <div class="heading-actions"><ElButton :loading="loading" :disabled="loading" @click="loadJobs">Refresh</ElButton><ElButton type="primary" @click="router.push({ name: 'HrJobNew' })">Create New Job</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demo only: this list contains tab-scoped mock jobs managed by the HR demo account. No real authentication or API request is performed. Published versions are read-only." />
      <p class="availability">Create and edit drafts in the shared wizard, or view version history. Case Results in the sidebar opens authorised cases.</p>
      <div class="filters"><ElInput v-model="filters.keyword" aria-label="Search managed jobs" placeholder="Search by job title, department or keyword" clearable :disabled="loading" />
        <ElSelect v-model="filters.status" aria-label="Job status" placeholder="All Statuses" :disabled="loading"><ElOption label="All Statuses" value="" /><ElOption label="Draft" value="Draft" /><ElOption label="Published" value="Published" /></ElSelect>
        <ElSelect v-model="filters.sort" aria-label="Sort jobs" :disabled="loading"><ElOption label="Newest First" value="latest" /><ElOption label="Oldest First" value="oldest" /></ElSelect></div>
      <div v-if="loading" class="panel state-panel" role="status"><p>Loading managed jobs...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>Jobs Could Not Be Loaded</h2><p>{{ error }}</p><ElButton type="primary" @click="loadJobs">Try Again</ElButton></div>
      <div v-else-if="!jobs.length" class="panel state-panel"><ElEmpty description="No managed jobs yet" /><h2>No Managed Jobs Yet</h2><p>There are no job drafts or published jobs in this demo list.</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty description="No jobs match your filters" /><ElButton @click="resetFilters">Reset Filters</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" aria-label="Managed jobs table"><table><caption>{{ filtered.length }} managed jobs · Published history cannot be edited directly</caption>
          <thead><tr><th scope="col">Job ID</th><th scope="col">Job Title</th><th scope="col">Department</th><th scope="col">Created Date</th><th scope="col">Status</th><th scope="col">Versions</th><th scope="col">Actions</th></tr></thead>
          <tbody><tr v-for="job in visible" :key="job.jobId"><th scope="row">{{ job.jobId }}</th><td>{{ job.title }}</td><td>{{ job.department }}</td><td>{{ formatDate(job.createdAt) }}</td><td><ElTag :type="job.status === 'Published' ? 'success' : 'info'">{{ job.status }}</ElTag></td>
            <td><span v-if="job.publishedVersion">Published {{ job.publishedVersion }}</span><span v-if="job.draftVersion" class="draft-version">Draft {{ job.draftVersion }}</span></td>
            <td><div class="row-actions"><ElButton size="small" @click="router.push({ name: 'HrJobVersions', params: { jobId: job.jobId } })">Versions</ElButton><ElButton v-if="job.draftVersion" size="small" @click="router.push({ name: 'HrJobEdit', params: { jobId: job.jobId } })">Edit Draft</ElButton></div></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="job in visible" :key="job.jobId" class="panel" :aria-label="job.title"><div class="card-heading"><span>{{ job.jobId }}</span><ElTag :type="job.status === 'Published' ? 'success' : 'info'">{{ job.status }}</ElTag></div>
          <h2>{{ job.title }}</h2><p>{{ job.department }} · {{ formatDate(job.createdAt) }}</p><p><span v-if="job.publishedVersion">Published {{ job.publishedVersion }}</span><span v-if="job.draftVersion" class="draft-version">Draft {{ job.draftVersion }}</span></p>
          <div class="row-actions"><ElButton @click="router.push({ name: 'HrJobVersions', params: { jobId: job.jobId } })">Versions</ElButton><ElButton v-if="job.draftVersion" @click="router.push({ name: 'HrJobEdit', params: { jobId: job.jobId } })">Edit Draft</ElButton></div></article></div>
        <div class="pagination"><div class="page-info"><span>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} of {{ filtered.length }} jobs</span><ElSelect v-model="pageSize" aria-label="Jobs per page"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="`${size} / page`" /></ElSelect></div>
          <ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </HrLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -0.7px; } h2 { color: #132941; font-size: 18px; }
p { font-size: 14px; line-height: 1.8; color: #586b83; }
.page-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.heading-actions, .row-actions { display: flex; flex-wrap: wrap; gap: 8px; } .el-button { margin: 0; }
.availability { font-size: 12px; margin: 16px 0; }
.filters { display: grid; grid-template-columns: minmax(0, 1fr) 160px 160px; gap: 14px; margin: 24px 0; }
.panel { background: white; border: 1px solid #e1eaf5; border-radius: 12px; padding: 20px; }
.state-panel { text-align: center; padding: 36px 20px; }
.table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid #176bdf; }
table { width: 100%; min-width: 830px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.8; }
caption { text-align: left; font-size: 12px; padding-bottom: 16px; }
th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; } thead th { background: #f3f7fd; color: #132941; }
tbody th { font-weight: 500; color: #176bdf; white-space: nowrap; }
.draft-version { display: block; color: #586b83; }
.pagination { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin: 24px 0; }
.page-info { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; font-size: 12px; color: #586b83; } .page-info .el-select { width: 120px; }
.mobile-list { display: none; } .card-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: #176bdf; }
@media (max-width: 1100px) { .filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } }
@media (max-width: 600px) { h1 { font-size: 25px; } .pagination { justify-content: center; } .page-info { justify-content: center; } }
</style>
