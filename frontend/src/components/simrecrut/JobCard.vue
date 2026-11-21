<script setup>
import { ElButton, ElTag } from 'element-plus'
import { useRouter } from 'vue-router'
import { formatPublishedDate } from '../../utils/job-list.js'

defineProps({ job: { type: Object, required: true } })
const router = useRouter()
</script>

<template>
  <article class="job-card" :aria-labelledby="`job-title-${job.jobId}`">
    <div class="card-heading">
      <h2 :id="`job-title-${job.jobId}`">{{ job.title }}</h2>
      <time :datetime="job.publishedAt">{{ formatPublishedDate(job.publishedAt) }}</time>
    </div>
    <p v-if="job.company" class="company">{{ job.company }}</p>
    <p v-if="job.location" class="location">{{ job.location }}</p>
    <div v-if="job.category || job.employmentType" class="job-tags">
      <ElTag size="small">{{ job.employmentType }}</ElTag>
      <ElTag size="small" type="info">{{ job.category }}</ElTag>
    </div>
    <p class="summary">{{ job.summary }}</p>
    <div class="card-footer">
      <span class="version">{{ $t("Version") }} {{ job.currentVersion }}</span>
      <ElButton type="primary" size="small"
        @click="router.push({ name: 'JobDetail', params: { jobId: job.jobId } })">{{ $t("View Details") }}</ElButton>
    </div>
  </article>
</template>

<style scoped>
.job-card { display: flex; flex-direction: column; min-width: 0; padding: 22px; background: white; border: 1px solid var(--sr-border); border-radius: var(--sr-radius); box-shadow: var(--sr-shadow-sm); transition: box-shadow .2s, border-color .2s, transform .2s; }
.job-card:hover { border-color: var(--sr-primary-200); box-shadow: var(--sr-shadow); transform: translateY(-2px); }
@media (prefers-reduced-motion: reduce) { .job-card, .job-card:hover { transition: none; transform: none; } }
.card-heading { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 8px 12px; }
h2 { flex: 1 1 170px; margin: 0; color: var(--sr-ink); font-size: 17px; line-height: 1.5; }
time { padding-top: 4px; color: var(--sr-text-muted); font-size: 11px; white-space: nowrap; }
.company { margin: 12px 0 0; color: var(--sr-text-secondary); font-size: 13px; }
.location { margin: 6px 0 0; color: var(--sr-text-muted); font-size: 12px; }
.job-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 0; }
.summary { flex: 1; margin: 16px 0 20px; color: var(--sr-text-muted); font-size: 13px; line-height: 1.7; }
.card-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 14px; border-top: 1px solid var(--sr-surface-sunken); }
.version { color: var(--sr-text-muted); font-size: 11px; }
@media (max-width: 700px) {
  .job-card { padding: 18px; }
  h2 { flex-basis: 145px; }
}
</style>
