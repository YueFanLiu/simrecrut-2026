<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { t } from '../../i18n/index.js'
import CvSkillEditor from './CvSkillEditor.vue'
import ResumeFactsTable from './ResumeFactsTable.vue'
import { cvGroups, cvGroupLabels, readSupplement, makeSection, sectionTextLimit, skillItems, putSkill, removeSkill } from '../../utils/cv-supplements.js'

const props = defineProps({ modelValue: { type: Object, required: true }, locked: Boolean,
  suggestions: { type: Array, default: () => [] }, counts: { type: Object, default: () => ({}) },
  confirmedAbsent: { type: Array, default: () => [] }, requireAbsence: Boolean, focusSection: String })
const emit = defineEmits(['update:modelValue', 'update:confirmedAbsent', 'accept', 'dismiss', 'pending-change'])
const active = ref('skills'), error = ref(''), adding = ref(false), sectionName = ref('')
const skillPending = ref(false)
const pending = computed(() => skillPending.value || adding.value)
watch(pending, value => emit('pending-change', value), { immediate: true, flush: 'sync' })
onBeforeUnmount(() => emit('pending-change', false))
const skills = computed(() => skillItems(props.modelValue))
const sections = computed(() => (props.modelValue.other || []).flatMap((row, index) => {
  const item = readSupplement(row)
  return item?.kind === 'section' ? [{ ...item, index, key: `extra:${index}` }] : []
}))
const custom = computed(() => sections.value.find(item => item.key === active.value))
const count = group => group === 'skills' ? skills.value.length : props.counts[group] ?? (props.modelValue[group] || []).filter(row => !readSupplement(row)).length
const rawOther = computed(() => (props.modelValue.other || []).filter(row => !readSupplement(row)))
const extraForGroup = computed(() => sections.value.filter(item => item.name === cvGroupLabels[active.value]))
const customTabs = computed(() => sections.value.filter(item => !Object.values(cvGroupLabels).includes(item.name)))
const clone = () => JSON.parse(JSON.stringify(props.modelValue))
function commit(value) { error.value = ''; emit('update:modelValue', value) }
function attempt(action) { try { action() } catch (failure) { error.value = failure.message } }
function editSkill(item) {
  // Vue props contain proxies; the transport adapter works with ordinary JSON.
  attempt(() => commit(putSkill(clone(), item.name, item.id)))
}
function eraseSkill(id) { commit(removeSkill(clone(), id)) }
function updateGroup(group, rows) {
  const next = clone()
  next[group] = group === 'other' ? [...rows, ...(next.other || []).filter(readSupplement)] : rows
  commit(next)
}
function addSection(name, builtin = false) {
  attempt(() => {
    if ((props.modelValue.other || []).length >= 100) throw new Error('You can keep up to 100 additional CV items.')
    const clean = name.trim()
    if (!builtin && Object.values(cvGroupLabels).includes(clean)) throw new Error('Use the existing section to add this information.')
    if (sections.value.some(item => item.name.toLowerCase() === clean.toLowerCase())) throw new Error('This section already exists.')
    if (!Object.values(cvGroupLabels).includes(clean) && cvGroups.some(group => t(cvGroupLabels[group]).toLowerCase() === clean.toLowerCase())) throw new Error('Use the existing section to add this information.')
    const next = clone(); next.other ||= []; next.other.push(makeSection(clean))
    active.value = Object.values(cvGroupLabels).includes(clean) ? Object.keys(cvGroupLabels).find(key => cvGroupLabels[key] === clean) : `extra:${next.other.length - 1}`
    commit(next); adding.value = false; sectionName.value = ''
  })
}
function updateSection(item, value) {
  attempt(() => { const next = clone(); next.other[item.index] = makeSection(item.name, value); commit(next) })
}
function deleteSection(item) { const next = clone(); next.other.splice(item.index, 1); active.value = 'skills'; commit(next) }
watch(() => props.locked, () => { error.value = '' })
watch(active, () => { error.value = '' })
watch(() => props.focusSection, value => { if (cvGroups.includes(value)) active.value = value }, { immediate: true })
</script>

