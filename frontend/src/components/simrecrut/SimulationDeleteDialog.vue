<script setup>
import { demoMode } from '../../config/runtime.js'
import { ElButton, ElDialog } from 'element-plus'

defineProps({ modelValue: Boolean, caseId: { type: String, default: '' } })
defineEmits(['update:modelValue', 'confirm'])
</script>

<template>
  <ElDialog :model-value="modelValue" :title="$t('Delete this simulation?')"
    width="min(480px, calc(100% - 32px))" :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)">
    <p v-if="caseId">{{ $t("Case:") }} {{ caseId }}</p>
    <p v-if="demoMode">{{ $t("The case, confirmed facts, optional research details and all mock results will be removed from this session. Pending mock reviews will be cancelled.") }}</p>
    <p v-else>{{ $t("Access ends immediately. The server will remove the case data and cancel pending reviews.") }}</p>
    <p>{{ $t("You will return to My simulations.") }}</p>
    <template #footer><ElButton @click="$emit('update:modelValue', false)">{{ $t('Keep simulation') }}</ElButton>
      <ElButton type="danger" @click="$emit('confirm')">{{ $t('Delete simulation') }}</ElButton></template>
  </ElDialog>
</template>
