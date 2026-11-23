<!-- Keep the existing wizard UI; connected actions wait for server responses. -->
<script setup>
import { t } from '../../../i18n/index.js'

import { demoMode } from '../../../config/runtime.js'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElInput, ElInputNumber, ElMessageBox, ElOption, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import RequirementPayloadEditor from '../../../components/simrecrut/RequirementPayloadEditor.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useHrJobsStore } from '../../../store/modules/hr-jobs.js'
import { createJobForm, criteria, extractMockRequirements, validateDescription, validateJob, validateWeights, weightUnits } from '../../../mock/hr-job-wizard.js'
import { hrWizardScenario } from '../../../config/demo.js'
import { extractJob, validateJob as validateConnectedJob } from '../../../api/jobs.js'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useHrJobsStore()
const steps = ['Description', 'Extraction', 'Review Requirements', 'Mandatory', 'Weights', 'Validation', 'Publish']
const form = ref(createJobForm())
const revision = ref(0)
const step = ref(0)
const busy = ref(false)
const loading = ref(false)
const loadError = ref('')
const errors = ref([])
const notice = ref('')
const report = ref(null)
const published = ref(null)
const saved = ref('')
const jobId = computed(() => route.params.jobId || '')
const isNew = computed(() => !jobId.value)
const dirty = computed(() => !loading.value && !loadError.value && Boolean(saved.value) && JSON.stringify(form.value) !== saved.value)
const canPublish = computed(() => report.value?.valid && !dirty.value && !busy.value && !published.value)
const total = computed(() => {
  const units = form.value.percentages.map(weightUnits)
  return units.includes(null) ? 'Invalid' : `${units.reduce((sum, value) => sum + value, 0) / 100000}%`
})
let active = true
let sequence = 0
let manualId = 0
const extractionFailed = ref(false)
const operationKeys = new Map()
function operationKey(action, body) {
  const signature = JSON.stringify([action, jobId.value, body])
  if (!operationKeys.has(signature)) operationKeys.set(signature, crypto.randomUUID())
  return operationKeys.get(signature)
}
function descriptionIssues() {
  if (demoMode) return validateDescription(form.value)
  return form.value.title.trim() && form.value.description.trim()
    ? [] : ['Enter a job title and description.']
}
function applyDraft(draft) {
  form.value = draft.form
  revision.value = draft.revision
  saved.value = JSON.stringify(draft.form)
}

watch(form, () => { report.value = null; notice.value = '' }, { deep: true })

async function run(action) {
  if (busy.value) return
  const request = ++sequence
  const username = auth.demoUser?.username
  busy.value = true
  errors.value = []
  notice.value = ''
  try {
    if (demoMode) await new Promise((resolve) => setTimeout(resolve, 450))
    if (!active || request !== sequence || !auth.hasDemoRole('HR') || auth.demoUser.username !== username) return
    await action()
  } catch (error) { if (active && request === sequence) errors.value = [error.message] }
  finally { if (active && request === sequence) busy.value = false }
}

async function loadDraft() {
  sequence += 1
  busy.value = false
  loading.value = true
  loadError.value = ''
  errors.value = []
  report.value = null
  published.value = null
  extractionFailed.value = false
  const task = run(async () => {
    if (demoMode && hrWizardScenario === 'load-error') throw new Error('Unable to load the draft. Please try again.')
    const draft = isNew.value ? { revision: 0, form: createJobForm() } : await store.readDraft(auth.demoUser, jobId.value)
    applyDraft(draft)
    step.value = route.name === 'HrJobRequirements' ? 2 : route.name === 'HrJobWeights' ? 4 : 0
  })
  const request = sequence
  await task
  if (active && request === sequence) { loadError.value = errors.value[0] || ''; loading.value = false }
}
watch(() => route.params.jobId, loadDraft, { immediate: true })
watch(() => route.name, (name) => {
  if (name === 'HrJobRequirements') step.value = 2
  if (name === 'HrJobWeights') step.value = 4
  if (name === 'HrJobEdit') step.value = 0
})