<template>
  <section class="cv-editor" :aria-label="$t('CV information')">
    <div class="editor-intro"><div><h2>{{ $t('CV information') }}</h2><p>{{ $t('Your extracted information and additions, organised by CV section.') }}</p></div></div>
    <div class="section-nav" :aria-label="$t('CV sections')">
      <button v-for="group in cvGroups" :key="group" type="button" :disabled="pending && active !== group" :aria-pressed="active === group" @click="active = group">{{ $t(cvGroupLabels[group]) }}<span>{{ count(group) }}</span></button>
      <button v-for="section in customTabs" :key="section.key" type="button" :disabled="pending && active !== section.key" :aria-pressed="active === section.key" @click="active = section.key">{{ section.name }}</button>
      <el-button v-if="!locked" plain :disabled="skillPending" @click="adding = !adding">+ {{ $t('Add section') }}</el-button>
    </div>
    <form v-if="adding && !locked" class="section-form" @submit.prevent="addSection(sectionName)">
      <label for="cv-section-name">{{ $t('Section name') }}</label>
      <div class="form-row"><el-input id="cv-section-name" v-model="sectionName" maxlength="60" :placeholder="$t('For example: Certificates, volunteering, portfolio')"/><el-button native-type="submit" :disabled="!sectionName.trim()">{{ $t('Create section') }}</el-button><el-button @click="adding = false; sectionName = ''">{{ $t('Cancel') }}</el-button></div>
      <p class="source" role="status">{{ $t('Create this section or cancel it before saving or confirming the CV.') }}</p>
    </form>
    <div class="section-body">
      <CvSkillEditor v-if="active === 'skills'" :items="skills" :locked="locked || adding" :error="error" :suggestions="suggestions" @pending-change="skillPending = $event" @add="editSkill" @edit="editSkill" @remove="eraseSkill" @accept="emit('accept', $event)" @dismiss="emit('dismiss', $event)" />
      <template v-else-if="custom">
        <div class="section-heading"><h3>{{ custom.name }}</h3><el-button v-if="!locked" text type="danger" @click="deleteSection(custom)">{{ $t('Remove section') }}</el-button></div>
        <label class="field-label" for="cv-custom-details">{{ $t('Details') }}</label>
        <el-input id="cv-custom-details" type="textarea" :rows="6" :maxlength="sectionTextLimit(custom.name)" show-word-limit :model-value="custom.text" :readonly="locked" :placeholder="$t('Write the information you want to include in your CV.')" @update:model-value="updateSection(custom, $event)" />
        <p class="source">{{ $t('Your addition or correction') }}</p>
      </template>
      <template v-else>
        <slot name="section" :group="active">
          <h3 class="group-heading">{{ $t(cvGroupLabels[active]) }}</h3>
          <ResumeFactsTable :key="active" :group="active" :model-value="active === 'other' ? rawOther : modelValue[active] || []" :locked="locked" @update:model-value="updateGroup(active, $event)" />
        </slot>
      </template>
      <template v-if="!custom && !['skills', 'personal', 'other'].includes(active)">
        <article v-for="section in extraForGroup" :key="section.key" class="extra-details">
          <div class="section-heading"><label :for="`cv-details-${section.index}`">{{ $t('Additional details') }}</label><el-button v-if="!locked" text type="danger" @click="deleteSection(section)">{{ $t('Remove') }}</el-button></div>
          <el-input :id="`cv-details-${section.index}`" type="textarea" :rows="4" :maxlength="sectionTextLimit(section.name)" show-word-limit :model-value="section.text" :readonly="locked" @update:model-value="updateSection(section, $event)" />
          <p class="source">{{ $t('Your addition or correction') }}</p>
        </article>
        <el-button v-if="!locked && !extraForGroup.length" class="details-button" plain @click="addSection(cvGroupLabels[active], true)">+ {{ $t('Add free-text details') }}</el-button>
      </template>
      <el-checkbox v-if="requireAbsence && !locked && ['skills', 'experience', 'education', 'languages', 'projects'].includes(active) && !modelValue[active]?.length" :model-value="confirmedAbsent.includes(active.toUpperCase())" @update:model-value="value => emit('update:confirmedAbsent', value ? [...confirmedAbsent, active.toUpperCase()] : confirmedAbsent.filter(item => item !== active.toUpperCase()))">{{ $t('I have reviewed this section and have no further extracted facts to confirm.') }}</el-checkbox>
      <slot v-if="active === 'skills'" name="skills-footer" />
      <p v-if="active === 'skills' && !locked" class="compatibility-note">{{ $t('You can add any skill. Additional skills and sections are saved with your CV; the current assessment uses the recognised facts only.') }}</p>
      <p v-if="error && active !== 'skills'" class="error" role="alert">{{ $t(error) }}</p>
    </div>
  </section>
