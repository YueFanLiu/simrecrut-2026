<script setup>
import { dateLocale, t } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElTag } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import OriginalPdfStatus from '../../../components/simrecrut/OriginalPdfStatus.vue'
import { useCasesStore } from '../../../store/modules/cases.js'
import { simulationScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const submitting = ref(false)
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
onBeforeUnmount(() => clearInterval(timer))
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const ready = computed(() => entry.value?.processingStatus === 'CONFIRMED')
const running = computed(() => entry.value?.processingStatus === 'AUTOMATED_RUNNING')
const complete = computed(() => entry.value?.processingStatus === 'AUTOMATED_COMPLETE')
const failed = computed(() => Boolean(entry.value?.confirmedAt && ['FAILED_RETRYABLE', 'FAILED_FINAL'].includes(entry.value.processingStatus)))
const canRetry = computed(() => failed.value && entry.value.assessment?.retryable && (!demoMode || entry.value.assessment.retryCount < 1))
const methods = computed(() => entry.value?.assessment?.methods || ['Objective', 'Neutral ML', 'Biased ML'].map((name) => ({ name, status: 'Waiting' })))
const assessmentMethods = computed(() => methods.value.filter(method => method.status !== 'Skipped').map(method => method.name).join(', '))
const heading = computed(() => ready.value ? 'Ready to Run' : running.value ? 'Simulation in Progress' : complete.value ? 'Simulation Complete' : 'Simulation Failed')
const tagType = (status) => status === 'Complete' ? 'success' : status === 'Failed' ? 'danger' : status === 'Running' ? 'primary' : 'info'
const formatTime = (time) => new Intl.DateTimeFormat(dateLocale.value, { timeStyle: 'medium', timeZone: 'Europe/Paris' }).format(new Date(time))

async function runSimulation(caseId, scenario, retry = false) {
  if (submitting.value) return
  submitting.value = true
  try { await cases.runSimulation(caseId, scenario, retry) } catch (error) { ElMessage.error(t(error.message)) } finally { submitting.value = false }
}

// Direct navigation must load the authorized case instead of requiring demo memory.
let connectedRequest = 0, connectedActive = true, loadingCaseId
const statusLoading = ref(false), statusError = ref(false)
async function refreshConnectedCase() {
  if (demoMode) return
  const id = String(route.params.caseId)
  if (statusLoading.value && loadingCaseId === id) return
  const request = ++connectedRequest
  loadingCaseId = id
  statusLoading.value = true; statusError.value = false
  try {
    const row = await cases.loadConnectedCase(id)
    if (connectedActive && request === connectedRequest && route.params.caseId === id) cases.cases[id] = row
  } catch (error) {
    if (connectedActive && request === connectedRequest) {
      statusError.value = true
      if ([401, 403, 404, 410].includes(error.status)) { delete cases.cases[id]; ElMessage.error(t(error.message)) }
    }
  } finally { if (request === connectedRequest) statusLoading.value = false }
}
watch(() => route.params.caseId, refreshConnectedCase)
refreshConnectedCase()
const connectedTimer = setInterval(() => { if (!demoMode && entry.value && running.value && !statusLoading.value) refreshConnectedCase() }, 2000)
onBeforeUnmount(() => { connectedActive = false; connectedRequest++; clearInterval(connectedTimer) })
</script>

<template>
  <CandidateLayout>
    <section v-if="!entry" class="panel unavailable">
      <h1>{{ $t("Case Not Available") }}</h1>
      <p>{{ $t("This case does not exist, has expired or is not available to your account.") }}</p>
      <ElButton v-if="!demoMode" :loading="statusLoading" @click="refreshConnectedCase">{{ $t('Try Again') }}</ElButton>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
    </section>
    <section v-else-if="!entry.confirmedAt" class="panel unavailable">
      <h1>{{ $t("Confirm Your Facts First") }}</h1>
      <p>{{ $t("Complete upload and fact review before running a simulation.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: entry.processingStatus === 'REVIEW_REQUIRED' ? 'CvReview' : 'CvUpload', params: { caseId: entry.caseId } })">{{ $t("Continue Preparation") }}</ElButton>
    </section>
    <section v-else aria-labelledby="progress-title">
      <nav class="breadcrumb" :aria-label="$t(&quot;Breadcrumb&quot;)"><RouterLink :to="{ name: 'PublishedJobs' }">{{ $t("Jobs") }}</RouterLink><span>/</span>
        <RouterLink :to="{ name: 'CvReview', params: { caseId: entry.caseId } }">{{ entry.caseId }}</RouterLink><span>/</span><span aria-current="page">{{ $t("Simulation Progress") }}</span></nav>
      <h1 id="progress-title">{{ $t("Simulation Progress") }}</h1>
      <p class="subtitle">{{ $t("Follow the automated assessment stages for your confirmed case.") }}</p>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demo only: fixed mock states, no model inference or API calls. Confirmed checkpoints survive reloads in this tab. Retry continues interrupted stages with the retained job version. Sign out clears case checkpoints.&quot;)" />
      <CaseProgressStepper :current-step="2" review-confirmed :simulation-complete="complete" />
      <OriginalPdfStatus :record="entry" :source="demoMode ? 'demo' : 'case'" :refreshable="!demoMode" auto-refresh
        :refreshing="statusLoading" :error="statusError" :disabled="submitting" @refresh="refreshConnectedCase" />
      <div class="progress-grid">
        <div class="main-column">
          <section class="panel" :aria-busy="running" aria-labelledby="status-title">
            <div class="panel-heading"><h2 id="status-title">{{ $t(heading) }}</h2><ElTag :type="complete ? 'success' : failed ? 'danger' : 'info'">{{ $t(ready ? 'Ready' : running ? 'Running' : complete ? 'Complete' : 'Failed') }}</ElTag></div>
            <div role="status" aria-live="polite">
              <p v-if="ready">{{ $t("Run the simulation to assess your confirmed facts.") }}</p>
              <p v-else-if="running">{{ $t("Running the") }} {{ entry.stage }} {{ $t("stage. Completed methods are retained.") }}</p>
              <p v-else-if="complete">{{ $t("Assessment complete. Open results to see the scores and current human review status.") }}</p>
            </div>
            <div v-if="failed" role="alert"><p class="error">{{ entry.failure }}</p><p>{{ $t("Failed stage:") }} {{ entry.stage }}{{ $t(". Confirmed facts and the job version remain locked.") }}</p></div>
            <div class="method-grid"><article v-for="method in methods" :key="method.name" class="method-card" :aria-label="$t(method.name)">
              <h3>{{ method.name }}</h3><ElTag :type="tagType(method.status)">{{ $t(method.status) }}</ElTag>
              <p>{{ $t(method.status === 'Complete' ? 'Stage completed.' : method.status === 'Running' ? 'Stage running...' : method.status === 'Failed' ? 'No result was produced.' : method.status === 'Skipped' ? 'Disabled for this assessment.' : 'Waiting for this stage.') }}</p>
            </article></div>
            <ElButton v-if="ready" type="primary" :loading="submitting" @click="runSimulation(entry.caseId, simulationScenario)">{{ $t("Run Simulation") }}</ElButton>
            <ElButton v-if="canRetry" type="primary" :loading="submitting" @click="runSimulation(entry.caseId, simulationScenario, true)">{{ $t("Retry Simulation") }}</ElButton>
            <p v-if="failed && !canRetry">{{ $t("Retry is unavailable for this failure. Return to Jobs to start a new case.") }}</p>
            <div v-if="complete || (!demoMode && (running || failed))" class="result-action"><ElButton type="primary" @click="router.push({ name: 'SimulationResults', params: { caseId: entry.caseId } })">{{ $t("View Results") }}</ElButton><p>{{ $t("View the five result cards and their separate score scales.") }}</p></div>
          </section>
          <section v-if="demoMode" class="panel log-panel" aria-labelledby="log-title"><h2 id="log-title">{{ $t("Simulation Log") }}</h2>
            <p v-if="!entry.assessment?.events.length">{{ $t("No run has started. Confirmed facts are ready.") }}</p>
            <ol v-else><li v-for="(event, index) in entry.assessment.events" :key="index"><time :datetime="event.time">{{ formatTime(event.time) }}</time><span>{{ $t(event.message) }}</span></li></ol>
            <p class="muted">{{ $t("Log timestamps use Paris time and describe local mock events.") }}</p>
          </section>
        </div>
        <aside class="side-column">
          <section class="panel"><h2>{{ $t("Simulation Parameters") }}</h2><dl>
            <div><dt>{{ $t("Case ID") }}</dt><dd>{{ entry.caseId }}</dd></div><div v-if="entry.jobTitle"><dt>{{ $t("Job") }}</dt><dd>{{ entry.jobTitle }}</dd></div>
            <div v-if="entry.jobVersionId"><dt>{{ $t("Job version") }}</dt><dd>{{ entry.jobVersionId }}</dd></div><div><dt>{{ $t("Professional facts") }}</dt><dd>{{ $t("Confirmed and locked") }}</dd></div>
            <div><dt>{{ $t("Assessment methods") }}</dt><dd>{{ assessmentMethods }}</dd></div><div v-if="demoMode"><dt>{{ $t("Configuration") }}</dt><dd>{{ $t("Fixed frontend mock stages") }}</dd></div>
          </dl></section>
          <section class="panel"><h2>{{ $t("Progress Summary") }}</h2><dl><div><dt>{{ $t("Current stage") }}</dt><dd>{{ $t(entry.stage === 'Ready for review' || ready ? 'Ready to run' : entry.stage) }}</dd></div>
            <div><dt>{{ $t("Last update") }}</dt><dd>{{ $t(entry.assessment?.updatedAt ? formatTime(entry.assessment.updatedAt) + ' (Paris)' : 'Not started') }}</dd></div><div v-if="demoMode"><dt>{{ $t("Human review") }}</dt><dd>{{ $t("Pending") }}</dd></div></dl>
            <p class="muted">{{ $t("No estimated duration or percentage is available.") }}</p></section>
          <ElButton @click="router.push({ name: 'CvReview', params: { caseId: entry.caseId } })">{{ $t("Back to Case") }}</ElButton>
          <ElButton @click="router.push({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
        </aside>
      </div>
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -0.7px; }
h2 { margin: 0 0 16px; font-size: 18px; color: var(--sr-ink); }
h3 { margin: 0 0 14px; font-size: 15px; color: var(--sr-ink); }
p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; }
.subtitle { margin: 10px 0 20px; }
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; font-size: 13px; color: var(--sr-text-muted); overflow-wrap: anywhere; }
.breadcrumb a { color: var(--sr-primary); text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.progress-grid { display: grid; grid-template-columns: minmax(0, 2.4fr) minmax(0, 1fr); gap: 22px; }
.main-column, .side-column { min-width: 0; display: flex; flex-direction: column; gap: 20px; }
.panel { padding: 24px; border: 1px solid var(--sr-border); background: white; border-radius: 12px; overflow-wrap: anywhere; }
.panel-heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; padding-bottom: 16px; border-bottom: 1px solid var(--sr-border); }
.panel-heading h2 { margin: 0; }
.method-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 24px 0; }
.method-card { padding: 18px 14px; background: var(--sr-surface-tint); border: 1px solid var(--sr-border); border-radius: 8px; }
.method-card p, .muted { font-size: 12px; }
.error { color: #b42336; }
.log-panel ol { list-style: none; padding: 0; margin: 0; }
.log-panel li { display: flex; gap: 20px; padding: 14px 0; border-bottom: 1px solid var(--sr-surface-sunken); color: var(--sr-text-muted); font-size: 13px; line-height: 1.7; }
time { flex-shrink: 0; color: var(--sr-primary); font-variant-numeric: tabular-nums; }
dl { margin: 0; font-size: 13px; line-height: 1.7; }
dl div { display: grid; grid-template-columns: 1fr 1.2fr; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--sr-surface-sunken); }
dt { color: var(--sr-text-muted); } dd { margin: 0; color: var(--sr-ink); }
.side-column > .el-button { margin: 0; }
.unavailable { text-align: center; padding: 50px 24px; }
.panel-heading .el-tag { max-width: 100%; white-space: normal; height: auto; min-height: 24px; }
@media (max-width: 1000px) { .progress-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 600px) { .panel { padding: 18px; } .method-grid { grid-template-columns: minmax(0, 1fr); } h1 { font-size: 25px; } .log-panel li { gap: 12px; } }
</style>
