<script setup>
import { ElButton, ElCheckbox, ElInput, ElOption, ElSelect } from 'element-plus'

const props = defineProps({
  definition: { type: Object, required: true }, group: { type: Object, required: true },
  errors: { type: Object, required: true }, required: Boolean, disabled: Boolean,
})
const emit = defineEmits(['change', 'add', 'remove', 'evidence'])
const errorKey = (item, field) => `${props.definition.key}.${item.id}.${field.key}`

function editField(item, field, value) {
  item[field.key].value = value
  item[field.key].status = 'USER_SUPPLIED'
  emit('change')
}
</script>

<template>
  <section class="fact-group" :aria-labelledby="`group-${definition.key}`">
    <div class="group-heading">
      <h2 :id="`group-${definition.key}`">{{ definition.label }}</h2>
      <span>{{ required ? 'Required by demo criteria' : 'Optional group' }}</span>
    </div>
    <p v-if="errors[definition.key]" class="field-error" role="alert">{{ errors[definition.key] }}</p>
    <p v-if="!group.items.length" class="empty-message">No {{ definition.label.toLowerCase() }} added.</p>
    <article v-for="(item, index) in group.items" :key="item.id" class="fact-item">
      <div class="item-heading"><h3>{{ definition.singular }} {{ index + 1 }}</h3>
        <ElButton text type="danger" :disabled="disabled" :aria-label="`Remove ${definition.singular} ${index + 1}`"
          @click="emit('remove', item.id)">Remove</ElButton></div>
      <div v-for="field in definition.fields" :key="field.key" class="fact-field">
        <label :for="`fact-${item.id}-${field.key}`">{{ field.label }}</label>
        <ElSelect v-if="field.options" :id="`fact-${item.id}-${field.key}`" :model-value="item[field.key].value"
          :aria-label="`${definition.singular} ${index + 1}: ${field.label}`" :disabled="disabled" placeholder="Choose a value"
          :aria-invalid="Boolean(errors[errorKey(item, field)])" :aria-describedby="errors[errorKey(item, field)] ? `error-${item.id}-${field.key}` : undefined"
          @update:model-value="editField(item, field, $event)">
          <ElOption v-for="option in field.options" :key="option" :value="option" :label="option" />
        </ElSelect>
        <ElInput v-else :id="`fact-${item.id}-${field.key}`" :model-value="item[field.key].value"
          :aria-label="`${definition.singular} ${index + 1}: ${field.label}`" :disabled="disabled"
          :type="field.type === 'number' ? 'number' : field.key === 'summary' ? 'textarea' : 'text'"
          :min="field.type === 'number' ? 0 : undefined" :max="field.type === 'number' ? field.max : undefined"
          :maxlength="field.type === 'number' ? undefined : field.max" :rows="2"
          :aria-invalid="Boolean(errors[errorKey(item, field)])" :aria-describedby="errors[errorKey(item, field)] ? `error-${item.id}-${field.key}` : undefined"
          @update:model-value="editField(item, field, $event)" />
        <p v-if="errors[errorKey(item, field)]" :id="`error-${item.id}-${field.key}`" class="field-error" role="alert">{{ errors[errorKey(item, field)] }}</p>
        <div class="field-actions"><span>{{ item[field.key].status.replaceAll('_', ' ') }}</span>
          <ElButton text :aria-label="`View evidence for ${definition.singular} ${index + 1}: ${field.label}`"
            @click="emit('evidence', { label: `${definition.singular} ${index + 1}: ${field.label}`, field: item[field.key], itemId: item.id, inputId: `fact-${item.id}-${field.key}` })">View Evidence</ElButton>
          <ElButton v-if="['UNKNOWN', 'CONFLICT'].includes(item[field.key].status) && String(item[field.key].value).trim()"
            text :disabled="disabled" @click="editField(item, field, item[field.key].value)">Use This Value</ElButton>
        </div>
      </div>
    </article>
    <ElCheckbox v-if="!group.items.length" v-model="group.confirmedAbsent" :disabled="disabled" @change="emit('change')">
      I confirm I have no {{ definition.label.toLowerCase() }} to report.
    </ElCheckbox>
    <ElButton class="add-button" :disabled="disabled" @click="emit('add')">Add {{ definition.singular }}</ElButton>
  </section>
</template>

<style scoped>
.fact-group { padding: 24px; border: 1px solid #e1eaf5; border-radius: 10px; background: white; }
.group-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
h2 { margin: 0; color: #20354e; font-size: 17px; }
.group-heading span, .empty-message { color: #68798c; font-size: 12px; line-height: 1.7; }
.fact-item { margin-top: 18px; padding: 18px; border: 1px solid #edf1f7; border-radius: 8px; background: #fbfdff; }
.item-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
h3 { margin: 0; color: #425269; font-size: 14px; }
.fact-field + .fact-field { margin-top: 16px; }
label { display: block; margin-bottom: 7px; color: #425269; font-size: 13px; font-weight: 600; }
.el-select { width: 100%; }
.field-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; margin-top: 5px; }
.field-actions span { font-size: 10px; color: #68798c; }
.field-actions .el-button { font-size: 12px; margin: 0; padding: 4px 0; height: auto; }
.field-error { margin: 7px 0; color: #b42318; font-size: 12px; line-height: 1.7; }
.add-button { display: block; margin-top: 18px; }
:deep(.el-checkbox) { height: auto; margin-top: 12px; }
:deep(.el-checkbox__label) { white-space: normal; font-size: 12px; line-height: 1.7; }
@media (max-width: 700px) { .fact-group { padding: 20px; } .fact-item { padding: 14px; } }
</style>
