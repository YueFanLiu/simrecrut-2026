<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDrawer, ElEmpty, ElInput, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'
import AdminLayout from '../../../layouts/AdminLayout.vue'
import { useAuthStore } from '../../../store/modules/auth.js'
import { filterFrozenProtocols, listFrozenProtocols, readFrozenProtocol } from '../../../mock/admin-protocols.js'
import { adminProtocolsScenario } from '../../../config/demo.js'

const auth = useAuthStore()
const rows = ref([]), loading = ref(false), error = ref(''), detailError = ref(''), detailLoading = ref(false)
const drawer = ref(false), selected = ref(null), selectedVersion = ref(''), page = ref(1), pageSize = ref(10)
const filters = reactive({ keyword: '', type: '', status: '' })
const filtered = computed(() => filterFrozenProtocols(rows.value, filters))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const configuration = computed(() => selected.value ? JSON.stringify({
  protocolVersion: selected.value.version, featureSchema: selected.value.schema,
  preprocessingVersion: selected.value.preprocessing, mlCutoff: selected.value.cutoff,
  modelTypes: selected.value.modelTypes, matchingSummary: selected.value.matching,
  labelConfiguration: null, featureOrdering: null, categoryMappings: null,
  preprocessingParameters: null, objectiveRuleThreshold: null,
}, null, 2) : '')
let active = true, listRequest = 0, detailRequest = 0
const delay = () => new Promise(resolve => setTimeout(resolve, 400))
watch(filters, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
function resetFilters() { Object.assign(filters, { keyword: '', type: '', status: '' }) }
async function load() {
  const request = ++listRequest, user = auth.demoUser
  const current = () => active && request === listRequest && auth.demoUser === user
  detailRequest += 1; drawer.value = false; selected.value = null
  loading.value = true; error.value = ''; rows.value = []
  try {
    const result = listFrozenProtocols(user)
    await delay()
    if (adminProtocolsScenario === 'load-error') throw new Error('Frozen protocols could not be loaded. Refresh to try again.')
    if (current()) { rows.value = adminProtocolsScenario === 'empty' ? [] : result; page.value = 1 }
  } catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) loading.value = false }
}
async function viewProtocol(version) {
  const request = ++detailRequest, user = auth.demoUser
  const current = () => active && request === detailRequest && drawer.value && auth.demoUser === user
  selectedVersion.value = version; selected.value = null; detailError.value = ''; drawer.value = true; detailLoading.value = true
  try {
    const result = readFrozenProtocol(version, user)
    await delay()
    if (adminProtocolsScenario === 'detail-error') throw new Error('Protocol details could not be loaded. Refresh the details to try again.')
    if (current()) selected.value = result
  } catch (failure) { if (current()) detailError.value = failure.message }
  finally { if (current()) detailLoading.value = false }
}
onBeforeUnmount(() => { active = false; listRequest += 1; detailRequest += 1 })
load()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="protocols-title" :aria-busy="loading">
      <div class="heading"><div><p class="breadcrumb">Administration / Protocols</p><h1 id="protocols-title">Frozen Protocols</h1><p>Inspect fixed assessment configuration and version references.</p></div><ElButton :loading="loading" :disabled="loading" @click="load">Refresh</ElButton></div>
      <ElAlert type="info" :closable="false" title="Read-only frontend demonstration. This synthetic summary does not execute matching, generate labels or change models and historical results. Missing configuration is marked Unavailable." />
      <form class="filters panel" @submit.prevent="page = 1">
        <label>Search<ElInput v-model="filters.keyword" aria-label="Search protocols" placeholder="Name, version, schema or description" clearable /></label>
        <label>Model Type<ElSelect v-model="filters.type" aria-label="Model Type" placeholder="All model types" clearable><ElOption label="Neutral ML" value="Neutral ML" /><ElOption label="Biased ML" value="Biased ML" /></ElSelect></label>
        <label>Status<ElSelect v-model="filters.status" aria-label="Status" placeholder="All statuses" clearable><ElOption label="Frozen" value="Frozen" /></ElSelect></label>
        <ElButton native-type="button" @click="resetFilters">Reset</ElButton>
      </form>
      <div class="panel results">
        <div class="list-heading"><h2>Protocol Versions</h2><span>{{ filtered.length }} {{ filtered.length === 1 ? 'version' : 'versions' }}</span></div>
        <ElSkeleton v-if="loading" :rows="5" animated />
        <ElAlert v-else-if="error" type="error" :closable="false" :title="error" />
        <ElEmpty v-else-if="!filtered.length" :description="rows.length ? 'No protocols match your filters.' : 'No frozen protocols are available.'" />
        <template v-else>
          <table class="protocol-table"><thead><tr><th>Protocol / Version</th><th>Model Type</th><th>Feature Schema</th><th>Cutoff</th><th>Status</th><th>Action</th></tr></thead><tbody><tr v-for="row in visible" :key="row.version"><td><strong>{{ row.name }}</strong><p>{{ row.version }}</p><small>{{ row.description }}</small></td><td>{{ row.modelTypes.join(' / ') }}</td><td>{{ row.schema }}</td><td>{{ row.cutoff }}</td><td><ElTag type="info">{{ row.status }}</ElTag></td><td><ElButton type="primary" plain @click="viewProtocol(row.version)">View Protocol</ElButton></td></tr></tbody></table>
          <div class="protocol-cards"><article v-for="row in visible" :key="row.version"><div class="list-heading"><h3>{{ row.name }}</h3><ElTag type="info">Frozen</ElTag></div><p>{{ row.version }}</p><p>{{ row.description }}</p><dl><dt>Feature Schema</dt><dd>{{ row.schema }}</dd><dt>Cutoff</dt><dd>{{ row.cutoff }}</dd><dt>Model Type</dt><dd>{{ row.modelTypes.join(' / ') }}</dd></dl><ElButton type="primary" plain @click="viewProtocol(row.version)">View Protocol</ElButton></article></div>
          <ElPagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[10, 20]" layout="prev, pager, next" aria-label="Protocol pagination" />
        </template>
      </div>
    </section>
    <ElDrawer v-model="drawer" title="Protocol Details" size="min(620px, 100%)" @closed="detailRequest += 1">
      <ElSkeleton v-if="detailLoading" :rows="8" animated />
      <div v-else-if="detailError"><ElAlert type="error" :closable="false" :title="detailError" /><ElButton class="retry" @click="viewProtocol(selectedVersion)">Refresh</ElButton></div>
      <div v-else-if="selected" class="details">
        <ElTag type="info">Frozen / Read only</ElTag><h2>{{ selected.name }}</h2><p>{{ selected.description }}</p>
        <dl><dt>Protocol Version</dt><dd>{{ selected.version }}</dd><dt>Feature Schema</dt><dd>{{ selected.schema }}</dd><dt>Preprocessing Version</dt><dd>{{ selected.preprocessing }}</dd><dt>ML Cutoff</dt><dd>{{ selected.cutoff }}</dd><dt>Model Type</dt><dd>{{ selected.modelTypes.join(' / ') }}</dd><dt>Frozen At</dt><dd>Unavailable in this demo fixture</dd></dl>
        <h3>Frozen Matching Configuration</h3><p>{{ selected.matching }}</p>
        <h3>Frozen Label Configuration</h3><p>{{ selected.labels }}</p>
        <h3>Feature Schema &amp; Preprocessing</h3><p>{{ selected.inputs }}</p><p>{{ selected.encoding }}</p>
        <h3>Cutoff Context</h3><p>{{ selected.threshold }}</p>
        <h3>Configuration Summary</h3><p>Read-only JSON. Null means configuration is unavailable; it is never interpreted as zero.</p><pre tabindex="0" aria-label="Read-only protocol configuration">{{ configuration }}</pre>
      </div>
    </ElDrawer>
  </AdminLayout>
