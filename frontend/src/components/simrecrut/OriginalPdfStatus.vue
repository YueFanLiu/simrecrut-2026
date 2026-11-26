<script setup>
import { computed, onBeforeUnmount, watch } from 'vue'
import { ElButton, ElTag } from 'element-plus'
import { originalPdfStatus } from '../../utils/original-pdf-status.js'

const props = defineProps({ record: Object, source: { type: String, default: 'library' },
  compact: Boolean, refreshable: Boolean, refreshing: Boolean, disabled: Boolean,
  autoRefresh: Boolean, error: Boolean })
const emit = defineEmits(['refresh'])
const status = computed(() => originalPdfStatus(props.record, props.source))
let timer
watch(() => [status.value.pending, props.autoRefresh, props.refreshable, props.refreshing, props.disabled, props.error], () => {
  clearTimeout(timer)
  if (status.value.pending && props.autoRefresh && props.refreshable && !props.refreshing && !props.disabled && !props.error) {
    timer = setTimeout(() => emit('refresh'), 10000)
  }
}, { immediate: true })
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <span v-if="compact" class="pdf-status-compact" :title="$t(status.description)">
    <ElTag size="small" :type="status.type">{{ $t(status.label) }}</ElTag>
  </span>
  <section v-else class="pdf-status" :aria-label="$t('Original PDF')" :aria-busy="refreshing">
    <div class="pdf-heading"><strong>{{ $t('Original PDF') }}</strong>
      <ElTag :type="status.type">{{ $t(status.label) }}</ElTag></div>
    <p role="status" aria-live="polite">{{ $t(status.description) }}</p>
    <p v-if="error" class="pdf-error" role="alert">{{ $t('PDF status could not be refreshed. Showing the last reported status; retry to check it.') }}</p>
    <ElButton v-if="refreshable && source !== 'demo'" size="small" :loading="refreshing" :disabled="disabled" @click="emit('refresh')">{{ $t('Refresh PDF status') }}</ElButton>
  </section>
</template>

<style scoped>
.pdf-status { margin:18px 0; padding:16px; border:1px solid var(--sr-border); border-radius:9px; background:var(--sr-surface); }
.pdf-heading { display:flex; align-items:center; flex-wrap:wrap; gap:10px; color:var(--sr-ink); font-size:14px; }
.pdf-status p { margin:10px 0; color:var(--sr-text-muted); font-size:13px; line-height:1.7; }
.pdf-status .pdf-error { color:var(--el-color-danger); }
.pdf-status-compact { display:block; margin-top:6px; }
.el-tag { white-space:normal; height:auto; min-height:24px; line-height:1.5; padding:3px 8px; max-width:100%; }
</style>
