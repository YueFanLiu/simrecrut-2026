<script setup>
import { dateLocale } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { getCaseDestination } from '../../../utils/case-history.js'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CaseHistoryCard from '../../../components/simrecrut/CaseHistoryCard.vue'
import SimulationDeleteDialog from '../../../components/simrecrut/SimulationDeleteDialog.vue'
import { useCasesStore } from '../../../store/modules/cases.js'
import { historyScenario } from '../../../config/demo.js'

const router = useRouter()
const cases = useCasesStore()
const loading = ref(true)
const loaded = ref(false)
const loadError = ref('')
const actionError = ref('')
const feedback = ref('')
const page = ref(1)
const pageSize = ref(10)
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
const rows = computed(() => loaded.value ? cases.listOwnedCases(clock.value) : [])
const visibleRows = computed(() => rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const selected = ref(null)
const confirmDelete = ref(false)
const deleting = ref(false)
let sequence = 0
let pageActive = true
const formatDate = (value) => value ? new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(value)) : 'Not uploaded'

watch(pageSize, () => { page.value = 1 })
watch(() => rows.value.length, () => {
  page.value = Math.min(page.value, Math.max(1, Math.ceil(rows.value.length / pageSize.value)))
})
watch(() => selected.value && cases.getOwnedCase(selected.value.caseId, clock.value), (entry) => {
  if (!entry && !deleting.value) { confirmDelete.value = false; selected.value = null }
})

async function loadHistory() {
  if (deleting.value) return
  const request = ++sequence
  loading.value = true
  loaded.value = false
  loadError.value = ''
  actionError.value = ''
  feedback.value = ''
  try {
    await cases.loadHistory(historyScenario)
    if (pageActive && request === sequence) loaded.value = true
  } catch (error) {
    if (pageActive && request === sequence) loadError.value = error.message
  } finally { if (pageActive && request === sequence) loading.value = false }
}

async function continueCase(row) {
  if (deleting.value) return
  // Re-read the state at click time; a case may finish, expire or be deleted while listed.
  const latest = cases.listOwnedCases().find((entry) => entry.caseId === row.caseId)
  if (!latest?.destination) { actionError.value = 'This case is no longer available to continue.'; return }
  try {
    const destination = demoMode ? latest.destination : getCaseDestination(await cases.loadConnectedCase(row.caseId))
    if (pageActive && destination) router.push(destination)
  } catch (error) { if (pageActive) actionError.value = error.message }
}

function requestDelete(row) {
  if (deleting.value) return
  actionError.value = ''
  feedback.value = ''
  if (!cases.getOwnedCase(row.caseId)) { actionError.value = 'This case is no longer available.'; return }
  selected.value = row
  confirmDelete.value = true
}

async function deleteSimulation() {
  if (deleting.value || !selected.value) return
  const caseId = selected.value.caseId
  deleting.value = true
  confirmDelete.value = false
  actionError.value = ''
  try {
    await cases.deleteSimulation(caseId, historyScenario)
    if (pageActive) feedback.value = demoMode ? 'Demo case deleted.' : 'Access revoked. Case data is queued for deletion.'
  } catch (error) { if (pageActive) actionError.value = error.message }
  finally { if (pageActive) { deleting.value = false; selected.value = null } }
}

onMounted(loadHistory)
onBeforeUnmount(() => { pageActive = false; sequence += 1; selected.value = null; clearInterval(timer) })
</script>

