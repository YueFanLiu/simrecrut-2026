<script setup>
import { dateLocale, t } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElSkeleton, ElTabPane, ElTabs, ElTag } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import ResultCard from '../../../components/simrecrut/ResultCard.vue'
import ScoreScalePanel from '../../../components/simrecrut/ScoreScalePanel.vue'
import SimulationDeleteDialog from '../../../components/simrecrut/SimulationDeleteDialog.vue'
import { useCasesStore } from '../../../store/modules/cases.js'
import { resultsScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const ready = computed(() => demoMode ? entry.value?.processingStatus === 'AUTOMATED_COMPLETE' : Boolean(entry.value?.confirmedAt))
const results = ref(null)
const objectiveOnly = computed(() => !demoMode && results.value?.automatedStatus === 'AUTOMATED_COMPLETE'
  && !results.value.neutral && !results.value.biased)
const loading = ref(false)
const busy = ref('')
const error = ref('')
const feedback = ref('')
const confirmDelete = ref(false)
const activeTab = ref('summary')
let sequence = 0
let pageActive = true
const humanCards = computed(() => results.value ? [
  { key: 'standard', title: 'Standard Human Decision', ...results.value.humans.standard },
  { key: 'instructedBias', title: 'Instructed-Bias Human Decision', ...results.value.humans.instructedBias },
] : [])
const comparisonRows = computed(() => results.value ? [
  { method: 'Objective Fit Score', score: results.value.objective?.displayScore ?? '—', scale: '0–100', decision: results.value.objective?.decision ?? '—', status: results.value.objective ? 'Complete' : 'Pending' },
  { method: 'Neutral ML Acceptance Score', score: results.value.neutral?.acceptanceScore ?? '—', scale: '0–1', decision: results.value.neutral?.decision ?? '—', status: objectiveOnly.value ? 'Skipped' : results.value.neutral ? 'Complete' : 'Pending' },
  { method: 'Biased ML Acceptance Score', score: results.value.biased?.acceptanceScore ?? '—', scale: '0–1', decision: results.value.biased?.decision ?? '—', status: objectiveOnly.value ? 'Skipped' : results.value.biased ? 'Complete' : 'Pending' },
  ...humanCards.value.map((human) => ({ method: human.title, score: 'Not applicable', scale: 'Decision only', decision: human.decision || 'No decision yet', status: human.status })),
] : [])
const researchLabels = { schoolPrestige: 'School prestige', referral: 'Referral', gender: 'Gender', ethnicity: 'Ethnicity', nameCue: 'Name cue',
  SCHOOL_CATEGORY: 'School prestige', REFERRAL: 'Referral', GENDER: 'Gender',
  ETHNICITY: 'Ethnicity', NAME_CUE: 'Name cue' }
const formatDate = (time) => new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(time))

async function loadResults(refreshHuman = false) {
  if (!ready.value || busy.value) return
  const request = ++sequence
  const caseId = route.params.caseId
  error.value = ''
  feedback.value = ''
  if (refreshHuman) busy.value = 'refresh'
  else { loading.value = true; results.value = null }
  try {
    const data = await cases.loadResults(caseId, resultsScenario, refreshHuman)
    if (pageActive && request === sequence && route.params.caseId === caseId) {
      results.value = data
      if (refreshHuman) feedback.value = 'Results refreshed.'
    }
  } catch (failure) {
    if (pageActive && request === sequence) error.value = failure.message
  } finally {
    if (pageActive && request === sequence) { loading.value = false; busy.value = '' }
  }
}

async function deleteSimulation() {
  if (busy.value || !entry.value) return
  const caseId = route.params.caseId
  busy.value = 'delete'
  error.value = ''
  feedback.value = ''
  confirmDelete.value = false
  try {
    await cases.deleteSimulation(caseId, resultsScenario)
    if (pageActive && route.params.caseId === caseId) router.replace({ name: 'MySimulations' })
  } catch (failure) {
    if (pageActive && route.params.caseId === caseId) error.value = failure.message
  } finally { if (pageActive && route.params.caseId === caseId) busy.value = '' }
}

watch(() => [route.params.caseId, entry.value?.processingStatus], ([caseId], previous) => {
  if (previous && previous[0] !== caseId) busy.value = ''
  sequence += 1
  results.value = null
  error.value = ''
  feedback.value = ''
  confirmDelete.value = false
  loading.value = false
  activeTab.value = 'summary'
  if (ready.value) loadResults()
}, { immediate: true })
onBeforeUnmount(() => { pageActive = false; sequence += 1; results.value = null; clearInterval(timer) })

