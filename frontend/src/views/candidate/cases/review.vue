<script setup>
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElDialog, ElInput, ElSkeleton } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CaseProgressStepper from '../../../components/simrecrut/CaseProgressStepper.vue'
import FactGroupEditor from '../../../components/simrecrut/FactGroupEditor.vue'
import EvidencePanel from '../../../components/simrecrut/EvidencePanel.vue'
import { factGroups, profileGroups, researchFields, createEmptyFactItem } from '../../../mock/review-draft.js'
import { emptyResearchAttributes, validateReviewDraft } from '../../../utils/review-validation.js'
import { researchAttributesEnabled, reviewScenario } from '../../../config/demo.js'
import { useCasesStore } from '../../../store/modules/cases.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const clock = ref(Date.now())
const expiryTimer = setInterval(() => { clock.value = Date.now() }, 5000)
const entry = computed(() => cases.getOwnedCase(route.params.caseId, clock.value))
const editable = computed(() => entry.value?.processingStatus === 'REVIEW_REQUIRED')
const confirmed = computed(() => Boolean(entry.value?.confirmedAt))
const draft = ref(null)
const loading = ref(false)
const busy = ref('')
const loadError = ref('')
const feedback = ref('')
const actionError = ref('')
const errors = ref({})
const dirty = ref(false)
const reviewed = ref(false)
const evidence = ref(null)
const confirmDelete = ref(false)
const research = ref(emptyResearchAttributes())
const researchDirty = ref(false)
const researchConfirmed = ref(false)
let loadSequence = 0
let pageActive = true
const noFacts = computed(() => draft.value && factGroups.every((group) => !draft.value.professional[group.key].items.length))
const unresolvedFacts = computed(() => draft.value && Object.keys(validateReviewDraft(draft.value, true)).length > 0)

