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
const groups = ["skills", "experience", "education", "languages", "projects"];
const editors = ref({});
const activeGroup = ref("skills");
const groupLabels = { skills: "Skills", experience: "Experience", education: "Education", languages: "Languages", projects: "Projects" };
const router = useRouter();
async function removeResume() {
  const id = route.params.id;
  try {
    await ElMessageBox.confirm("Delete this CV, its original and published JSON? This cannot be undone.", "Delete CV", { type: "warning", confirmButtonText: "Delete CV", cancelButtonText: "Cancel" });
    busy.value = true;
    const result = await deleteResume(id);
    if (stopped || route.params.id !== id) return;
    ElMessage.success(result.data.deleted ? "CV deleted." : "Access blocked. Deletion will retry automatically.");
    await router.push("/candidate/resumes");
  } catch (error) { if (error !== "cancel" && error !== "close") ElMessage.error("Deletion could not finish. Reload its current status before retrying."); }
  finally { busy.value = false; }
}
async function retryExtraction() {
  const id = route.params.id;
  busy.value = true;
  try { await resumeAction(id, "clean"); if (!stopped && route.params.id === id) await refresh(); }
  catch { if (!stopped && route.params.id === id) ElMessage.error("Extraction could not restart. Reload the current status before retrying."); }
  finally { busy.value = false; }
}
let timer,
  stopped = false;
let readController;
async function refresh() {
  if (demoMode || stopped || !route.params.id) return;
  try {
    const id = route.params.id;
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
    ElMessage.error("Could not load this resume.");
  }
}
watch(() => route.params.id, () => {
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {}; confirmed.value = false;
  refresh();
}, { immediate: true });
onUnmounted(() => {
  stopped = true;
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {};
});
async function publish() {
  const id = route.params.id;
  busy.value = true;
  try {
    const professional = Object.fromEntries(
      groups.map((group) => [group, editors.value[group] || []]),
    );
    if (!record.value.confirmed) await resumeAction(id, "confirm", {
      draft: { professional, warnings: record.value.draft?.warnings || [] },
      confirmed: true,
      expectedRevision: record.value.revision,
    });
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (!record.value || stopped || route.params.id !== id) return;
    const result = await resumeAction(id, "upload");
    if (stopped || route.params.id !== id) return;
    await refresh();
    // Cloud commit and temporary-file deletion can succeed independently.
    if (result.data.originalRemoved)
      ElMessage.success("Confirmed JSON published. Temporary original removed.");
    else
      ElMessage.warning("JSON published. Temporary-file cleanup is pending; retry cleanup.");
  } catch {
    if (stopped || route.params.id !== id) return;
    await refresh();
    ElMessage.error(
      "Publication failed. Check the facts and cloud configuration; the original is retained until publication succeeds.",
    );
  } finally {
    busy.value = false;
  }
}
async function saveDraft() {
  busy.value = true;
  const id = route.params.id;
  try {
    const professional = Object.fromEntries(groups.map(group => [group, editors.value[group] || []]));
    await resumeAction(id, "save", { draft: { professional, warnings: record.value.draft?.warnings || [] }, confirmed: false, expectedRevision: record.value.revision });
    if (stopped || route.params.id !== id) return;
    await refresh(); ElMessage.success("Draft saved. Facts remain editable.");
  } catch { if (!stopped && route.params.id === id) ElMessage.error("Draft was not saved. Check the fields and reload if another revision exists."); }
  finally { busy.value = false; }
}
async function retryCleanup() {
  const id = route.params.id;
  busy.value = true;
  try {
    await resumeAction(id, "cleanup");
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (!record.value.originalAvailable) ElMessage.success("Temporary original removed.");
  } catch {
    ElMessage.error("Cleanup could not finish. Your published JSON remains saved.");
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <CandidateLayout>
  <el-card
    ><h2>Review professional facts</h2>
    <p v-if="record?.expires_at">Expires: {{ new Date(record.expires_at).toLocaleString('en-GB') }}. Confirmed facts are locked.</p>
    <p v-if="!record">No connected resume is loaded.</p>
    <template v-else
      ><el-alert
        v-if="record.status === 'EXTRACTING'"
        title="Extracting professional facts…"
        type="info"
        :closable="false"
      /><el-alert
        v-if="record.error"
        :title="uiMessage(record.error, 'Extraction failed. Please retry or upload another CV.')"
        type="error"
        :closable="false"
      /><p v-for="warning in record.draft?.warnings || []" :key="warning">{{ uiMessage(warning, 'Some extracted facts need review. Check the facts and evidence before confirming.') }}</p>
      <el-tabs v-model="activeGroup" v-if="record.status !== 'EXTRACTING'"
        ><el-tab-pane v-for="group in groups" :key="group" :label="groupLabels[group]" :name="group"
          ><p>
            Check every fact and its evidence. EXTRACTED values come from the CV;
            USER_SUPPLIED values contain your corrections. UNKNOWN values need review.
          </p>
          <ResumeFactsTable v-model="editors[group]" :group="group" :locked="!!record.confirmed || busy" /></el-tab-pane></el-tabs
      ><el-button
        v-if="record.originalAvailable && record.cloud_revision === record.revision"
        v-hasPermi="['simrecrut:resume:confirm']"
        :loading="busy"
        @click="retryCleanup"
        >Retry temporary-file cleanup</el-button
      ><el-button v-if="!record.confirmed && record.originalAvailable && ['UPLOADED', 'FAILED'].includes(record.status)" v-hasPermi="['simrecrut:resume:edit']" :disabled="busy" @click="retryExtraction">Retry extraction</el-button
      ><el-button v-if="!record.confirmed" v-hasPermi="['simrecrut:resume:edit']" :disabled="busy || record.status === 'EXTRACTING'" @click="saveDraft">Save draft</el-button
      ><el-button v-hasPermi="['simrecrut:resume:delete']" type="danger" :disabled="busy" @click="removeResume">Delete CV</el-button
      ><el-checkbox v-if="!record.confirmed" v-model="confirmed"
        >I have reviewed these professional facts.</el-checkbox
      ><el-button
        v-hasPermi="['simrecrut:resume:confirm']"
        type="primary"
        :disabled="(!confirmed && !record.confirmed) || record.status === 'EXTRACTING' || record.cloud_revision === record.revision"
        :loading="busy"
        @click="publish"
        >{{ record.confirmed ? (record.cloud_revision === record.revision ? 'Facts published' : 'Publish confirmed facts') : 'Confirm and publish facts' }}</el-button
      ></template
    ></el-card
  >
  </CandidateLayout>
</template>
