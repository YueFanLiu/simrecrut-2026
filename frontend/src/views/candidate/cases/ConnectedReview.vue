<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElRadioGroup, ElRadio, ElMessageBox } from 'element-plus'
import { t } from '../../../i18n/index.js'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import CvFactsEditor from '../../../components/simrecrut/CvFactsEditor.vue'
import { caseAction, caseResearch, caseReview, saveCaseDraft, saveCaseResearch, caseModelInputs, saveCaseModelInputs } from '../../../api/cases.js'
import { useCasesStore } from '../../../store/modules/cases.js'
import { getCaseDestination } from '../../../utils/case-history.js'
import { createCaseReviewFlow } from '../../../utils/case-review-flow.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const legacyFamilies = { SCHOOL_CATEGORY: 'School category', REFERRAL: 'Referral', GENDER: 'Gender', ETHNICITY: 'Ethnicity' }
const families = computed(() => options.value?.NAME_CUE
  ? { ...legacyFamilies, NAME_CUE: 'Name cue' } : legacyFamilies)
const options = ref(null)
const media = ref(null)
const portraitFile = ref('')
const portraitReading = ref(false)
const modelTextEdited = ref(false), previousModelText = ref(null)
const portraitInput = ref(null)
const modelPayload = () => {
  const portrait = portraitFile.value || (media.value.portraitIndex === -1 ? media.value.portrait?.dataBase64 : '')
  return {
    useText: media.value.useText, usePortrait: media.value.usePortrait,
    name: media.value.name || '', biography: media.value.biography || '',
    portraitIndex: media.value.portraitIndex ?? -1,
    ...(media.value.usePortrait && portrait ? { portraitBase64: portrait } : {}),
  }
}
let savedModel = ''
const entry = ref(null)
const draft = ref(null)
const attributes = ref({})
const reviewed = ref(false)
const editorPending = ref(false)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const retryPending = ref(false)
const optionalOpen = ref(false)
const locked = computed(() => busy.value || retryPending.value || portraitReading.value)
let flow
let loadSequence = 0
let savedDraft = ''
let savedAttributes = ''
const destination = computed(() => entry.value && getCaseDestination(entry.value))
const draftPayload = () => ({
  professional: draft.value.professional,
  confirmedAbsent: draft.value.confirmedAbsent.filter(group => !draft.value.professional[group.toLowerCase()].length),
})
const attributePayload = () => Object.entries(attributes.value)
  .map(([type, value]) => ({ type, value: value.trim() || 'UNKNOWN' }))

function applyDraft(value) {
  draft.value = value
  savedDraft = JSON.stringify(draftPayload())
}

async function load() {
  const sequence = ++loadSequence
  const caseId = route.params.caseId
  const current = () => sequence === loadSequence
  loading.value = true
  error.value = ''
  feedback.value = ''
  entry.value = null
  draft.value = null
  reviewed.value = false
  editorPending.value = false
  media.value = null
  options.value = null
  portraitFile.value = ''
  portraitReading.value = false
  modelTextEdited.value = false; previousModelText.value = null
  savedModel = ''
  retryPending.value = false
  optionalOpen.value = false
  flow = createCaseReviewFlow({
    saveDraft: (body, key) => saveCaseDraft(caseId, body, key),
    readDraft: () => caseReview(caseId),
    readModel: () => caseModelInputs(caseId),
    saveAttributes: (body, key) => saveCaseResearch(caseId, body, key),
    saveModel: (body, key) => saveCaseModelInputs(caseId, body, key),
    confirm: (body, key) => caseAction(caseId, 'confirm', body, key),
  })
  try {
    const row = await cases.loadConnectedCase(caseId)
    if (!current()) return
    entry.value = row
    if (row.processingStatus !== 'REVIEW_REQUIRED') return
    const facts = await caseReview(caseId)
    const research = await caseResearch(caseId)
    if (!current()) return
    if (facts.revision !== research.revision) throw new Error('The case changed while loading. Reload the draft.')
    options.value = research.allowedValues
    if (options.value?.NAME_CUE) {
      const inputs = await caseModelInputs(caseId)
      if (!current()) return
      if (inputs.revision !== facts.revision) throw new Error('The case changed while loading. Reload the draft.')
      media.value = inputs
      savedModel = JSON.stringify(modelPayload())
    }
    attributes.value = Object.fromEntries(Object.keys(families.value).map(type =>
      [type, research.attributes.find(item => item.type === type)?.value || 'UNKNOWN']))
    savedAttributes = JSON.stringify(attributePayload())
    applyDraft(facts)
  } catch (failure) {
    if (current()) error.value = failure.message
  } finally {
    if (current()) loading.value = false
  }
}

