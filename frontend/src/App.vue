<script setup>
import { ElConfigProvider } from 'element-plus'
import { elementLocale } from './i18n/index.js'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './store/modules/auth.js'

const route = useRoute()
const auth = useAuthStore()
// Unmount private pages as soon as the session ends. A page's unsaved-change
// guard must never keep the previous user's CV visible after sign-out.
const canRenderRoute = computed(() => !route.meta.requiresDemoSession || auth.isDemoSignedIn)
const wizardPages = ['HrJobEdit', 'HrJobRequirements', 'HrJobWeights']
const routeKey = computed(() => wizardPages.includes(route.name)
  ? `hr-job-wizard-${route.params.jobId}`
  : route.path)
const pageKey = computed(() => route.meta.requiresDemoSession
  ? `${auth.demoUser?.username}:${routeKey.value}` : routeKey.value)
</script>

<template>
  <ElConfigProvider :locale="elementLocale">
    <LanguageSwitcher />
    <!-- Keep draft edits when navigating between steps of the same job. -->
    <RouterView v-if="canRenderRoute" :key="pageKey" />
  </ElConfigProvider>
</template>