<template>
  <CandidateLayout>
    <section aria-labelledby="history-title" :aria-busy="loading || deleting">
      <nav class="breadcrumb" :aria-label="$t(&quot;Breadcrumb&quot;)"><RouterLink :to="{ name: 'PublishedJobs' }">{{ $t("Jobs") }}</RouterLink><span>/</span><span aria-current="page">{{ $t("My Simulations") }}</span></nav>
      <div class="page-heading"><div><h1 id="history-title">{{ $t("My Simulations") }}</h1><p class="subtitle">{{ $t("Continue and manage your recruitment simulation cases.") }}</p></div>
        <div class="heading-actions"><ElButton :loading="loading" :disabled="loading || deleting" @click="loadHistory">{{ $t("Refresh") }}</ElButton>
          <ElButton type="primary" :disabled="deleting" @click="router.push({ name: 'PublishedJobs' })">{{ $t("Start a New Simulation") }}</ElButton></div></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demo: only your retained cases in this tab are listed. No backend history is loaded. Confirmed checkpoints survive reload; uploads and unconfirmed drafts do not. Sign out clears private case state. Scores are fixed examples.&quot;)" />
      <div v-if="loading" class="panel state-panel" role="status"><p>{{ $t("Loading your simulations...") }}</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="loadError" class="panel state-panel" role="alert"><h2>{{ $t("Simulations Could Not Be Loaded") }}</h2><p>{{ $t(loadError) }}</p><ElButton type="primary" @click="loadHistory">{{ $t("Try Again") }}</ElButton></div>
      <template v-else>
        <ElAlert v-if="actionError" class="feedback" type="error" :closable="false" :title="$t(actionError)" />
        <p v-if="feedback" class="feedback" role="status">{{ $t(feedback) }}</p>
        <p v-if="deleting" role="status">{{ $t("Requesting case deletion.") }}</p>
        <div v-if="!rows.length" class="panel state-panel"><ElEmpty :image-size="150"><template #description /></ElEmpty>
          <h2>{{ $t("No Simulation Records Yet") }}</h2><p>{{ $t("Start a case from a published job. Deleted and expired cases are not listed.") }}</p>
          <ElButton type="primary" :disabled="deleting" @click="router.push({ name: 'PublishedJobs' })">{{ $t("Browse Jobs") }}</ElButton></div>
        <template v-else>
          <div class="list-toolbar"><p>{{ rows.length }} {{ $t(rows.length === 1 ? 'case' : 'cases') }} · {{ $t(demoMode ? 'Newest uploads first' : 'Newest cases first') }} {{ $t("· Dates use Paris time") }}</p>
            <ElSelect v-model="pageSize" :aria-label="$t(&quot;Cases per page&quot;)" class="page-size" :disabled="deleting"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="$t('{size} / page', { size })" /></ElSelect></div>
          <div class="panel table-panel"><div class="table-scroll" tabindex="0" role="region" :aria-label="$t(&quot;My simulations table&quot;)"><table>
            <caption>{{ $t("Owned retained cases. Unavailable results display —. Objective and ML scores use different scales.") }}</caption>
            <thead><tr><th scope="col">{{ $t("Case ID") }}</th><th scope="col">{{ $t("Job Position") }}</th><th scope="col">{{ $t(demoMode ? 'Upload Date' : 'Created') }}</th><th scope="col">{{ $t("Status") }}</th>
              <th scope="col">{{ $t("Objective") }}<br><span>0–100</span></th><th scope="col">{{ $t("Neutral ML") }}<br><span>0–1</span></th><th scope="col">{{ $t("Biased ML") }}<br><span>0–1</span></th>
              <th scope="col">{{ $t("Standard Human") }}</th><th scope="col">{{ $t("Instructed-Bias Human") }}</th><th scope="col">{{ $t("Available Until") }}</th><th scope="col">{{ $t("Actions") }}</th></tr></thead>
            <tbody><tr v-for="row in visibleRows" :key="row.caseId"><th scope="row">{{ row.caseId }}</th><td><strong>{{ row.jobTitle }}</strong><span v-if="row.version" class="version">{{ $t("Version") }} {{ row.version }}</span></td>
              <td>{{ formatDate(demoMode ? row.uploadedAt : row.createdAt) }}</td><td><ElTag :type="row.statusType">{{ $t(row.status) }}</ElTag></td><td>{{ $t(row.objective ?? '—') }}</td><td>{{ $t(row.neutral ?? '—') }}</td><td>{{ $t(row.biased ?? '—') }}</td>
              <td>{{ $t(row.standardHuman ?? '—') }}</td><td>{{ $t(row.instructedBiasHuman ?? '—') }}</td><td>{{ formatDate(row.expiresAt) }}</td>
              <td><div class="row-actions"><ElButton type="primary" plain size="small" :disabled="deleting || !row.destination" :aria-label="$t(row.actionLabel) + ' ' + row.caseId" @click="continueCase(row)">{{ $t(row.actionLabel) }}</ElButton>
                <ElButton type="danger" plain size="small" :disabled="deleting" :aria-label="$t('Delete') + ' ' + row.caseId" @click="requestDelete(row)">{{ $t("Delete") }}</ElButton></div></td></tr></tbody>
          </table></div></div>
          <div class="mobile-list"><CaseHistoryCard v-for="row in visibleRows" :key="row.caseId" :entry="row" :busy="deleting" @continue="continueCase" @delete="requestDelete" /></div>
          <div class="pagination"><p>{{ $t("Showing") }} {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, rows.length) }} {{ $t("of") }} {{ rows.length }} {{ $t("cases") }}</p>
            <ElPagination v-model:current-page="page" :page-size="pageSize" :total="rows.length" layout="prev, pager, next" :pager-count="5" :disabled="deleting" background /></div>
        </template>
      </template>
      <SimulationDeleteDialog v-model="confirmDelete" :case-id="selected?.caseId || ''" @confirm="deleteSimulation" />
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 28px; color: var(--sr-ink); letter-spacing: -0.7px; }
h2 { color: var(--sr-ink); font-size: 20px; }
p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; }
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: var(--sr-text-muted); font-size: 13px; }
.breadcrumb a { color: var(--sr-primary); text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.page-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 18px; }
.subtitle { margin: 10px 0 0; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.heading-actions .el-button { margin: 0; }
.panel { background: white; border: 1px solid var(--sr-border); border-radius: 12px; padding: 22px; }
.state-panel { margin-top: 24px; text-align: center; padding: 36px 22px; }
.list-toolbar, .pagination { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin: 20px 0; }
.list-toolbar p, .pagination p { font-size: 12px; margin: 0; }
.page-size { width: 120px; }
.table-panel { padding: 0 16px 12px; }
.table-scroll { overflow-x: auto; }
.table-scroll:focus-visible { outline: 2px solid var(--sr-primary); outline-offset: 2px; }
table { width: 100%; min-width: 1120px; border-collapse: collapse; font-size: 12px; line-height: 1.8; color: var(--sr-text-muted); }
caption { padding: 16px 0; text-align: left; font-size: 12px; }
th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid var(--sr-border); }
thead th { background: var(--sr-primary-50); color: var(--sr-ink); font-weight: 600; }
thead span { color: var(--sr-text-muted); font-weight: 400; }
tbody th { color: var(--sr-primary); font-weight: 500; white-space: nowrap; }
td strong { color: var(--sr-ink); font-weight: 500; }
.version { display: block; color: var(--sr-text-subtle); }
.row-actions { display: flex; flex-direction: column; align-items: stretch; gap: 8px; }
.row-actions .el-button { margin: 0; }
.mobile-list { display: none; }
.feedback { margin: 18px 0; }
@media (max-width: 760px) { .table-panel { display: none; } .mobile-list { display: grid; gap: 16px; } }
@media (max-width: 600px) { h1 { font-size: 25px; } .heading-actions { width: 100%; } .pagination { justify-content: center; } }
</style>
