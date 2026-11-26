<script setup>
import { demoMode } from '../../../config/runtime.js'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import JobCard from '../../../components/simrecrut/JobCard.vue'
import { loadPublishedJobs } from '../../../mock/jobs.js'
import { filterPublishedJobs } from '../../../utils/job-list.js'
import { jobsScenario } from '../../../config/demo.js'
import { useRoute, useRouter } from 'vue-router'
import { cvContext } from '../../../utils/cv-workflow.js'

const jobs = ref([])
const route = useRoute(), router = useRouter()
const resumeId = computed(() => !demoMode ? cvContext(route.query).resumeId : '')
const loading = ref(true)
const loadError = ref('')
const page = ref(1)
const pageSize = 6
const filters = reactive({ keyword: '', category: '', location: '', employmentType: '', sort: 'latest' })
const filteredJobs = computed(() => filterPublishedJobs(jobs.value, filters))
const visibleJobs = computed(() => filteredJobs.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const categories = computed(() => [...new Set(jobs.value.map((job) => job.category))].sort())
const locations = computed(() => [...new Set(jobs.value.map((job) => job.location))].sort())
const employmentTypes = computed(() => [...new Set(jobs.value.map((job) => job.employmentType))].sort())
const hasFilters = computed(() => Boolean(filters.keyword || filters.category || filters.location || filters.employmentType))

watch(filters, () => { page.value = 1 })

async function loadJobs() {
  loading.value = true
  loadError.value = ''
  try {
    jobs.value = await loadPublishedJobs(jobsScenario)
    page.value = 1
  } catch (error) {
    jobs.value = []
    loadError.value = error.message || 'Unable to load published jobs. Please try again.'
  } finally {
    loading.value = false
  }
}

function clearFilters() {
  Object.assign(filters, { keyword: '', category: '', location: '', employmentType: '', sort: 'latest' })
  page.value = 1
}

onMounted(loadJobs)
</script>

<template>
  <CandidateLayout>
    <section aria-labelledby="jobs-title" :aria-busy="loading">
      <div class="page-heading">
        <h1 id="jobs-title">{{ $t("Published Jobs") }}</h1>
        <p>{{ $t("Browse available job postings for your next recruitment simulation.") }}</p>
      </div>
      <section v-if="resumeId" class="cv-context" aria-live="polite"><p>{{ $t('A library CV is selected. Choose a job to use it in a simulation.') }}</p><ElButton @click="router.push({ name: 'ConnectedResumes' })">{{ $t('Change CV') }}</ElButton></section>
      <ElAlert v-if="demoMode" class="demo-notice" type="info" :closable="false"
        :title="$t(&quot;Frontend demo: search, filters and sorting use local mock data. Open My simulations to continue or delete your retained demo cases.&quot;)" />

      <form class="job-filters" :aria-label="$t(&quot;Filter published jobs&quot;)" @submit.prevent>
        <div class="search-field">
          <label for="job-search">{{ $t("Search jobs") }}</label>
          <ElInput id="job-search" v-model="filters.keyword" clearable :disabled="loading || Boolean(loadError)"
            :placeholder="$t(&quot;Search by title, company or keyword&quot;)" autocomplete="off" />
        </div>
        <div v-if="demoMode">
          <label id="category-label">{{ $t("Category") }}</label>
          <ElSelect v-model="filters.category" :aria-label="$t(&quot;Category&quot;)" :empty-values="[null, undefined]" :disabled="loading || Boolean(loadError)">
            <ElOption :label="$t(&quot;All Categories&quot;)" value="" />
            <ElOption v-for="category in categories" :key="category" :label="$t(category)" :value="category" />
          </ElSelect>
        </div>
        <div v-if="demoMode">
          <label id="location-label">{{ $t("Location") }}</label>
          <ElSelect v-model="filters.location" :aria-label="$t(&quot;Location&quot;)" :empty-values="[null, undefined]" :disabled="loading || Boolean(loadError)">
            <ElOption :label="$t(&quot;All Locations&quot;)" value="" />
            <ElOption v-for="location in locations" :key="location" :label="$t(location)" :value="location" />
          </ElSelect>
        </div>
        <div v-if="demoMode">
          <label id="type-label">{{ $t("Type") }}</label>
          <ElSelect v-model="filters.employmentType" :aria-label="$t(&quot;Type&quot;)" :empty-values="[null, undefined]" :disabled="loading || Boolean(loadError)">
            <ElOption :label="$t(&quot;All Types&quot;)" value="" />
            <ElOption v-for="type in employmentTypes" :key="type" :label="$t(type)" :value="type" />
          </ElSelect>
        </div>
        <div>
          <label id="sort-label">{{ $t("Sort by") }}</label>
          <ElSelect v-model="filters.sort" :aria-label="$t(&quot;Sort by&quot;)" :disabled="loading || Boolean(loadError)">
            <ElOption :label="$t(&quot;Latest first&quot;)" value="latest" />
            <ElOption :label="$t(&quot;Oldest first&quot;)" value="oldest" />
            <ElOption :label="$t(&quot;Title A-Z&quot;)" value="title" />
          </ElSelect>
        </div>
      </form>

      <div v-if="loading" class="loading-state" role="status">
        <p>{{ $t("Loading published jobs...") }}</p>
        <ElSkeleton :rows="5" animated />
      </div>
      <div v-else-if="loadError" class="state-panel" role="alert">
        <h2>{{ $t("Jobs could not be loaded") }}</h2>
        <p>{{ $t(loadError) }}</p>
        <ElButton type="primary" @click="loadJobs">{{ $t("Try again") }}</ElButton>
      </div>
      <div v-else-if="!jobs.length" class="state-panel">
        <ElEmpty :description="$t(&quot;No published jobs yet&quot;)" />
        <p>{{ $t("There are currently no job postings available. Please check back later.") }}</p>
      </div>
      <div v-else-if="!filteredJobs.length" class="state-panel">
        <ElEmpty :description="$t(&quot;No matching jobs found&quot;)" />
        <p>{{ $t("Try different keywords or adjust the filters.") }}</p>
        <ElButton type="primary" @click="clearFilters">{{ $t("Clear Filters") }}</ElButton>
      </div>
      <template v-else>
        <div class="results-bar">
          <p role="status" aria-live="polite"> {{ $t("Showing") }} {{ (page - 1) * pageSize + 1 }}-{{ Math.min(page * pageSize, filteredJobs.length) }} {{ $t("of") }} {{ filteredJobs.length }} {{ $t("jobs") }} </p>
          <ElButton v-if="hasFilters" text @click="clearFilters">{{ $t("Clear Filters") }}</ElButton>
        </div>
        <div class="job-grid">
          <JobCard v-for="job in visibleJobs" :key="job.jobId" :job="job" :resume-id="resumeId" />
        </div>
        <ElPagination v-if="filteredJobs.length > pageSize" v-model:current-page="page"
          class="job-pagination" :page-size="pageSize" :total="filteredJobs.length"
          layout="prev, pager, next" :aria-label="$t(&quot;Published jobs pages&quot;)" />
      </template>
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -0.7px; }
.page-heading p { margin: 10px 0 22px; color: var(--sr-text-muted); font-size: 14px; line-height: 1.7; }
.demo-notice { margin-bottom: 22px; }
.cv-context { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; margin-bottom:20px; padding:16px; background:var(--sr-primary-50); border-radius:9px; } .cv-context p { margin:0; color:var(--sr-text-secondary); font-size:13px; }
.job-filters { display: grid; grid-template-columns: 2fr repeat(4, 1fr); gap: 14px; margin-bottom: 20px; }
.job-filters > div { min-width: 0; }
.job-filters label { display: block; margin-bottom: 7px; font-size: 12px; color: var(--sr-text-secondary); font-weight: 600; }
.job-filters .el-select { width: 100%; }
.results-bar { display: flex; justify-content: space-between; align-items: center; min-height: 32px; margin-bottom: 12px; }
.results-bar p { margin: 0; color: var(--sr-text-muted); font-size: 12px; }
.job-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }
.job-pagination { display: flex; justify-content: center; margin-top: 28px; }
.loading-state, .state-panel { padding: 40px 24px; border: 1px solid var(--sr-border); border-radius: 10px; background: white; }
.loading-state p { margin: 0 0 24px; color: var(--sr-text-muted); font-size: 14px; }
.state-panel { text-align: center; }
.state-panel h2 { font-size: 20px; color: var(--sr-text); }
.state-panel p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.7; }
.state-panel :deep(.el-empty) { padding: 0 0 12px; }
@media (max-width: 1000px) {
  .job-filters { grid-template-columns: repeat(4, 1fr); }
  .search-field { grid-column: 1 / -1; }
  .job-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 700px) {
  .job-filters { grid-template-columns: repeat(2, 1fr); gap: 12px; }
  .job-grid { grid-template-columns: 1fr; }
  h1 { font-size: 25px; }
  .state-panel { padding: 32px 16px; }
}
</style>
