<!-- C04: Review every CV section: personal information, five professional groups and other information. -->
<script setup>
import { t, dateLocale } from '../../i18n/index.js'

import CvFactsEditor from "../../components/simrecrut/CvFactsEditor.vue";
import OriginalPdfStatus from '../../components/simrecrut/OriginalPdfStatus.vue'
import { originalPdfStatus, mayRetryLibraryPdfCleanup, withOriginalPdfMetadata } from '../../utils/original-pdf-status.js'
import CandidateLayout from "../../layouts/CandidateLayout.vue";
import { computed, onUnmounted, ref, watch } from "vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { getResume, resumeAction, deleteResume } from "@/api/resumes";
import { demoMode } from "@/store/modules/user";
import { uiMessage } from "../../utils/ui-message.js";
import { cvContext, cvSelectionRoute, jobsForResume, caseQuery, createCvAttachment, cvRevisionRoute } from '../../utils/cv-workflow.js'
import { caseDetail, useLibraryResume } from '../../api/cases.js'
import { getCaseDestination } from '../../utils/case-history.js'
const route = useRoute(),
  record = ref(null),
  busy = ref(false),
  confirmed = ref(false);
const loading = ref(false)
const context = computed(() => cvContext(route.query))
const loadError = ref('')
const actionError = ref('')
const editorPending = ref(false)
const pdfLoading = ref(false)
const pdfError = ref(false)
const pdfStatus = computed(() => originalPdfStatus(record.value))
let pdfController, pdfRequest = 0
let deletionKey
let leavingAfterAction = false;
async function continueAfterAction(destination) {
  leavingAfterAction = true;
  try { return await router.push(destination); }
  finally { leavingAfterAction = false; }
}
// Recruiters never see the PDF, so every CV section is extracted and reviewed here.
const professionalGroups = ["skills", "experience", "education", "languages", "projects"];
const groups = ["personal", ...professionalGroups, "other"];
const editors = ref({});
const savedEditors = ref('');
const dirty = computed(() => Boolean(record.value && !record.value.confirmed
  && (editorPending.value || savedEditors.value && JSON.stringify(editors.value) !== savedEditors.value)));