</template>

<style scoped>
.cv-editor { background:var(--sr-surface); border:1px solid var(--sr-border); border-radius:12px; overflow:hidden; }
.editor-intro { display:flex; justify-content:space-between; align-items:start; flex-wrap:wrap; gap:12px; padding:24px 24px 0; }
h2,h3,p { margin:0; } h2 { color:var(--sr-ink); font-size:21px; } h3 { color:var(--sr-text); font-size:17px; }
.editor-intro p { margin-top:6px; color:var(--sr-text-muted); font-size:13px; line-height:1.8; }
.section-nav { display:flex; flex-wrap:wrap; gap:8px; padding:20px 24px; border-bottom:1px solid var(--sr-border); }
.section-nav > button:not(.el-button) { display:flex; align-items:center; gap:8px; border:1px solid var(--sr-border); border-radius:7px; padding:9px 12px; background:var(--sr-surface); color:var(--sr-text-secondary); cursor:pointer; font:inherit; font-size:13px; overflow-wrap:anywhere; max-width:100%; }
.section-nav > button[aria-pressed="true"] { background:var(--sr-primary); color:#fff; border-color:var(--sr-primary); }
.section-nav span { opacity:.75; font-size:12px; }
.section-nav .el-button { margin:0; height:auto; }
.section-nav button:focus-visible { outline:2px solid var(--sr-accent); outline-offset:3px; }
.section-nav button:disabled { opacity:.5; cursor:default; }
.section-body { padding:24px; min-width:0; } .section-form { padding:18px 24px; background:var(--sr-surface-tint); }
.section-form label,.field-label { display:block; margin-bottom:8px; font-size:13px; color:var(--sr-text); }
.form-row { display:flex; flex-wrap:wrap; gap:8px; } .form-row .el-input { flex:1; min-width:160px; } .form-row .el-button { margin:0; }
.section-heading { display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px; margin-bottom:14px; }
.source { color:var(--sr-text-muted); font-size:12px; margin-top:8px; }
.group-heading { margin-bottom:18px; } .extra-details { border-top:1px solid var(--sr-border); padding-top:20px; margin-top:20px; }
.details-button { margin-top:18px; }
.compatibility-note { margin-top:20px; color:var(--sr-text-muted); font-size:12px; line-height:1.8; }
.error { color:var(--el-color-danger); margin-top:12px; font-size:13px; }
:deep(.el-checkbox) { height:auto; margin-top:18px; } :deep(.el-checkbox__label) { white-space:normal; line-height:1.7; }
@media(max-width:600px) { .editor-intro,.section-body,.section-form { padding:18px; } .editor-intro { padding-bottom:0; } .section-nav { padding:16px 18px; } .form-row .el-input { flex-basis:100%; } }
</style>
