<script setup>
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
const ready = computed(() => entry.value?.processingStatus === 'AUTOMATED_COMPLETE')
const results = ref(null)
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
  { method: 'Objective Fit Score', score: results.value.objective.displayScore, scale: '0–100', decision: results.value.objective.decision, status: 'Complete' },
  { method: 'Neutral ML Acceptance Score', score: results.value.neutral.acceptanceScore, scale: '0–1', decision: results.value.neutral.decision, status: 'Complete' },
  { method: 'Biased ML Acceptance Score', score: results.value.biased.acceptanceScore, scale: '0–1', decision: results.value.biased.decision, status: 'Complete' },
  ...humanCards.value.map((human) => ({ method: human.title, score: 'Not applicable', scale: 'Decision only', decision: human.decision || 'No decision yet', status: human.status })),
] : [])
const researchLabels = { schoolPrestige: 'School prestige', referral: 'Referral', gender: 'Gender', ethnicity: 'Ethnicity' }
const formatDate = (time) => new Intl.DateTimeFormat('en-GB', {
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
      if (refreshHuman) feedback.value = 'Human results refreshed. Automated example results are unchanged.'
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
</script>

<template>
  <CandidateLayout>
    <section v-if="busy === 'delete'" class="panel state-panel" role="status"><h1>Deleting Demo Case...</h1><p>Removing local case data and all mock results from this session.</p></section>
    <section v-else-if="!entry" class="panel state-panel">
      <h1>Demo Case Not Available</h1><p>This case was removed, has expired or is not available in your demo session.</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">Back to Jobs</ElButton>
    </section>
    <section v-else-if="!ready" class="panel state-panel"><h1>Results Not Ready</h1>
      <p>All three automated methods must complete before results can be viewed. Failed or missing results are never shown as zero or Reject.</p>
      <ElButton type="primary" @click="router.replace({ name: 'SimulationProgress', params: { caseId: entry.caseId } })">Back to Progress</ElButton>
    </section>
    <section v-else aria-labelledby="results-title">
      <nav class="breadcrumb" aria-label="Breadcrumb"><RouterLink :to="{ name: 'PublishedJobs' }">Jobs</RouterLink><span>/</span>
        <RouterLink :to="{ name: 'SimulationProgress', params: { caseId: entry.caseId } }">{{ entry.caseId }}</RouterLink><span>/</span><span aria-current="page">Results</span></nav>
      <h1 id="results-title">Simulation Results</h1><p class="subtitle">Three automated assessments and two separate human review conditions.</p>
      <ElAlert type="info" :closable="false" title="Frontend demo: scores and decisions are fixed examples unrelated to your CV or selected job. No model or real reviewer is involved. Reload restores the checkpoint and regenerates fixed examples; optional research values and human updates are not retained. Sign out clears the case." />
      <CaseProgressStepper :current-step="3" review-confirmed simulation-complete />
      <div v-if="loading" class="panel" role="status"><p>Loading demo results...</p><ElSkeleton :rows="8" animated /></div>
      <div v-else-if="!results" class="panel state-panel" role="alert"><h2>Results Could Not Be Loaded</h2><p>{{ error }}</p>
        <ElButton type="primary" @click="loadResults()">Try Again</ElButton></div>
      <template v-else>
        <ElAlert v-if="error" class="feedback" type="error" :title="error" :closable="false" />
        <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
        <div class="results-grid">
          <aside class="case-column">
            <section class="panel"><h2>Case Information</h2><dl>
              <div><dt>Job</dt><dd>{{ results.job.title }}</dd></div><div><dt>Company</dt><dd>{{ results.job.company }}</dd></div>
              <div><dt>Location</dt><dd>{{ results.job.location }}</dd></div><div><dt>Employment</dt><dd>{{ results.job.employmentType }}</dd></div>
              <div><dt>Job version</dt><dd>{{ results.job.version }}</dd></div><div><dt>Case ID</dt><dd>{{ results.caseId }}</dd></div>
              <div><dt>Completed</dt><dd>{{ formatDate(results.completedAt) }} (Paris)</dd></div><div><dt>Deletion deadline</dt><dd>{{ formatDate(results.expiresAt) }} (Paris)</dd></div>
            </dl><p class="small">All five methods refer to the same confirmed facts and retained job version. Results are read-only.</p></section>
            <ElButton @click="router.push({ name: 'SimulationProgress', params: { caseId: entry.caseId } })">Back to Progress</ElButton>
            <ElButton @click="router.push({ name: 'PublishedJobs' })">Choose Another Job</ElButton>
            <ElButton @click="router.push({ name: 'MySimulations' })">My simulations</ElButton>
            <ElButton type="danger" plain :disabled="Boolean(busy)" @click="confirmDelete = true">Delete My Simulation Data</ElButton>
          </aside>
          <div class="result-column">
            <ElTabs v-model="activeTab" class="result-tabs" aria-label="Result views">
              <ElTabPane label="Summary" name="summary">
                <h2 class="section-title">Automated Results</h2><div class="automated-grid">
                  <ResultCard title="Objective Fit Score" :score="results.objective.displayScore" scale="0–100" :decision="results.objective.decision" note="Fit under the example objective rule; a simulator outcome." />
                  <ResultCard title="Neutral ML Acceptance Score" :score="results.neutral.acceptanceScore" scale="0–1" :decision="results.neutral.decision" note="An example model score, not a real hiring probability." />
                  <ResultCard title="Biased ML Acceptance Score" :score="results.biased.acceptanceScore" scale="0–1" :decision="results.biased.decision" note="An experimental example model score, not a real hiring probability." />
                </div>
                <div class="section-heading"><h2>Human Results</h2><ElButton :loading="busy === 'refresh'" :disabled="Boolean(busy)" @click="loadResults(true)">Refresh Human Results</ElButton></div>
                <div class="human-grid"><ResultCard v-for="human in humanCards" :key="human.key" :title="human.title" :status="human.status" :decision="human.decision"
                  :note="human.status === 'Submitted' ? 'A submitted mock primary review; no numeric score applies.' : 'No decision or reason is available until this review is submitted.'">
                  <p v-if="human.reason" class="human-reason">{{ human.reason }}</p>
                </ResultCard></div>
                <h2 class="section-title chart-heading">Scores on Separate Scales</h2><div class="scales-grid">
                  <ScoreScalePanel title="Objective Fit" :maximum="100" :items="[{ label: 'Objective', value: results.objective.displayScore }]" note="Objective fit uses its own 0–100 display scale." />
                  <ScoreScalePanel title="ML Acceptance Scores" :maximum="1" :items="[{ label: 'Neutral ML', value: results.neutral.acceptanceScore }, { label: 'Biased ML', value: results.biased.acceptanceScore }]" note="ML scores use 0–1. Do not compare or subtract them from the Objective display score." />
                </div>
                <section class="panel interpretation"><h2>How to Read These Results</h2><p>Accept and Reject are outcomes under each method's own rule or instruction. They are not a hiring recommendation.</p>
                  <p>Pending and Not assigned mean no human decision exists yet. Different example outcomes do not establish the cause of a difference or prove discrimination.</p></section>
              </ElTabPane>
              <ElTabPane label="Detailed Comparison" name="comparison" lazy>
                <section class="panel"><h2>Five-Method Comparison</h2><p>Compare decisions with their status and score scale. Human results contain decisions only.</p>
                  <div class="table-scroll" tabindex="0" role="region" aria-label="Five-method results table"><table><caption>Read-only public result summary</caption><thead><tr><th scope="col">Method</th><th scope="col">Status</th><th scope="col">Decision</th><th scope="col">Score</th><th scope="col">Scale</th></tr></thead>
                    <tbody><tr v-for="row in comparisonRows" :key="row.method"><th scope="row">{{ row.method }}</th><td>{{ row.status }}</td><td>{{ row.decision }}</td><td>{{ row.score }}</td><td>{{ row.scale }}</td></tr></tbody></table></div>
                  <p class="small">Objective and ML scores have different meanings. No combined score or cross-scale difference is calculated.</p>
                </section>
              </ElTabPane>
              <ElTabPane label="Explanations" name="explanations" lazy>
                <section class="panel explanation"><h2>Objective Fit Score</h2><h3>Example Strengths</h3><ul><li v-for="strength in results.objective.strengths" :key="strength">{{ strength }}</li></ul>
                  <h3>Example Gaps</h3><ul><li v-for="gap in results.objective.gaps" :key="gap">{{ gap }}</li></ul><p class="small">These fixed example explanations were not calculated from your confirmed facts.</p></section>
                <section class="panel explanation"><h2>Neutral ML Acceptance Score</h2><p>The Neutral method uses professional information without research attributes. This demo shows a fixed example output; no model explanation is available.</p></section>
                <section class="panel explanation"><h2>Biased ML Acceptance Score</h2><p>The Biased method represents an experimental condition. This fixed example does not measure the effect of any particular attribute.</p>
                  <template v-if="results.biased.attributeSummary"><h3>Permitted Research Attribute Summary</h3><dl><div v-for="(value, key) in results.biased.attributeSummary" :key="key"><dt>{{ researchLabels[key] }}</dt><dd>{{ value }}</dd></div></dl>
                    <p class="small">UNKNOWN means no value was provided. These saved values do not change the fixed demo score.</p></template>
                </section>
                <section v-for="human in humanCards" :key="human.key" class="panel explanation"><div class="section-heading"><h2>{{ human.title }}</h2><ElTag type="info">{{ human.status }}</ElTag></div><p>{{ human.reason || 'No submitted decision or reason is available for this condition.' }}</p></section>
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
