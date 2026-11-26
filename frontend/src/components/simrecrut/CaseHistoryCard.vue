<script setup>
import { dateLocale } from '../../i18n/index.js'

import { ElButton, ElTag } from 'element-plus'

defineProps({ entry: { type: Object, required: true }, busy: Boolean })
defineEmits(['continue', 'delete'])
const formatDate = (value) => value ? new Intl.DateTimeFormat(dateLocale.value, {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Paris',
}).format(new Date(value)) : 'Not uploaded'
</script>

<template>
  <article class="history-card" :aria-label="$t(`${entry.caseId} ${entry.jobTitle}`)">
    <div class="card-heading"><div><span class="case-id">{{ entry.caseId }}</span><h2>{{ entry.jobTitle }}</h2></div>
      <ElTag :type="entry.statusType">{{ $t(entry.status) }}</ElTag></div>
    <p class="metadata">{{ $t("Version") }} {{ entry.version }} {{ $t("· Uploaded:") }} {{ formatDate(entry.uploadedAt) }}</p>
    <dl><div><dt>{{ $t("Objective (0–100)") }}</dt><dd>{{ $t(entry.objective ?? '—') }}</dd></div>
      <div><dt>{{ $t("Neutral ML (0–1)") }}</dt><dd>{{ $t(entry.neutral ?? '—') }}</dd></div>
      <div><dt>{{ $t("Biased ML (0–1)") }}</dt><dd>{{ $t(entry.biased ?? '—') }}</dd></div>
      <div><dt>{{ $t("Standard Human") }}</dt><dd>{{ $t(entry.standardHuman ?? '—') }}</dd></div>
      <div><dt>{{ $t("Instructed-Bias Human") }}</dt><dd>{{ $t(entry.instructedBiasHuman ?? '—') }}</dd></div>
    </dl><p class="metadata">{{ $t("Available until:") }} {{ formatDate(entry.expiresAt) }} {{ $t("(Paris)") }}</p>
    <div class="card-actions"><ElButton type="primary" :disabled="busy || !entry.destination" :aria-label="$t(entry.actionLabel) + ' ' + entry.caseId" @click="$emit('continue', entry)">{{ $t(entry.actionLabel) }}</ElButton>
      <ElButton type="danger" plain :disabled="busy" :aria-label="$t('Delete') + ' ' + entry.caseId" @click="$emit('delete', entry)">{{ $t("Delete") }}</ElButton></div>
  </article>
</template>

<style scoped>
.history-card { padding: 20px; background: white; border: 1px solid var(--sr-border); border-radius: 12px; overflow-wrap: anywhere; }
.card-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: flex-start; gap: 12px; }
.case-id { color: var(--sr-primary); font-size: 12px; }
h2 { margin: 8px 0 0; color: var(--sr-ink); font-size: 17px; line-height: 1.6; }
.metadata { font-size: 12px; color: var(--sr-text-muted); line-height: 1.8; }
dl { margin: 16px 0; font-size: 13px; line-height: 1.8; }
dl div { display: flex; justify-content: space-between; gap: 12px; padding: 7px 0; border-bottom: 1px solid var(--sr-surface-sunken); }
dt { color: var(--sr-text-muted); } dd { margin: 0; color: var(--sr-ink); text-align: right; }
.card-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.card-actions .el-button { margin: 0; }
</style>