async function loadDraft() {
  const sequence = ++loadSequence
  const caseId = route.params.caseId
  busy.value = ''
  draft.value = null
  evidence.value = null
  errors.value = {}
  dirty.value = false
  reviewed.value = false
  researchDirty.value = false
  researchConfirmed.value = false
  loadError.value = ''
  feedback.value = ''
  actionError.value = ''
  if (!editable.value) { loading.value = false; return }
  loading.value = true
  try {
    const loaded = await cases.loadReview(caseId, reviewScenario, researchAttributesEnabled)
    if (sequence !== loadSequence) return
    draft.value = loaded
    research.value = { ...entry.value.researchAttributes }
  } catch (error) {
    if (sequence === loadSequence) loadError.value = error.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function markChanged() {
  dirty.value = true
  reviewed.value = false
  feedback.value = ''
  actionError.value = ''
  errors.value = {}
}

// Personal and other information come first, as on a CV; they are stored apart from the professional block.
const groupData = group => draft.value.profile?.[group.key] ?? draft.value.professional[group.key]
const visibleGroups = computed(() => {
  if (!draft.value) return []
  const profile = profileGroups.filter(group => draft.value.profile?.[group.key])
  return [...profile.filter(group => group.key === 'personal'), ...factGroups, ...profile.filter(group => group.key !== 'personal')]
})
const activeDefinition = computed(() => visibleGroups.value.find(group => group.key === activeGroup.value) || visibleGroups.value[0])
// One tab per CV section keeps the page short; a tab with problems is marked and opened first.
const activeGroup = ref('personal')
const groupHasError = group => Object.keys(errors.value).some(key => key === group.key || key.startsWith(`${group.key}.`))
watch(errors, () => {
  const first = visibleGroups.value.find(groupHasError)
  if (first) activeGroup.value = first.key
})

function addItem(group) {
  groupData(group).items.push(createEmptyFactItem(group))
  groupData(group).confirmedAbsent = false
  markChanged()
}

function removeItem(group, itemId) {
  const data = groupData(group)
  data.items = data.items.filter((item) => item.id !== itemId)
  data.confirmedAbsent = false
  if (evidence.value?.itemId === itemId) evidence.value = null
  markChanged()
}

function showEvidence(selection) {
  evidence.value = selection
  document.getElementById('evidence-title')?.scrollIntoView({ behavior: 'auto', block: 'center' })
}

function returnToFact() {
  const input = document.getElementById(evidence.value?.inputId)
  input?.scrollIntoView({ behavior: 'auto', block: 'center' })
  input?.focus({ preventScroll: true })
}

async function saveChanges() {
  if (busy.value || !editable.value || !draft.value) return
  errors.value = validateReviewDraft(draft.value)
  if (Object.keys(errors.value).length) { actionError.value = 'Correct the highlighted values before saving.'; return }
  busy.value = 'save'
  const caseId = route.params.caseId
  feedback.value = ''
  actionError.value = ''
  try {
    await cases.saveReview(caseId, draft.value, reviewScenario)
    if (!pageActive || route.params.caseId !== caseId) return
    dirty.value = false
    feedback.value = 'Changes saved in this demo session.'
  } catch (error) { if (pageActive && route.params.caseId === caseId) actionError.value = error.message }
  finally { if (route.params.caseId === caseId) busy.value = '' }
}

async function saveOptionalDetails() {
  if (busy.value || !editable.value) return
  busy.value = 'research'
  const caseId = route.params.caseId
  feedback.value = ''
  actionError.value = ''
  try {
    await cases.saveResearch(caseId, research.value, researchConfirmed.value, reviewScenario)
    if (!pageActive || route.params.caseId !== caseId) return
    research.value = { ...entry.value.researchAttributes }
    researchDirty.value = false
    feedback.value = 'Optional details saved separately in this demo session.'
  } catch (error) { if (pageActive && route.params.caseId === caseId) actionError.value = error.message }
  finally { if (route.params.caseId === caseId) busy.value = '' }
}

async function confirmFacts() {
  if (busy.value || !editable.value || !draft.value) return
  errors.value = validateReviewDraft(draft.value, true)
  actionError.value = ''
  if (Object.keys(errors.value).length) {
    actionError.value = 'Resolve the highlighted facts or explicitly confirm their absence before confirming.'
    return
  }
  if (!reviewed.value) { actionError.value = 'Confirm that you reviewed the professional facts.'; return }
  if (researchDirty.value) { actionError.value = 'Save optional details before confirming, or restore their saved values.'; return }
  busy.value = 'confirm'
  const caseId = route.params.caseId
  try {
    await cases.confirmReview(caseId, draft.value, reviewed.value, reviewScenario)
    if (!pageActive || route.params.caseId !== caseId) return
    draft.value = null
    evidence.value = null
    dirty.value = false
  } catch (error) { if (pageActive && route.params.caseId === caseId) actionError.value = error.message }
  finally { if (route.params.caseId === caseId) busy.value = '' }
}

async function deleteCase() {
  try {
    await cases.deleteCase(route.params.caseId)
    confirmDelete.value = false
    router.replace({ name: 'MySimulations' })
  } catch (error) { actionError.value = error.message }
}

watch(() => route.params.caseId, () => { confirmDelete.value = false; loadDraft() }, { immediate: true })
// Clear editable content immediately when a case is deleted, expires or loses access.
watch(() => entry.value?.processingStatus, (status) => {
  if (status !== 'REVIEW_REQUIRED') {
    loadSequence += 1
    draft.value = null
    evidence.value = null
    research.value = emptyResearchAttributes()
  }
})
onBeforeUnmount(() => { loadSequence += 1; pageActive = false; clearInterval(expiryTimer) })

</script>

<template>
  <CandidateLayout>
    <section v-if="!entry" class="state-panel">
      <h1>{{ $t("Demo Case Not Available") }}</h1>
      <p>{{ $t("This case does not exist, has expired or is not available in your demo session.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
    </section>
    <section v-else-if="!editable && !confirmed" class="state-panel">
      <h1>{{ $t("Fact Review Not Available Yet") }}</h1>
      <p>{{ $t("Complete the upload and extraction demo before reviewing professional facts.") }}</p>
      <ElButton type="primary" @click="router.replace({ name: 'CvUpload', params: { caseId: entry.caseId } })">{{ $t("Back to Upload") }}</ElButton>
    </section>
    <section v-else aria-labelledby="review-title">
      <nav class="breadcrumb" :aria-label="$t(&quot;Breadcrumb&quot;)">
        <RouterLink :to="{ name: 'PublishedJobs' }">{{ $t("Jobs") }}</RouterLink><span aria-hidden="true">/</span>
        <span>{{ entry.jobTitle }}</span><span aria-hidden="true">/</span><span aria-current="page">{{ $t("Review Facts") }}</span>
      </nav>
      <h1 id="review-title">{{ $t("Review Your CV Facts") }}</h1>
      <p class="subtitle">{{ $t("Check professional facts against short evidence excerpts and correct missing or uncertain values.") }}</p>
      <p class="case-context">{{ entry.jobTitle }} {{ $t("· Version") }} {{ entry.version }} · {{ entry.caseId }}</p>
      <ElAlert type="info" :closable="false"
        :title="$t(&quot;Frontend demo: these fixed sample facts and excerpts are unrelated to your PDF. Changes stay in memory and disappear on refresh or sign out. No assessment is run on this page.&quot;)" />
      <CaseProgressStepper :current-step="1" :review-confirmed="confirmed" />
      <div v-if="confirmed" class="state-panel confirmed-panel" role="status">
        <h2>{{ $t("Facts Confirmed") }}</h2>
        <p>{{ $t("Your professional facts are saved and locked in this demo session. Temporary file metadata and the editable draft have been cleared.") }}</p>
        <p>{{ $t("Continue to the simulation progress page to run or check the automated demo stages.") }}</p>
        <ElButton type="primary" @click="router.push({ name: 'SimulationProgress', params: { caseId: entry.caseId } })">{{ $t("Continue to Simulation") }}</ElButton>
        <ElButton @click="router.push({ name: 'PublishedJobs' })">{{ $t("Back to Jobs") }}</ElButton>
      </div>
      <div v-else-if="loading" class="state-panel" role="status"><p>{{ $t("Loading review draft...") }}</p><ElSkeleton :rows="8" animated /></div>
      <div v-else-if="loadError" class="state-panel" role="alert"><h2>{{ $t("Review Draft Could Not Be Loaded") }}</h2>
        <p>{{ $t(loadError) }}</p><ElButton type="primary" @click="loadDraft">{{ $t("Try Again") }}</ElButton></div>
      <template v-else-if="draft">
        <ElAlert v-if="noFacts" class="review-notice" type="warning" :closable="false"
          :title="$t(&quot;No professional facts were extracted in this demo scenario. Add facts manually or explicitly confirm that a group has no facts to report.&quot;)" />
        <ElAlert v-if="!noFacts && unresolvedFacts" class="review-notice" type="warning" :closable="false"
          :title="$t(&quot;Some sample facts are missing or conflicting. Resolve required facts before confirming.&quot;)" />
        <div class="fact-tabs" role="tablist" :aria-label="$t('CV sections')">
          <button v-for="group in visibleGroups" :key="group.key" type="button" role="tab" class="fact-tab"
            :class="{ active: activeDefinition.key === group.key }" :aria-selected="activeDefinition.key === group.key"
            @click="activeGroup = group.key">
            {{ $t(group.label) }}<em>{{ groupData(group).items.length }}</em>
            <i v-if="groupHasError(group)" class="tab-error" :aria-label="$t('Needs attention')" />
          </button>
        </div>
        <div class="review-grid">
          <div class="fact-column" role="tabpanel">
            <FactGroupEditor :key="activeDefinition.key" :definition="activeDefinition"
              :group="groupData(activeDefinition)" :required="draft.requiredGroups.includes(activeDefinition.key)"
              :errors="errors" :disabled="Boolean(busy)" @change="markChanged" @add="addItem(activeDefinition)"
              @remove="removeItem(activeDefinition, $event)" @evidence="showEvidence" />
          </div>
          <aside class="evidence-column"><EvidencePanel :selection="evidence" @back="returnToFact" /></aside>
        </div>
        <section v-if="entry.researchAllowed" class="research-panel" aria-labelledby="research-title">
          <h2 id="research-title">{{ $t("Optional Research Details") }}</h2>
          <p>{{ $t("Enabled by a demo permission flag. These details are saved separately from professional facts. Leave UNKNOWN when you do not wish to provide a value; no value is inferred.") }}</p>
          <p>{{ $t("These local text values demonstrate the form only. The future research schema will define permitted categories.") }}</p>
          <div class="research-grid"><div v-for="field in researchFields" :key="field.key">
            <label :for="`research-${field.key}`">{{ $t(field.label) }}</label>
            <ElInput :id="`research-${field.key}`" v-model="research[field.key]" :disabled="Boolean(busy)" maxlength="80"
              @input="researchDirty = true; researchConfirmed = false; feedback = ''" />
          </div></div>
          <ElCheckbox v-model="researchConfirmed" :disabled="Boolean(busy)">{{ $t("I confirm the optional details I chose to provide.") }}</ElCheckbox>
          <div class="research-actions"><ElButton :disabled="Boolean(busy)" :loading="busy === 'research'" @click="saveOptionalDetails">{{ $t("Save Optional Details") }}</ElButton>
            <ElButton v-if="researchDirty" text :disabled="Boolean(busy)"
              @click="research = { ...entry.researchAttributes }; researchDirty = false; researchConfirmed = false">{{ $t("Restore Saved Optional Details") }}</ElButton></div>
        </section>
        <div class="confirmation-panel">
          <p>{{ $t(dirty ? 'You have unsaved professional changes.' : 'Professional draft is saved in this demo session.') }}</p>
          <p>{{ $t("Save Changes can keep an incomplete draft. Confirm Facts saves the valid professional draft and locks editing. Continue to Simulation after confirmation to run the demo.") }}</p>
          <ElCheckbox v-model="reviewed" :disabled="Boolean(busy)">{{ $t("I have reviewed the professional facts and confirm that they are correct.") }}</ElCheckbox>
          <ElAlert v-if="actionError" class="action-feedback" type="error" :closable="false" :title="$t(actionError)" />
          <ElAlert v-if="feedback" class="action-feedback" type="success" :closable="false" :title="$t(feedback)" />
          <div class="page-actions"><ElButton :disabled="Boolean(busy)" @click="confirmDelete = true">{{ $t("Cancel and Delete") }}</ElButton>
            <ElButton :disabled="Boolean(busy)" :loading="busy === 'save'" @click="saveChanges">{{ $t("Save Changes") }}</ElButton>
            <ElButton type="primary" :disabled="Boolean(busy)" :loading="busy === 'confirm'" @click="confirmFacts">{{ $t("Confirm Facts") }}</ElButton></div>
        </div>
      </template>
    </section>
    <ElDialog v-model="confirmDelete" :title="$t(&quot;Cancel and delete this demo case?&quot;)" width="min(460px, calc(100% - 32px))" :close-on-click-modal="false">
      <p>{{ $t("The professional draft, optional details and file metadata will be removed from this session. You will return to My simulations.") }}</p>
      <template #footer><ElButton @click="confirmDelete = false">{{ $t("Keep Case") }}</ElButton><ElButton type="danger" @click="deleteCase">{{ $t("Delete Demo Case") }}</ElButton></template>
    </ElDialog>
  </CandidateLayout>
</template>

<style scoped>
.breadcrumb { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 24px; color: var(--sr-text-muted); font-size: 13px; line-height: 1.7; }
.breadcrumb a { color: var(--sr-primary); text-decoration: none; }
.breadcrumb a:focus-visible { outline: 2px solid var(--sr-primary); outline-offset: 3px; }
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; line-height: 1.4; letter-spacing: -0.7px; }
h2 { font-size: 17px; color: var(--sr-text); }
.subtitle { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; margin: 10px 0 16px; }
.case-context { margin: 0 0 20px; color: var(--sr-text-muted); font-size: 12px; line-height: 1.8; }
.review-notice { margin-bottom: 20px; }
.review-grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: 22px; align-items: start; }
.fact-column { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
.evidence-column { position: sticky; top: 104px; min-width: 0; }
.fact-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.fact-tab { display: inline-flex; align-items: center; gap: 6px; padding: 9px 14px; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer;
  color: var(--sr-text-secondary); background: var(--sr-surface); border: 1px solid var(--sr-border); border-radius: 999px; transition: border-color .15s, color .15s, background .15s; }
.fact-tab:hover { border-color: var(--sr-primary-200); color: var(--sr-ink); }
.fact-tab.active { color: #fff; background: var(--sr-primary); border-color: var(--sr-primary); }
.fact-tab.active em { color: var(--sr-primary); background: #fff; }
.fact-tab:focus-visible { outline: 2px solid var(--sr-accent); outline-offset: 2px; }
.fact-tab em { min-width: 18px; padding: 0 6px; font-style: normal; font-size: 11px; line-height: 18px; text-align: center; color: var(--sr-primary); background: var(--sr-primary-50); border-radius: 999px; }
.tab-error { width: 7px; height: 7px; border-radius: 50%; background: #d14343; }
.state-panel, .research-panel, .confirmation-panel { padding: 24px; border: 1px solid var(--sr-border); border-radius: 10px; background: white; }
.state-panel { text-align: center; }
.state-panel p, .research-panel p, .confirmation-panel p { color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; }
.state-panel h2 { font-size: 22px; }
.confirmed-panel h2 { color: #218458; }
.research-panel, .confirmation-panel { margin-top: 24px; }
.research-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 20px 0; }
.research-grid label { display: block; margin-bottom: 7px; font-size: 13px; color: var(--sr-text-secondary); }
.research-actions, .page-actions { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 18px; }
.page-actions .el-button, .research-actions .el-button { margin: 0; }
.page-actions .el-button:first-child { margin-right: auto; }
.action-feedback { margin-top: 16px; }
:deep(.el-checkbox) { height: auto; }
:deep(.el-checkbox__label) { white-space: normal; line-height: 1.8; font-size: 13px; }
@media (max-width: 700px) {
  h1 { font-size: 25px; }
  .review-grid { grid-template-columns: 1fr; }
  .evidence-column { position: static; }
  .research-grid { grid-template-columns: 1fr; }
  .page-actions { flex-direction: column; }
  .page-actions .el-button { width: 100%; }
  .state-panel, .research-panel, .confirmation-panel { padding: 20px; }
}
</style>
