<script setup>
import { dateLocale } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { managedJobs } from '../../../api/jobs.js'
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
const formatDate = (date) => new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeZone: 'UTC',
}).format(new Date(`${date}T00:00:00Z`))

async function loadJobs() {
  const request = ++sequence
  const username = auth.demoUser?.username
  loading.value = true
  error.value = ''
  jobs.value = []
  try {
    const data = await (demoMode ? loadHrJobs(auth.demoUser, hrJobsScenario, store.jobs) : managedJobs())
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
      <div class="page-heading"><div><h1 id="jobs-title">{{ $t("Job Management") }}</h1><p>{{ $t("View the drafts and published versions of jobs you manage.") }}</p></div>
        <div class="heading-actions"><ElButton :loading="loading" :disabled="loading" @click="loadJobs">{{ $t("Refresh") }}</ElButton><ElButton type="primary" @click="router.push({ name: 'HrJobNew' })">{{ $t("Create New Job") }}</ElButton></div></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demo only: this list contains tab-scoped mock jobs managed by the HR demo account. No real authentication or API request is performed. Published versions are read-only.&quot;)" />
      <p class="availability">{{ $t("Create and edit drafts in the shared wizard, or view version history. Case Results in the sidebar opens authorised cases.") }}</p>
      <div class="filters"><ElInput v-model="filters.keyword" :aria-label="$t(&quot;Search managed jobs&quot;)" :placeholder="$t(&quot;Search by job title, department or keyword&quot;)" clearable :disabled="loading" />
        <ElSelect v-model="filters.status" :aria-label="$t(&quot;Job status&quot;)" :placeholder="$t(&quot;All Statuses&quot;)" :disabled="loading"><ElOption :label="$t(&quot;All Statuses&quot;)" value="" /><ElOption :label="$t(&quot;Draft&quot;)" value="Draft" /><ElOption :label="$t(&quot;Published&quot;)" value="Published" /></ElSelect>
        <ElSelect v-model="filters.sort" :aria-label="$t(&quot;Sort jobs&quot;)" :disabled="loading"><ElOption :label="$t(&quot;Newest First&quot;)" value="latest" /><ElOption :label="$t(&quot;Oldest First&quot;)" value="oldest" /></ElSelect></div>
      <div v-if="loading" class="panel state-panel" role="status"><p>{{ $t("Loading managed jobs...") }}</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="error" class="panel state-panel" role="alert"><h2>{{ $t("Jobs Could Not Be Loaded") }}</h2><p>{{ $t(error) }}</p><ElButton type="primary" @click="loadJobs">{{ $t("Try Again") }}</ElButton></div>
      <div v-else-if="!jobs.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No managed jobs yet&quot;)" /><h2>{{ $t("No Managed Jobs Yet") }}</h2><p>{{ $t("There are no job drafts or published jobs in this demo list.") }}</p></div>
      <div v-else-if="!filtered.length" class="panel state-panel"><ElEmpty :description="$t(&quot;No jobs match your filters&quot;)" /><ElButton @click="resetFilters">{{ $t("Reset Filters") }}</ElButton></div>
      <template v-else>
        <div class="panel desktop-list"><div class="table-scroll" role="region" tabindex="0" :aria-label="$t(&quot;Managed jobs table&quot;)"><table><caption>{{ filtered.length }} {{ $t("managed jobs · Published history cannot be edited directly") }}</caption>
          <thead><tr><th scope="col">{{ $t("Job ID") }}</th><th scope="col">{{ $t("Job Title") }}</th><th v-if="demoMode" scope="col">{{ $t("Department") }}</th><th scope="col">{{ $t("Created Date") }}</th><th scope="col">{{ $t("Status") }}</th><th scope="col">{{ $t("Versions") }}</th><th scope="col">{{ $t("Actions") }}</th></tr></thead>
          <tbody><tr v-for="job in visible" :key="job.jobId"><th scope="row">{{ job.jobId }}</th><td>{{ job.title }}</td><td v-if="demoMode">{{ job.department }}</td><td>{{ formatDate(job.createdAt) }}</td><td><ElTag :type="job.status === 'Published' ? 'success' : 'info'">{{ $t(job.status) }}</ElTag></td>
            <td><span v-if="job.publishedVersion">{{ $t("Published") }} {{ job.publishedVersion }}</span><span v-if="job.draftVersion" class="draft-version">{{ $t("Draft") }} {{ job.draftVersion }}</span></td>
            <td><div class="row-actions"><ElButton size="small" @click="router.push({ name: 'HrJobVersions', params: { jobId: job.jobId } })">{{ $t("Versions") }}</ElButton><ElButton v-if="job.draftVersion" size="small" @click="router.push({ name: 'HrJobEdit', params: { jobId: job.jobId } })">{{ $t("Edit Draft") }}</ElButton></div></td></tr></tbody></table></div></div>
        <div class="mobile-list"><article v-for="job in visible" :key="job.jobId" class="panel" :aria-label="$t(job.title)"><div class="card-heading"><span>{{ job.jobId }}</span><ElTag :type="job.status === 'Published' ? 'success' : 'info'">{{ $t(job.status) }}</ElTag></div>
          <h2>{{ job.title }}</h2><p><template v-if="demoMode">{{ job.department }} · </template>{{ formatDate(job.createdAt) }}</p><p><span v-if="job.publishedVersion">{{ $t("Published") }} {{ job.publishedVersion }}</span><span v-if="job.draftVersion" class="draft-version">{{ $t("Draft") }} {{ job.draftVersion }}</span></p>
          <div class="row-actions"><ElButton @click="router.push({ name: 'HrJobVersions', params: { jobId: job.jobId } })">{{ $t("Versions") }}</ElButton><ElButton v-if="job.draftVersion" @click="router.push({ name: 'HrJobEdit', params: { jobId: job.jobId } })">{{ $t("Edit Draft") }}</ElButton></div></article></div>
        <div class="pagination"><div class="page-info"><span>{{ $t("Showing") }} {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, filtered.length) }} {{ $t("of") }} {{ filtered.length }} {{ $t("jobs") }}</span><ElSelect v-model="pageSize" :aria-label="$t(&quot;Jobs per page&quot;)"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="$t('{size} / page', { size })" /></ElSelect></div>
          <ElPagination v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" :pager-count="5" background /></div>
      </template>
    </section>
  </HrLayout>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -0.7px; } h2 { color: var(--sr-ink); font-size: 18px; }
