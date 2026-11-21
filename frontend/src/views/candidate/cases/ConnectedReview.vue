<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElTabPane, ElTabs, ElSelect, ElOption, ElRadioGroup, ElRadio } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import ResumeFactsTable from '../../../components/simrecrut/ResumeFactsTable.vue'
import { caseAction, caseResearch, caseReview, saveCaseDraft, saveCaseResearch, caseModelInputs, saveCaseModelInputs } from '../../../api/cases.js'
import { useCasesStore } from '../../../store/modules/cases.js'
import { getCaseDestination } from '../../../utils/case-history.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
// Personal and Other information complete the CV record; only the five professional groups are matched.
const groups = ['Personal', 'Skills', 'Experience', 'Education', 'Languages', 'Projects', 'Other']
const profileGroups = ['Personal', 'Other']
const groupLabels = { Personal: 'Personal information', Other: 'Other information' }
const legacyFamilies = { SCHOOL_CATEGORY: 'School category', REFERRAL: 'Referral', GENDER: 'Gender', ETHNICITY: 'Ethnicity' }
const families = computed(() => options.value?.NAME_CUE
  ? { ...legacyFamilies, NAME_CUE: 'Name cue' } : legacyFamilies)
const options = ref(null)
const media = ref(null)
const portraitFile = ref('')
const portraitInput = ref(null)
const modelPayload = () => ({
  useText: media.value.useText, usePortrait: media.value.usePortrait,
  name: media.value.name || '', biography: media.value.biography || '',
  portraitIndex: media.value.portraitIndex ?? -1,
  ...(portraitFile.value ? { portraitBase64: portraitFile.value } : {}),
})
let savedMedia = null
const entry = ref(null)
const draft = ref(null)
const attributes = ref({})
const reviewed = ref(false)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const tab = ref('Personal')
let pending
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
  media.value = null
  options.value = null
  portraitFile.value = ''
  savedMedia = null
  pending = undefined
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

async function write(action, payload, send) {
  const signature = JSON.stringify([action, payload])
  if (pending?.signature !== signature) pending = { signature, key: crypto.randomUUID() }
  const operation = pending
  try {
    const result = await send(operation.key)
    if (pending === operation) pending = undefined
    return result
  } catch (error) {
    if (pending === operation && error.status >= 400 && error.status < 500) pending = undefined
    throw error
  }
}

async function perform(action) {
  if (busy.value || !draft.value) return
  const id = route.params.caseId
  const sequence = loadSequence
  const current = () => sequence === loadSequence
  busy.value = true
  feedback.value = ''
  error.value = ''
  try {
    if (action === 'research') {
      const attributes = attributePayload()
      const payload = { attributes, revision: draft.value.revision }
      const result = await write(action, payload, key => saveCaseResearch(id, payload, key))
      if (!current()) return
      draft.value.revision = result.revision
      savedAttributes = JSON.stringify(attributes)
      feedback.value = 'Optional attributes saved.'
      return
    }
    if (action === 'confirm' && JSON.stringify(attributePayload()) !== savedAttributes) {
      throw new Error('Save optional attributes before confirming the draft.')
    }
    const payload = draftPayload()
    if (JSON.stringify(payload) !== savedDraft) {
      const update = { ...payload, revision: draft.value.revision }
      const result = await write('draft', update, key => saveCaseDraft(id, update, key))
      if (!current()) return
      draft.value.revision = result.revision
      // The previous validation messages describe a different revision.
      draft.value.unresolvedRequiredFields = []
      savedDraft = JSON.stringify(payload)
      const fresh = await caseReview(id)
      if (!current()) return
      applyDraft(fresh)
      if (media.value) {
        media.value = await caseModelInputs(id)
        if (!current()) return
        savedMedia = null
      }
    }
    if (action === 'model') {
      const payload = { ...modelPayload(), revision: draft.value.revision }
      const result = await write(action, payload, key => saveCaseModelInputs(id, payload, key))
      if (!current()) return
      const fresh = await caseModelInputs(id)
      if (!current()) return
      if (fresh.revision !== result.revision) throw new Error('The case changed while loading. Reload the draft.')
      draft.value.revision = result.revision
      media.value = fresh
      if (fresh.portrait && fresh.portraitIndex >= 0) {
        media.value.candidates[fresh.portraitIndex] = fresh.portrait.dataBase64
      }
      portraitFile.value = ''
      savedMedia = JSON.stringify(modelPayload())
      feedback.value = 'Optional model inputs saved.'
      return
    }
    if (action === 'confirm') {
      if (media.value && JSON.stringify(modelPayload()) !== savedMedia) {
        throw new Error('Review and save the optional model inputs before confirming.')
      }
      const confirmation = { revision: draft.value.revision, confirmed: true }
      await write(action, confirmation, key => caseAction(id, 'confirm', confirmation, key))
      if (current()) await router.push({ name: 'SimulationProgress', params: { caseId: id } })
    } else feedback.value = 'Professional facts saved.'
  } catch (failure) {
    if (current()) error.value = failure.message
  } finally {
    busy.value = false
  }
}
async function readPortrait(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 1400000) {
    error.value = 'Use a PNG or JPEG portrait under 1.4 MB.'
    event.target.value = ''
    return
  }
  const sequence = loadSequence
  const reader = new FileReader()
  reader.onload = () => {
    if (sequence === loadSequence) {
      portraitFile.value = String(reader.result).split(',')[1]
      media.value.usePortrait = true
    }
  }
  reader.readAsDataURL(file)
}
watch(() => route.params.caseId, load, { immediate: true })
onBeforeUnmount(() => { loadSequence++; pending = undefined })
</script>

