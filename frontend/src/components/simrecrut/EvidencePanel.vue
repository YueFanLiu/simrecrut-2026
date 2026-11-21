<script setup>
import { ElButton } from 'element-plus'
defineProps({ selection: { type: Object, default: null } })
defineEmits(['back'])
</script>

<template>
  <section class="evidence-panel" aria-labelledby="evidence-title" aria-live="polite">
    <h2 id="evidence-title">{{ $t("Evidence") }}</h2>
    <p class="mock-note">{{ $t("Short mock excerpts only. These were not extracted from your PDF.") }}</p>
    <template v-if="selection">
      <h3>{{ $t(selection.label) }}</h3>
      <p>{{ $t("Source:") }} {{ $t(selection.field.status.replaceAll('_', ' ')) }}</p>
      <blockquote v-if="selection.field.evidenceExcerpt">{{ selection.field.evidenceExcerpt }}</blockquote>
      <p v-else>{{ $t("No source excerpt is available for this user-supplied or unknown value.") }}</p>
      <p v-if="selection.field.evidenceLocation">{{ selection.field.evidenceLocation }}</p>
      <p v-if="selection.field.status === 'USER_SUPPLIED'">{{ $t("This value was corrected by you. Any excerpt above describes the original mock observation.") }}</p>
      <ElButton @click="$emit('back')">{{ $t("Back to Fact") }}</ElButton>
    </template>
    <p v-else>{{ $t("Choose View Evidence beside a fact to see its short source excerpt and page reference.") }}</p>
  </section>
</template>

<style scoped>
.evidence-panel { padding: 24px; border: 1px solid var(--sr-border); border-radius: 10px; background: white; }
h2 { margin: 0 0 16px; color: var(--sr-text); font-size: 17px; }
h3 { margin: 24px 0 12px; font-size: 15px; color: var(--sr-text); }
p { font-size: 13px; line-height: 1.8; color: var(--sr-text-muted); }
.mock-note { padding: 12px; border-radius: 6px; background: var(--sr-primary-50); margin-bottom: 22px; }
blockquote { margin: 18px 0; padding: 16px; border-left: 3px solid var(--sr-primary); background: var(--sr-surface-tint); color: var(--sr-text-secondary); font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; }
</style>
