<!-- Present editable CV facts without exposing JSON syntax. -->
<script setup>
import { computed } from 'vue'
const props = defineProps({ group: String, modelValue: { type: Array, default: () => [] }, locked: Boolean })
const emit = defineEmits(['update:modelValue'])
const fields = {
 personal: [['fieldCode','Field'],['value','Value']],
 skills: [['skillCode','Skill']],
 experience: [['roleFamilyCode','Role'],['startMonth','Start month'],['endMonth','End month'],['isCurrent','Current role'],['supportedSkillCodes','Skills'],['reviewedRelevantYears','Relevant years']],
 education: [['degreeLevelCode','Degree'],['subjectCode','Subject']],
 languages: [['languageCode','Language'],['levelCode','Level']],
 projects: [['projectId','Project'],['supportedSkillCodes','Skills'],['supportedConditionCodes','Conditions']],
 other: [['categoryCode','Category'],['value','Details']],
}
// Personal and other information use fixed codes; every value stays editable.
const options = {
 fieldCode: [['FULL_NAME','Full name'],['FIRST_NAME','First name'],['LAST_NAME','Last name'],['GENDER','Gender'],
  ['PHOTO','Photo'],['EMAIL','Email'],['PHONE','Phone'],['ADDRESS','Address'],['CITY','City'],['COUNTRY','Country'],
  ['LINKEDIN','LinkedIn'],['GITHUB','GitHub'],['WEBSITE','Website'],['NATIONALITY','Nationality'],
  ['DATE_OF_BIRTH','Date of birth'],['OTHER','Other']],
 categoryCode: [['INTEREST','Interests and hobbies'],['VOLUNTEERING','Volunteering and associations'],
  ['CERTIFICATION','Certification'],['AWARD','Award'],['ACTIVITY','Activity'],['PUBLICATION','Publication'],
  ['REFERENCE','Reference'],['OTHER','Other']],
}
const columns = computed(() => fields[props.group] || [])
const isPhoto = row => props.group === 'personal' && row.fieldCode === 'PHOTO'
function copy() { return props.modelValue.map(row => ({ ...row, evidence: { ...row.evidence } })) }
function update(index, key, value) {
 const rows = copy()
 if (key.endsWith('Codes')) value = value.split(',').map(item => item.trim()).filter(Boolean)
 if (key.endsWith('Month')) value = value || null
 rows[index][key] = value
 if (key === 'isCurrent' && value) rows[index].endMonth = null
 if (key === 'fieldCode' && value !== 'PHOTO') delete rows[index].imageBase64
 rows[index].evidence = { text: '', page: null, ...rows[index].evidence, source: 'USER_SUPPLIED' }
 emit('update:modelValue', rows)
}
function removePhoto(index) {
 const rows = copy()
 delete rows[index].imageBase64
 rows[index].value = 'NO'
 rows[index].evidence = { text: '', page: null, ...rows[index].evidence, source: 'USER_SUPPLIED' }
 emit('update:modelValue', rows)
}
function add() {
 const row = Object.fromEntries(columns.value.map(([key]) => [key, options[key] ? 'OTHER' : key.endsWith('Codes') ? [] : key === 'isCurrent' ? false : key.endsWith('Month') || key === 'reviewedRelevantYears' ? null : '']))
 row.evidence = { text: '', page: null, source: 'USER_SUPPLIED' }
 emit('update:modelValue', [...props.modelValue, row])
}
</script>
<template>
 <div>
  <el-empty v-if="!modelValue.length" :description="$t(&quot;No facts found. Add missing information if needed.&quot;)" :image-size="65" />
  <article v-for="(row,index) in modelValue" :key="index" class="fact-row">
   <div class="fact-fields">
    <label v-for="[key,label] in columns" :key="key" :class="{ wide: key === 'value' }"><span>{{ $t(label) }}</span>
     <el-switch v-if="key === 'isCurrent'" :model-value="row[key]" :disabled="locked" @update:model-value="update(index,key,$event)" />
     <el-input-number v-else-if="key === 'reviewedRelevantYears'" :model-value="row[key] ?? undefined" :min="0" :max="80" :disabled="locked" @update:model-value="update(index,key,$event ?? null)" />
     <el-select v-else-if="options[key]" :model-value="row[key]" :disabled="locked" :aria-label="$t(label)" @update:model-value="update(index,key,$event)">
      <el-option v-for="[code, name] in options[key]" :key="code" :value="code" :label="$t(name)" />
     </el-select>
     <el-select v-else-if="key === 'value' && isPhoto(row)" :model-value="row.value" :disabled="locked" :aria-label="$t(label)" @update:model-value="update(index,key,$event)">
      <el-option value="YES" :label="$t('Photo on the CV')" /><el-option value="NO" :label="$t('No photo')" />
     </el-select>
     <el-input v-else-if="key === 'value'" type="textarea" :autosize="{ minRows: 1, maxRows: 6 }" maxlength="2000" :model-value="row[key] || ''" :readonly="locked" :placeholder="$t(label)" @update:model-value="update(index,key,$event)" />
     <el-input v-else :model-value="Array.isArray(row[key]) ? row[key].join(', ') : row[key] || ''" :readonly="locked" :disabled="key === 'endMonth' && row.isCurrent" :placeholder="$t(key.endsWith('Month') ? 'YYYY-MM or leave empty' : key.endsWith('Codes') ? 'Separate with commas' : label)" @update:model-value="update(index,key,$event)" />
    </label>
   </div>
   <div v-if="isPhoto(row) && row.imageBase64" class="photo">
    <img :src="'data:image/jpeg;base64,' + row.imageBase64" :alt="$t('Photo extracted from the CV')" />
    <el-button v-if="!locked" size="small" plain @click="removePhoto(index)">{{ $t("Remove photo") }}</el-button>
   </div>
   <p class="evidence"><el-tag size="small">{{ $t(row.evidence?.source === 'USER_SUPPLIED' ? 'Your correction' : 'From CV') }}</el-tag> <span v-if="row.evidence?.page">{{ $t("Page") }} {{ row.evidence.page }} · </span>{{ row.evidence?.text || $t('No supporting excerpt available') }}</p>
   <el-button v-if="!locked" type="danger" plain size="small" @click="emit('update:modelValue',modelValue.filter((_,i) => i !== index))">{{ $t("Remove fact") }}</el-button>
  </article>
  <el-button v-if="!locked && modelValue.length < 100" plain @click="add">{{ $t("Add fact") }}</el-button>
 </div>
</template>
<style scoped>
.fact-row { border: 1px solid var(--sr-border); border-radius: 8px; padding: 18px; margin-bottom: 14px; background: var(--sr-surface-tint); }
.fact-fields { display: grid; grid-template-columns: repeat(auto-fit,minmax(210px,1fr)); gap: 16px; }
.fact-fields .wide { grid-column: span 2; }
label { display: flex; flex-direction: column; gap: 8px; color: var(--sr-text-secondary); font-size: 14px; }
.photo { display: flex; align-items: center; gap: 14px; margin-top: 14px; }
.photo img { width: 96px; height: 96px; object-fit: cover; border-radius: 8px; border: 1px solid var(--sr-border); }
.evidence { color: var(--sr-text-muted); font-size: 13px; overflow-wrap: anywhere; line-height: 1.8; }
.evidence .el-tag { margin-right: 8px; }
@media (max-width: 600px) { .fact-fields .wide { grid-column: auto; } }
</style>
