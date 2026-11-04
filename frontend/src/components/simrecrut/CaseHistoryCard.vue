<script setup>
import { ElButton, ElTag } from 'element-plus'

defineProps({ entry: { type: Object, required: true }, busy: Boolean })
defineEmits(['continue', 'delete'])
const formatDate = (value) => value ? new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(value)) : 'Not uploaded'
</script>

<template>
  <article class="history-card" :aria-label="`${entry.caseId} ${entry.jobTitle}`">
    <div class="card-heading"><div><span class="case-id">{{ entry.caseId }}</span><h2>{{ entry.jobTitle }}</h2></div>
      <ElTag :type="entry.statusType">{{ entry.status }}</ElTag></div>
    <p class="metadata">Version {{ entry.version }} · Uploaded: {{ formatDate(entry.uploadedAt) }}</p>
    <dl><div><dt>Objective (0–100)</dt><dd>{{ entry.objective ?? '—' }}</dd></div>
      <div><dt>Neutral ML (0–1)</dt><dd>{{ entry.neutral ?? '—' }}</dd></div>
      <div><dt>Biased ML (0–1)</dt><dd>{{ entry.biased ?? '—' }}</dd></div>
      <div><dt>Standard Human</dt><dd>{{ entry.standardHuman ?? '—' }}</dd></div>
      <div><dt>Instructed-Bias Human</dt><dd>{{ entry.instructedBiasHuman ?? '—' }}</dd></div>
    </dl><p class="metadata">Available until: {{ formatDate(entry.expiresAt) }} (Paris)</p>
    <div class="card-actions"><ElButton type="primary" :disabled="busy || !entry.destination" :aria-label="`${entry.actionLabel} ${entry.caseId}`" @click="$emit('continue', entry)">{{ entry.actionLabel }}</ElButton>
      <ElButton type="danger" plain :disabled="busy" :aria-label="`Delete ${entry.caseId}`" @click="$emit('delete', entry)">Delete</ElButton></div>
  </article>
</template>

<style scoped>
.history-card { padding: 20px; background: white; border: 1px solid #e1eaf5; border-radius: 12px; overflow-wrap: anywhere; }
.card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 12px; }
.case-id { color: #176bdf; font-size: 12px; }
h2 { margin: 8px 0 0; color: #132941; font-size: 17px; line-height: 1.6; }
.metadata { font-size: 12px; color: #586b83; line-height: 1.8; }
dl { margin: 16px 0; font-size: 13px; line-height: 1.8; }
dl div { display: flex; justify-content: space-between; gap: 12px; padding: 7px 0; border-bottom: 1px solid #edf1f7; }
dt { color: #586b83; } dd { margin: 0; color: #132941; text-align: right; }
.card-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.card-actions .el-button { margin: 0; }
</style>
