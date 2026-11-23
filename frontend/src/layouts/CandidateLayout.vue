<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { demoMode } from '../config/runtime.js'
import { useAuthStore } from '../store/modules/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

async function signOut() {
  try { await auth.logout() }
  finally { await router.replace({ name: 'Login' }) }
}
</script>

<template>
  <div class="candidate-layout">
    <header>
      <div class="header-content">
        <BrandMark />
        <nav :aria-label="$t(&quot;Candidate navigation&quot;)">
          <RouterLink class="jobs-link" :to="{ name: 'PublishedJobs' }"
            :class="{ 'current-link': ['PublishedJobs', 'JobDetail'].includes(route.name) }"
            :aria-current="['PublishedJobs', 'JobDetail'].includes(route.name) ? 'page' : undefined">{{ $t("Jobs") }}</RouterLink>
          <RouterLink v-if="auth.isDemoSignedIn" class="jobs-link" :to="{ name: 'MySimulations' }"
            :class="{ 'current-link': ['MySimulations', 'CvUpload', 'CvReview', 'SimulationProgress', 'SimulationResults'].includes(route.name) }"
            :aria-current="['MySimulations', 'CvUpload', 'CvReview', 'SimulationProgress', 'SimulationResults'].includes(route.name) ? 'page' : undefined">{{ $t("My simulations") }}</RouterLink>
          <RouterLink v-if="!demoMode && auth.isDemoSignedIn" class="jobs-link" :to="{ name: 'ConnectedResumes' }"
            :class="{ 'current-link': route.meta.connected }"
            :aria-current="route.meta.connected ? 'page' : undefined">{{ $t("My CVs") }}</RouterLink>
        </nav>
        <div v-if="auth.isDemoSignedIn" class="session-controls">
          <span class="user-name">{{ auth.demoUser?.displayName }}</span>
          <ElButton @click="router.push({ name: 'DemoDashboard' })">{{ $t("Dashboard") }}</ElButton>
          <ElButton @click="signOut">{{ $t("Sign out") }}</ElButton>
        </div>
        <div v-else class="session-controls">
          <span class="user-name">{{ $t("Visitor") }}</span>
          <ElButton @click="router.push({ name: 'Login', query: { redirect: route.fullPath } })">{{ $t("Sign in") }}</ElButton>
          <ElButton type="primary" @click="router.push({ name: 'Register' })">{{ $t("Create an account") }}</ElButton>
        </div>
      </div>
    </header>
    <main><slot /></main>
    <footer>{{ $t("SimRecrut") }} <span>·</span> {{ $t("Candidate workspace") }} <span>·</span> {{ $t(demoMode ? 'Frontend demonstration' : 'Connected workspace') }}</footer>
  </div>
</template>

<style scoped>
.candidate-layout { min-height: 100svh; display: flex; flex-direction: column; }
header { position: sticky; top: 0; z-index: 20; background: var(--sr-surface); border-bottom: 1px solid var(--sr-border); box-shadow: var(--sr-shadow-sm); }
.header-content { display: flex; align-items: center; gap: 32px; max-width: 1240px; margin: auto; padding: 18px 32px; }
:deep(.brand-mark .brand-name) { font-size: 25px; letter-spacing: -0.8px; }
:deep(.brand-mark svg) { width: 32px; height: 32px; }
nav { display: flex; align-self: stretch; align-items: center; gap: 24px; }
.jobs-link { color: var(--sr-text-secondary); font-size: 14px; font-weight: 600; text-decoration: none; padding: 10px 2px; border-bottom: 3px solid transparent; transition: color .15s, border-color .15s; }
.jobs-link:hover { color: var(--sr-ink); }
.current-link { color: var(--sr-ink); border-bottom-color: var(--sr-accent); }
.session-controls { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.session-controls .el-button { margin: 0; }
.user-name { max-width: 160px; overflow-wrap: anywhere; margin-right: 6px; color: var(--sr-text-muted); font-size: 13px; }
main { flex: 1; width: min(1240px, 100%); margin: auto; padding: 32px; }
footer { border-top: 1px solid var(--sr-border); background: var(--sr-surface); padding: 16px 24px 24px; color: var(--sr-text-muted); text-align: center; font-size: 12px; line-height: 1.7; }
footer span { margin: 0 8px; }
@media (max-width: 1000px) {
  .header-content { gap: 20px; }
  .user-name { display: none; }
}
@media (max-width: 700px) {
  .header-content { flex-wrap: wrap; gap: 14px; padding: 16px; }
  :deep(.brand-mark) { gap: 8px; }
  :deep(.brand-mark .brand-name) { font-size: 22px; }
  :deep(.brand-mark svg) { width: 28px; height: 28px; }
  .session-controls { gap: 6px; }
  .session-controls .el-button { padding: 8px 10px; font-size: 12px; }
  nav { width: 100%; order: 3; gap: 24px; }
  main { padding: 24px 16px; }
}
</style>