</template>

<style scoped>
.heading, .list-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.heading { margin-bottom: 24px; }
h1 { margin: 8px 0; font-size: 28px; color: #213957; }
h2 { font-size: 18px; } h3 { font-size: 15px; }
p, dd, small { color: #586b83; line-height: 1.7; }
.breadcrumb { font-size: 12px; }
.panel { border: 1px solid #e1eaf5; border-radius: 8px; background: white; padding: 22px; margin-top: 22px; }
.filters { display: grid; grid-template-columns: minmax(180px, 2fr) minmax(120px, 1fr) minmax(100px, 1fr) auto; gap: 16px; align-items: end; }
label { display: flex; flex-direction: column; gap: 8px; font-size: 13px; min-width: 0; }
label .el-select { width: 100%; }
.list-heading { margin-bottom: 16px; }.list-heading h2 { margin: 0; }.list-heading span { color: #586b83; font-size: 12px; }
table { border-collapse: collapse; width: 100%; font-size: 13px; } th { text-align: left; color: #586b83; background: #f8fbff; } td, th { padding: 14px 10px; border-bottom: 1px solid #e1eaf5; } td { overflow-wrap: anywhere; } td p { margin: 6px 0; } td:first-child { width: 30%; } small { font-size: 12px; }
.el-pagination { margin-top: 20px; justify-content: end; }
.protocol-cards { display: none; }
dl { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 12px; font-size: 13px; } dt { font-weight: 600; color: #314866; } dd { margin: 0; overflow-wrap: anywhere; }
.details { overflow-wrap: anywhere; }.details h3 { margin-top: 26px; } pre { background: #f4f7fb; border-radius: 6px; padding: 16px; font-size: 12px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }.retry { margin-top: 16px; }
@media (max-width: 1100px) { .filters { grid-template-columns: 1fr 1fr; } .filters .el-button { justify-self: start; } }
@media (max-width: 760px) { .heading { align-items: start; flex-wrap: wrap; } h1 { font-size: 24px; }.protocol-table { display: none; }.protocol-cards { display: grid; gap: 16px; }.protocol-cards article { border-bottom: 1px solid #e1eaf5; padding-bottom: 18px; } .list-heading { flex-wrap: wrap; } .panel { padding: 16px; } }
@media (max-width: 480px) { .filters { grid-template-columns: minmax(0, 1fr); } dl { grid-template-columns: minmax(0, 1fr); gap: 6px; } dd { margin-bottom: 8px; }.details h2 { font-size: 20px; } }
</style>