async function perform(action) {
  if (busy.value || portraitReading.value || editorPending.value || !draft.value) return
  if (action === 'confirm' && !reviewed.value) return
  const id = route.params.caseId
  const sequence = loadSequence
  const current = () => sequence === loadSequence
  const operation = flow
  busy.value = true
  feedback.value = ''
  error.value = ''
  try {
    const facts = draftPayload(), attributes = attributePayload()
    const result = await operation.run({
      revision: draft.value.revision, facts, attributes,
      factsChanged: JSON.stringify(facts) !== savedDraft,
      attributesChanged: JSON.stringify(attributes) !== savedAttributes,
      model: media.value ? modelPayload() : null,
      modelTextEdited: modelTextEdited.value,
      confirm: action === 'confirm',
    }, (state, stage) => {
      if (!current()) return
      draft.value.revision = state.revision
      if (stage === 'saveDraft') savedDraft = JSON.stringify(state.facts)
      if (stage === 'readDraft') applyDraft(state.draft)
      if (stage === 'readModel') {
        media.value.biography = state.model.biography
        if (state.previousModelText !== undefined && previousModelText.value === null) previousModelText.value = state.previousModelText
      }
      if (stage === 'saveModel') { savedModel = JSON.stringify(modelPayload()); previousModelText.value = null }
      if (stage === 'saveAttributes') savedAttributes = JSON.stringify(state.attributes)
    })
    if (!current()) return
    retryPending.value = false
    if (result.requiresModelReview) {
      reviewed.value = false; optionalOpen.value = true
      feedback.value = 'Your CV information was saved. Review the updated model text before confirming.'
    } else if (result.confirm) {
      entry.value = { ...entry.value, processingStatus: 'CONFIRMED', confirmedAt: new Date().toISOString() }
      draft.value = null
      await router.push({ name: 'SimulationProgress', params: { caseId: id } })
    } else feedback.value = 'All review changes saved.'
  } catch (failure) {
    if (current()) {
      error.value = failure.message
      retryPending.value = operation.hasPending
      optionalOpen.value = true
    }
  } finally {
    if (current()) busy.value = false
  }
}
async function readPortrait(event) {
  const file = event.target.files?.[0]
  if (!file || locked.value) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 1400000) {
    error.value = 'Use a PNG or JPEG portrait under 1.4 MB.'
    event.target.value = ''
    return
  }
  const sequence = loadSequence
  portraitReading.value = true
  const reader = new FileReader()
  reader.onload = () => {
    if (sequence === loadSequence) {
      portraitFile.value = String(reader.result).split(',')[1]
      media.value.usePortrait = true
      media.value.portraitIndex = -1
    }
  }
  reader.onerror = () => { if (sequence === loadSequence) error.value = 'The portrait could not be read. Choose it again.' }
  reader.onloadend = () => { if (sequence === loadSequence) { portraitReading.value = false; event.target.value = '' } }
  reader.readAsDataURL(file)
}
function restoreModelText() {
  if (locked.value || previousModelText.value === null) return
  media.value.biography = previousModelText.value
  previousModelText.value = null; modelTextEdited.value = true; reviewed.value = false
}
function hasUnsavedChanges() {
  return draft.value && (editorPending.value || retryPending.value || JSON.stringify(draftPayload()) !== savedDraft
    || JSON.stringify(attributePayload()) !== savedAttributes
    || (media.value && JSON.stringify(modelPayload()) !== savedModel))
}
async function mayDiscard() {
  if ((busy.value || portraitReading.value) && draft.value) return false
  if (!hasUnsavedChanges()) return true
  try {
    await ElMessageBox.confirm(t('Leave this review? Unsaved changes will be lost. A save with an uncertain result may already have reached the server.'),
      t('Leave Review'), { type: 'warning', confirmButtonText: t('Leave'), cancelButtonText: t('Keep Editing') })
    return true
  } catch { return false }
}
async function reloadDraft() { if (await mayDiscard()) await load() }
function beforeUnload(event) {
  if (busy.value || portraitReading.value || hasUnsavedChanges()) { event.preventDefault(); event.returnValue = '' }
}
globalThis.window?.addEventListener('beforeunload', beforeUnload)
onBeforeRouteLeave(mayDiscard)
onBeforeRouteUpdate((to, from) => to.params.caseId === from.params.caseId || mayDiscard())
watch(() => route.params.caseId, load, { immediate: true })
watch(() => [draft.value?.professional, draft.value?.confirmedAbsent, attributes.value, media.value, portraitFile.value],
  () => { if (!busy.value) reviewed.value = false }, { deep: true, flush: 'sync' })