// Direct navigation must load the authorized case instead of requiring demo memory.
let connectedRequest = 0, connectedActive = true
async function refreshConnectedCase() {
  if (demoMode) return
  const request = ++connectedRequest, id = String(route.params.caseId)
  try {
    const row = await cases.loadConnectedCase(id)
    if (connectedActive && request === connectedRequest && route.params.caseId === id) cases.cases[id] = row
  } catch (error) {
    if (connectedActive && request === connectedRequest) { delete cases.cases[id]; ElMessage.error(t(error.message)) }
  }
}
watch(() => route.params.caseId, refreshConnectedCase)
refreshConnectedCase()
onBeforeUnmount(() => { connectedActive = false; connectedRequest++ })
</script>

<template>
  <CandidateLayout>
    <section v-if="busy === 'delete'" class="panel state-panel" role="status"><h1>{{ $t("Deleting Case...") }}</h1><p>{{ $t("Revoking access and requesting removal of case data.") }}</p></section>
    <section v-else-if="!entry" class="panel state-panel">
      <h1>{{ $t("Case Not Available") }}</h1><p>{{ $t("This case was removed, has expired or is not available to your account.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
    </section>
    <section v-else-if="!ready" class="panel state-panel"><h1>{{ $t("Results Not Ready") }}</h1>
      <p>{{ $t(demoMode ? 'Complete the automated assessment to view results.' : 'Confirm your professional facts to view results.') }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'SimulationProgress', params: { caseId: entry.caseId } })">{{ $t("Back to Progress") }}</ElButton>
    </section>
    <section v-else aria-labelledby="results-title">
      <nav class="breadcrumb" :aria-label="$t(&quot;Breadcrumb&quot;)"><RouterLink :to="{ name: 'PublishedJobs' }">{{ $t("Jobs") }}</RouterLink><span>/</span>
        <RouterLink :to="{ name: 'SimulationProgress', params: { caseId: entry.caseId } }">{{ entry.caseId }}</RouterLink><span>/</span><span aria-current="page">{{ $t("Results") }}</span></nav>
      <h1 id="results-title">{{ $t("Simulation Results") }}</h1><p class="subtitle">{{ $t("Three automated assessments and two separate human review conditions.") }}</p>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demo: scores and decisions are fixed examples unrelated to your CV or selected job. No model or real reviewer is involved. Reload restores the checkpoint and regenerates fixed examples; optional research values and human updates are not retained. Sign out clears the case.&quot;)" />
      <CaseProgressStepper :current-step="3" review-confirmed :simulation-complete="entry.processingStatus === 'AUTOMATED_COMPLETE'" />
      <div v-if="loading" class="panel" role="status"><p>{{ $t("Loading results...") }}</p><ElSkeleton :rows="8" animated /></div>
      <div v-else-if="!results" class="panel state-panel" role="alert"><h2>{{ $t("Results Could Not Be Loaded") }}</h2><p>{{ $t(error) }}</p>
        <ElButton type="primary" @click="loadResults()">{{ $t("Try Again") }}</ElButton></div>
      <template v-else>
        <ElAlert v-if="error" class="feedback" type="error" :title="$t(error)" :closable="false" />
        <p v-if="feedback" class="feedback" role="status">{{ $t(feedback) }}</p>
        <div class="results-grid">
          <aside class="case-column">
            <section class="panel"><h2>{{ $t("Case Information") }}</h2><dl>
              <div><dt>{{ $t("Job") }}</dt><dd>{{ results.job.title }}</dd></div><div v-if="results.job.company"><dt>{{ $t("Company") }}</dt><dd>{{ results.job.company }}</dd></div>
              <div v-if="results.job.location"><dt>{{ $t("Location") }}</dt><dd>{{ results.job.location }}</dd></div><div v-if="results.job.employmentType"><dt>{{ $t("Employment") }}</dt><dd>{{ results.job.employmentType }}</dd></div>
              <div><dt>{{ $t("Job version") }}</dt><dd>{{ results.job.version }}</dd></div><div><dt>{{ $t("Case ID") }}</dt><dd>{{ results.caseId }}</dd></div>
              <div v-if="results.completedAt"><dt>{{ $t("Completed") }}</dt><dd>{{ formatDate(results.completedAt) }} {{ $t("(Paris)") }}</dd></div><div><dt>{{ $t("Deletion deadline") }}</dt><dd>{{ formatDate(results.expiresAt) }} {{ $t("(Paris)") }}</dd></div>
            </dl><p class="small">{{ $t("All five methods refer to the same confirmed facts and retained job version. Results are read-only.") }}</p></section>
            <ElButton @click="router.push({ name: 'SimulationProgress', params: { caseId: entry.caseId } })">{{ $t("Back to Progress") }}</ElButton>
            <ElButton @click="router.push({ name: 'PublishedJobs' })">{{ $t("Choose Another Job") }}</ElButton>
            <ElButton @click="router.push({ name: 'MySimulations' })">{{ $t("My simulations") }}</ElButton>
            <ElButton type="danger" plain :disabled="Boolean(busy) || results.canDeleteNow === false" @click="confirmDelete = true">{{ $t("Delete My Simulation Data") }}</ElButton>
          </aside>
          <div class="result-column">
            <ElTabs v-model="activeTab" class="result-tabs" :aria-label="$t(&quot;Result views&quot;)">
              <ElTabPane :label="$t(&quot;Summary&quot;)" name="summary">
                <h2 class="section-title">{{ $t("Automated Results") }}</h2><div class="automated-grid">
                  <ResultCard :title="$t(&quot;Objective Fit Score&quot;)" :status="results.objective ? 'Complete' : 'Unavailable'" :score="results.objective?.displayScore" scale="0–100" :decision="results.objective?.decision" note="Fit under the published matching rule." />
                  <ElAlert v-if="objectiveOnly" :title="$t(&quot;This assessment used the objective rule only. AI scoring was skipped.&quot;)" type="info" :closable="false" />
                  <template v-else>
                    <ResultCard :title="$t(&quot;Neutral ML Acceptance Score&quot;)" :status="results.neutral ? 'Complete' : 'Unavailable'" :score="results.neutral?.acceptanceScore" scale="0–1" :decision="results.neutral?.decision" note="Model acceptance score on a 0–1 scale." />
                    <ResultCard :title="$t(&quot;Biased ML Acceptance Score&quot;)" :status="results.biased ? 'Complete' : 'Unavailable'" :score="results.biased?.acceptanceScore" scale="0–1" :decision="results.biased?.decision" note="Acceptance score under the experimental condition." />
                  </template>
                </div>
                <div class="section-heading"><h2>{{ $t("Human Results") }}</h2><ElButton :loading="busy === 'refresh'" :disabled="Boolean(busy)" @click="loadResults(true)">{{ $t("Refresh Human Results") }}</ElButton></div>
                <div class="human-grid"><ResultCard v-for="human in humanCards" :key="human.key" :title="$t(human.title)" :status="human.status" :decision="human.decision"
                  :note="human.status === 'Submitted' ? 'A submitted mock primary review; no numeric score applies.' : 'No decision or reason is available until this review is submitted.'">
                  <p v-if="human.reason" class="human-reason">{{ human.reason }}</p>
                </ResultCard></div>
                <h2 class="section-title chart-heading">{{ $t("Scores on Separate Scales") }}</h2><div class="scales-grid">
                  <ScoreScalePanel :title="$t(&quot;Objective Fit&quot;)" :maximum="100" :items="[{ label: 'Objective', value: results.objective?.displayScore }]" note="Objective fit uses its own 0–100 display scale." />
                  <ScoreScalePanel v-if="!objectiveOnly" :title="$t(&quot;ML Acceptance Scores&quot;)" :maximum="1" :items="[{ label: 'Neutral ML', value: results.neutral?.acceptanceScore }, { label: 'Biased ML', value: results.biased?.acceptanceScore }]" note="ML scores use 0–1. Do not compare or subtract them from the Objective display score." />
                </div>
                <section class="panel interpretation"><h2>{{ $t("How to Read These Results") }}</h2><p>{{ $t("Accept and Reject are outcomes under each method's own rule or instruction. They are not a hiring recommendation.") }}</p>
                  <p>{{ $t("Pending and Not assigned mean no human decision exists yet. Different outcomes do not establish the cause of a difference or prove discrimination.") }}</p></section>
              </ElTabPane>
              <ElTabPane :label="$t(&quot;Detailed Comparison&quot;)" name="comparison" lazy>
                <section class="panel"><h2>{{ $t("Five-Method Comparison") }}</h2><p>{{ $t("Compare decisions with their status and score scale. Human results contain decisions only.") }}</p>
                  <div class="table-scroll" tabindex="0" role="region" :aria-label="$t(&quot;Five-method results table&quot;)"><table><caption>{{ $t("Read-only public result summary") }}</caption><thead><tr><th scope="col">{{ $t("Method") }}</th><th scope="col">{{ $t("Status") }}</th><th scope="col">{{ $t("Decision") }}</th><th scope="col">{{ $t("Score") }}</th><th scope="col">{{ $t("Scale") }}</th></tr></thead>
                    <tbody><tr v-for="row in comparisonRows" :key="row.method"><th scope="row">{{ row.method }}</th><td>{{ $t(row.status) }}</td><td>{{ $t(row.decision) }}</td><td>{{ row.score }}</td><td>{{ row.scale }}</td></tr></tbody></table></div>
                  <p class="small">{{ $t("Objective and ML scores have different meanings. No combined score or cross-scale difference is calculated.") }}</p>
                </section>
              </ElTabPane>
              <ElTabPane :label="$t(&quot;Explanations&quot;)" name="explanations" lazy>
                <section class="panel explanation"><h2>{{ $t("Objective Fit Score") }}</h2><h3>{{ $t("Strengths") }}</h3><ul><li v-for="strength in results.objective?.strengths" :key="strength">{{ $t(strength) }}</li></ul>
                  <p v-if="!results.objective?.strengths?.length">{{ $t('No assessed strengths are available for this result.') }}</p>
                  <h3>{{ $t("Gaps") }}</h3><ul><li v-for="gap in results.objective?.gaps" :key="gap">{{ $t(gap) }}</li></ul><p v-if="!results.objective?.gaps?.length">{{ $t('No assessed gaps are available for this result.') }}</p><p v-if="demoMode" class="small">{{ $t("These fixed example explanations were not calculated from your confirmed facts.") }}</p></section>
                <section class="panel explanation"><h2>{{ $t("Neutral ML Acceptance Score") }}</h2><p>{{ $t("The Neutral method uses professional information without research attributes.") }} </p></section>
                <section class="panel explanation"><h2>{{ $t("Biased ML Acceptance Score") }}</h2><p>{{ $t("The Biased method represents an experimental condition. Its score reflects the configured research scenario.") }}</p>
                  <template v-if="results.biased?.attributeSummary"><h3>{{ $t("Permitted Research Attribute Summary") }}</h3><dl><div v-for="(value, key) in results.biased?.attributeSummary" :key="key"><dt>{{ $t(researchLabels[key]) }}</dt><dd>{{ value }}</dd></div></dl>
                    <p class="small">{{ $t("UNKNOWN means no value was provided.") }} </p></template>
                </section>
                <section v-for="human in humanCards" :key="human.key" class="panel explanation"><div class="section-heading"><h2>{{ human.title }}</h2><ElTag type="info">{{ $t(human.status) }}</ElTag></div><p>{{ $t(human.reason || 'No submitted decision or reason is available for this condition.') }}</p></section>
              </ElTabPane>
            </ElTabs>
          </div>
        </div>
      </template>
    </section>
    <SimulationDeleteDialog v-model="confirmDelete" :case-id="String(route.params.caseId)" @confirm="deleteSimulation" />
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; color: #132941; font-size: 28px; letter-spacing: -0.7px; }
h2 { margin: 0 0 16px; color: #132941; font-size: 18px; line-height: 1.6; }
h3 { color: #132941; font-size: 14px; }
p, li { color: #586b83; font-size: 14px; line-height: 1.8; }
.subtitle { margin: 10px 0 20px; }
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; font-size: 13px; color: #586b83; overflow-wrap: anywhere; }
.breadcrumb a { color: #176bdf; text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.results-grid { display: grid; grid-template-columns: minmax(0, 250px) minmax(0, 1fr); gap: 22px; }
.case-column, .result-column { min-width: 0; }
.case-column { display: flex; flex-direction: column; gap: 14px; }
.case-column > .el-button { margin: 0; }
.panel { padding: 22px; background: white; border: 1px solid #e1eaf5; border-radius: 12px; overflow-wrap: anywhere; }
.state-panel { text-align: center; padding: 50px 24px; }
dl { margin: 0; font-size: 13px; line-height: 1.8; }
dl div { padding: 12px 0; border-bottom: 1px solid #edf1f7; }
dt { color: #586b83; } dd { margin: 2px 0 0; color: #132941; }
.small, .human-reason { font-size: 12px; }
.automated-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.human-grid, .scales-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.section-title { margin-top: 8px; }
.section-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin: 24px 0 16px; }
.section-heading h2 { margin: 0; }
.chart-heading { margin-top: 28px; }
.interpretation, .explanation { margin-top: 20px; }
.explanation .section-heading { margin-top: 0; }
.feedback { margin: 16px 0; }
.table-scroll { overflow-x: auto; }
.table-scroll:focus-visible { outline: 2px solid #176bdf; outline-offset: 2px; }
table { width: 100%; min-width: 640px; border-collapse: collapse; color: #586b83; font-size: 13px; line-height: 1.7; }
caption { text-align: left; padding: 12px 0; color: #132941; }
th, td { text-align: left; padding: 14px 12px; border-bottom: 1px solid #e1eaf5; }
thead th { background: #f3f7fd; color: #132941; }
tbody th { color: #132941; font-weight: 500; }
:deep(.el-tabs__item) { font-size: 13px; }
@media (max-width: 1100px) { .results-grid { grid-template-columns: minmax(0, 1fr); } .case-column { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .case-column .panel { grid-column: 1 / -1; } }
@media (max-width: 700px) { .automated-grid, .human-grid, .scales-grid { grid-template-columns: minmax(0, 1fr); } .panel { padding: 18px; } h1 { font-size: 25px; } }
@media (max-width: 420px) { .case-column { display: flex; } }
</style>