async function saveCurrent() {
  const descriptionErrors = descriptionIssues()
  if (descriptionErrors.length) throw new Error(descriptionErrors.join(' '))
  if (demoMode && hrWizardScenario === 'save-error') throw new Error('Unable to save the draft. Your inputs have been kept. Please try again.')
  if (demoMode && hrWizardScenario === 'conflict') throw new Error('This draft changed elsewhere. Reload the saved draft before trying again. Your unsaved inputs have been kept.')
  const result = await store.saveDraft(auth.demoUser, jobId.value, form.value, revision.value, (value, invalidated) => {
    revision.value = value
    if (invalidated) {
      for (const row of form.value.requirements) {
        row.reviewed = false
        row.evidenceExcerpt = null
        row.evidenceLocation = null
        row.warnings = ['The description changed. Review this requirement against the new source.']
      }
    }
  })
  if (demoMode) revision.value = result
  else applyDraft(result)
  saved.value = JSON.stringify(form.value)
}
async function createDraft() {
  const issues = descriptionIssues()
  if (issues.length) { errors.value = issues; return }
  await run(async () => {
    if (demoMode && hrWizardScenario === 'save-error') throw new Error('Unable to create the draft. Your inputs have been kept. Please try again.')
    const id = await store.createDraft(auth.demoUser, form.value, demoMode ? undefined : operationKey('create', form.value))
    saved.value = JSON.stringify(form.value)
    await router.replace({ name: 'HrJobEdit', params: { jobId: id } })
  })
}
async function saveDraft() {
  await run(async () => { await saveCurrent(); notice.value = 'Draft saved.' })
}
async function extract() {
  await run(async () => {
    if (demoMode && hrWizardScenario === 'extraction-error' && !extractionFailed.value) {
      extractionFailed.value = true
      throw new Error('Mock extraction failed. Your saved draft is safe. Please retry extraction.')
    }
    if (form.value.requirements.length) throw new Error('Remove the existing requirements before extracting again. This protects your reviewed edits.')
    if (demoMode) {
      await saveCurrent()
      form.value.requirements = extractMockRequirements(form.value.description)
      form.value.extractedDescription = form.value.description
    } else {
      if (dirty.value) await saveCurrent()
      const request = sequence
      const key = crypto.randomUUID()
      try {
        const draft = await extractJob(jobId.value, key, revision.value, () => active && request === sequence)
        if (!draft || !active || request !== sequence) return
        applyDraft(draft)
        extractionFailed.value = false
      } catch (error) {
        extractionFailed.value = true
        throw error
      }
    }
    report.value = null
    step.value = 2
    router.push({ name: 'HrJobRequirements', params: { jobId: jobId.value } })
  })
}
function addRequirement(category) {
  form.value.requirements.push({ id: demoMode ? `manual-${Date.now()}-${++manualId}` : `requirement-${crypto.randomUUID()}`, category, text: '', equivalents: '',
    ...(!demoMode && { payloadText: '{}', warnings: [] }),
    assessmentIncluded: true, mandatory: false, publicVisible: true, reviewed: false, evidenceExcerpt: '', evidenceLocation: '' })
}
function removeRequirement(row) {
  form.value.requirements = form.value.requirements.filter((item) => item.id !== row.id)
  if (!form.value.requirements.length) form.value.extractedDescription = ''
}
function goStep(index) {
  if (index === 0) router.push({ name: 'HrJobEdit', params: { jobId: jobId.value } })
  else if (index === 2) router.push({ name: 'HrJobRequirements', params: { jobId: jobId.value } })
  else if (index === 4) router.push({ name: 'HrJobWeights', params: { jobId: jobId.value } })
  step.value = index
  errors.value = []
}
function next() {
  const issues = step.value === 0 ? descriptionIssues()
    : step.value === 2 ? (!form.value.requirements.length || form.value.requirements.some((row) => (demoMode && !row.text.trim()) || !row.reviewed)
      ? ['Check each requirement against its source and confirm its review before continuing.'] : [])
      : step.value === 4 ? validateWeights(form.value) : []
  if (issues.length) { errors.value = issues; return }
  goStep(step.value + 1)
}
async function validate() {
  await run(async () => {
    await saveCurrent()
    if (demoMode && hrWizardScenario === 'validation-error') throw new Error('Mock validation is unavailable. Publication remains disabled. Please retry.')
    report.value = demoMode ? validateJob((await store.readDraft(auth.demoUser, jobId.value)).form) : await validateConnectedJob(jobId.value)
    errors.value = report.value.errors.map(issue => typeof issue === 'string' ? issue : issue.message)
    if (report.value.valid) notice.value = 'Validation passed.'
  })
}
async function publish() {
  if (!canPublish.value) return
  await run(async () => {
    if (demoMode && hrWizardScenario === 'publish-error') throw new Error('Unable to publish the mock version. The saved draft is still editable. Please retry.')
    published.value = await store.publishDraft(auth.demoUser, jobId.value, revision.value, demoMode ? undefined : operationKey('publish', revision.value))
    notice.value = `Version ${published.value.version} published.`
  })
}
async function reloadDraft() {
  if (dirty.value) {
    try { await ElMessageBox.confirm(t('Discard your unsaved inputs and reload the saved draft?'), t('Reload Draft'), { confirmButtonText: t('Reload'), cancelButtonText: t('Keep Editing'), type: 'warning' }) }
    catch { return }
  }
  await loadDraft()
}
async function checkLeave(to) {
  if (to.params.jobId === jobId.value && ['HrJobEdit', 'HrJobRequirements', 'HrJobWeights'].includes(to.name)) return true
  if (!dirty.value || published.value || to.name === 'Login') return true
  try { await ElMessageBox.confirm(t('Leave without saving your changes?'), t('Unsaved Changes'), { confirmButtonText: t('Leave'), cancelButtonText: t('Keep Editing'), type: 'warning' }); return true }
  catch { return false }
}
onBeforeRouteLeave(checkLeave)
onBeforeRouteUpdate(checkLeave)
onBeforeUnmount(() => { active = false; sequence += 1 })
</script>

