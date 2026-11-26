<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { t } from '../../i18n/index.js'
const props = defineProps({ items: { type: Array, default: () => [] }, locked: Boolean, error: String,
  suggestions: { type: Array, default: () => [] } })
const emit = defineEmits(['add', 'edit', 'remove', 'accept', 'dismiss', 'pending-change'])
const name = ref(''), editing = ref(null), selected = ref(null)
const pending = computed(() => editing.value !== null || Boolean(name.value.trim()))
watch(pending, value => emit('pending-change', value), { immediate: true, flush: 'sync' })
onBeforeUnmount(() => emit('pending-change', false))
async function submit() {
  const value = name.value.trim()
  if (!value || props.locked) return
  emit(editing.value ? 'edit' : 'add', { name: value, id: editing.value })
  await nextTick()
  if (!props.error) { name.value = ''; editing.value = null }
}
function edit(item) { editing.value = item.id; name.value = item.name }
watch(() => props.items, () => { selected.value = null; editing.value = null; name.value = '' })
</script>

<template>
  <section class="skill-editor" :aria-label="$t('Skills')">
    <div class="heading"><h2>{{ $t('Your skills') }}</h2><span>{{ $t('Review, edit or add any skill') }}</span></div>
    <p v-if="!items.length" class="empty">{{ $t('No skills yet. Add the skills you want to include in your CV.') }}</p>
    <ul v-else class="skill-list" :aria-label="$t('Skills')">
      <li v-for="item in items" :key="item.id">
        <button type="button" class="skill-label" :aria-label="$t('View evidence for {skill}', { skill: item.name })" @click="selected = item">{{ item.name }}</button>
        <button v-if="!locked" type="button" class="chip-action" :disabled="pending" :aria-label="$t('Edit skill {skill}', { skill: item.name })" @click="edit(item)"><span aria-hidden="true">✎</span></button>
        <button v-if="!locked" type="button" class="chip-action" :disabled="pending" :aria-label="$t('Remove skill {skill}', { skill: item.name })" @click="emit('remove', item.id)"><span aria-hidden="true">×</span></button>
      </li>
    </ul>
    <form v-if="!locked" class="skill-form" @submit.prevent="submit">
      <label for="cv-skill-name">{{ $t(editing ? 'Edit skill' : 'Add a skill') }}</label>
      <div class="input-row"><el-input id="cv-skill-name" v-model="name" maxlength="100" :placeholder="$t('For example: Figma, user interviews, event planning…')" :aria-invalid="Boolean(error)" />
        <el-button native-type="submit" :disabled="!name.trim()">{{ $t(editing ? 'Save skill' : 'Add skill') }}</el-button>
        <el-button v-if="editing || name" @click="editing = null; name = ''">{{ $t('Cancel') }}</el-button>
      </div>
    </form>
    <p v-if="pending" class="pending-note" role="status">{{ $t('Add or save this skill, or cancel its edit, before saving or confirming the CV.') }}</p>
    <p v-if="error" class="error" role="alert">{{ $t(error) }}</p>
    <div v-if="selected" class="evidence" aria-live="polite">
      <div class="heading"><h3>{{ selected.name }}</h3><span>{{ $t(selected.source) }}<template v-if="selected.evidence?.page"> · {{ $t('Page') }} {{ selected.evidence.page }}</template></span></div>
      <p>{{ selected.evidence?.text || $t('Added by you. No excerpt from the original CV is attached.') }}</p>
    </div>
    <aside v-for="suggestion in suggestions" :key="suggestion.id" class="suggestion">
      <h3>{{ $t('Suggestion to review') }}: {{ suggestion.name }}</h3><p>{{ suggestion.reason }}</p>
      <blockquote v-if="suggestion.evidence?.text">{{ suggestion.evidence.text }}</blockquote>
      <div v-if="!locked" class="input-row"><el-button :disabled="pending" @click="emit('accept', suggestion)">{{ $t('Confirm and add') }}</el-button><el-button text :disabled="pending" @click="emit('dismiss', suggestion.id)">{{ $t('Dismiss suggestion') }}</el-button></div>
    </aside>
  </section>
</template>

<style scoped>
.heading { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:8px; }
h2,h3,p { margin:0; } h2 { color:var(--sr-ink); font-size:18px; } h3 { font-size:14px; }
.heading span,.empty,.evidence p { color:var(--sr-text-muted); font-size:13px; line-height:1.8; }
.empty { margin:18px 0; }
.skill-list { display:flex; flex-wrap:wrap; gap:10px; list-style:none; padding:0; margin:20px 0; }
.skill-list li { display:flex; align-items:center; max-width:100%; background:var(--sr-primary-50); border:1px solid var(--sr-primary-100); border-radius:8px; }
.skill-list button { border:0; background:transparent; color:var(--sr-primary-dark); font:inherit; cursor:pointer; }
.skill-label { padding:9px 8px 9px 13px; text-align:left; overflow-wrap:anywhere; min-width:0; }
.chip-action { padding:8px; flex:none; } .chip-action:last-child { padding-right:12px; }
.skill-form { margin-top:18px; } label { display:block; margin-bottom:7px; color:var(--sr-text); font-size:14px; }
.input-row { display:flex; gap:9px; align-items:center; flex-wrap:wrap; } .input-row .el-input { flex:1; min-width:180px; } .input-row .el-button { margin:0; }
.evidence { margin-top:18px; padding:16px; background:var(--sr-surface-tint); border-radius:8px; } .evidence p { margin-top:8px; white-space:pre-wrap; overflow-wrap:anywhere; }
.suggestion { margin-top:18px; padding:18px; border-radius:8px; background:var(--sr-accent-50); } .suggestion p { margin:8px 0 12px; color:var(--sr-text-secondary); line-height:1.7; }
.error { margin-top:10px; color:var(--el-color-danger); font-size:13px; }
.pending-note { margin-top:10px; color:var(--sr-text-secondary); font-size:13px; line-height:1.7; }
.chip-action:disabled { opacity:.5; cursor:default; }
button:focus-visible { outline:2px solid var(--sr-primary); outline-offset:3px; }
@media(pointer:coarse) { .skill-list button { min-height:44px; } .chip-action { min-width:44px; } }
@media(max-width:500px) { .input-row .el-input { flex-basis:100%; } }
</style>
