<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElButton } from 'element-plus'
import { formatCvSize, validateCvFile } from '../../utils/cv-validation.js'

defineProps({ disabled: Boolean, selected: { type: Object, default: null } })
const emit = defineEmits(['select'])
const input = ref()
const checking = ref(false)
const error = ref('')
let selection = 0

async function chooseFiles(files) {
  const current = ++selection
  emit('select', null)
  error.value = ''
  if (!files.length) return
  if (files.length !== 1) {
    error.value = 'Choose one PDF at a time.'
    return
  }
  checking.value = true
  try {
    const metadata = await validateCvFile(files[0])
    if (current === selection) emit('select', metadata)
  } catch (failure) {
    if (current === selection) error.value = failure.message
  } finally {
    if (current === selection) checking.value = false
    if (input.value) input.value.value = ''
  }
}

onBeforeUnmount(() => { selection += 1 })
</script>

<template>
  <div class="upload-card">
    <div class="drop-zone" @dragover.prevent @drop.prevent="!disabled && !checking && chooseFiles($event.dataTransfer.files)">
      <svg class="upload-icon" viewBox="0 0 64 64" aria-hidden="true"><path d="M18 44H13a11 11 0 0 1-1-22 20 20 0 0 1 38-4 13 13 0 0 1 2 26h-6" fill="#d6e8ff" /><path d="M32 50V25m-9 9 9-9 9 9" fill="none" stroke="#176bdf" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" /></svg>
      <h2>Drag and drop your CV here</h2>
      <p>Choose one PDF, up to 10 MB and 20 pages.<br />Encrypted PDFs are not supported.</p>
      <input ref="input" class="file-input" type="file" accept=".pdf,application/pdf"
        aria-label="Choose a PDF CV" :disabled="disabled || checking" @change="chooseFiles($event.target.files)" />
      <ElButton type="primary" :loading="checking" :disabled="disabled || checking"
        @click="input.click()">{{ checking ? 'Checking PDF...' : selected ? 'Replace File' : 'Choose File' }}</ElButton>
    </div>
    <p v-if="error" class="file-error" role="alert">{{ error }}</p>
    <div v-if="selected" class="selected-file" role="status">
      <strong>{{ selected.name }}</strong>
      <span>{{ formatCvSize(selected.size) }} · {{ selected.pageCount }} {{ selected.pageCount === 1 ? 'page' : 'pages' }}</span>
      <span>PDF checks passed. Ready for the frontend demo.</span>
      <ElButton text :disabled="disabled || checking" @click="emit('select', null)">Remove File</ElButton>
    </div>
  </div>
</template>

<style scoped>
.drop-zone { padding: 30px 20px; border: 2px dashed #b9d3f5; border-radius: 10px; text-align: center; background: #f9fcff; }
.upload-icon { width: 64px; height: 64px; margin-bottom: 14px; }
h2 { margin: 0; color: #20354e; font-size: 17px; line-height: 1.5; }
p { color: #586b83; font-size: 13px; line-height: 1.8; margin: 12px 0 18px; }
.file-input { display: none; }
.file-error { margin-bottom: 0; padding: 12px; background: #fff3f3; border-radius: 6px; color: #b42318; }
.selected-file { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; padding: 18px; margin-top: 16px; background: white; border: 1px solid #e1eaf5; border-radius: 8px; font-size: 13px; color: #586b83; }
.selected-file strong { color: #20354e; overflow-wrap: anywhere; max-width: 100%; }
.selected-file .el-button { padding: 0; }
</style>
