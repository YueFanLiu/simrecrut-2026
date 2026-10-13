<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElSkeleton, ElTag } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import { loadJobDetail } from '../../../mock/job-details.js'
import { formatPublishedDate } from '../../../utils/job-list.js'
import { caseCreationScenario, jobDetailScenario } from '../../../config/demo.js'
import { useCasesStore } from '../../../store/modules/cases.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const creatingCase = ref(false)
const creationError = ref('')
const job = ref(null)
const loading = ref(true)
const loadError = ref('')
let loadSequence = 0
let pageActive = true

const keyInformation = computed(() => job.value ? [
  ['Employment Type', job.value.employmentType], ['Location', job.value.location],
  ['Category', job.value.category], ['Required Experience', job.value.requirements.experience],
  ['Education Level', job.value.requirements.education],
  ['Language Requirement', job.value.requirements.language],
  ['Application Deadline', formatPublishedDate(job.value.applicationDeadline)],
  ['Published Version', job.value.version],
] : [])
const companyInformation = computed(() => job.value ? [
  ['Company', job.value.company], ['Industry', job.value.industry],
  ['Company Size', job.value.companySize],
] : [])

async function loadDetails() {
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  job.value = null
  creationError.value = ''
  try {
    const result = await loadJobDetail(route.params.jobId, jobDetailScenario)
    if (sequence === loadSequence) job.value = result
  } catch (error) {
    if (sequence === loadSequence) {
      loadError.value = error.message || 'Unable to load job details. Please try again.'
    }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

// Ignore late responses after a different job is selected or this page is left.
watch(() => route.params.jobId, loadDetails, { immediate: true })
onBeforeUnmount(() => { loadSequence += 1; pageActive = false })

async function startSimulation() {
  if (creatingCase.value || !job.value) return
  creatingCase.value = true
  creationError.value = ''
  const displayedJob = job.value
  try {
    const caseId = await cases.createCase(displayedJob, caseCreationScenario)
    if (pageActive && route.name === 'JobDetail' && route.params.jobId === displayedJob.jobId) {
      await router.push({ name: 'CvUpload', params: { caseId } })
    } else {
      cases.deleteCase(caseId)
    }
  } catch (error) {
    creationError.value = error.message || 'Unable to create the demo case. Please try again.'
  } finally {
    creatingCase.value = false
  }
}
</script>

<template>
  <CandidateLayout>
    <section :aria-busy="loading" aria-label="Job details">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <RouterLink :to="{ name: 'PublishedJobs' }">Jobs</RouterLink>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{{ job?.title || 'Job Details' }}</span>
      </nav>

      <div v-if="loading" class="state-panel loading-state" role="status">
        <p>Loading job details...</p>
        <ElSkeleton :rows="8" animated />
      </div>
      <div v-else-if="loadError" class="state-panel" role="alert">
        <h1>Job details could not be loaded</h1>
        <p>{{ loadError }}</p>
        <div class="state-actions">
          <ElButton type="primary" @click="loadDetails">Try again</ElButton>
          <RouterLink class="back-link" :to="{ name: 'PublishedJobs' }">Back to Jobs</RouterLink>
        </div>
      </div>
      <div v-else-if="!job" class="state-panel">
        <ElEmpty description="Job Not Available" />
        <h1>Job Not Available</h1>
        <p>This job posting is no longer available, has been unpublished or does not exist.</p>
        <RouterLink class="back-link" :to="{ name: 'PublishedJobs' }">Back to Jobs</RouterLink>
      </div>
      <template v-else>
        <div class="detail-heading">
          <div>
            <h1>{{ job.title }}</h1>
            <div class="job-meta">
              <span>{{ job.company }}</span><span>{{ job.location }}</span>
              <ElTag>{{ job.employmentType }}</ElTag>
              <span>Published on <time :datetime="job.publishedAt">{{ formatPublishedDate(job.publishedAt) }}</time></span>
            </div>
          </div>
          <ElButton type="primary" size="large" :loading="creatingCase" :disabled="creatingCase"
            aria-describedby="simulation-notice" @click="startSimulation">{{ creatingCase ? 'Creating Demo Case...' : 'Start Simulation' }}</ElButton>
        </div>
        <ElAlert id="simulation-notice" class="demo-notice" type="info" :closable="false"
          title="Frontend demo: Start Simulation creates a temporary case in memory and opens the PDF upload demo. No backend case or real assessment is created." />
        <ElAlert v-if="creationError" class="demo-notice" type="error" :closable="false" :title="creationError" />
        <div class="detail-grid">
          <div class="panel-column">
            <section class="detail-panel" aria-labelledby="description-title">
              <h2 id="description-title">Job Description</h2>
              <p>{{ job.description }}</p>
            </section>
            <section class="detail-panel" aria-labelledby="requirements-title">
              <h2 id="requirements-title">Requirements</h2>
              <h3>Key Responsibilities</h3>
              <ul><li v-for="item in job.requirements.responsibilities" :key="item">{{ item }}</li></ul>
              <h3>Qualifications</h3>
              <ul><li v-for="item in job.requirements.qualifications" :key="item">{{ item }}</li></ul>
            </section>
          </div>
          <div class="panel-column">
            <section class="detail-panel" aria-labelledby="information-title">
              <h2 id="information-title">Key Information</h2>
              <dl><div v-for="[label, value] in keyInformation" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></div></dl>
            </section>
            <section class="detail-panel" aria-labelledby="company-title">
              <h2 id="company-title">Additional Information</h2>
              <dl><div v-for="[label, value] in companyInformation" :key="label"><dt>{{ label }}</dt><dd>{{ value }}</dd></div></dl>
            </section>
          </div>
        </div>
        <RouterLink class="back-link bottom-back" :to="{ name: 'PublishedJobs' }">Back to Jobs</RouterLink>
      </template>
    </section>
  </CandidateLayout>
</template>

<style scoped>
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: #586b83; font-size: 13px; line-height: 1.7; }
.breadcrumb a { color: #176bdf; text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.detail-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; margin-bottom: 22px; }
.detail-heading > div { min-width: 0; }
h1 { margin: 0; color: #132941; font-size: 28px; line-height: 1.4; letter-spacing: -0.7px; overflow-wrap: anywhere; }
.job-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 22px; margin-top: 16px; color: #586b83; font-size: 13px; line-height: 1.7; }
.demo-notice { margin-bottom: 22px; }
.detail-grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: 20px; }
.panel-column { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.detail-panel, .state-panel { padding: 24px; border: 1px solid #e1eaf5; border-radius: 10px; background: white; }
h2 { margin: 0 0 16px; color: #20354e; font-size: 17px; }
h3 { margin: 22px 0 10px; color: #425269; font-size: 14px; }
.detail-panel p, .detail-panel li { color: #586b83; font-size: 14px; line-height: 1.9; }
.detail-panel p { margin: 0; }
ul { margin: 0; padding-left: 20px; }
li + li { margin-top: 6px; }
dl { margin: 0; font-size: 13px; line-height: 1.7; }
dl > div { display: grid; grid-template-columns: 1fr 1.1fr; gap: 16px; padding: 9px 0; border-bottom: 1px solid #edf1f7; }
dl > div:last-child { border-bottom: 0; padding-bottom: 0; }
dt { color: #68798c; }
dd { margin: 0; color: #425269; overflow-wrap: anywhere; }
.back-link { display: inline-block; padding: 10px 16px; color: #176bdf; border: 1px solid #b9d3f5; border-radius: 6px; text-decoration: none; font-size: 14px; line-height: 1.5; background: white; }
.back-link:hover { background: #edf5ff; }
.back-link:focus-visible, .breadcrumb a:focus-visible { outline: 2px solid #176bdf; outline-offset: 3px; }
.bottom-back { margin-top: 24px; }
.state-panel { padding: 40px 24px; text-align: center; }
.state-panel h1 { font-size: 24px; }
.state-panel p { color: #586b83; font-size: 14px; line-height: 1.8; margin: 16px 0 24px; }
.state-actions { display: flex; justify-content: center; align-items: center; flex-wrap: wrap; gap: 12px; }
.state-panel :deep(.el-empty) { padding: 0 0 16px; }
.state-panel :deep(.el-empty__description) { display: none; }
.loading-state { text-align: left; }
.loading-state p { margin-top: 0; }
@media (max-width: 700px) {
  .detail-grid { grid-template-columns: 1fr; }
  .detail-heading { flex-direction: column; gap: 18px; }
  .detail-heading .el-button { width: 100%; }
  h1 { font-size: 25px; }
  .detail-panel { padding: 20px; }
  dl > div { grid-template-columns: 1fr 1fr; gap: 12px; }
}
</style>
