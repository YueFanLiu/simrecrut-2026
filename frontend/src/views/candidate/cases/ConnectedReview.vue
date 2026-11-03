<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElTabPane, ElTabs } from 'element-plus'
import CandidateLayout from '../../../layouts/CandidateLayout.vue'
import ResumeFactsTable from '../../../components/simrecrut/ResumeFactsTable.vue'
import { caseAction, caseResearch, caseReview, saveCaseDraft, saveCaseResearch } from '../../../api/cases.js'
import { useCasesStore } from '../../../store/modules/cases.js'
import { getCaseDestination } from '../../../utils/case-history.js'

const route = useRoute()
const router = useRouter()
const cases = useCasesStore()
const groups = ['Skills', 'Experience', 'Education', 'Languages', 'Projects']
const families = { SCHOOL_CATEGORY: 'School category', REFERRAL: 'Referral', GENDER: 'Gender', ETHNICITY: 'Ethnicity' }
const entry = ref(null)
const draft = ref(null)
const attributes = ref({})
const reviewed = ref(false)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const tab = ref('Skills')
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
    attributes.value = Object.fromEntries(Object.keys(families).map(type =>
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
      savedDraft = JSON.stringify(payload)
      const fresh = await caseReview(id)
      if (!current()) return
      applyDraft(fresh)
    }
    if (action === 'confirm') {
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
watch(() => route.params.caseId, load, { immediate: true })
onBeforeUnmount(() => { loadSequence++; pending = undefined })
</script>

<template>
  <CandidateLayout>
    <h1>Review Your CV Facts</h1>
    <p>Case {{ route.params.caseId }}</p>
    <p v-if="loading" role="status">Loading case…</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="feedback" role="status">{{ feedback }}</p>
    <ElButton v-if="error" :disabled="busy || loading" @click="load">Reload Case</ElButton>
    <template v-if="draft">
      <p>Correct the extracted facts and mark any empty groups you have reviewed.</p>
      <ElAlert v-if="draft.unresolvedRequiredFields.length" type="warning" :closable="false"
        :title="'Unresolved requirements from the last saved draft: ' + draft.unresolvedRequiredFields.join(', ')" />
      <ElTabs v-model="tab">
        <ElTabPane v-for="group in groups" :key="group" :label="group" :name="group">
          <ResumeFactsTable v-model="draft.professional[group.toLowerCase()]" :group="group.toLowerCase()" :locked="busy" />
          <ElCheckbox v-if="!draft.professional[group.toLowerCase()].length" v-model="draft.confirmedAbsent"
            :value="group.toUpperCase()" :disabled="busy">I confirm there are no facts to report in this group.</ElCheckbox>
        </ElTabPane>
      </ElTabs>
      <ElButton :disabled="busy" @click="perform('draft')">Save Draft</ElButton>

      <h2>Optional Research Attributes</h2>
      <p>Use codes from the study protocol. Leave a field blank or use UNKNOWN if you choose to omit it.</p>
      <ElForm label-position="top" :disabled="busy">
        <ElFormItem v-for="(label, type) in families" :key="type" :label="label">
          <ElInput v-model="attributes[type]" maxlength="128" />
        </ElFormItem>
        <ElButton @click="perform('research')">Save Optional Attributes</ElButton>
      </ElForm>

      <h2>Confirm Facts</h2>
      <ElCheckbox v-model="reviewed" :disabled="busy">I have reviewed the professional facts.</ElCheckbox>
      <ElButton type="primary" :disabled="busy || !reviewed" @click="perform('confirm')">Confirm and Continue</ElButton>
    </template>
    <template v-else-if="destination && !loading && destination.name !== 'CvReview'">
      <p>{{ entry.confirmedAt ? 'Professional facts are confirmed and locked.' : 'The draft is not ready to review.' }}</p>
      <ElButton type="primary" @click="router.replace(destination)">Continue Case</ElButton>
    </template>
  </CandidateLayout>
</template>
