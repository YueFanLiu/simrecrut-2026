<script setup>
import { ref } from 'vue'
import { ElInput } from 'element-plus'
import FieldIcon from './FieldIcon.vue'

const model = defineModel({ type: String, default: '' })
defineProps({
  placeholder: { type: String, default: 'Enter your password' },
  autocomplete: { type: String, default: 'new-password' },
  disabled: { type: Boolean, default: false },
})
const visible = ref(false)
</script>

<template>
  <ElInput v-model="model" :type="visible ? 'text' : 'password'" :placeholder="placeholder"
    :autocomplete="autocomplete" :disabled="disabled" size="large">
    <template #prefix><FieldIcon name="lock" /></template>
    <template #suffix>
      <button type="button" class="password-toggle" :disabled="disabled"
        :aria-label="visible ? 'Hide password' : 'Show password'" :aria-pressed="visible"
        @click="visible = !visible">
        <FieldIcon :name="visible ? 'eye' : 'eye-off'" />
      </button>
    </template>
  </ElInput>
</template>

<style scoped>
.password-toggle {
  display: flex;
  padding: 4px;
  border: 0;
  background: transparent;
  color: #68798c;
  cursor: pointer;
}
.password-toggle:focus-visible { outline: 2px solid #176bdf; border-radius: 3px; }
.password-toggle:disabled { cursor: default; }
</style>
