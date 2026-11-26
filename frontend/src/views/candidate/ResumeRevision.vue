<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CandidateLayout from '../../layouts/CandidateLayout.vue'
import CvFactsEditor from '../../components/simrecrut/CvFactsEditor.vue'
import { getResume, createResumeRevision } from '../../api/resumes.js'
import { cvRevisionsEnabled } from '../../config/runtime.js'
import useUserStore from '../../store/modules/user.js'
import { t } from '../../i18n/index.js'
import { cvContext, cvReviewRoute } from '../../utils/cv-workflow.js'
import { createRevisionDraft, createRevisionSaver, exportRevisionDraft, restoreRevisionDraft } from '../../utils/cv-revisions.js'

const route = useRoute(), router = useRouter(), user = useUserStore()
const source = ref(null), revision = ref(null), loading = ref(true), busy = ref(false)
const error = ref(''), loadError = ref(''), uncertain = ref(false), savedId = ref('')
const restoring = ref(false), editorPending = ref(false)
const restoreInput = ref(null), feedback = ref('')
const caseId = computed(() => cvContext(route.query).caseId)
const canSave = computed(() => cvRevisionsEnabled && ['simrecrut:resume:upload', 'simrecrut:resume:edit'].every(permission => user.permissions.includes(permission)))
const locked = computed(() => busy.value || restoring.value || uncertain.value || Boolean(savedId.value))
let original = '', sequence = 0, restoreRequest = 0, controller
let saver
const dirty = computed(() => revision.value && !savedId.value && (editorPending.value || JSON.stringify(revision.value) !== original))

async function load() {
  const request = ++sequence, id = String(route.params.id)
  restoreRequest++; restoring.value = false; editorPending.value = false
  controller?.abort(); controller = new AbortController()
  source.value = null; revision.value = null; loading.value = true; loadError.value = ''; error.value = ''; feedback.value = ''; savedId.value = ''; uncertain.value = false
  saver = createRevisionSaver(async (sourceId, body, key) => (await createResumeRevision(sourceId, body, key)).data)
  try {
    if (!cvContext({ resumeId: id }).resumeId) throw new Error('Choose a confirmed CV before creating a revised copy.')
    const result = await getResume(id, controller.signal)
    if (request !== sequence) return
    source.value = result.data
    const name = source.value.display_name || source.value.filename || 'CV'
    const suffix = t('Revised copy')
    revision.value = createRevisionDraft(source.value, `${name.slice(0, 120 - suffix.length - 3)} — ${suffix}`)
    original = JSON.stringify(revision.value)
  } catch (failure) { if (request === sequence) loadError.value = failure.message || 'Could not load this CV. Please retry.' }
  finally { if (request === sequence) loading.value = false }
}

async function save() {
  if (!canSave.value || busy.value || restoring.value || editorPending.value || !revision.value || savedId.value) return
  const request = sequence
  busy.value = true; error.value = ''; feedback.value = ''
  try {
    const result = await saver.save(revision.value)
    if (request !== sequence) return
    savedId.value = result.id; uncertain.value = false
    busy.value = false
    ElMessage.success(t('Revised copy saved as a draft. Review it before confirming.'))
    await router.replace(cvReviewRoute(result.id, caseId.value))
  } catch (failure) {
    if (request !== sequence) return
    uncertain.value = saver.hasPending
    const status = failure.status ?? failure.response?.status
    error.value = [404, 405, 501].includes(status)
      ? 'Saving revised copies is not available on this service yet. Your edits are still on this page.'
      : failure.message || 'The revised copy was not saved. Your edits are still on this page.'
  } finally { if (request === sequence) busy.value = false }
}

function download() {
  if (!revision.value || busy.value || restoring.value || editorPending.value) return
  error.value = ''
  try {
    const blob = new Blob([exportRevisionDraft(revision.value)], { type: 'application/json' })
    const url = URL.createObjectURL(blob), anchor = document.createElement('a')
    anchor.href = url; anchor.download = 'cv-revised-copy-draft.json'; anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    feedback.value = 'Draft downloaded to this device. It is not saved to your CV library.'
  } catch (failure) { error.value = failure.message }
}

async function restore(event) {
  const file = event.target.files?.[0], request = sequence
  event.target.value = ''
  if (!file || locked.value || editorPending.value) return
  const operation = ++restoreRequest
  const current = () => request === sequence && operation === restoreRequest
  restoring.value = true
  error.value = ''; feedback.value = ''
  try {
    if (file.size > 2 * 1024 * 1024) throw new Error('Choose a revision draft file smaller than 2 MB.')
    const text = await file.text()
    if (!current() || busy.value || uncertain.value || savedId.value) return
    const restored = restoreRevisionDraft(text, source.value)
    revision.value = restored
    feedback.value = 'Draft restored on this page. Save it to add a new CV to your library.'
  } catch (failure) { if (current()) error.value = failure.message }
  finally { if (current()) restoring.value = false }
}

