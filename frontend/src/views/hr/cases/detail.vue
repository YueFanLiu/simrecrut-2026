<!-- Server access checks determine which case details are returned. -->
<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useHrJobsStore } from '../../../store/modules/hr-jobs.js'
import { useHrCasesStore } from '../../../store/modules/hr-cases.js'
import { demoMode } from '../../../config/runtime.js'
import { hrDetailScenario } from '../../../config/demo.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const jobs = useHrJobsStore()
const cases = useHrCasesStore()
const data = ref(null)
const loading = ref(true)
const error = ref('')
const clock = ref(Date.now())
let active = true
let sequence = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
const accessible = computed(() => auth.hasDemoRole('HR') && (!demoMode || cases.listCases(auth.demoUser, jobs.jobs, clock.value).some((entry) => entry.caseId === route.params.caseId)))
watch(accessible, (allowed) => { if (!allowed) { sequence += 1; data.value = null; loading.value = false; error.value = 'This case is unavailable or access is no longer authorised.' } })
const resultSlots = computed(() => !data.value?.resultsVisible ? [] : [
  { title: 'Objective Rule', value: data.value.objective, scale: 'Professional fit / 100' },
  { title: 'Neutral ML', value: data.value.neutral, scale: 'Acceptance score / 1' },
  { title: 'Biased ML', value: data.value.biased, scale: 'Acceptance score / 1' },
  { title: 'Standard Human', value: data.value.standard },
  { title: 'Instructed-Bias Human', value: data.value.instructedBias },
])
const percent = (value) => `${Number((value * 100).toFixed(5))}%`
const points = (value) => Number((value * 100).toFixed(5))
const resultsRoute = computed(() => ({ name: 'HrCases', query: { jobId: typeof route.query.jobId === 'string' ? route.query.jobId : undefined } }))