<template>
  <CandidateLayout>
    <h1>{{ $t("Review Your CV Facts") }}</h1>
    <p>{{ $t("Case") }} {{ route.params.caseId }}</p>
    <p v-if="loading" role="status">{{ $t("Loading case…") }}</p>
    <ElAlert v-if="error" :title="$t(error)" type="error" :closable="false" />
    <p v-if="feedback" role="status">{{ $t(feedback) }}</p>
    <ElButton v-if="error" :disabled="busy || loading" @click="load">{{ $t("Reload Case") }}</ElButton>
    <template v-if="draft">
      <p>{{ $t("Correct the extracted facts and mark any empty groups you have reviewed.") }}</p>
      <ElAlert v-for="warning in draft.extractionWarnings ?? []" :key="warning"
        type="warning" :closable="false" :title="warning" />
      <ElAlert v-if="draft.unresolvedRequiredFields.length" type="warning" :closable="false"
        :title="$t('Unresolved requirements from the last saved draft:') + ' ' + draft.unresolvedRequiredFields.map(field => $t(field)).join(', ')" />
      <ElTabs v-model="tab">
        <ElTabPane v-for="group in groups" :key="group" :label="$t(groupLabels[group] || group)" :name="group">
          <p v-if="group === 'Personal'">{{ $t("Check your name, contact details and photo. Recruiters see these reviewed values instead of your PDF.") }}</p>
          <p v-if="group === 'Other'">{{ $t("Interests, volunteering, certifications, awards and anything else from your CV.") }}</p>
          <ResumeFactsTable v-model="draft.professional[group.toLowerCase()]" :group="group.toLowerCase()" :locked="busy" />
          <ElCheckbox v-if="!profileGroups.includes(group) && !draft.professional[group.toLowerCase()].length" v-model="draft.confirmedAbsent"
            :value="group.toUpperCase()" :disabled="busy">{{ $t("I confirm there are no facts to report in this group.") }}</ElCheckbox>
        </ElTabPane>
      </ElTabs>
      <ElButton :disabled="busy" @click="perform('draft')">{{ $t("Save Draft") }}</ElButton>

      <h2>{{ $t("Optional Research Attributes") }}</h2>
      <p>{{ $t("Use codes from the study protocol. Leave a field blank or use UNKNOWN if you choose to omit it.") }}</p>
      <ElForm label-position="top" :disabled="busy">
        <ElFormItem v-for="(label, type) in families" :key="type" :label="$t(label)">
          <ElSelect v-if="options?.[type]" v-model="attributes[type]">
            <ElOption v-for="value in options[type]" :key="value" :value="value"
              :label="value.replaceAll('_', ' ')" />
          </ElSelect>
          <ElInput v-else v-model="attributes[type]" maxlength="128" />
        </ElFormItem>
        <ElButton @click="perform('research')">{{ $t("Save Optional Attributes") }}</ElButton>
      </ElForm>

      <section v-if="media" class="model-inputs">
        <h2>{{ $t("Optional Model Inputs") }}</h2>
        <p>{{ $t("Only Biased uses these inputs. You can omit text, the portrait, or both.") }}</p>
        <p>{{ $t("Research codes are assigned study settings. They do not describe your real ethnicity.") }}</p>
        <ElForm label-position="top" :disabled="busy">
          <ElCheckbox v-model="media.useText">{{ $t("Use the reviewed text and optional name") }}</ElCheckbox>
          <ElFormItem :label="$t('Optional name')">
            <ElInput v-model="media.name" maxlength="200" :disabled="!media.useText" />
          </ElFormItem>
          <ElFormItem :label="$t('Reviewed model text')">
            <ElInput v-model="media.biography" type="textarea" :rows="6" maxlength="100000"
              :disabled="!media.useText" />
          </ElFormItem>
          <p>{{ $t("Check the text against your CV and remove contact details before saving.") }}</p>
          <ElCheckbox v-model="media.usePortrait">{{ $t("Use a reviewed portrait") }}</ElCheckbox>
          <p v-if="media.portraitNotice">{{ $t(media.portraitNotice) }}</p>
          <ElRadioGroup v-if="media.candidates?.length" v-model="media.portraitIndex"
            :disabled="!media.usePortrait || busy" class="portrait-options" @change="portraitFile = ''">
            <ElRadio v-for="(portrait, index) in media.candidates" :key="index" :value="index">
              <img :src="'data:image/jpeg;base64,' + portrait" :alt="$t('Portrait candidate') + ' ' + (index + 1)" />
            </ElRadio>
          </ElRadioGroup>
          <ElFormItem :label="$t('Choose another local portrait (PNG or JPEG)')">
            <input ref="portraitInput" type="file" accept="image/png,image/jpeg" hidden
              :disabled="busy" @change="readPortrait" />
            <ElButton :disabled="busy" @click="portraitInput?.click()">{{ $t("Choose Portrait") }}</ElButton>
          </ElFormItem>
          <img v-if="portraitFile || (media.portrait && media.portraitIndex === -1)"
            :src="'data:image/jpeg;base64,' + (portraitFile || media.portrait.dataBase64)"
            class="portrait-preview" :alt="$t('Selected portrait preview')" />
          <p>{{ $t("The original PDF is deleted after confirmation. Approved model inputs are deleted after both predictions finish.") }}</p>
          <ElButton :disabled="busy" @click="perform('model')">{{ $t("Save Optional Model Inputs") }}</ElButton>
        </ElForm>
      </section>

      <h2>{{ $t("Confirm Facts") }}</h2>
      <p>{{ $t("Your original PDF is deleted as soon as you confirm.") }}</p>
      <ElCheckbox v-model="reviewed" :disabled="busy">{{ $t("I have reviewed the professional facts.") }}</ElCheckbox>
      <ElButton type="primary" :disabled="busy || !reviewed" @click="perform('confirm')">{{ $t("Confirm and Continue") }}</ElButton>
    </template>
    <template v-else-if="destination && !loading && destination.name !== 'CvReview'">
      <p>{{ $t(entry.confirmedAt ? 'Professional facts are confirmed and locked.' : 'The draft is not ready to review.') }}</p>
      <ElButton type="primary" @click="router.replace(destination)">{{ $t("Continue Case") }}</ElButton>
    </template>
  </CandidateLayout>
</template>

<style scoped>
.model-inputs { margin: 2rem 0; max-width: 850px; }
.portrait-options { display: flex; gap: 2rem; margin: 1rem 0; }
.portrait-options img, .portrait-preview { width: 112px; height: 112px; object-fit: cover; }
.portrait-options :deep(.el-radio) { height: auto; }
</style>
