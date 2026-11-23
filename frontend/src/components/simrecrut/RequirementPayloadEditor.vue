<!-- Edit a job requirement's structured payload with form fields instead of raw JSON. -->
<script setup>
import { computed } from 'vue'
import { ElButton, ElCheckbox, ElInputNumber, ElOption, ElSelect } from 'element-plus'
import { requirementCodes } from '../../config/requirement-codes.js'

const props = defineProps({ modelValue: { type: String, default: '{}' }, category: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])

// The API still stores the payload as text; unreadable text starts an empty form.
const payload = computed(() => {
  try {
    const value = JSON.parse(props.modelValue || '{}')
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch { return {} }
})
function set(key, value, extra = {}) {
  const next = { ...payload.value }
  for (const [field, item] of Object.entries({ [key]: value, ...extra })) {
    const empty = item === undefined || item === null || item === '' || (Array.isArray(item) && !item.length)
    if (empty && field !== 'unrestrictedSubject') delete next[field]
    else next[field] = item
  }
  emit('update:modelValue', JSON.stringify(next, null, 2))
}
const list = key => Array.isArray(payload.value[key]) ? payload.value[key] : []
const languages = computed(() => list('requiredLanguages'))
function setLanguage(index, field, value) {
  set('requiredLanguages', languages.value.map((item, position) => position === index ? { ...item, [field]: value } : item))
}
const addLanguage = () => set('requiredLanguages', [...languages.value, { languageCode: '', minimumLevelCode: '' }])
const removeLanguage = index => set('requiredLanguages', languages.value.filter((_, position) => position !== index))
// Codes read better as words: SPRING_BOOT becomes "Spring Boot"; short codes such as EN or B2 stay as they are.
const label = code => code.length <= 3 ? code : code.toLowerCase().split('_').map(word => word[0].toUpperCase() + word.slice(1)).join(' ')
</script>

<template>
  <div class="payload-editor">
    <label v-if="category === 'Skills'" class="wide">{{ $t("Assessed skills") }}
      <ElSelect :model-value="list('assessedSkillCodes')" multiple filterable allow-create default-first-option
        :placeholder="$t('Choose or type codes')" @update:model-value="value => set('assessedSkillCodes', value)">
        <ElOption v-for="code in requirementCodes.skillCodes" :key="code" :label="label(code)" :value="code" />
      </ElSelect>
    </label>

    <template v-else-if="category === 'Experience'">
      <label>{{ $t("Target years") }}
        <ElInputNumber :model-value="payload.targetYears ?? undefined" :min="0" :max="50" :step="0.5"
          @update:model-value="value => set('targetYears', value)" />
      </label>
      <label>{{ $t("Minimum years (optional)") }}
        <ElInputNumber :model-value="payload.minimumYears ?? undefined" :min="0" :max="50" :step="0.5"
          @update:model-value="value => set('minimumYears', value)" />
      </label>
      <label class="wide">{{ $t("Relevant role families") }}
        <ElSelect :model-value="list('relevantRoleFamilyCodes')" multiple filterable allow-create default-first-option
          :placeholder="$t('Choose or type codes')" @update:model-value="value => set('relevantRoleFamilyCodes', value)">
          <ElOption v-for="code in requirementCodes.roleFamilyCodes" :key="code" :label="label(code)" :value="code" />
        </ElSelect>
      </label>
    </template>

    <template v-else-if="category === 'Education'">
      <label>{{ $t("Minimum degree") }}
        <ElSelect :model-value="payload.minimumDegreeLevelCode || ''" filterable allow-create default-first-option
          :placeholder="$t('Choose a degree level')" @update:model-value="value => set('minimumDegreeLevelCode', value)">
          <ElOption v-for="code in requirementCodes.degreeLevelCodes" :key="code" :label="label(code)" :value="code" />
        </ElSelect>
      </label>
      <div class="check-field">
        <ElCheckbox :model-value="payload.unrestrictedSubject === true"
          @update:model-value="value => set('unrestrictedSubject', value, value ? { acceptedSubjectCodes: [] } : {})">{{ $t("Any subject is accepted") }}</ElCheckbox>
      </div>
      <label v-if="payload.unrestrictedSubject !== true" class="wide">{{ $t("Accepted subjects") }}
        <ElSelect :model-value="list('acceptedSubjectCodes')" multiple filterable allow-create default-first-option
          :placeholder="$t('Choose or type codes')" @update:model-value="value => set('acceptedSubjectCodes', value)">
          <ElOption v-for="code in requirementCodes.subjectCodes" :key="code" :label="label(code)" :value="code" />
        </ElSelect>
      </label>
    </template>

    <div v-else-if="category === 'Languages'" class="wide languages">
      <span class="field-title">{{ $t("Required languages") }}</span>
      <div v-for="(item, index) in languages" :key="index" class="language-row">
        <ElSelect :model-value="item.languageCode" filterable allow-create default-first-option :aria-label="$t('Language')"
          :placeholder="$t('Language')" @update:model-value="value => setLanguage(index, 'languageCode', value)">
          <ElOption v-for="code in requirementCodes.languageCodes" :key="code" :label="code" :value="code" />
        </ElSelect>
        <ElSelect :model-value="item.minimumLevelCode" filterable allow-create default-first-option :aria-label="$t('Minimum level')"
          :placeholder="$t('Minimum level')" @update:model-value="value => setLanguage(index, 'minimumLevelCode', value)">
          <ElOption v-for="code in requirementCodes.languageLevelCodes" :key="code" :label="code" :value="code" />
        </ElSelect>
        <ElButton text type="danger" @click="removeLanguage(index)">{{ $t("Remove") }}</ElButton>
      </div>
      <ElButton size="small" @click="addLanguage">{{ $t("Add language") }}</ElButton>
    </div>

    <label v-else-if="category === 'Projects'" class="wide">{{ $t("Assessed project conditions") }}
      <ElSelect :model-value="list('assessedConditionCodes')" multiple filterable allow-create default-first-option
        :placeholder="$t('Choose or type codes')" @update:model-value="value => set('assessedConditionCodes', value)">
        <ElOption v-for="code in requirementCodes.conditionCodes" :key="code" :label="label(code)" :value="code" />
      </ElSelect>
    </label>
  </div>
</template>

<style scoped>
.payload-editor { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; margin-bottom: 6px; }
.payload-editor label { display: grid; gap: 8px; color: var(--sr-ink); font-size: 13px; margin-bottom: 16px; }
.wide { grid-column: 1 / -1; }
.el-select, .el-input-number { width: 100%; }
.check-field { display: flex; align-items: flex-end; margin-bottom: 16px; }
.languages { display: grid; gap: 10px; margin-bottom: 16px; justify-items: start; }
.field-title { color: var(--sr-ink); font-size: 13px; }
.language-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: 10px; width: 100%; }
@media (max-width: 600px) { .payload-editor { grid-template-columns: minmax(0, 1fr); } }
</style>