async function loadDetail() {
  const request = ++sequence
  const caseId = route.params.caseId
  const username = auth.demoUser?.username
  loading.value = true
  data.value = null
  error.value = ''
  const current = () => active && sequence === request && auth.hasDemoRole('HR') && auth.demoUser.username === username
  try {
    await new Promise((resolve) => setTimeout(resolve, 400))
    if (!current()) return
    if (hrDetailScenario === 'error') throw new Error('Unable to load case details. Please try again.')
    const result = await cases.readDetail(auth.demoUser, jobs.jobs, caseId, hrDetailScenario)
    if (!current()) return
    data.value = result
    clock.value = Date.now()
  } catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
watch(() => route.params.caseId, loadDetail, { immediate: true })
onBeforeUnmount(() => { active = false; sequence += 1; data.value = null; clearInterval(timer) })
</script>

<template>
  <HrLayout>
    <section aria-labelledby="detail-title" :aria-busy="loading">
      <nav :aria-label="$t(&quot;Breadcrumb&quot;)" class="breadcrumb"><RouterLink :to="resultsRoute">{{ $t("Authorized Cases") }}</RouterLink><span>/</span><span>{{ $t("Case Details") }}</span></nav>
      <div class="page-heading"><div><h1 id="detail-title">{{ $t("Authorized Case Details") }}</h1><p>{{ $t("Read-only professional snapshot and recorded assessment results.") }}</p></div><div class="actions"><ElButton @click="router.push(resultsRoute)">{{ $t("Back to Results") }}</ElButton><ElButton :loading="loading" :disabled="loading" @click="loadDetail">{{ $t("Refresh") }}</ElButton></div></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demonstration only. These fixed examples are unrelated to a real CV. Scores and contributions are stored mock values; no backend calculation or model request is performed.&quot;)" />
      <div v-if="loading" class="panel" role="status"><p>{{ $t("Loading authorised case details...") }}</p><ElSkeleton :rows="7" animated /></div>
      <div v-else-if="error || !accessible" class="panel state-panel" role="alert"><h2>{{ $t("Case Unavailable") }}</h2><p>{{ $t(error || 'This case is unavailable or access is no longer authorised.') }}</p><ElButton type="primary" @click="loadDetail">{{ $t("Try Again") }}</ElButton></div>
      <template v-else-if="data">
        <div class="panel summary"><div><span>{{ $t("Case ID") }}</span><strong>{{ data.caseId }}</strong></div><div><span>{{ $t("Job / Fixed Version") }}</span><strong>{{ data.jobTitle }} · {{ data.version }}</strong></div><div><span>{{ $t("Access") }}</span><strong>{{ $t(data.accessLabel) }}</strong></div><ElTag v-if="data.processingLabel" :type="data.statusType">{{ $t(data.processingLabel) }}</ElTag></div>
        <div class="content-grid">
          <article class="panel"><h2>{{ $t("Professional Snapshot") }}</h2><img v-if="data.photo" class="cv-photo" :src="'data:image/jpeg;base64,' + data.photo" :alt="$t('Photo extracted from the CV')" /><dl v-if="data.professional"><template v-for="row in data.professional" :key="row.category"><dt>{{ row.category }}</dt><dd>{{ row.value }}</dd></template></dl><p v-else>{{ $t("Awaiting confirmed professional facts. No confirmed snapshot is available yet.") }}</p></article>
          <article class="panel"><h2>{{ $t("Recorded Versions") }}</h2><dl><dt>{{ $t("Job version ID") }}</dt><dd>{{ data.versions.jobVersionId }}</dd><template v-if="data.versions.weights"><dt>{{ $t("Weight configuration") }}</dt><dd>{{ data.versions.weights }}</dd></template><dt>{{ $t("Matching rules") }}</dt><dd>{{ data.versions.matching }}</dd><dt>{{ $t("Protocol") }}</dt><dd>{{ data.versions.protocol }}</dd><dt>{{ $t("Neutral model") }}</dt><dd>{{ data.versions.neutral }}</dd><dt>{{ $t("Biased model") }}</dt><dd>{{ data.versions.biased }}</dd></dl></article>
        </div>
        <div v-if="!data.resultsVisible" class="panel"><ElAlert type="warning" :closable="false" :title="$t(&quot;Results are hidden for the current blind-review visibility policy. Scores, decisions and matching contributions are not included in this view.&quot;)" /></div>
        <template v-else>
          <div class="panel"><h2>{{ $t("Objective Matching and Contributions") }}</h2><template v-if="data.matching"><p>{{ $t("Mandatory conditions:") }} <strong>{{ data.matching.mandatory }}</strong> {{ $t("· Recorded rule cutoff:") }} {{ $t(data.matching.cutoff === null ? 'Unavailable' : percent(data.matching.cutoff)) }}</p><div class="table-scroll"><table><caption>{{ $t("Five fixed categories · Contribution shown as points out of 100") }}</caption><thead><tr><th scope="col">{{ $t("Criterion") }}</th><th scope="col">{{ $t("HR Weight") }}</th><th scope="col">{{ $t("Match") }}</th><th scope="col">{{ $t("Contribution") }}</th></tr></thead><tbody><tr v-for="row in data.matching.rows" :key="row.category"><th scope="row">{{ row.category }}</th><td>{{ percent(row.weight) }}</td><td>{{ percent(row.match) }}</td><td>{{ points(row.contribution) }}</td></tr></tbody></table></div></template><p v-else>{{ $t("Matching is not available yet. No missing match or contribution is replaced with zero.") }}</p></div>
          <div class="panel"><h2>{{ $t("Five Decision Outcomes") }}</h2><p>{{ $t("Objective fit uses a 0–100 scale. ML acceptance scores use a 0–1 scale. Human reviews have no numerical score.") }}</p><div class="results-grid"><article v-for="slot in resultSlots" :key="slot.title" class="outcome" :aria-label="$t(slot.title)"><h3>{{ slot.title }}</h3><ElTag>{{ $t(slot.value.status) }}</ElTag><p v-if="slot.value.score !== null && slot.value.score !== undefined" class="score">{{ slot.value.score }}<span>{{ slot.scale }}</span></p><p v-if="slot.value.decision" class="decision" :class="slot.value.decision.toLowerCase()">{{ $t(slot.value.decision) }}</p><p v-else class="pending">{{ $t(slot.value.status === 'Pending' ? 'Awaiting review' : slot.value.status === 'Not assigned' ? 'No review assigned' : 'No result available') }}</p><p v-if="slot.value.reason">{{ slot.value.reason }}</p><p v-if="slot.value.status === 'Failed'">{{ $t("Technical failure. No hiring decision is recorded.") }}</p></article></div></div>
          <ElAlert v-if="data.matching?.mandatory === 'Failed' && [data.neutral.decision, data.biased.decision].includes('Accept')" class="mismatch" type="warning" :closable="false" :title="$t(&quot;An ML prediction accepts despite a failed mandatory condition. The recorded research prediction is shown unchanged.&quot;)" />
        </template>
      </template>
    </section>
  </HrLayout>
