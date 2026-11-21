<!-- C04: Review every CV section: personal information, five professional groups and other information. -->
<script setup>
import { t, dateLocale } from '../../i18n/index.js'

import ResumeFactsTable from "../../components/simrecrut/ResumeFactsTable.vue";
import CandidateLayout from "../../layouts/CandidateLayout.vue";
import { computed, onUnmounted, ref, watch } from "vue";
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
// Recruiters never see the PDF, so every CV section is extracted and reviewed here.
const professionalGroups = ["skills", "experience", "education", "languages", "projects"];
const groups = ["personal", ...professionalGroups, "other"];
const editors = ref({});
const hasFacts = computed(() => professionalGroups.some(group => editors.value[group]?.length));
const activeGroup = ref("personal");
const groupLabels = { personal: "Personal information", skills: "Skills", experience: "Experience", education: "Education",
  languages: "Languages", projects: "Projects", other: "Other information" };
const groupHints = { personal: "Check your name, contact details and photo. Recruiters see these reviewed values instead of your PDF.",
  other: "Interests, volunteering, certifications, awards and anything else from your CV." };
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
    await ElMessageBox.confirm(t("Delete this CV, its original and published JSON? This cannot be undone."), t("Delete CV"), { type: "warning", confirmButtonText: t("Delete CV"), cancelButtonText: t("Cancel") });
    if (stopped || route.params.id !== id) return;
    busy.value = true; actionError.value = "";
    deletionKey ||= crypto.randomUUID();
    const result = await deleteResume(id, deletionKey);
    if (stopped || route.params.id !== id) return;
    ElMessage.success(t(result.data.deleted ? "CV deleted." : "Access blocked. Deletion will retry automatically."));
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
  if (!record.value.confirmed && !hasFacts.value) return;
  const id = route.params.id;
  busy.value = true; actionError.value = "";
  let factsLocked = Boolean(record.value.confirmed);
  try {
    const professional = Object.fromEntries(
      groups.map((group) => [group, editors.value[group] || []]),
    );
    let originalRemoved = false;
    if (!record.value.confirmed) originalRemoved = Boolean((await performAction(id, "confirm", {
      draft: { professional, warnings: record.value.draft?.warnings || [] },
      confirmed: true,
      expectedRevision: record.value.revision,
    })).data?.originalRemoved);
    factsLocked = true;
    if (originalRemoved) ElMessage.success(t("Facts confirmed. Your original PDF has been deleted."));
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (!record.value || stopped || route.params.id !== id) return;
    const result = await performAction(id, "upload");
    if (stopped || route.params.id !== id) return;
    await refresh();
    // Cloud commit and temporary-file deletion can succeed independently.
    if (result.data.originalRemoved)
      ElMessage.success(t("Confirmed JSON published. Temporary original removed."));
    else
      ElMessage.warning(t("JSON published. Temporary-file cleanup is pending; retry cleanup."));
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
    await refresh(); ElMessage.success(t("Draft saved. Facts remain editable."));
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
    if (record.value && !record.value.originalAvailable) ElMessage.success(t("Temporary original removed."));
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
    <el-button text :disabled="busy" @click="router.push('/candidate/resumes')">{{ $t("Back to my CVs") }}</el-button>
    <section class="review-panel" :aria-busy="loading || busy">
      <h1>{{ record?.display_name || record?.filename || $t('Review professional facts') }}</h1>
      <p v-if="record?.display_name && record.display_name !== record.filename" class="expiry">{{ $t("File:") }} {{ record.filename }}</p>
      <p>{{ $t("Review and correct your professional facts before confirming. Confirmed facts are locked.") }}</p>
      <p v-if="record?.expires_at" class="expiry">{{ $t("Expires:") }} {{ $t(new Date(record.expires_at).toLocaleString(dateLocale)) }}</p>
      <p v-if="loading" role="status">{{ $t("Loading CV...") }}</p>
      <el-alert v-if="loadError" :title="$t(loadError)" type="error" show-icon :closable="false" />
      <el-button v-if="loadError" :loading="loading" @click="refresh">{{ $t("Retry loading CV") }}</el-button>
      <p v-if="!record && !loading && !loadError">{{ $t("No connected CV is loaded.") }}</p>
      <template v-if="record">
        <el-button :disabled="busy || loading" @click="refresh">{{ $t("Reload saved facts") }}</el-button>
        <el-alert v-if="record.confirmed && !record.originalAvailable" type="success" show-icon :closable="false"
          :title="$t(&quot;Your original PDF was deleted after confirmation. Only the reviewed facts are kept.&quot;)" />
        <el-alert v-if="record.status === 'EXTRACTING'" :title="$t(&quot;Extracting professional facts…&quot;)"
          type="info" :closable="false" />
        <el-alert v-if="record.error" :title="$t(uiMessage(record.error, 'Extraction failed. Please retry or upload another CV.'))"
          type="error" show-icon :closable="false" />
        <p v-for="warning in record.draft?.warnings || []" :key="warning">
          {{ $t(uiMessage(warning, 'Some extracted facts need review. Check the facts before confirming.')) }}
        </p>
        <el-tabs v-if="record.status !== 'EXTRACTING'" v-model="activeGroup">
          <el-tab-pane v-for="group in groups" :key="group" :label="$t(groupLabels[group])" :name="group">
            <p>{{ $t(groupHints[group] || "Check each fact against its supporting excerpt. Add missing information or correct any mistakes.") }}</p>
            <ResumeFactsTable v-model="editors[group]" :group="group"
              :locked="!!record.confirmed || busy || loading" />
          </el-tab-pane>
        </el-tabs>
        <el-alert v-if="actionError" :title="$t(actionError)" type="error" show-icon :closable="false" />
        <div class="review-actions">
          <el-button v-if="record.originalAvailable && record.cloud_revision === record.revision"
            v-hasPermi="['simrecrut:resume:confirm']" :disabled="busy || loading" @click="retryCleanup"> {{ $t("Retry temporary-file cleanup") }}</el-button>
          <el-button v-if="!record.confirmed && record.originalAvailable && ['UPLOADED', 'FAILED'].includes(record.status)"
            v-hasPermi="['simrecrut:resume:edit']" :disabled="busy || loading" @click="retryExtraction"> {{ $t("Retry extraction") }}</el-button>
          <el-button v-if="!record.confirmed" v-hasPermi="['simrecrut:resume:edit']"
            :disabled="busy || loading || record.status === 'EXTRACTING'" @click="saveDraft">{{ $t("Save draft") }}</el-button>
          <el-button v-hasPermi="['simrecrut:resume:delete']" type="danger" plain
            :disabled="busy || loading" @click="removeResume">{{ $t("Delete CV") }}</el-button>
        </div>
        <div class="confirmation">
          <p v-if="!record.confirmed && !hasFacts && record.status !== 'EXTRACTING'">{{ $t("Add a professional fact before confirming this CV.") }}</p>
          <el-checkbox v-if="!record.confirmed" v-model="confirmed"
            :disabled="busy || loading || !hasFacts || record.status === 'EXTRACTING'"> {{ $t("I have reviewed these professional facts.") }}</el-checkbox>
          <p v-if="!record.confirmed" class="expiry">{{ $t("Your original PDF is deleted as soon as you confirm.") }}</p>
          <el-button v-hasPermi="['simrecrut:resume:confirm']" type="primary" :loading="busy"
            :disabled="busy || loading || (!record.confirmed && (!confirmed || !hasFacts)) || record.status === 'EXTRACTING' || record.cloud_revision === record.revision"
            @click="publish">{{ $t(record.confirmed
              ? (record.cloud_revision === record.revision ? 'Facts published' : 'Publish confirmed facts')
              : 'Confirm and publish facts') }}</el-button>
        </div>
      </template>
    </section>
  </CandidateLayout>
</template>

<style scoped>
.review-panel { background: white; border: 1px solid var(--sr-border); border-radius: 12px; padding: 28px; margin-top: 14px; }
h1 { margin: 0; color: var(--sr-ink); font-size: 26px; overflow-wrap: anywhere; }
p { color: var(--sr-text-muted); font-size: 14px; line-height: 1.7; }
.expiry { font-size: 12px; }
.el-tabs { margin-top: 20px; }
.el-alert { margin: 16px 0; }
.review-actions, .confirmation { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
.review-actions .el-button, .confirmation .el-button { margin: 0; }
.confirmation { padding-top: 20px; border-top: 1px solid var(--sr-border); justify-content: space-between; }
:deep(.el-checkbox) { white-space: normal; height: auto; }
:deep(.el-checkbox__label) { line-height: 1.7; }
@media (max-width: 600px) {
  .review-panel { padding: 18px; }
  h1 { font-size: 23px; }
  .confirmation .el-button { width: 100%; }
}
</style>