p { font-size: 14px; line-height: 1.8; color: var(--sr-text-muted); }
.page-heading { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.heading-actions, .row-actions { display: flex; flex-wrap: wrap; gap: 8px; } .el-button { margin: 0; }
.availability { font-size: 12px; margin: 16px 0; }
.filters { display: grid; grid-template-columns: minmax(0, 1fr) 160px 160px; gap: 14px; margin: 24px 0; }
.panel { background: white; border: 1px solid var(--sr-border); border-radius: 12px; padding: 20px; }
.state-panel { text-align: center; padding: 36px 20px; }
.table-scroll { overflow-x: auto; } .table-scroll:focus-visible { outline: 2px solid var(--sr-primary); }
table { width: 100%; min-width: 830px; border-collapse: collapse; color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; }
caption { text-align: left; font-size: 12px; padding-bottom: 16px; }
th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid var(--sr-border); } thead th { background: var(--sr-primary-50); color: var(--sr-ink); }
tbody th { font-weight: 500; color: var(--sr-primary); white-space: nowrap; }
.draft-version { display: block; color: var(--sr-text-muted); }
.pagination { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin: 24px 0; }
.page-info { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; font-size: 12px; color: var(--sr-text-muted); } .page-info .el-select { width: 120px; }
.mobile-list { display: none; } .card-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: var(--sr-primary); }
@media (max-width: 1100px) { .filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } .filters .el-input { grid-column: 1 / -1; } }
@media (max-width: 760px) { .desktop-list { display: none; } .mobile-list { display: grid; gap: 16px; } }
@media (max-width: 600px) { h1 { font-size: 25px; } .pagination { justify-content: center; } .page-info { justify-content: center; } }
</style>