</template>

<style scoped>
.cv-photo { width: 96px; height: 96px; object-fit: cover; border-radius: 8px; border: 1px solid var(--sr-border); margin-bottom: 12px; }
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -.7px; } h2 { color: var(--sr-ink); font-size: 19px; margin-top: 0; } h3 { color: var(--sr-ink); font-size: 16px; } p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; }
.breadcrumb { display: flex; gap: 12px; margin-bottom: 18px; color: var(--sr-text-muted); font-size: 12px; } .breadcrumb a { color: var(--sr-primary); text-decoration: none; } .page-heading, .actions { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; } .page-heading { margin-bottom: 20px; } .el-button { margin: 0; }
.panel { background: white; padding: 24px; border: 1px solid var(--sr-border); border-radius: 12px; margin-top: 20px; min-width: 0; } .summary { display: flex; flex-wrap: wrap; align-items: center; gap: 20px; justify-content: space-between; } .summary span, .summary strong { display: block; line-height: 1.8; font-size: 13px; } .summary span { color: var(--sr-text-muted); } .summary strong { color: var(--sr-ink); } .content-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
dl { display: grid; grid-template-columns: 140px minmax(0, 1fr); gap: 14px; font-size: 13px; line-height: 1.8; } dt { color: var(--sr-text-muted); } dd { margin: 0; color: var(--sr-ink); overflow-wrap: anywhere; } .table-scroll { overflow-x: auto; } table { width: 100%; min-width: 430px; border-collapse: collapse; color: var(--sr-text-muted); font-size: 13px; } caption { text-align: left; padding: 12px 0; font-size: 12px; } th, td { padding: 14px 10px; text-align: left; border-bottom: 1px solid var(--sr-border); } thead th { background: var(--sr-primary-50); color: var(--sr-ink); }
.mismatch { margin-top: 20px; } .results-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; } .outcome { padding: 20px; border: 1px solid var(--sr-border); border-radius: 8px; background: var(--sr-surface-tint); min-width: 0; overflow-wrap: anywhere; } .score { font-size: 28px; color: var(--sr-ink); font-weight: 700; } .score span { display: block; font-size: 12px; font-weight: 400; } .decision { font-size: 22px; font-weight: 600; } .accept { color: #218458; } .reject { color: #b42336; } .pending { color: var(--sr-text-muted); } .state-panel { text-align: center; }
@media (max-width: 1100px) { .content-grid { grid-template-columns: minmax(0, 1fr); gap: 0; } .results-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .panel { padding: 20px 16px; } h1 { font-size: 25px; } .results-grid { grid-template-columns: minmax(0, 1fr); } dl { grid-template-columns: minmax(0, 1fr); gap: 6px; } dd { margin-bottom: 12px; } }
</style>
