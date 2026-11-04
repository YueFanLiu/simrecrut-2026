<script setup>
defineProps({
  title: { type: String, required: true },
  maximum: { type: Number, required: true },
  items: { type: Array, required: true },
  note: { type: String, required: true },
})
</script>

<template>
  <section class="scale-panel" :aria-label="title">
    <h3>{{ title }}</h3><p class="scale-label">Scale: 0–{{ maximum }}</p>
    <div v-for="item in items" :key="item.label" class="scale-row">
      <div class="row-label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
      <meter min="0" :max="maximum" :value="item.value" :aria-label="`${item.label}: ${item.value} on a scale of 0 to ${maximum}`">{{ item.value }}</meter>
      <div class="endpoints" aria-hidden="true"><span>0</span><span>{{ maximum }}</span></div>
    </div><p class="note">{{ note }}</p>
  </section>
</template>

<style scoped>
.scale-panel { padding: 22px; background: white; border: 1px solid #e1eaf5; border-radius: 12px; }
h3 { margin: 0; color: #132941; font-size: 16px; line-height: 1.7; }
.scale-label, .note, .endpoints { color: #586b83; font-size: 12px; line-height: 1.8; }
.scale-row { margin: 20px 0; }
.row-label, .endpoints { display: flex; justify-content: space-between; gap: 12px; }
.row-label { color: #132941; font-size: 13px; line-height: 1.8; }
meter { width: 100%; height: 22px; }
.note { margin: 14px 0 0; }
</style>
