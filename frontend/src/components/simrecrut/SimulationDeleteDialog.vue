<script setup>
import { demoMode } from '../../config/runtime.js'
import { ElButton, ElDialog } from 'element-plus'

defineProps({ modelValue: Boolean, caseId: { type: String, default: '' } })
defineEmits(['update:modelValue', 'confirm'])
</script>

<template>
  <ElDialog :model-value="modelValue" title="Delete your simulation data?"
    width="min(480px, calc(100% - 32px))" :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)">
    <p v-if="caseId">Case: {{ caseId }}</p>
    <p v-if="demoMode">The case, confirmed facts, optional research details and all mock results will be removed from this session. Pending mock reviews will be cancelled.</p>
    <p v-else>Access ends immediately. The server will remove the case data and cancel pending reviews.</p>
    <p>You will return to My simulations.</p>
    <template #footer><ElButton @click="$emit('update:modelValue', false)">Keep My Data</ElButton>
      <ElButton type="danger" @click="$emit('confirm')">Delete Simulation Data</ElButton></template>
  </ElDialog>
</template>
