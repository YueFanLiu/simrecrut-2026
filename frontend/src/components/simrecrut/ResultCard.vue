<script setup>
import { ElTag } from 'element-plus'

defineProps({
  title: { type: String, required: true },
  status: { type: String, default: 'Complete' },
  decision: { type: String, default: null },
  score: { type: Number, default: null },
  scale: { type: String, default: '' },
  note: { type: String, default: '' },
})
</script>

<template>
  <article class="result-card" :aria-label="$t(title)">
    <div class="card-heading"><h3>{{ $t(title) }}</h3><ElTag type="info">{{ $t(status) }}</ElTag></div>
    <p v-if="decision" class="decision" :class="decision.toLowerCase()">{{ $t(decision) }}</p>
    <p v-else class="pending">{{ $t(status === 'Not assigned' ? 'No review assigned' : status === 'Unavailable' ? 'Awaiting result' : 'Awaiting review') }}</p>
    <div v-if="score !== null" class="score"><strong>{{ score }}</strong><span>{{ $t("Scale") }} {{ scale }}</span></div>
    <p class="note">{{ $t(note) }}</p>
    <slot />
  </article>
</template>

<style scoped>
.result-card { min-width: 0; padding: 22px; background: white; border: 1px solid var(--sr-border); border-radius: 12px; overflow-wrap: anywhere; }
.card-heading { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
h3 { margin: 0; color: var(--sr-ink); font-size: 16px; line-height: 1.6; }
.decision, .pending { margin: 20px 0 16px; font-size: 22px; font-weight: 700; }
.accept { color: #218458; } .reject { color: #b42336; } .pending { color: var(--sr-text-muted); font-size: 18px; }
.score { display: flex; flex-wrap: wrap; align-items: baseline; gap: 12px; }
.score strong { font-size: 28px; color: var(--sr-ink); }
.score span, .note { color: var(--sr-text-muted); font-size: 12px; line-height: 1.8; }
.note { margin: 14px 0 0; }
</style>
