<script setup>
import { ElButton } from 'element-plus'
defineProps({ selection: { type: Object, default: null } })
defineEmits(['back'])
</script>

<template>
  <section class="evidence-panel" aria-labelledby="evidence-title" aria-live="polite">
    <h2 id="evidence-title">Evidence</h2>
    <p class="mock-note">Short mock excerpts only. These were not extracted from your PDF.</p>
    <template v-if="selection">
      <h3>{{ selection.label }}</h3>
      <p>Source: {{ selection.field.status.replaceAll('_', ' ') }}</p>
      <blockquote v-if="selection.field.evidenceExcerpt">{{ selection.field.evidenceExcerpt }}</blockquote>
      <p v-else>No source excerpt is available for this user-supplied or unknown value.</p>
      <p v-if="selection.field.evidenceLocation">{{ selection.field.evidenceLocation }}</p>
      <p v-if="selection.field.status === 'USER_SUPPLIED'">This value was corrected by you. Any excerpt above describes the original mock observation.</p>
      <ElButton @click="$emit('back')">Back to Fact</ElButton>
    </template>
    <p v-else>Choose View Evidence beside a fact to see its short source excerpt and page reference.</p>
  </section>
</template>

<style scoped>
.evidence-panel { padding: 24px; border: 1px solid #e1eaf5; border-radius: 10px; background: white; }
h2 { margin: 0 0 16px; color: #20354e; font-size: 17px; }
h3 { margin: 24px 0 12px; font-size: 15px; color: #20354e; }
p { font-size: 13px; line-height: 1.8; color: #586b83; }
.mock-note { padding: 12px; border-radius: 6px; background: #edf5ff; margin-bottom: 22px; }
blockquote { margin: 18px 0; padding: 16px; border-left: 3px solid #176bdf; background: #f8fbff; color: #425269; font-size: 14px; line-height: 1.8; overflow-wrap: anywhere; }
</style>
