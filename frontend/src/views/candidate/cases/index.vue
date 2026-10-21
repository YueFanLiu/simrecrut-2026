<script setup>
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
const formatDate = (value) => value ? new Intl.DateTimeFormat('en-GB', {
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

function continueCase(row) {
  if (deleting.value) return
  // Re-read the state at click time; a case may finish, expire or be deleted while listed.
  const latest = cases.listOwnedCases().find((entry) => entry.caseId === row.caseId)
  if (!latest?.destination) { actionError.value = 'This case is no longer available to continue.'; return }
  router.push(latest.destination)
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
    if (pageActive) feedback.value = 'Demo case deleted. Its data and pending mock reviews are no longer available.'
  } catch (error) { if (pageActive) actionError.value = error.message }
  finally { if (pageActive) { deleting.value = false; selected.value = null } }
}

onMounted(loadHistory)
onBeforeUnmount(() => { pageActive = false; sequence += 1; selected.value = null; clearInterval(timer) })
</script>

<template>
  <CandidateLayout>
    <section aria-labelledby="history-title" :aria-busy="loading || deleting">
      <nav class="breadcrumb" aria-label="Breadcrumb"><RouterLink :to="{ name: 'PublishedJobs' }">Jobs</RouterLink><span>/</span><span aria-current="page">My Simulations</span></nav>
      <div class="page-heading"><div><h1 id="history-title">My Simulations</h1><p class="subtitle">Continue and manage your recruitment simulation cases.</p></div>
        <div class="heading-actions"><ElButton :loading="loading" :disabled="loading || deleting" @click="loadHistory">Refresh</ElButton>
          <ElButton type="primary" :disabled="deleting" @click="router.push({ name: 'PublishedJobs' })">Start a New Simulation</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demo: only your retained cases in this tab are listed. No backend history is loaded. Confirmed checkpoints survive reload; uploads and unconfirmed drafts do not. Sign out clears private case state. Scores are fixed examples." />
      <div v-if="loading" class="panel state-panel" role="status"><p>Loading your simulations...</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="loadError" class="panel state-panel" role="alert"><h2>Simulations Could Not Be Loaded</h2><p>{{ loadError }}</p><ElButton type="primary" @click="loadHistory">Try Again</ElButton></div>
      <template v-else>
        <ElAlert v-if="actionError" class="feedback" type="error" :closable="false" :title="actionError" />
        <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
        <p v-if="deleting" role="status">Deleting the demo case. Its content is no longer readable.</p>
        <div v-if="!rows.length" class="panel state-panel"><ElEmpty description="No simulation records yet" />
          <h2>No Simulation Records Yet</h2><p>Start a case from a published job. Deleted and expired cases are not listed.</p>
          <ElButton type="primary" :disabled="deleting" @click="router.push({ name: 'PublishedJobs' })">Browse Jobs</ElButton></div>
        <template v-else>
          <div class="list-toolbar"><p>{{ rows.length }} {{ rows.length === 1 ? 'case' : 'cases' }} · Newest uploads first · Dates use Paris time</p>
            <ElSelect v-model="pageSize" aria-label="Cases per page" class="page-size" :disabled="deleting"><ElOption v-for="size in [5, 10, 20]" :key="size" :value="size" :label="`${size} / page`" /></ElSelect></div>
          <div class="panel table-panel"><div class="table-scroll" tabindex="0" role="region" aria-label="My simulations table"><table>
            <caption>Owned retained cases. Unavailable results display —. Objective and ML scores use different scales.</caption>
            <thead><tr><th scope="col">Case ID</th><th scope="col">Job Position</th><th scope="col">Upload Date</th><th scope="col">Status</th>
              <th scope="col">Objective<br><span>0–100</span></th><th scope="col">Neutral ML<br><span>0–1</span></th><th scope="col">Biased ML<br><span>0–1</span></th>
              <th scope="col">Standard Human</th><th scope="col">Instructed-Bias Human</th><th scope="col">Available Until</th><th scope="col">Actions</th></tr></thead>
            <tbody><tr v-for="row in visibleRows" :key="row.caseId"><th scope="row">{{ row.caseId }}</th><td><strong>{{ row.jobTitle }}</strong><span class="version">Version {{ row.version }}</span></td>
              <td>{{ formatDate(row.uploadedAt) }}</td><td><ElTag :type="row.statusType">{{ row.status }}</ElTag></td><td>{{ row.objective ?? '—' }}</td><td>{{ row.neutral ?? '—' }}</td><td>{{ row.biased ?? '—' }}</td>
              <td>{{ row.standardHuman ?? '—' }}</td><td>{{ row.instructedBiasHuman ?? '—' }}</td><td>{{ formatDate(row.expiresAt) }}</td>
              <td><div class="row-actions"><ElButton type="primary" plain size="small" :disabled="deleting || !row.destination" :aria-label="`${row.actionLabel} ${row.caseId}`" @click="continueCase(row)">{{ row.actionLabel }}</ElButton>
                <ElButton type="danger" plain size="small" :disabled="deleting" :aria-label="`Delete ${row.caseId}`" @click="requestDelete(row)">Delete</ElButton></div></td></tr></tbody>
          </table></div></div>
          <div class="mobile-list"><CaseHistoryCard v-for="row in visibleRows" :key="row.caseId" :entry="row" :busy="deleting" @continue="continueCase" @delete="requestDelete" /></div>
          <div class="pagination"><p>Showing {{ (page - 1) * pageSize + 1 }}–{{ Math.min(page * pageSize, rows.length) }} of {{ rows.length }} cases</p>
            <ElPagination v-model:current-page="page" :page-size="pageSize" :total="rows.length" layout="prev, pager, next" :pager-count="5" :disabled="deleting" background /></div>
        </template>
      </template>
      <SimulationDeleteDialog v-model="confirmDelete" :case-id="selected?.caseId || ''" @confirm="deleteSimulation" />
    </section>
  </CandidateLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 28px; color: #132941; letter-spacing: -0.7px; }
h2 { color: #132941; font-size: 20px; }
p { color: #586b83; font-size: 14px; line-height: 1.8; }
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: #586b83; font-size: 13px; }
.breadcrumb a { color: #176bdf; text-decoration: none; }
.breadcrumb a:hover { text-decoration: underline; }
.page-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 18px; }
.subtitle { margin: 10px 0 0; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.heading-actions .el-button { margin: 0; }
.panel { background: white; border: 1px solid #e1eaf5; border-radius: 12px; padding: 22px; }
.state-panel { margin-top: 24px; text-align: center; padding: 36px 22px; }
.list-toolbar, .pagination { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 16px; margin: 20px 0; }
.list-toolbar p, .pagination p { font-size: 12px; margin: 0; }
.page-size { width: 120px; }
.table-panel { padding: 0 16px 12px; }
.table-scroll { overflow-x: auto; }
.table-scroll:focus-visible { outline: 2px solid #176bdf; outline-offset: 2px; }
table { width: 100%; min-width: 1120px; border-collapse: collapse; font-size: 12px; line-height: 1.8; color: #586b83; }
caption { padding: 16px 0; text-align: left; font-size: 12px; }
th, td { text-align: left; padding: 14px 10px; border-bottom: 1px solid #e1eaf5; }
thead th { background: #f3f7fd; color: #132941; font-weight: 600; }
thead span { color: #586b83; font-weight: 400; }
tbody th { color: #176bdf; font-weight: 500; white-space: nowrap; }
td strong { color: #132941; font-weight: 500; }
.version { display: block; color: #8491a3; }
.row-actions { display: flex; flex-direction: column; align-items: stretch; gap: 8px; }
.row-actions .el-button { margin: 0; }
.mobile-list { display: none; }
.feedback { margin: 18px 0; }
@media (max-width: 760px) { .table-panel { display: none; } .mobile-list { display: grid; gap: 16px; } }
@media (max-width: 600px) { h1 { font-size: 25px; } .heading-actions { width: 100%; } .pagination { justify-content: center; } }
</style>