<template>
  <HrLayout>
    <section aria-labelledby="wizard-title" :aria-busy="busy || loading">
      <div class="page-heading"><div><h1 id="wizard-title">{{ $t(isNew ? 'Create New Job' : 'Job Configuration Wizard') }}</h1><p>{{ isNew ? $t('Start a new job draft.') : $t('{title} · Revision {revision}', { title: form.title || $t('Job draft'), revision }) }}</p></div>
        <ElTag v-if="!isNew" :type="published ? 'success' : 'info'">{{ $t(published ? 'Published' : 'Draft') }}</ElTag></div>
      <ElAlert v-if="demoMode" type="info" :closable="false" :title="$t(&quot;Frontend demonstration only. Saved drafts and publication metadata survive sign out and reload in this browser tab. Extraction and validation are local mocks; no API or AI service is called.&quot;)" />
      <ol class="steps" :aria-label="$t(&quot;Job configuration steps&quot;)"><li v-for="(label, index) in steps" :key="label" :class="{ current: step === index, complete: step > index }" :aria-current="step === index ? 'step' : undefined"><span>{{ index + 1 }}</span><b class="step-name">{{ $t(label) }}</b></li></ol>
      <div v-if="loading" class="panel" role="status"><p>{{ $t("Loading job draft...") }}</p><ElSkeleton :rows="6" animated /></div>
      <div v-else-if="loadError" class="panel" role="alert"><h2>{{ $t("Draft Unavailable") }}</h2><p>{{ $t(loadError) }}</p><ElButton @click="loadDraft">{{ $t("Try Again") }}</ElButton><ElButton @click="router.push({ name: 'HrJobs' })">{{ $t("Back to Jobs") }}</ElButton></div>
      <div v-else class="panel">
        <div v-if="errors.length" class="messages" role="alert"><h3>{{ $t("Please Review") }}</h3><ul><li v-for="error in errors" :key="error">{{ $t(error) }}</li></ul></div>
        <ElAlert v-if="notice" :title="$t(notice)" type="success" :closable="false" class="notice" />
        <template v-if="published"><h2>{{ $t("Job Published") }}</h2><p>{{ form.title }} {{ $t("· Version") }} {{ published.version }} {{ $t("is now read-only.") }}</p><p>{{ $t("View the published configuration in Job Versions. The current public version is also available in Candidate Published Jobs.") }}</p><ElButton type="primary" @click="router.push({ name: 'HrJobVersions', params: { jobId } })">{{ $t("View Job Versions") }}</ElButton><ElButton @click="router.push({ name: 'HrJobs' })">{{ $t("Back to Jobs") }}</ElButton></template>
        <template v-else>
          <p class="step-label">{{ $t("Step") }} {{ step + 1 }} {{ $t("of 7") }}</p><h2>{{ steps[step] }}</h2>
          <fieldset :disabled="busy" :inert="busy" class="editor">
            <div v-if="step === 0" class="basic-grid">
              <label>{{ $t("Job Title") }} <span class="required">*</span><ElInput v-model="form.title" :aria-label="$t(&quot;Job title&quot;)" :maxlength="demoMode ? 100 : 255" show-word-limit :placeholder="$t(&quot;Enter a job title&quot;)" /></label>
              <label v-if="demoMode">{{ $t("Department") }} <span class="required">*</span><ElInput v-model="form.department" :aria-label="$t(&quot;Department&quot;)" maxlength="100" :placeholder="$t(&quot;Enter a department&quot;)" /></label>
              <label v-if="demoMode">{{ $t("Employment Type") }} <span class="required">*</span><ElSelect v-model="form.employmentType" :aria-label="$t(&quot;Employment type&quot;)"><ElOption v-for="option in ['Full-time', 'Part-time', 'Internship', 'Contract']" :key="option" :label="$t(option)" :value="option" /></ElSelect></label>
              <label v-if="demoMode">{{ $t("Experience Level") }} <span class="required">*</span><ElSelect v-model="form.experienceLevel" :aria-label="$t(&quot;Experience level&quot;)"><ElOption v-for="option in ['Junior (0-2 years)', 'Mid-level (3-5 years)', 'Senior (6+ years)']" :key="option" :label="$t(option)" :value="option" /></ElSelect></label>
              <label class="description">{{ $t("Job Description") }} <span class="required">*</span><ElInput v-model="form.description" :aria-label="$t(&quot;Job description&quot;)" type="textarea" :rows="7" :maxlength="demoMode ? 2000 : 20000" show-word-limit :placeholder="$t(&quot;Describe the role and its professional requirements.&quot;)" /></label>
              <label>{{ $t("Work location (country, city or remote)") }}<ElInput v-model="form.location" :aria-label="$t(&quot;Location&quot;)" :maxlength="demoMode ? 100 : 200" :placeholder="$t(&quot;For example: Paris, France&quot;)" /></label>
              <label v-if="demoMode">{{ $t("Number of Positions") }}<ElInputNumber v-model="form.positions" :aria-label="$t(&quot;Number of positions&quot;)" :min="1" :max="100" :precision="0" /></label>
            </div>
            <div v-if="step === 1"><p>{{ $t("Group sentences from the saved description into proposed professional requirements. Review every proposal before publication.") }}</p><ElAlert v-if="demoMode" type="warning" :closable="false" :title="$t(&quot;Local mock: simple keyword grouping can include irrelevant company text or use the wrong category. Check the source, correct the text, and remove company-only statements.&quot;)" /><blockquote>{{ form.description }}</blockquote><ElButton type="primary" :loading="busy" @click="extract">{{ $t(extractionFailed ? 'Retry Extraction' : 'Extract Requirements') }}</ElButton><ElButton @click="goStep(2)">{{ $t("Review Manually") }}</ElButton></div>
            <div v-if="step === 2" class="groups"><p>{{ $t("Required/optional criteria, assessment inclusion and Candidate visibility are independent. Confirm each reviewed row; remove irrelevant proposals.") }}</p>
              <section v-for="category in criteria" :key="category" class="group" :aria-label="$t('{category} requirements', { category: $t(category) })"><div class="group-heading"><h3>{{ $t(category) }}</h3><ElButton size="small" @click="addRequirement(category)">{{ $t("Add") }} {{ $t(category) }} {{ $t("Requirement") }}</ElButton></div>
                <p v-if="!form.requirements.some(row => row.category === category)">{{ $t("No requirements in this group.") }}</p>
                <article v-for="row in form.requirements.filter(item => item.category === category)" :key="row.id" class="requirement">
                  <label v-if="demoMode">{{ $t("Requirement") }}<ElInput v-model="row.text" :aria-label="row.id + ' ' + $t('Requirement')" type="textarea" :rows="2" maxlength="2000" @update:model-value="row.reviewed = false" /></label>
                  <label>{{ $t("Category") }}<ElSelect v-model="row.category" :aria-label="row.id + ' ' + $t('Category')" @update:model-value="row.reviewed = false"><ElOption v-for="option in criteria" :key="option" :label="$t(option)" :value="option" /></ElSelect></label>
                  <label v-if="demoMode">{{ $t("Accepted Equivalents (optional)") }}<ElInput v-model="row.equivalents" :aria-label="row.id + ' ' + $t('Accepted Equivalents (optional)')" maxlength="500" @update:model-value="row.reviewed = false" /></label>
                  <template v-if="!demoMode">
                    <RequirementPayloadEditor v-model="row.payloadText" :category="row.category" @update:model-value="row.reviewed = false" />
                    <p class="hint">{{ $t("Pick codes from the configured matching rule, or type a new one.") }} {{ $t("Requirement code:") }} {{ row.id }}</p>
                    <ul v-if="row.warnings.length"><li v-for="warning in row.warnings" :key="warning">{{ $t(warning) }}</li></ul>
                  </template>
                  <details v-if="row.evidenceExcerpt"><summary>{{ $t("Source Evidence") }}</summary><p>{{ row.evidenceLocation }}</p><blockquote>{{ row.evidenceExcerpt }}</blockquote></details><p v-else class="hint">{{ $t("Manually added requirement. Check it against the job description.") }}</p>
                  <div class="checks"><ElCheckbox v-model="row.assessmentIncluded">{{ $t("Include in assessment") }}</ElCheckbox><ElCheckbox v-model="row.publicVisible">{{ $t("Visible to Candidates") }}</ElCheckbox><ElCheckbox v-model="row.reviewed" @change="value => { if (!demoMode && value) row.warnings = [] }">{{ $t("Reviewed against source") }}</ElCheckbox></div><ElButton size="small" type="danger" plain @click="removeRequirement(row)">{{ $t("Remove Requirement") }}</ElButton>
                </article>
              </section>
            </div>
            <div v-if="step === 3"><p>{{ $t("A mandatory condition is a separate gate. Select only reviewed requirements included in an active assessment category. No mandatory conditions is allowed.") }}</p>
              <div v-for="row in form.requirements" :key="row.id" class="mandatory-row"><ElCheckbox v-model="row.mandatory" :disabled="!row.assessmentIncluded || !row.reviewed || !form.activeCriteria.includes(row.category)">{{ row.category }}: {{ $t(row.text || 'Untitled requirement') }}</ElCheckbox><ElTag :type="row.mandatory ? 'warning' : 'info'">{{ $t(row.mandatory ? 'Mandatory' : 'Optional') }}</ElTag></div>
            </div>
            <div v-if="step === 4"><p>{{ $t("Set active categories and percentage weights. Inactive means not assessed and requires zero weight. Active categories must have reviewed, assessed requirements.") }}</p>
              <div v-for="(category, index) in criteria" :key="category" class="weight-row"><ElCheckbox :model-value="form.activeCriteria.includes(category)" @update:model-value="value => form.activeCriteria = value ? [...form.activeCriteria, category] : form.activeCriteria.filter(item => item !== category)">{{ $t(category) }} {{ $t("active") }}</ElCheckbox><label>{{ $t(category) }} {{ $t("Weight (%)") }}<ElInput v-model="form.percentages[index]" :aria-label="$t('{category} weight percentage', { category: $t(category) })" inputmode="decimal" /></label></div>
              <p class="total" role="status">{{ $t("Total:") }} {{ total }}</p><p class="hint">{{ $t("No automatic normalization. Up to five percentage decimal places are accepted; published weights use fractions with seven decimal places.") }}</p>
            </div>
            <div v-if="step === 5"><p>{{ $t("Save and validate this configuration before publication.") }}</p><ElButton type="primary" :loading="busy" @click="validate">{{ $t("Validate Draft") }}</ElButton><p v-if="report?.valid">{{ $t("Validation passed for revision") }} {{ revision }}.</p><ul v-if="report?.warnings.length"><li v-for="warning in report.warnings" :key="warning">{{ $t(warning) }}</li></ul></div>
            <div v-if="step === 6"><p>{{ $t("Review the fixed configuration. Publishing revalidates the saved draft and creates a read-only version.") }}</p><dl><dt>{{ $t("Job Title") }}</dt><dd>{{ form.title }}</dd><dt v-if="demoMode">{{ $t("Department") }}</dt><dd v-if="demoMode">{{ form.department }}</dd><dt v-if="demoMode">{{ $t("Employment Type") }}</dt><dd v-if="demoMode">{{ form.employmentType }}</dd><dt v-if="demoMode">{{ $t("Experience Level") }}</dt><dd v-if="demoMode">{{ form.experienceLevel }}</dd><dt>{{ $t("Location") }}</dt><dd>{{ form.location || $t('Not specified') }}</dd><dt v-if="demoMode">{{ $t("Positions") }}</dt><dd v-if="demoMode">{{ form.positions }}</dd><dt>{{ $t("Requirements") }}</dt><dd>{{ form.requirements.length }} {{ $t("reviewed rows;") }} {{ form.requirements.filter(row => row.mandatory).length }} {{ $t("mandatory conditions") }}</dd><dt>{{ $t("Weights") }}</dt><dd><span v-for="(category, index) in criteria" :key="category">{{ $t(category) }}: {{ form.percentages[index] }}%{{ $t(form.activeCriteria.includes(category) ? '' : ' (not assessed)') }}<br /></span></dd></dl><details class="publish-preview"><summary>{{ $t("Review Description and Requirements") }}</summary><blockquote>{{ form.description }}</blockquote><article v-for="row in form.requirements" :key="row.id" class="requirement"><h3>{{ row.category }}</h3><p>{{ row.text }}</p><p v-if="row.equivalents">{{ $t("Accepted equivalents:") }} {{ row.equivalents }}</p><p>{{ $t(row.assessmentIncluded ? 'Assessed' : 'Excluded from assessment') }} · {{ $t(row.mandatory ? 'Mandatory' : 'Optional') }} · {{ $t(row.publicVisible ? 'Visible to Candidates' : 'Hidden from Candidates') }}</p></article></details><ElAlert v-if="!canPublish && !busy" type="warning" :closable="false" :title="$t(&quot;Validate the saved draft before publishing. Any change invalidates the previous validation.&quot;)" /><ElButton type="success" :disabled="!canPublish" :loading="busy" @click="publish">{{ $t("Publish Job") }}</ElButton></div>
          </fieldset>
          <div class="actions"><div><ElButton :disabled="busy" @click="router.push({ name: 'HrJobs' })">{{ $t("Cancel") }}</ElButton><ElButton v-if="!isNew" :disabled="busy" @click="reloadDraft">{{ $t("Reload Draft") }}</ElButton></div>
            <div><ElButton v-if="!isNew" :loading="busy" :disabled="busy" @click="saveDraft">{{ $t("Save Draft") }}</ElButton><ElButton v-if="step > 0" :disabled="busy" @click="goStep(step - 1)">{{ $t("Previous") }}</ElButton><ElButton v-if="isNew" type="primary" :loading="busy" @click="createDraft">{{ $t("Create Draft") }}</ElButton><ElButton v-else-if="step < 6 && step !== 1" type="primary" :disabled="busy || (step === 5 && !report?.valid)" @click="next">{{ $t("Next:") }} {{ steps[step + 1] }}</ElButton></div>
          </div><p class="hint">{{ $t(dirty ? 'Unsaved changes' : 'No unsaved changes') }} {{ $t("· Published versions cannot be edited directly.") }}</p>
        </template>
      </div>
    </section>
  </HrLayout>