async function leave() {
  if (restoring.value) { ElMessage.info(t('Wait until the draft finishes loading before leaving.')); return false }
  if (busy.value) { ElMessage.info(t('Wait until the save finishes before leaving.')); return false }
  if (!dirty.value && !uncertain.value) return true
  try {
    await ElMessageBox.confirm(t(uncertain.value
      ? 'The save result is unknown. Retry the save to check it, or leave this page without checking.'
      : 'This revised copy is not saved to your library. Download the draft or stay to keep editing.'), t('Leave this revised copy?'),
    { confirmButtonText: t('Leave page'), cancelButtonText: t('Keep editing'), type: 'warning' })
    return true
  } catch { return false }
}
function beforeUnload(event) { if (dirty.value || uncertain.value || busy.value || restoring.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(leave)
onBeforeRouteUpdate(to => to.params.id !== route.params.id ? leave() : true)
watch(() => route.params.id, load, { immediate: true })
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { sequence++; restoreRequest++; controller?.abort(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <CandidateLayout>
    <el-button text :disabled="busy || restoring" @click="router.push(cvReviewRoute(route.params.id, caseId))">← {{ $t('Back to source CV') }}</el-button>
    <section class="revision-page" :aria-busy="loading || busy || restoring">
      <div class="page-heading"><div><p class="eyebrow">{{ $t('Revised copy') }}</p><h1>{{ $t('Edit a copy of your CV') }}</h1><p>{{ $t('Start from your confirmed information. Your original CV and existing simulations stay unchanged.') }}</p></div><el-tag :type="savedId ? 'success' : 'warning'">{{ $t(savedId ? 'Draft saved' : 'Not saved to your library') }}</el-tag></div>
      <p v-if="loading" role="status">{{ $t('Loading source CV…') }}</p>
      <el-alert v-if="loadError" type="error" :closable="false" :title="$t(loadError)" />
      <el-button v-if="loadError" :disabled="loading" @click="load">{{ $t('Try Again') }}</el-button>
      <template v-if="revision">
        <p class="source-name">{{ $t('Source CV') }}: <strong>{{ source.display_name || source.filename }}</strong></p>
        <el-alert v-if="!cvRevisionsEnabled" type="info" :closable="false" :title="$t('Saving revised copies is not available on this service yet. You can edit, download and restore a draft here; it is not added to your CV library.')" />
        <label class="name-field" for="revised-cv-name">{{ $t('Name of the revised copy') }}</label>
        <el-input id="revised-cv-name" v-model="revision.displayName" maxlength="120" show-word-limit :disabled="locked" />
        <CvFactsEditor v-model="revision.draft.professional" :locked="locked" class="facts-editor" @pending-change="editorPending = $event" />
        <p v-if="restoring" class="save-note" role="status">{{ $t('Restoring draft…') }}</p>
        <el-alert v-if="uncertain" type="warning" :closable="false" :title="$t('The save result is unknown. Editing is paused so a retry can check the same copy without creating another one.')" />
        <el-alert v-if="error" type="error" :closable="false" :title="$t(error)" />
        <p v-if="feedback" class="feedback" role="status">{{ $t(feedback) }}</p>
        <p class="save-note">{{ $t('The new copy will be saved as an editable draft. Confirm it separately before using it in a simulation.') }}</p>
        <div class="actions"><el-button :disabled="busy || restoring || editorPending" @click="download">{{ $t('Download draft') }}</el-button><input ref="restoreInput" type="file" accept="application/json,.json" hidden @change="restore"><el-button :loading="restoring" :disabled="locked || editorPending" @click="restoreInput?.click()">{{ $t('Restore draft') }}</el-button><el-button type="primary" :loading="busy" :disabled="!canSave || busy || restoring || editorPending || Boolean(savedId)" @click="save">{{ $t(uncertain ? 'Retry saving this copy' : 'Save as a new CV draft') }}</el-button><el-button v-if="savedId" @click="router.push(cvReviewRoute(savedId, caseId))">{{ $t('Open saved copy') }}</el-button></div>
        <p v-if="cvRevisionsEnabled && !canSave" class="save-note">{{ $t('Your account cannot create CV copies. You can download your draft instead.') }}</p>
      </template>
    </section>
  </CandidateLayout>
</template>

<style scoped>
.revision-page { padding:26px; margin-top:14px; border:1px solid var(--sr-border); border-radius:12px; background:var(--sr-surface); }
.page-heading { display:flex; justify-content:space-between; align-items:start; flex-wrap:wrap; gap:16px; margin-bottom:20px; }
.page-heading > div { flex:1; min-width:220px; } h1 { margin:6px 0; color:var(--sr-ink); font-size:26px; }
p { color:var(--sr-text-muted); font-size:13px; line-height:1.8; margin:0; } .eyebrow { color:var(--sr-primary); font-weight:600; }
.source-name { margin-bottom:16px; overflow-wrap:anywhere; } .name-field { display:block; margin:20px 0 8px; color:var(--sr-text); font-size:14px; }
.facts-editor { margin-top:22px; } .el-alert { margin:16px 0; } .actions { display:flex; flex-wrap:wrap; gap:10px; margin-top:18px; } .actions .el-button { margin:0; }
.save-note { margin-top:18px; } .feedback { color:var(--sr-primary); margin-top:14px; }
@media(max-width:600px) { .revision-page { padding:18px; } h1 { font-size:23px; } .actions .el-button { white-space:normal; height:auto; min-height:36px; } }
</style>
