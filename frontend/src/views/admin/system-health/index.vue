<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElEmpty, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { checkFreshness, loadHealthSummary } from '../../../mock/admin-health.js'
import { adminHealthScenario } from '../../../config/demo.js'

const auth = useAuthStore(), router = useRouter()
const summary = ref(null), loading = ref(false), error = ref(''), now = ref(Date.now())
const freshness = computed(() => checkFreshness(summary.value?.checkedAt, now.value))
const checkedTime = value => value ? new Date(value).toUTCString() : 'Unavailable'
let active = true, sequence = 0
const timer = setInterval(() => { now.value = Date.now() }, 1000)
async function refresh() {
  const request = ++sequence, user = auth.demoUser
  const current = () => active && sequence === request && auth.demoUser === user
  loading.value = true; error.value = ''; summary.value = null
  try { const result = await loadHealthSummary(user, adminHealthScenario); if (current()) { summary.value = result; now.value = Date.now() } }
  catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
onBeforeUnmount(() => { active = false; sequence += 1; clearInterval(timer) })
refresh()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="health-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / System Health</p><h1 id="health-title">Service Health</h1><p>Inspect runtime service checks and loaded model references.</p></div><div class="actions"><ElButton :loading="loading" :disabled="loading" @click="refresh">Refresh</ElButton><ElButton @click="router.push({ name: 'AdminModels' })">Models</ElButton><ElButton @click="router.push({ name: 'AdminFailures' })">View Failures</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration. No health API is connected. Unknown means service health has not been verified; Refresh only reloads the local summary." />
      <ElAlert v-if="adminHealthScenario === 'stale'" class="notice" type="warning" :closable="false" title="Synthetic stale-timestamp scenario. This timestamp is a UI fixture, not a real service check." />
      <div v-if="loading" class="panel"><ElSkeleton :rows="8" animated /></div>
      <div v-else-if="error" class="panel"><ElAlert type="error" :closable="false" :title="error" /></div>
      <ElEmpty v-else-if="!summary" description="No service health summary is available." />
      <template v-else>
        <div class="summary-grid"><article class="panel"><h2>Overall Status</h2><ElTag type="info">Unknown</ElTag><p>No runtime services have been verified.</p></article><article class="panel"><h2>Last Service Check (UTC)</h2><p>{{ checkedTime(summary.checkedAt) }}</p><ElTag :type="freshness === 'Stale' ? 'warning' : 'info'">{{ freshness }}</ElTag><p>Refresh does not create a service-check timestamp.</p></article></div>
        <div class="service-grid"><article v-for="component in summary.components" :key="component.id" class="panel"><h2>{{ component.name }}</h2><ElTag type="info">{{ component.status }}</ElTag><p>{{ component.details }}</p></article></div>
        <div class="panel"><h2>Service Status</h2>
          <table class="status-table"><thead><tr><th>Service</th><th>Status</th><th>Checked At (UTC)</th><th>Freshness</th><th>Details</th></tr></thead><tbody><tr v-for="component in summary.components" :key="component.id"><th scope="row">{{ component.name }}</th><td><ElTag type="info">{{ component.status }}</ElTag></td><td>{{ checkedTime(component.checkedAt) }}</td><td><ElTag :type="checkFreshness(component.checkedAt, now) === 'Stale' ? 'warning' : 'info'">{{ checkFreshness(component.checkedAt, now) }}</ElTag></td><td>{{ component.details }}</td></tr></tbody></table>
          <div class="status-cards"><article v-for="component in summary.components" :key="component.id"><h3>{{ component.name }}</h3><dl><dt>Status</dt><dd>{{ component.status }}</dd><dt>Checked At (UTC)</dt><dd>{{ checkedTime(component.checkedAt) }}</dd><dt>Freshness</dt><dd>{{ checkFreshness(component.checkedAt, now) }}</dd></dl><p>{{ component.details }}</p></article></div>
        </div>
        <div class="panel"><h2>Loaded Model Slots</h2><p>Registered demo versions and runtime-loaded models are separate. No inference service reports which model is loaded.</p><div class="model-grid"><article v-for="model in summary.models" :key="model.type"><h3>{{ model.type }}</h3><dl><dt>Load Status</dt><dd>{{ model.status }}</dd><dt>Loaded Version</dt><dd>{{ model.version || 'Unavailable' }}</dd><dt>Checked At (UTC)</dt><dd>{{ checkedTime(model.checkedAt) }}</dd></dl></article></div></div>
        <div class="panel"><h2>Temporary Workspace &amp; Cleanup</h2><dl><dt>Writable</dt><dd>{{ summary.workspaceWritable === null ? 'Unavailable' : String(summary.workspaceWritable) }}</dd><dt>Cleanup Status</dt><dd>{{ summary.cleanupStatus || 'Unavailable' }}</dd></dl><p>No filesystem paths, case content or cleanup operations are exposed by this page.</p></div>
      </template>
    </section>
  </AdminLayout>
</template>

<style scoped>
.heading, .actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }.heading { justify-content: space-between; margin-bottom: 24px; }.actions .el-button { margin-left: 0; }
h1 { margin: 8px 0; font-size: 28px; color: #213957; }h2 { font-size: 17px; margin-top: 0; }h3 { font-size: 15px; }p, dd { color: #586b83; line-height: 1.7; }.breadcrumb { font-size: 12px; }
.panel { border: 1px solid #e1eaf5; border-radius: 8px; padding: 22px; background: white; margin-top: 22px; min-width: 0; overflow-wrap: anywhere; }.notice { margin-top: 16px; }
.summary-grid, .model-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }.service-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; }.service-grid .panel { border-top: 3px solid #92baf0; }.model-grid article { background: #f8fbff; padding: 16px; border-radius: 6px; min-width: 0; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }th, td { padding: 14px 10px; text-align: left; border-bottom: 1px solid #e1eaf5; color: #586b83; }thead { background: #f8fbff; }tbody th { color: #314866; font-weight: 600; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 10px; font-size: 13px; }dt { color: #314866; font-weight: 600; }dd { margin: 0; overflow-wrap: anywhere; }.status-cards { display: none; }
@media (max-width: 1100px) { .service-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { h1 { font-size: 24px; }.status-table { display: none; }.status-cards { display: grid; gap: 18px; }.status-cards article { border-bottom: 1px solid #e1eaf5; padding-bottom: 12px; }.panel { padding: 16px; }.summary-grid, .service-grid, .model-grid { grid-template-columns: minmax(0, 1fr); }.service-grid { gap: 0; } }
@media (max-width: 480px) { dl { grid-template-columns: minmax(0, 1fr); gap: 6px; }dd { margin-bottom: 6px; } }
</style>