</template>

<style scoped>
h1 { margin: 0; color: var(--sr-ink); font-size: 28px; letter-spacing: -.7px; } h2 { color: var(--sr-ink); margin-top: 8px; } h3 { font-size: 16px; color: var(--sr-ink); }
p, li, dd { color: var(--sr-text-muted); font-size: 14px; line-height: 1.8; } .page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.panel { background: white; border: 1px solid var(--sr-border); border-radius: 12px; padding: 24px; } .steps { list-style: none; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); padding: 0; margin: 28px 0; gap: 8px; }
.step-name { font-weight: inherit; } .steps li { text-align: center; font-size: 12px; line-height: 1.4; overflow-wrap: anywhere; } .steps span { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; border: 1px solid var(--sr-border-strong); border-radius: 50%; margin: 0 auto 10px; background: white; }
.steps .current { color: var(--sr-primary); font-weight: 600; } .steps .current span { color: white; background: var(--sr-primary); border-color: var(--sr-primary); } .steps .complete span { border-color: var(--sr-primary); color: var(--sr-primary); }
.step-label, .hint { font-size: 12px; } .editor { padding: 0; margin: 20px 0; border: 0; min-width: 0; } label { display: grid; gap: 8px; color: var(--sr-ink); font-size: 13px; margin-bottom: 18px; } .required { color: #d14343; display: contents; }
.basic-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; } .description { grid-column: 1 / -1; } .el-select, .el-input-number { width: 100%; }
.actions, .actions > div, .group-heading { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; justify-content: space-between; } .el-button { margin: 0; max-width: 100%; } .actions { border-top: 1px solid var(--sr-border); padding-top: 20px; }
.messages { background: #fff3f2; color: #b83232; padding: 12px 20px; border-radius: 8px; margin-bottom: 20px; } .messages li { color: #b83232; } .notice { margin-bottom: 20px; }
.group { border-top: 1px solid var(--sr-border); margin-top: 24px; } .requirement { background: var(--sr-surface-tint); border: 1px solid var(--sr-border); padding: 18px; border-radius: 8px; margin: 12px 0; } .checks { display: flex; flex-wrap: wrap; gap: 0 20px; margin: 14px 0; }
blockquote { margin: 18px 0; padding: 14px; background: var(--sr-primary-50); color: var(--sr-text-muted); font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; } summary { color: var(--sr-primary); cursor: pointer; font-size: 13px; }
.mandatory-row, .weight-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; border-bottom: 1px solid var(--sr-border); padding: 14px 0; } .weight-row label { width: 200px; margin: 0; }
.mandatory-row :deep(.el-checkbox) { height: auto; min-width: 0; white-space: normal; } .mandatory-row :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; line-height: 1.8; } .mandatory-row .el-tag { flex-shrink: 0; }
.publish-preview { margin: 20px 0; } .total { font-weight: 600; color: var(--sr-primary); } dl { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 12px; font-size: 14px; } dt { color: var(--sr-ink); } dd { margin: 0; overflow-wrap: anywhere; }
@media (max-width: 760px) { .panel { padding: 20px 16px; } .steps li { font-size: 10px; } .steps { gap: 3px; } .steps span { width: 24px; height: 24px; } h1 { font-size: 25px; } }
@media (max-width: 600px) { .step-name { display: none; } .basic-grid { grid-template-columns: minmax(0, 1fr); } .weight-row { flex-wrap: wrap; } .weight-row label { width: 100%; } dl { grid-template-columns: minmax(0, 1fr); gap: 6px; } dd { margin-bottom: 12px; } .mandatory-row { align-items: flex-start; } }
</style>