const hasFacts = computed(() => professionalGroups.some(group => editors.value[group]?.length));
const router = useRouter();
const pendingActions = new Map();
const attachToCase = createCvAttachment({ readCase: caseDetail, attach: useLibraryResume })
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
    await ElMessageBox.confirm(t("Delete this CV, its original and its confirmed facts? This cannot be undone."), t("Delete CV"), { type: "warning", confirmButtonText: t("Delete CV"), cancelButtonText: t("Cancel") });
    if (stopped || route.params.id !== id) return;
    busy.value = true; actionError.value = "";
    deletionKey ||= crypto.randomUUID();
    const result = await deleteResume(id, deletionKey);
    if (stopped || route.params.id !== id) return;
    ElMessage.success(t(result.data.deleted ? "CV deleted." : "Access blocked. Deletion will retry automatically."));
    await continueAfterAction({ name: 'ConnectedResumes', query: caseQuery(context.value.caseId) });
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
  pdfController?.abort(); pdfRequest++; pdfLoading.value = false; pdfError.value = false;
  clearTimeout(timer);
  loading.value = true; loadError.value = "";
  const id = route.params.id;
  try {
    readController?.abort(); readController = new AbortController();
    const result = await getResume(id, readController.signal);
    if (stopped || id !== route.params.id) return;
    record.value = result.data;
    if (record.value.status === "EXTRACTING") timer = setTimeout(refresh, 2000);
    else {
      for (const group of groups)
        editors.value[group] = JSON.parse(JSON.stringify(record.value.draft?.professional?.[group] || []));
      savedEditors.value = JSON.stringify(editors.value);
    }
  } catch (error) {
    if (stopped || error.code === "ERR_CANCELED") return;
    clearTimeout(timer); record.value = null; editors.value = {};
    loadError.value = error.message || "Could not load this CV. Please retry.";
  } finally { if (!stopped && id === route.params.id) loading.value = false; }
}
watch(() => route.params.id, () => {
  pdfController?.abort(); pdfRequest++; pdfLoading.value = false; pdfError.value = false;
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {}; savedEditors.value = ''; confirmed.value = false; actionError.value = ""; pendingActions.clear(); deletionKey = undefined;
  refresh();
}, { immediate: true });
onUnmounted(() => {
  stopped = true;
  pdfController?.abort(); pdfRequest++;
  readController?.abort(); clearTimeout(timer); record.value = null; editors.value = {};
  globalThis.window?.removeEventListener('beforeunload', beforeUnload);
});
async function mayLeave() {
  if (leavingAfterAction) return true;
  if (busy.value) { ElMessage.info(t('Wait until the current operation finishes before leaving.')); return false; }
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(t('Your CV changes have not been saved. Leave and discard them?'), t('Leave this CV review?'),
      { type: 'warning', confirmButtonText: t('Leave page'), cancelButtonText: t('Keep editing') });
    return true;
  } catch { return false; }
}
function beforeUnload(event) {
  if (busy.value || dirty.value) { event.preventDefault(); event.returnValue = ''; }
}
globalThis.window?.addEventListener('beforeunload', beforeUnload);
onBeforeRouteLeave(mayLeave);
onBeforeRouteUpdate((to, from) => to.params.id === from.params.id || mayLeave());
async function reloadSavedCv() {
  if (busy.value || loading.value) return
  const id = route.params.id
  if (dirty.value) {
    try {
      await ElMessageBox.confirm(t('Reload the saved CV? Your unsaved changes will be discarded.'), t('Reload saved CV'),
        { type: 'warning', confirmButtonText: t('Reload saved CV'), cancelButtonText: t('Keep editing') })
    } catch { return }
  }
  if (!stopped && route.params.id === id) await refresh()
}
async function refreshPdfStatus() {
  if (demoMode || stopped || busy.value || loading.value || pdfLoading.value || !record.value) return
  const id = route.params.id, request = ++pdfRequest
  pdfLoading.value = true; pdfError.value = false
  pdfController = new AbortController()
  try {
    const latest = await getResume(id, pdfController.signal)
    if (!stopped && request === pdfRequest && route.params.id === id && record.value) {
      record.value = withOriginalPdfMetadata(record.value, latest.data)
    }
  } catch (error) {
    if (!stopped && request === pdfRequest && error.code !== 'ERR_CANCELED') {
      pdfError.value = true
      if ([401, 403, 404, 410].includes(error.response?.status ?? error.status)) {
        record.value = null; editors.value = {}; loadError.value = error.message
      }
    }
  } finally { if (request === pdfRequest) pdfLoading.value = false }
}
function reportPdfStatus() {
  const status = originalPdfStatus(record.value)
  if (status.code === 'absent') ElMessage.success(t(status.label))
  else ElMessage.warning(t(status.label))
}
async function publish() {
  if (busy.value || loading.value || editorPending.value || !record.value) return;
  if (!record.value.confirmed && !hasFacts.value) return;
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
    await performAction(id, "upload");
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (stopped || route.params.id !== id) return;
    ElMessage.success(t('Confirmed information published.'));
    // Publication and original-file deletion are independent outcomes.
    if (record.value) reportPdfStatus();
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
async function useForCase() {
  if (busy.value || loading.value || editorPending.value || !record.value || !context.value.caseId) return
  if (!record.value.confirmed && (!confirmed.value || !hasFacts.value)) return
  const id = route.params.id, caseId = context.value.caseId
  busy.value = true; actionError.value = ''
  try {
    if (!record.value.confirmed) {
      await performAction(id, 'confirm', { draft: { professional: Object.fromEntries(groups.map(group => [group, editors.value[group] || []])), warnings: record.value.draft?.warnings || [] }, confirmed: true, expectedRevision: record.value.revision })
      if (stopped || route.params.id !== id || context.value.caseId !== caseId) return
      await refresh()
      if (!stopped && route.params.id === id && originalPdfStatus(record.value).pending) reportPdfStatus()
    }
    if (stopped || route.params.id !== id || context.value.caseId !== caseId) return
    const result = await attachToCase(caseId, id)
    if (!stopped && route.params.id === id && context.value.caseId === caseId) {
      if (result.alreadyStarted) ElMessage.info(t('This simulation already has CV information. Continuing from its saved step.'))
      await continueAfterAction(getCaseDestination({ ...result.row, caseId }) || { name: 'CvReview', params: { caseId } })
    }
  } catch (error) {
    if (!stopped && route.params.id === id) {
      // Confirmation and attachment can succeed independently. Read the stored
      // state without discarding edits when confirmation itself was rejected.
      try {
        const latest = await getResume(id)
        if (!stopped && route.params.id === id && latest.data?.confirmed) await refresh()
      } catch { /* Keep the current edits and report the original operation error. */ }
      actionError.value = error.message || 'Your CV could not be used for this simulation. Retry or choose another CV.'
    }
  } finally { busy.value = false }
}
async function saveDraft() {
  if (busy.value || loading.value || editorPending.value || !record.value) return;
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
  if (busy.value || loading.value || !mayRetryLibraryPdfCleanup(record.value)) return;
  const id = route.params.id;
  busy.value = true; actionError.value = "";
  try {
    await performAction(id, "cleanup");
    if (stopped || route.params.id !== id) return;
    await refresh();
    if (!stopped && route.params.id === id && record.value) reportPdfStatus();
  } catch (error) {
    actionError.value = (error.message || "Cleanup could not finish.") + " Your published facts remain saved.";
  } finally {
    busy.value = false;
  }
}
watch(editors, () => { confirmed.value = false }, { deep: true });
watch(editorPending, value => { if (value) confirmed.value = false }, { flush: 'sync' });
</script>
<template>
  <CandidateLayout>
    <el-button text :disabled="busy" @click="router.push({ name: 'ConnectedResumes', query: caseQuery(context.caseId) })">{{ $t("Back to my CVs") }}</el-button>
    <el-button v-if="context.caseId" text :disabled="busy" @click="router.push(cvSelectionRoute(context.caseId))">{{ $t('Back to CV selection') }}</el-button>
    <section class="review-panel" :aria-busy="loading || busy">
      <h1>{{ record?.display_name || record?.filename || $t('Review your CV') }}</h1>
      <p v-if="record?.display_name && record.display_name !== record.filename" class="expiry">{{ $t("File:") }} {{ record.filename }}</p>
      <p>{{ $t(record?.confirmed ? 'This CV version is confirmed. Create a revised copy to make changes.' : 'Check all CV sections, including personal information and additions, before confirming. Confirmed information is locked.') }}</p>
      <p v-if="record?.expires_at" class="expiry">{{ $t("Expires:") }} {{ $t(new Date(record.expires_at).toLocaleString(dateLocale)) }}</p>
      <p v-if="loading" role="status">{{ $t("Loading CV...") }}</p>
      <el-alert v-if="loadError" :title="$t(loadError)" type="error" show-icon :closable="false" />
      <el-button v-if="loadError" :loading="loading" @click="refresh">{{ $t("Retry loading CV") }}</el-button>
      <p v-if="!record && !loading && !loadError">{{ $t("No connected CV is loaded.") }}</p>
      <template v-if="record">
        <section v-if="record.confirmed" class="reuse-panel" aria-live="polite">
          <div><h2>{{ $t('Your CV is ready to reuse') }}</h2><p>{{ $t('Use these confirmed facts for another job without uploading the PDF again. Changes in a simulation do not change this library CV.') }}</p></div>
          <el-button type="primary" :disabled="busy || loading" @click="context.caseId ? useForCase() : router.push(jobsForResume(route.params.id))">{{ $t(context.caseId ? 'Use for this simulation' : 'Choose a job for this CV') }}</el-button>
        </section>
        <el-button :disabled="busy || loading" @click="reloadSavedCv">{{ $t('Reload saved CV') }}</el-button>
        <OriginalPdfStatus :record="record" refreshable auto-refresh :refreshing="pdfLoading" :error="pdfError"
          :disabled="busy || loading" @refresh="refreshPdfStatus" />
        <el-alert v-if="record.status === 'EXTRACTING'" :title="$t('Extracting CV information…')"
          type="info" :closable="false" />
        <el-alert v-if="record.error" :title="$t(uiMessage(record.error, 'Extraction failed. Please retry or upload another CV.'))"
          type="error" show-icon :closable="false" />
        <p v-for="warning in record.draft?.warnings || []" :key="warning">
          {{ $t(uiMessage(warning, 'Some extracted facts need review. Check the facts before confirming.')) }}
        </p>
        <CvFactsEditor v-if="record.status !== 'EXTRACTING'" v-model="editors" class="facts-workspace"
          :locked="!!record.confirmed || busy || loading" @pending-change="editorPending = $event" />
        <p v-if="dirty" class="draft-state" role="status">{{ $t('You have unsaved CV changes.') }}</p>
        <el-alert v-if="actionError" :title="$t(actionError)" type="error" show-icon :closable="false" />
        <div class="review-actions">
          <el-button v-if="record.confirmed" v-hasPermi="['simrecrut:resume:edit']" :disabled="busy || loading" @click="router.push(cvRevisionRoute(route.params.id, context.caseId))">{{ $t('Edit a revised copy') }}</el-button>
          <el-button v-if="mayRetryLibraryPdfCleanup(record)"
            v-hasPermi="['simrecrut:resume:confirm']" :disabled="busy || loading" @click="retryCleanup"> {{ $t("Retry temporary-file cleanup") }}</el-button>
          <el-button v-if="!record.confirmed && record.originalAvailable && ['UPLOADED', 'FAILED'].includes(record.status)"
            v-hasPermi="['simrecrut:resume:edit']" :disabled="busy || loading" @click="retryExtraction"> {{ $t("Retry extraction") }}</el-button>
          <el-button v-if="!record.confirmed" v-hasPermi="['simrecrut:resume:edit']"
            :disabled="busy || loading || editorPending || record.status === 'EXTRACTING'" @click="saveDraft">{{ $t("Save draft") }}</el-button>
          <el-button v-hasPermi="['simrecrut:resume:delete']" type="danger" plain
            :disabled="busy || loading" @click="removeResume">{{ $t("Delete CV") }}</el-button>
        </div>
        <div class="confirmation">
          <p v-if="!record.confirmed && !hasFacts && record.status !== 'EXTRACTING'">{{ $t('The current service requires at least one recognised fact before confirmation. You can save your additions as a draft.') }}</p>
          <el-checkbox v-if="!record.confirmed" v-model="confirmed"
            :disabled="busy || loading || editorPending || !hasFacts || record.status === 'EXTRACTING'"> {{ $t('I have reviewed all the CV information shown above.') }}</el-checkbox>
          <p v-if="!record.confirmed && pdfStatus.code !== 'absent'" class="expiry">{{ $t('Confirmation requests cleanup of the original PDF. Check the PDF status above for completion.') }}</p>
          <el-button v-if="context.caseId && !record.confirmed" v-hasPermi="['simrecrut:resume:confirm']" type="primary" :loading="busy"
            :disabled="busy || loading || editorPending || !confirmed || !hasFacts || record.status === 'EXTRACTING'" @click="useForCase">{{ $t('Confirm and use for this simulation') }}</el-button>
          <el-button v-else v-hasPermi="['simrecrut:resume:confirm']" :type="context.caseId ? 'default' : 'primary'" :loading="busy"
            :disabled="busy || loading || editorPending || (!record.confirmed && (!confirmed || !hasFacts)) || record.status === 'EXTRACTING' || record.cloud_revision === record.revision"
            @click="publish">{{ $t(record.confirmed
              ? (record.cloud_revision === record.revision ? 'Published' : 'Publish confirmed information')
              : 'Confirm and publish') }}</el-button>
          <p v-if="!context.caseId || record.confirmed" class="publication-note">{{ $t('Publication includes all CV sections except Personal information.') }}</p>
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
.facts-workspace { margin-top:20px; }
.reuse-panel { display:flex; align-items:center; justify-content:space-between; gap:18px; flex-wrap:wrap; background:var(--sr-primary-50); padding:18px; border-radius:9px; margin:20px 0; }
.reuse-panel > div { flex:1; min-width:220px; } .reuse-panel h2 { font-size:17px; margin:0; color:var(--sr-ink); } .reuse-panel p { margin:7px 0 0; font-size:13px; }
.el-alert { margin: 16px 0; }
.review-actions, .confirmation { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
.review-actions .el-button, .confirmation .el-button { margin: 0; white-space:normal; height:auto; min-height:34px; max-width:100%; line-height:1.5; }
.draft-state { color:var(--sr-primary); }
.publication-note { flex-basis:100%; margin:0; font-size:12px; }
.confirmation { padding-top: 20px; border-top: 1px solid var(--sr-border); justify-content: space-between; }
:deep(.el-checkbox) { white-space: normal; height: auto; }
:deep(.el-checkbox__label) { line-height: 1.7; }
@media (max-width: 600px) {
  .review-panel { padding: 18px; }
  h1 { font-size: 23px; }
  .confirmation .el-button { width: 100%; }
}
</style>
