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
    <p class="company">{{ job.company }}</p>
    <p class="location">{{ job.location }}</p>
    <div class="job-tags">
      <ElTag size="small">{{ job.employmentType }}</ElTag>
      <ElTag size="small" type="info">{{ job.category }}</ElTag>
    </div>
    <p class="summary">{{ job.summary }}</p>
    <div class="card-footer">
      <span class="version">Version {{ job.currentVersion }}</span>
      <ElButton type="primary" size="small"
        @click="router.push({ name: 'JobDetail', params: { jobId: job.jobId } })">View Details</ElButton>
    </div>
  </article>
</template>

<style scoped>
.job-card { display: flex; flex-direction: column; min-width: 0; padding: 22px; background: white; border: 1px solid #e1eaf5; border-radius: 10px; }
.card-heading { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 8px 12px; }
h2 { flex: 1 1 170px; margin: 0; color: #20354e; font-size: 17px; line-height: 1.5; }
time { padding-top: 4px; color: #68798c; font-size: 11px; white-space: nowrap; }
.company { margin: 12px 0 0; color: #425269; font-size: 13px; }
.location { margin: 6px 0 0; color: #68798c; font-size: 12px; }
.job-tags { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 0; }
.summary { flex: 1; margin: 16px 0 20px; color: #586b83; font-size: 13px; line-height: 1.7; }
.card-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 14px; border-top: 1px solid #edf1f7; }
.version { color: #68798c; font-size: 11px; }
@media (max-width: 700px) {
  .job-card { padding: 18px; }
  h2 { flex-basis: 145px; }
}
</style>
