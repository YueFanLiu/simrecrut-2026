<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElRadio, ElRadioGroup, ElSkeleton, ElTag } from 'element-plus'
import HrLayout from '../../../layouts/HrLayout.vue'
import ResearchLayout from '../../../layouts/ResearchLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { useResearchModuleStore } from '../../../store/modules/research-module.js'
import { useReviewTasksStore } from '../../../store/modules/review-tasks.js'
import { reviewDetailScenario } from '../../../config/demo.js'

const route = useRoute(), router = useRouter(), auth = useAuthStore(), research = useResearchModuleStore(), tasks = useReviewTasksStore()
const loaded = ref(false), loading = ref(true), loadError = ref(''), submitError = ref(''), submitting = ref(false), confirm = ref(false), clock = ref(Date.now()), formRef = ref()
const form = reactive({ decision: '', reason: '' })
const rules = {
  decision: [{ required: true, message: 'Select Accept or Reject.', trigger: 'change' }],
  reason: [{ validator: (_, value, callback) => callback(typeof value === 'string' && value.trim().length >= 1 && value.trim().length <= 2000 ? undefined : new Error('Enter a reason of 1 to 2000 characters.')), trigger: 'blur' }],
}
const detail = computed(() => {
  if (!loaded.value) return null
  try { return tasks.readDetail(String(route.params.reviewId), auth.demoUser, research, clock.value) }
  catch { return null }
})
const labels = { experience: 'Work Experience', education: 'Education', skills: 'Skills', projects: 'Projects', languages: 'Languages' }
const instructionMissing = computed(() => detail.value?.controlled?.instructionAvailable === false)
let active = true, sequence = 0
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
watch(detail, value => { if (!value || value.status === 'Submitted') { confirm.value = false; form.decision = ''; form.reason = '' } })
async function load() {
  const request = ++sequence, id = String(route.params.reviewId), user = auth.demoUser
  const current = () => active && sequence === request && route.params.reviewId === id && auth.demoUser === user
  submitting.value = false; loaded.value = false; loading.value = true; loadError.value = ''; submitError.value = ''; confirm.value = false
  form.decision = ''; form.reason = ''
  try { await tasks.loadDetail(id, user, research, reviewDetailScenario); if (current()) { loaded.value = true; clock.value = Date.now() } }
  catch (failure) { if (current()) loadError.value = failure.message }
  finally { if (current()) loading.value = false }
}
async function openConfirmation() {
  submitError.value = ''
  if (!await formRef.value.validate().catch(() => false)) return
  clock.value = Date.now()
  if (detail.value?.status === 'Pending' && !instructionMissing.value) confirm.value = true
}
async function submit() {
  if (submitting.value || !confirm.value) return
  const request = sequence, user = auth.demoUser, id = String(route.params.reviewId)
  submitting.value = true; submitError.value = ''
  try { await tasks.submitReview(id, { ...form }, user, research, reviewDetailScenario) }
  catch (failure) { if (active && sequence === request && auth.demoUser === user) submitError.value = failure.message }
  finally { if (active && sequence === request && auth.demoUser === user) { submitting.value = false; confirm.value = false; clock.value = Date.now() } }
}
watch(() => route.params.reviewId, load)
onBeforeUnmount(() => { active = false; sequence += 1; clearInterval(timer) })
load()
</script>

