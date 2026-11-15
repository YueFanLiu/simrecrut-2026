<script setup>
import { ElConfigProvider } from 'element-plus'
import { elementLocale } from './i18n/index.js'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const wizardPages = ['HrJobEdit', 'HrJobRequirements', 'HrJobWeights']
const pageKey = computed(() => wizardPages.includes(route.name)
  ? `hr-job-wizard-${route.params.jobId}`
  : route.path)
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <LanguageSwitcher />
    <!-- Keep draft edits when navigating between steps of the same job. -->
    <RouterView :key="pageKey" />
  </ElConfigProvider>
</template>
