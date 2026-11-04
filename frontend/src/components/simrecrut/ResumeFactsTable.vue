<!-- Present editable professional facts without exposing JSON syntax. -->
<script setup>
import { computed } from 'vue'
const props = defineProps({ group: String, modelValue: { type: Array, default: () => [] }, locked: Boolean })
const emit = defineEmits(['update:modelValue'])
const fields = {
 skills: [['skillCode','Skill']],
 experience: [['roleFamilyCode','Role'],['startMonth','Start month'],['endMonth','End month'],['isCurrent','Current role'],['supportedSkillCodes','Skills'],['reviewedRelevantYears','Relevant years']],
 education: [['degreeLevelCode','Degree'],['subjectCode','Subject']],
 languages: [['languageCode','Language'],['levelCode','Level']],
 projects: [['projectId','Project'],['supportedSkillCodes','Skills'],['supportedConditionCodes','Conditions']],
}
const columns = computed(() => fields[props.group] || [])
function update(index, key, value) {
 const rows = props.modelValue.map(row => ({ ...row, evidence: { ...row.evidence } }))
 if (key.endsWith('Codes')) value = value.split(',').map(item => item.trim()).filter(Boolean)
 if (key.endsWith('Month')) value = value || null
 rows[index][key] = value
 if (key === 'isCurrent' && value) rows[index].endMonth = null
 rows[index].evidence = { text: '', page: null, ...rows[index].evidence, source: 'USER_SUPPLIED' }
 emit('update:modelValue', rows)
}
function add() {
 const row = Object.fromEntries(columns.value.map(([key]) => [key, key.endsWith('Codes') ? [] : key === 'isCurrent' ? false : key.endsWith('Month') || key === 'reviewedRelevantYears' ? null : '']))
 row.evidence = { text: '', page: null, source: 'USER_SUPPLIED' }
 emit('update:modelValue', [...props.modelValue, row])
}
</script>
<template>
 <div>
  <el-empty v-if="!modelValue.length" description="No facts found. Add missing information if needed." :image-size="65" />
  <article v-for="(row,index) in modelValue" :key="index" class="fact-row">
   <div class="fact-fields">
    <label v-for="[key,label] in columns" :key="key"><span>{{ label }}</span>
     <el-switch v-if="key === 'isCurrent'" :model-value="row[key]" :disabled="locked" @update:model-value="update(index,key,$event)" />
     <el-input-number v-else-if="key === 'reviewedRelevantYears'" :model-value="row[key] ?? undefined" :min="0" :max="80" :disabled="locked" @update:model-value="update(index,key,$event ?? null)" />
     <el-input v-else :model-value="Array.isArray(row[key]) ? row[key].join(', ') : row[key] || ''" :readonly="locked" :disabled="key === 'endMonth' && row.isCurrent" :placeholder="key.endsWith('Month') ? 'YYYY-MM or leave empty' : key.endsWith('Codes') ? 'Separate with commas' : label" @update:model-value="update(index,key,$event)" />
    </label>
   </div>
   <p class="evidence"><el-tag size="small">{{ row.evidence?.source === 'USER_SUPPLIED' ? 'Your correction' : 'From CV' }}</el-tag> <span v-if="row.evidence?.page">Page {{ row.evidence.page }} · </span>{{ row.evidence?.text || 'No supporting excerpt available' }}</p>
   <el-button v-if="!locked" type="danger" plain size="small" @click="emit('update:modelValue',modelValue.filter((_,i) => i !== index))">Remove fact</el-button>
  </article>
  <el-button v-if="!locked && modelValue.length < 100" plain @click="add">Add fact</el-button>
 </div>
</template>
<style scoped>
.fact-row { border: 1px solid #dce5ef; border-radius: 8px; padding: 18px; margin-bottom: 14px; background: #fbfdff; }
.fact-fields { display: grid; grid-template-columns: repeat(auto-fit,minmax(210px,1fr)); gap: 16px; }
label { display: flex; flex-direction: column; gap: 8px; color: #40536a; font-size: 14px; }
.evidence { color: #60728a; font-size: 13px; overflow-wrap: anywhere; line-height: 1.8; }
.evidence .el-tag { margin-right: 8px; }
</style>