watch(editorPending, value => { if (value) reviewed.value = false }, { flush: 'sync' })
onBeforeUnmount(() => { loadSequence++; globalThis.window?.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <CandidateLayout>
    <h1>{{ $t('Review your CV') }}</h1>
    <p>{{ $t("Case") }} {{ route.params.caseId }}</p>
    <p v-if="loading" role="status">{{ $t("Loading case…") }}</p>
    <ElAlert v-if="error && !draft" :title="$t(error)" type="error" :closable="false" />
    <ElButton v-if="error && !draft" :disabled="busy || loading" @click="load">{{ $t("Reload Case") }}</ElButton>
    <ElButton v-if="error && draft && !retryPending" :disabled="busy" @click="reloadDraft">{{ $t("Reload Saved Draft") }}</ElButton>
    <template v-if="draft">
      <p>{{ $t('Check each CV section, correct any details and review empty sections before confirming.') }}</p>
      <ElAlert v-for="warning in draft.extractionWarnings ?? []" :key="warning"
        type="warning" :closable="false" :title="warning" />
      <ElAlert v-if="draft.unresolvedRequiredFields.length" type="warning" :closable="false"
        :title="$t('Unresolved requirements from the last saved draft:') + ' ' + draft.unresolvedRequiredFields.map(field => $t(field)).join(', ')" />
      <CvFactsEditor v-model="draft.professional" v-model:confirmed-absent="draft.confirmedAbsent" require-absence :locked="locked" @pending-change="editorPending = $event" />

      <details class="optional-settings" :open="optionalOpen" @toggle="optionalOpen = $event.target.open">
      <summary>{{ $t("Optional settings — saved with your review") }}</summary>
      <p>{{ $t("Optional text and portrait inputs are omitted unless you enable them. Confirm and Continue saves all changes together.") }}</p>
      <h2>{{ $t("Optional Research Attributes") }}</h2>
      <p>{{ $t("Use codes from the study protocol. Leave a field blank or use UNKNOWN if you choose to omit it.") }}</p>
      <ElForm label-position="top" :disabled="locked">
        <ElFormItem v-for="(label, type) in families" :key="type" :label="$t(label)">
          <ElSelect v-if="options?.[type]" v-model="attributes[type]">
            <ElOption v-for="value in options[type]" :key="value" :value="value"
              :label="value.replaceAll('_', ' ')" />
          </ElSelect>
          <ElInput v-else v-model="attributes[type]" maxlength="128" />
        </ElFormItem>
      </ElForm>

      <section v-if="media" class="model-inputs">
        <h2>{{ $t("Optional Model Inputs") }}</h2>
        <p>{{ $t("Only Biased uses these inputs. You can omit text, the portrait, or both.") }}</p>
        <p>{{ $t("Research codes are assigned study settings. They do not describe your real ethnicity.") }}</p>
        <ElForm label-position="top" :disabled="locked">
          <ElCheckbox v-model="media.useText">{{ $t("Use the reviewed text and optional name") }}</ElCheckbox>
          <ElFormItem :label="$t('Optional name')">
            <ElInput v-model="media.name" maxlength="200" :disabled="!media.useText" />
          </ElFormItem>
          <ElFormItem :label="$t('Reviewed model text')">
            <ElInput v-model="media.biography" type="textarea" :rows="6" maxlength="100000"
              :disabled="!media.useText" @input="modelTextEdited = true" />
          </ElFormItem>
          <template v-if="previousModelText !== null">
            <p>{{ $t('The model text was updated after your CV changes. You can restore the previous text and review it instead.') }}</p>
            <ElButton :disabled="locked" @click="restoreModelText">{{ $t('Restore previous model text') }}</ElButton>
          </template>
          <p>{{ $t("Check the text against your CV and remove contact details before saving.") }}</p>
          <ElCheckbox v-model="media.usePortrait">{{ $t("Use a reviewed portrait") }}</ElCheckbox>
          <p v-if="media.portraitNotice">{{ $t(media.portraitNotice) }}</p>
          <ElRadioGroup v-if="media.candidates?.length" v-model="media.portraitIndex"
            :disabled="!media.usePortrait || locked" class="portrait-options" @change="portraitFile = ''">
            <ElRadio v-for="(portrait, index) in media.candidates" :key="index" :value="index">
              <img :src="'data:image/jpeg;base64,' + portrait" :alt="$t('Portrait candidate') + ' ' + (index + 1)" />
            </ElRadio>
          </ElRadioGroup>
          <ElFormItem :label="$t('Choose another local portrait (PNG or JPEG)')">
            <input ref="portraitInput" type="file" accept="image/png,image/jpeg" hidden
              :disabled="locked" @change="readPortrait" />
            <ElButton :disabled="locked" @click="portraitInput?.click()">{{ $t("Choose Portrait") }}</ElButton>
            <span v-if="portraitReading" role="status">{{ $t("Reading portrait…") }}</span>
          </ElFormItem>
          <img v-if="portraitFile || (media.portrait && media.portraitIndex === -1)"
            :src="'data:image/jpeg;base64,' + (portraitFile || media.portrait.dataBase64)"
            class="portrait-preview" :alt="$t('Selected portrait preview')" />
          <p>{{ $t("Approved model inputs are temporary and have a separate cleanup process after predictions finish. Their status is separate from original PDF cleanup.") }}</p>
        </ElForm>
      </section>
      </details>

      <section class="confirmation-panel">
      <h2>{{ $t('Confirm your CV information') }}</h2>
      <p>{{ $t("Confirm and Continue saves your review, locks the facts and opens simulation progress.") }}</p>
      <p>{{ $t("After confirmation, the server requests deletion of any original PDF. Check its status on simulation progress.") }}</p>
      <ElCheckbox v-model="reviewed" :disabled="locked || editorPending">{{ $t('I have reviewed my CV information and any optional inputs I chose to use.') }}</ElCheckbox>
      <p v-if="retryPending" role="status">{{ $t("The previous save result is uncertain. Retry it before making further changes.") }}</p>
      <ElAlert v-if="error" :title="$t(error)" type="error" :closable="false" />
      <p v-if="feedback" role="status">{{ $t(feedback) }}</p>
      <div class="review-actions">
        <ElButton v-if="retryPending" :loading="busy" @click="perform('retry')">{{ $t("Retry Previous Save") }}</ElButton>
        <template v-else>
          <ElButton :disabled="locked || editorPending" :loading="busy" @click="perform('save')">{{ $t("Save All Changes") }}</ElButton>
          <ElButton type="primary" :disabled="locked || editorPending || !reviewed" :loading="busy" @click="perform('confirm')">{{ $t("Confirm and Continue") }}</ElButton>
        </template>
      </div>
      </section>
    </template>
    <template v-else-if="destination && !loading && destination.name !== 'CvReview'">
      <p>{{ $t(entry.confirmedAt ? 'Professional facts are confirmed and locked.' : 'The draft is not ready to review.') }}</p>
      <ElButton type="primary" @click="router.replace(destination)">{{ $t("Continue Case") }}</ElButton>
    </template>
  </CandidateLayout>
</template>

<style scoped>
.model-inputs { margin: 2rem 0; max-width: 850px; }
.optional-settings { margin:24px 0; padding:18px; background:var(--sr-surface); border:1px solid var(--sr-border); border-radius:10px; }
.optional-settings summary { cursor:pointer; font-weight:600; color:var(--sr-ink); }
.optional-settings summary:focus-visible { outline:2px solid var(--sr-primary); outline-offset:4px; }
.confirmation-panel { padding:20px; margin:24px 0; border:1px solid var(--sr-border); border-radius:10px; }
.review-actions { display:flex; flex-wrap:wrap; gap:12px; margin-top:16px; }
.review-actions .el-button { margin:0; white-space:normal; height:auto; min-height:34px; max-width:100%; line-height:1.5; }
.portrait-options { display: flex; gap: 2rem; margin: 1rem 0; }
.portrait-options img, .portrait-preview { width: 112px; height: 112px; object-fit: cover; }
.portrait-options :deep(.el-radio) { height: auto; }
</style>