<template>
  <component :is="auth.hasDemoRole('HR') ? HrLayout : ResearchLayout">
    <section aria-labelledby="review-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Reviews / My Review Tasks / {{ route.params.reviewId }}</p><h1 id="review-title">Review Candidate Profile</h1><p>Evaluate the anonymised professional facts against the public job requirements.</p></div><ElButton @click="router.push({ name: 'MyReviewTasks' })">Back to Tasks</ElButton></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration only. Your review is independent. Model scores, hiring thresholds and other reviewers' decisions are hidden." />
      <div v-if="loading" class="panel state" role="status"><p>Loading review task...</p><ElSkeleton :rows="8" animated /></div>
      <div v-else-if="loadError || !detail" class="panel state" role="alert"><h2>Review Task Unavailable</h2><p>{{ loadError || 'This task is unavailable or access is no longer authorised.' }}</p><ElButton @click="load">Try Again</ElButton></div>
      <template v-else>
        <ol class="steps" aria-label="Review workflow"><li>1. Read Profile</li><li>2. Evaluate</li><li>{{ detail.status === 'Submitted' ? '3. Submitted' : '3. Confirm and Submit' }}</li></ol>
        <div class="review-grid">
          <article class="panel profile"><h2>Candidate Profile (Anonymised)</h2><p>Personal identity information is withheld. All facts below are mock data.</p><section v-for="(label, key) in labels" :key="key"><h3>{{ label }}</h3><p>{{ detail.professional[key] }}</p></section></article>
          <article class="panel evaluation"><h2>Your Evaluation</h2>
            <section v-if="detail.controlled" class="instruction"><h3>Assigned Instructed-Bias Condition</h3><p>{{ detail.controlled.attribute }}: {{ detail.controlled.value }}</p><p>{{ detail.controlled.instruction }}</p></section>
            <template v-if="detail.status === 'Submitted'"><ElAlert type="success" :closable="false" title="Review submitted. Your decision is locked and cannot be changed." /><div v-if="detail.submission" class="own-submission"><h3>Your Decision</h3><ElTag>{{ detail.submission.decision }}</ElTag><h3>Your Reason</h3><p>{{ detail.submission.reason }}</p><p v-if="detail.submission.submittedAt">Submitted: {{ new Date(detail.submission.submittedAt).toUTCString() }}</p></div><p v-else>Your original decision and reason are unavailable. This task remains locked.</p></template>
            <ElForm v-else ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="submitting || confirm || instructionMissing" @submit.prevent="openConfirmation">
              <ElFormItem label="Overall Decision" prop="decision"><ElRadioGroup v-model="form.decision" aria-label="Overall Decision"><ElRadio value="Accept">Accept</ElRadio><ElRadio value="Reject">Reject</ElRadio></ElRadioGroup></ElFormItem>
              <ElFormItem label="Reason" prop="reason"><ElInput v-model="form.reason" type="textarea" :rows="7" maxlength="2000" show-word-limit aria-label="Reason" placeholder="Explain the relevant professional evidence or gaps (1 to 2000 characters)." /></ElFormItem>
              <p>Submission requires confirmation. Once confirmed, your decision and reason cannot be changed.</p><ElAlert v-if="submitError" type="error" :closable="false" :title="submitError" /><ElButton type="primary" native-type="submit" :loading="submitting" :disabled="submitting || confirm || instructionMissing">Submit Decision</ElButton>
            </ElForm>
          </article>
          <aside class="context"><article class="panel"><h2>Task Information</h2><dl><dt>Task ID</dt><dd>{{ detail.reviewId }}</dd><dt>Experiment</dt><dd>{{ detail.experimentId || 'No research experiment' }}</dd><dt>Condition</dt><dd>{{ detail.condition }}</dd><dt>Purpose</dt><dd>{{ detail.purpose }}</dd><dt>Job</dt><dd>{{ detail.job }}</dd><dt>Assigned Date (UTC)</dt><dd>{{ new Date(detail.assignedAt).toUTCString() }}</dd><dt>Status</dt><dd><ElTag :type="detail.status === 'Submitted' ? 'success' : 'warning'">{{ detail.status }}</ElTag></dd></dl></article><article class="panel"><h2>Public Job Requirements</h2><h3>{{ detail.job }}</h3><ul><li v-for="requirement in detail.requirements" :key="requirement">{{ requirement }}</li></ul></article><article class="panel"><h2>Review Guidelines / Rubric</h2><ul><li v-for="guideline in detail.rubric" :key="guideline">{{ guideline }}</li></ul></article></aside>
        </div>
      </template>
      <ElDialog v-model="confirm" title="Confirm Review Submission" width="min(520px, calc(100vw - 32px))" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting" :before-close="done => { if (!submitting) done() }">
        <p>Submit this independent review for {{ detail?.reviewId }}? This action locks your decision and reason.</p><h3>{{ form.decision }}</h3><p class="confirmation-reason">{{ form.reason.trim() }}</p><template #footer><ElButton :disabled="submitting" @click="confirm = false">Cancel</ElButton><ElButton type="primary" :loading="submitting" :disabled="submitting" @click="submit">Confirm Submission</ElButton></template>
      </ElDialog>
    </section>
  </component>
</template>

<style scoped>
h1 { color: #132941; font-size: 28px; margin: 0; letter-spacing: -.7px; } h2 { color: #132941; font-size: 18px; margin: 0 0 16px; } h3 { color: #176bdf; font-size: 14px; } p, li, dl { color: #586b83; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; } .heading { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 20px; } .breadcrumb { font-size: 12px; } .panel { border: 1px solid #e1eaf5; background: white; border-radius: 12px; padding: 20px; min-width: 0; } .state { margin-top: 24px; text-align: center; } .steps { padding: 20px 0; list-style: none; display: flex; flex-wrap: wrap; justify-content: space-around; gap: 10px; } .steps li { color: #176bdf; } .review-grid { display: grid; grid-template-columns: minmax(0,1.2fr) minmax(0,1fr) minmax(0,.9fr); align-items: start; gap: 16px; } .profile section { margin-top: 24px; } .profile h3 { background: #f0f6ff; padding: 10px; border-radius: 5px; } .context { display: grid; gap: 16px; } dl { margin-bottom: 0; } dt { font-weight: 600; color: #132941; } dd { margin: 0 0 10px; } ul { padding-left: 20px; margin-bottom: 0; } .instruction { background: #fff8e8; padding: 12px; border-radius: 6px; margin-bottom: 20px; } .evaluation .el-button { margin-top: 16px; } .evaluation :deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: 10px; } .own-submission p, .confirmation-reason { white-space: pre-wrap; } .confirmation-reason { max-height: 240px; overflow-y: auto; } .el-form { margin-top: 20px; }
@media (max-width: 1200px) { .review-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } .context { grid-column: 1 / -1; grid-template-columns: repeat(3,minmax(0,1fr)); } }
@media (max-width: 850px) { .review-grid, .context { grid-template-columns: minmax(0,1fr); } .context { grid-column: auto; } h1 { font-size: 25px; } }
</style>
