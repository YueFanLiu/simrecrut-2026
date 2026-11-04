<!-- C04: Review five professional fact groups without editing identity fields. -->
<script setup>
import ResumeFactsTable from "../../components/simrecrut/ResumeFactsTable.vue";
import CandidateLayout from "../../layouts/CandidateLayout.vue";
import { onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { getResume, resumeAction, deleteResume } from "@/api/resumes";
import { demoMode } from "@/store/modules/user";
import { uiMessage } from "../../utils/ui-message.js";
const route = useRoute(),
  record = ref(null),
  busy = ref(false),
  confirmed = ref(false);
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')
let deletionKey
const groups = ["skills", "experience", "education", "languages", "projects"];
const editors = ref({});
const activeGroup = ref("skills");
const groupLabels = { skills: "Skills", experience: "Experience", education: "Education", languages: "Languages", projects: "Projects" };
const router = useRouter();
const pendingActions = new Map();
async function performAction(id, action, data = {}) {
  const signature = JSON.stringify([id, action, data]);
  if (!pendingActions.has(signature)) pendingActions.set(signature, crypto.randomUUID());
  const result = await resumeAction(id, action, data, {
    operationKey: pendingActions.get(signature), silent: true,
  });
  pendingActions.delete(signature);
  return result;
}
async function removeResume() {
  if (busy.value || loading.value) return;
  const id = route.params.id;
  try {
    await ElMessageBox.confirm("Delete this CV, its original and published JSON? This cannot be undone.", "Delete CV", { type: "warning", confirmButtonText: "Delete CV", cancelButtonText: "Cancel" });
    if (stopped || route.params.id !== id) return;
    busy.value = true; actionError.value = "";
    deletionKey ||= crypto.randomUUID();
    const result = await deleteResume(id, deletionKey);
    if (stopped || route.params.id !== id) return;
    ElMessage.success(result.data.deleted ? "CV deleted." : "Access blocked. Deletion will retry automatically.");
    await router.push("/candidate/resumes");
  } catch (error) { if (error !== "cancel" && error !== "close") actionError.value = error.message || "Deletion could not finish. Reload its current status before retrying."; }
  finally { busy.value = false; }
}
async function retryExtraction() {
  if (busy.value || loading.value) return;
  const id = route.params.id;
  busy.value = true; actionError.value = "";
  try { await performAction(id, "clean"); if (!stopped && route.params.id === id) await refresh(); }
  catch (error) { if (!stopped && route.params.id === id) actionError.value = error.message || "Extraction could not restart. Please retry."; }
  finally { busy.value = false; }
}
let timer,
  stopped = false;
let readController;
async function refresh() {
  if (demoMode || stopped || !route.params.id) return;
  clearTimeout(timer);
  loading.value = true; loadError.value = "";
  const id = route.params.id;
  try {
    readController?.abort(); readController = new AbortController();
    const result = await getResume(id, readController.signal);
    if (stopped || id !== route.params.id) return;
    record.value = result.data;
    if (record.value.status === "EXTRACTING") timer = setTimeout(refresh, 2000);
    else
      for (const group of groups)
        editors.value[group] = JSON.parse(JSON.stringify(record.value.draft?.professional?.[group] || []));
  } catch (error) {
    if (stopped || error.code === "ERR_CANCELED") return;
    clearTimeout(timer); record.value = null; editors.value = {};
    loadError.value = error.message || "Could not load this CV. Please retry.";
  } finally { if (!stopped && id === route.params.id) loading.value = false; }
}
watch(() => route.params.id, () => {
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {}; confirmed.value = false; actionError.value = ""; pendingActions.clear(); deletionKey = undefined;
  refresh();
}, { immediate: true });
onUnmounted(() => {
  stopped = true;
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {};
});
async function publish() {
  if (busy.value || loading.value || !record.value) return;
  const id = route.params.id;
  busy.value = true; actionError.value = "";
  let factsLocked = Boolean(record.value.confirmed);
  try {
    const professional = Object.fromEntries(
      groups.map((group) => [group, editors.value[group] || []]),
    );
    if (!record.value.confirmed) await performAction(id, "confirm", {
      draft: { professional, warnings: record.value.draft?.warnings || [] },
      confirmed: true,
      expectedRevision: record.value.revision,
    });
    factsLocked = true;
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (!record.value || stopped || route.params.id !== id) return;
    const result = await performAction(id, "upload");
    if (stopped || route.params.id !== id) return;
    await refresh();
    // Cloud commit and temporary-file deletion can succeed independently.
    if (result.data.originalRemoved)
      ElMessage.success("Confirmed JSON published. Temporary original removed.");
    else
      ElMessage.warning("JSON published. Temporary-file cleanup is pending; retry cleanup.");
  } catch (error) {
    if (stopped || route.params.id !== id) return;
    if (factsLocked) await refresh();
    actionError.value = (error.message ? error.message + " " : "") + (factsLocked
      ? "Your confirmed facts are saved. Publication could not finish. Retry publication."
      : "Confirmation failed. Your edits are preserved. Check the fields, or reload the latest version if it changed.");
  } finally {
    busy.value = false;
  }
}
async function saveDraft() {
  if (busy.value || loading.value || !record.value) return;
  busy.value = true; actionError.value = "";
  const id = route.params.id;
  try {
    const professional = Object.fromEntries(groups.map(group => [group, editors.value[group] || []]));
    await performAction(id, "save", { draft: { professional, warnings: record.value.draft?.warnings || [] }, confirmed: false, expectedRevision: record.value.revision });
    if (stopped || route.params.id !== id) return;
    await refresh(); ElMessage.success("Draft saved. Facts remain editable.");
  } catch (error) { if (!stopped && route.params.id === id) actionError.value = (error.message || "Draft was not saved.") + " Your edits are preserved. Reload saved facts if the revision changed."; }
  finally { busy.value = false; }
}
async function retryCleanup() {
  if (busy.value || loading.value) return;
  const id = route.params.id;
  busy.value = true; actionError.value = "";
  try {
    await performAction(id, "cleanup");
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (record.value && !record.value.originalAvailable) ElMessage.success("Temporary original removed.");
  } catch (error) {
    actionError.value = (error.message || "Cleanup could not finish.") + " Your published facts remain saved.";
  } finally {
    busy.value = false;
  }
}
watch(editors, () => { confirmed.value = false }, { deep: true });
</script>
<template>
  <CandidateLayout>
    <el-button text :disabled="busy" @click="router.push('/candidate/resumes')">Back to my CVs</el-button>
    <section class="review-panel" :aria-busy="loading || busy">
      <h1>{{ record?.filename || 'Review professional facts' }}</h1>
      <p>Review and correct your professional facts before confirming. Confirmed facts are locked.</p>
      <p v-if="record?.expires_at" class="expiry">Expires: {{ new Date(record.expires_at).toLocaleString('en-GB') }}</p>
      <p v-if="loading" role="status">Loading CV...</p>
      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
      <el-button v-if="loadError" :loading="loading" @click="refresh">Retry loading CV</el-button>
      <p v-if="!record && !loading && !loadError">No connected CV is loaded.</p>
      <template v-if="record">
        <el-button :disabled="busy || loading" @click="refresh">Reload saved facts</el-button>
        <el-alert v-if="record.status === 'EXTRACTING'" title="Extracting professional facts…"
          type="info" :closable="false" />
        <el-alert v-if="record.error" :title="uiMessage(record.error, 'Extraction failed. Please retry or upload another CV.')"
          type="error" show-icon :closable="false" />
        <p v-for="warning in record.draft?.warnings || []" :key="warning">
          {{ uiMessage(warning, 'Some extracted facts need review. Check the facts before confirming.') }}
        </p>
        <el-tabs v-if="record.status !== 'EXTRACTING'" v-model="activeGroup">
          <el-tab-pane v-for="group in groups" :key="group" :label="groupLabels[group]" :name="group">
            <p>Check each fact against its supporting excerpt. Add missing information or correct any mistakes.</p>
            <ResumeFactsTable v-model="editors[group]" :group="group"
              :locked="!!record.confirmed || busy || loading" />
          </el-tab-pane>
        </el-tabs>
        <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
        <div class="review-actions">
          <el-button v-if="record.originalAvailable && record.cloud_revision === record.revision"
            v-hasPermi="['simrecrut:resume:confirm']" :disabled="busy || loading" @click="retryCleanup">
            Retry temporary-file cleanup</el-button>
          <el-button v-if="!record.confirmed && record.originalAvailable && ['UPLOADED', 'FAILED'].includes(record.status)"
            v-hasPermi="['simrecrut:resume:edit']" :disabled="busy || loading" @click="retryExtraction">
            Retry extraction</el-button>
          <el-button v-if="!record.confirmed" v-hasPermi="['simrecrut:resume:edit']"
            :disabled="busy || loading || record.status === 'EXTRACTING'" @click="saveDraft">Save draft</el-button>
          <el-button v-hasPermi="['simrecrut:resume:delete']" type="danger" plain
            :disabled="busy || loading" @click="removeResume">Delete CV</el-button>
        </div>
        <div class="confirmation">
          <el-checkbox v-if="!record.confirmed" v-model="confirmed"
            :disabled="busy || loading || record.status === 'EXTRACTING'">
            I have reviewed these professional facts.</el-checkbox>
          <el-button v-hasPermi="['simrecrut:resume:confirm']" type="primary" :loading="busy"
            :disabled="busy || loading || (!confirmed && !record.confirmed) || record.status === 'EXTRACTING' || record.cloud_revision === record.revision"
            @click="publish">{{ record.confirmed
              ? (record.cloud_revision === record.revision ? 'Facts published' : 'Publish confirmed facts')
              : 'Confirm and publish facts' }}</el-button>
        </div>
      </template>
    </section>
  </CandidateLayout>
</template>

<style scoped>
.review-panel { background: white; border: 1px solid #e1eaf5; border-radius: 12px; padding: 28px; margin-top: 14px; }
h1 { margin: 0; color: #132941; font-size: 26px; overflow-wrap: anywhere; }
p { color: #586b83; font-size: 14px; line-height: 1.7; }
.expiry { font-size: 12px; }
.el-tabs { margin-top: 20px; }
.el-alert { margin: 16px 0; }
.review-actions, .confirmation { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
.review-actions .el-button, .confirmation .el-button { margin: 0; }
.confirmation { padding-top: 20px; border-top: 1px solid #e1eaf5; justify-content: space-between; }
:deep(.el-checkbox) { white-space: normal; height: auto; }
:deep(.el-checkbox__label) { line-height: 1.7; }
@media (max-width: 600px) {
  .review-panel { padding: 18px; }
  h1 { font-size: 23px; }
  .confirmation .el-button { width: 100%; }
}
</style>
