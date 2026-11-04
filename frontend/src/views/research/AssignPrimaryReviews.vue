<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus'
import ResearchLayout from '../../layouts/ResearchLayout.vue'
import { assignPrimaryReviews, eligibleReviewers } from '../../api/reviews.js'

const route = useRoute()
const caseId = ref(typeof route.query.caseId === 'string' ? route.query.caseId : '')
const query = ref('')
const reviewers = ref([])
const selected = ref({ STANDARD: '', INSTRUCTED_BIAS: '' })
const conditions = { STANDARD: 'Standard', INSTRUCTED_BIAS: 'Instructed Bias' }
const target = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const assigned = ref(null)
let sequence = 0
let pending
const assignments = computed(() => Object.entries(selected.value)
  .filter(([, reviewerId]) => reviewerId)
  .map(([condition, reviewerId]) => ({ condition, reviewerId })))

async function search() {
  if (saving.value) return
  const id = caseId.value.trim()
  if (!id) {
    error.value = 'Enter a Case ID.'
    return
  }
  const request = ++sequence
  loading.value = true
  error.value = ''
  target.value = ''
  reviewers.value = []
  selected.value = { STANDARD: '', INSTRUCTED_BIAS: '' }
  try {
    const rows = await eligibleReviewers(id, query.value.trim())
    if (request !== sequence) return
    target.value = id
    reviewers.value = rows
  } catch (failure) {
    if (request === sequence) error.value = failure.message
  } finally {
    if (request === sequence) loading.value = false
  }
}

async function submit() {
  if (saving.value || !target.value || !assignments.value.length) return
  const request = sequence
  saving.value = true
  error.value = ''
  const signature = JSON.stringify([target.value, assignments.value])
  if (pending?.signature !== signature) pending = { signature, key: crypto.randomUUID() }
  try {
    const result = await assignPrimaryReviews(target.value, assignments.value, pending.key)
    pending = undefined
    if (request !== sequence) return
    assigned.value = result.tasks
    target.value = ''
  } catch (failure) {
    if (request === sequence) error.value = failure.message
  } finally {
    if (request === sequence) saving.value = false
  }
}

watch(caseId, () => {
  sequence++
  loading.value = false
  target.value = ''
  error.value = ''
  assigned.value = null
})
onBeforeUnmount(() => { sequence++ })
</script>

<template>
  <ResearchLayout>
    <h1>Assign Primary Reviews</h1>
    <p>Enter the ID of a confirmed case you have research access to. Choose an eligible reviewer for one or both conditions.</p>
    <ElForm label-position="top" :disabled="saving" novalidate @submit.prevent="search">
      <ElFormItem label="Case ID" required><ElInput v-model="caseId" maxlength="26" aria-required="true" /></ElFormItem>
      <ElFormItem label="Reviewer username (optional)"><ElInput v-model="query" maxlength="100" /></ElFormItem>
      <ElButton native-type="submit" :loading="loading">Find Eligible Reviewers</ElButton>
    </ElForm>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="target && !reviewers.length">No eligible reviewers match your search.</p>
    <ElForm v-if="target && reviewers.length" class="assignment" label-position="top" :disabled="saving" @submit.prevent="submit">
      <ElFormItem v-for="(label, condition) in conditions" :key="condition" :label="label">
        <ElSelect v-model="selected[condition]" clearable placeholder="Choose a reviewer">
          <ElOption v-for="reviewer in reviewers" :key="reviewer.userId" :label="reviewer.displayName" :value="reviewer.userId" />
        </ElSelect>
      </ElFormItem>
      <ElButton native-type="submit" type="primary" :disabled="!assignments.length" :loading="saving">Assign Reviews</ElButton>
    </ElForm>
    <section v-if="assigned" role="status">
      <h2>Assigned Reviews</h2>
      <ul><li v-for="task in assigned" :key="task.reviewId">{{ conditions[task.condition] }} · {{ task.reviewId }} · {{ task.status }}</li></ul>
    </section>
  </ResearchLayout>
</template>

<style scoped>
.el-form { max-width: 540px; margin: 20px 0; }
</style>
