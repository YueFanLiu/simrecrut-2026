<script setup>
import { ElConfigProvider } from 'element-plus'
import en from 'element-plus/es/locale/lang/en'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const wizardPages = ['HrJobEdit', 'HrJobRequirements', 'HrJobWeights']
const pageKey = computed(() => wizardPages.includes(route.name)
  ? `hr-job-wizard-${route.params.jobId}`
  : route.path)
</script>

<template>
  <ElConfigProvider :locale="en">
    <!-- Keep draft edits when navigating between steps of the same job. -->
    <RouterView :key="pageKey" />
  </ElConfigProvider>
</template>
