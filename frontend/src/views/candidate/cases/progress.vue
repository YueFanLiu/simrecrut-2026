<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElTag } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import { useCasesStore } from '../../../store/modules/cases.js'
import { simulationScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
onBeforeUnmount(() => clearInterval(timer))
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const ready = computed(() => entry.value?.processingStatus === 'CONFIRMED')
const running = computed(() => entry.value?.processingStatus === 'AUTOMATED_RUNNING')
const complete = computed(() => entry.value?.processingStatus === 'AUTOMATED_COMPLETE')
const failed = computed(() => Boolean(entry.value?.confirmedAt && ['FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.value.processingStatus)))
const canRetry = computed(() => failed.value && entry.value.assessment?.retryable && entry.value.assessment.retryCount < 1)
const methods = computed(() => entry.value?.assessment?.methods || ['Objective', 'Neutral ML', 'Biased ML'].map((name) => ({ name, status: 'Waiting' })))
const heading = computed(() => ready.value ? 'Ready to Run' : running.value ? 'Simulation in Progress' : complete.value ? 'Simulation Complete' : 'Simulation Failed')
const tagType = (status) => status === 'Complete' ? 'success' : status === 'Failed' ? 'danger' : status === 'Running' ? 'primary' : 'info'
const formatTime = (time) => new Intl.DateTimeFormat('en-GB', { timeStyle: 'medium', timeZone: 'Europe/Paris' }).format(new Date(time))
</script>

<template>
  <CandidateLayout>
    <section v-if="!entry" class="panel unavailable">
      <h1>Demo Case Not Available</h1>
      <p>This case does not exist, has expired or is not available in your demo session.</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">Back to Jobs</ElButton>
    </section>
    <section v-else-if="!entry.confirmedAt" class="panel unavailable">
      <h1>Confirm Your Facts First</h1>
      <p>Complete upload and fact review before running a simulation.</p>
      <ElButton type="primary" @click="router.replace({ name: entry.processingStatus === 'REVIEW_REQUIRED' ? 'CvReview' : 'CvUpload', params: { caseId: entry.caseId } })">Continue Preparation</ElButton>
    </section>
    <section v-else aria-labelledby="progress-title">
      <nav class="breadcrumb" aria-label="Breadcrumb"><RouterLink :to="{ name: 'PublishedJobs' }">Jobs</RouterLink><span>/</span>
        <RouterLink :to="{ name: 'CvReview', params: { caseId: entry.caseId } }">{{ entry.caseId }}</RouterLink><span>/</span><span aria-current="page">Simulation Progress</span></nav>
      <h1 id="progress-title">Simulation Progress</h1>
      <p class="subtitle">Follow the three automated assessment stages for your confirmed case.</p>
      <ElAlert type="info" :closable="false" title="Frontend demo only: fixed mock states, no model inference or API calls. Confirmed checkpoints survive reloads in this tab. Retry continues interrupted stages with the retained job version. Sign out clears case checkpoints." />
      <CaseProgressStepper :current-step="2" review-confirmed :simulation-complete="complete" />
      <div class="progress-grid">
        <div class="main-column">
          <section class="panel" :aria-busy="running" aria-labelledby="status-title">
            <div class="panel-heading"><h2 id="status-title">{{ heading }}</h2><ElTag :type="complete ? 'success' : failed ? 'danger' : 'info'">{{ ready ? 'Ready' : running ? 'Running' : complete ? 'Complete' : 'Failed' }}</ElTag></div>
            <div role="status" aria-live="polite">
              <p v-if="ready">Your confirmed facts are locked. Run the demo to begin all three automated stages.</p>
              <p v-else-if="running">Running the mock {{ entry.stage }} stage. Completed methods are retained.</p>
              <p v-else-if="complete">All three automated demo stages have completed. Human review is pending.</p>
            </div>
            <div v-if="failed" role="alert"><p class="error">{{ entry.failure }}</p><p>Failed stage: {{ entry.stage }}. Confirmed facts and the job version remain locked.</p></div>
            <div class="method-grid"><article v-for="method in methods" :key="method.name" class="method-card" :aria-label="method.name">
              <h3>{{ method.name }}</h3><ElTag :type="tagType(method.status)">{{ method.status }}</ElTag>
              <p>{{ method.status === 'Complete' ? 'Demo stage completed.' : method.status === 'Running' ? 'Demo stage running...' : method.status === 'Failed' ? 'No result was produced.' : 'Waiting for this stage.' }}</p>
            </article></div>
            <ElButton v-if="ready" type="primary" @click="cases.runSimulation(entry.caseId, simulationScenario)">Run Simulation</ElButton>
            <ElButton v-if="canRetry" type="primary" @click="cases.runSimulation(entry.caseId, simulationScenario, true)">Retry Simulation</ElButton>
            <p v-if="failed && !canRetry">Retry is unavailable for this failure. Return to Jobs to start a new demo case.</p>
            <div v-if="complete" class="result-action"><ElButton type="primary" @click="router.push({ name: 'SimulationResults', params: { caseId: entry.caseId } })">View Results</ElButton><p>View the five result cards and their separate score scales.</p></div>
          </section>
          <section class="panel log-panel" aria-labelledby="log-title"><h2 id="log-title">Simulation Log</h2>
            <p v-if="!entry.assessment?.events.length">No run has started. Confirmed facts are ready.</p>
            <ol v-else><li v-for="(event, index) in entry.assessment.events" :key="index"><time :datetime="event.time">{{ formatTime(event.time) }}</time><span>{{ event.message }}</span></li></ol>
            <p class="muted">Log timestamps use Paris time and describe local mock events.</p>
          </section>
        </div>
        <aside class="side-column">
          <section class="panel"><h2>Simulation Parameters</h2><dl>
            <div><dt>Case ID</dt><dd>{{ entry.caseId }}</dd></div><div><dt>Job</dt><dd>{{ entry.jobTitle }}</dd></div>
            <div><dt>Job version</dt><dd>{{ entry.jobVersionId }}</dd></div><div><dt>Professional facts</dt><dd>Confirmed and locked</dd></div>
            <div><dt>Assessment methods</dt><dd>Objective, Neutral ML, Biased ML</dd></div><div><dt>Configuration</dt><dd>Fixed frontend mock stages</dd></div>
          </dl></section>
          <section class="panel"><h2>Progress Summary</h2><dl><div><dt>Current stage</dt><dd>{{ entry.stage === 'Ready for review' || ready ? 'Ready to run' : entry.stage }}</dd></div>
            <div><dt>Last update</dt><dd>{{ entry.assessment?.updatedAt ? formatTime(entry.assessment.updatedAt) + ' (Paris)' : 'Not started' }}</dd></div><div><dt>Human review</dt><dd>Pending</dd></div></dl>
            <p class="muted">No estimated duration or percentage is available.</p></section>
          <ElButton @click="router.push({ name: 'CvReview', params: { caseId: entry.caseId } })">Back to Case</ElButton>
          <ElButton @click="router.push({ name: 'PublishedJobs' })">Back to Jobs</ElButton>
        </aside>
      </div>
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -0.7px; }
h2 { margin: 0 0 16px; font-size: 18px; color: #132941; }
h3 { margin: 0 0 14px; font-size: 15px; color: #132941; }
p { color: #586b83; font-size: 14px; line-height: 1.8; }
.subtitle { margin: 10px 0 20px; }
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; font-size: 13px; color: #586b83; overflow-wrap: anywhere; }
.breadcrumb a { color: #176bdf; text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.progress-grid { display: grid; grid-template-columns: minmax(0, 2.4fr) minmax(0, 1fr); gap: 22px; }
.main-column, .side-column { min-width: 0; display: flex; flex-direction: column; gap: 20px; }
.panel { padding: 24px; border: 1px solid #e1eaf5; background: white; border-radius: 12px; overflow-wrap: anywhere; }
.panel-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid #e1eaf5; }
.panel-heading h2 { margin: 0; }
.method-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 24px 0; }
.method-card { padding: 18px 14px; background: #f6f9fd; border: 1px solid #e1eaf5; border-radius: 8px; }
.method-card p, .muted { font-size: 12px; }
.error { color: #b42336; }
.log-panel ol { list-style: none; padding: 0; margin: 0; }
.log-panel li { display: flex; gap: 20px; padding: 14px 0; border-bottom: 1px solid #edf1f7; color: #586b83; font-size: 13px; line-height: 1.7; }
time { flex-shrink: 0; color: #176bdf; font-variant-numeric: tabular-nums; }
dl { margin: 0; font-size: 13px; line-height: 1.7; }
dl div { display: grid; grid-template-columns: 1fr 1.2fr; gap: 12px; padding: 12px 0; border-bottom: 1px solid #edf1f7; }
dt { color: #586b83; } dd { margin: 0; color: #132941; }
.side-column > .el-button { margin: 0; }
.unavailable { text-align: center; padding: 50px 24px; }
.panel-heading .el-tag { max-width: 100%; white-space: normal; height: auto; min-height: 24px; }
@media (max-width: 1000px) { .progress-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 600px) { .panel { padding: 18px; } .method-grid { grid-template-columns: minmax(0, 1fr); } h1 { font-size: 25px; } .log-panel li { gap: 12px; } }
</style>
