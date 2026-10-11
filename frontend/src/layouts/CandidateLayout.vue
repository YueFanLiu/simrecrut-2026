<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

function signOut() {
  auth.logout()
  router.replace({ name: 'Login' })
}
</script>

<template>
  <div class="candidate-layout">
    <header>
      <div class="header-content">
        <BrandMark />
        <nav aria-label="Candidate navigation">
          <RouterLink class="jobs-link" :to="{ name: 'PublishedJobs' }"
            :class="{ 'current-link': ['PublishedJobs', 'JobDetail'].includes(route.name) }"
            :aria-current="['PublishedJobs', 'JobDetail'].includes(route.name) ? 'page' : undefined">Jobs</RouterLink>
          <RouterLink class="jobs-link" :to="{ name: 'MySimulations' }"
            :class="{ 'current-link': ['MySimulations', 'CvUpload', 'CvReview', 'SimulationProgress', 'SimulationResults'].includes(route.name) }"
            :aria-current="['MySimulations', 'CvUpload', 'CvReview', 'SimulationProgress', 'SimulationResults'].includes(route.name) ? 'page' : undefined">My simulations</RouterLink>
        </nav>
        <div class="session-controls">
          <span class="user-name">{{ auth.demoUser?.displayName }}</span>
          <ElButton @click="router.push({ name: 'DemoDashboard' })">Dashboard</ElButton>
          <ElButton @click="signOut">Sign out</ElButton>
        </div>
      </div>
    </header>
    <main><slot /></main>
    <footer>SimRecrut <span>·</span> Candidate workspace <span>·</span> Frontend demonstration</footer>
  </div>
</template>

<style scoped>
.candidate-layout { min-height: 100svh; display: flex; flex-direction: column; }
header { background: white; border-bottom: 1px solid #e1eaf5; }
.header-content { display: flex; align-items: center; gap: 32px; max-width: 1240px; margin: auto; padding: 18px 32px; }
:deep(.brand-mark span) { font-size: 25px; letter-spacing: -0.8px; }
:deep(.brand-mark svg) { width: 32px; height: 32px; }
nav { display: flex; align-self: stretch; align-items: center; gap: 24px; }
.jobs-link { color: #586b83; font-size: 14px; font-weight: 600; text-decoration: none; padding: 10px 2px; border-bottom: 2px solid transparent; }
.current-link { color: #176bdf; border-bottom-color: #176bdf; }
.session-controls { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.session-controls .el-button { margin: 0; }
.user-name { max-width: 160px; overflow-wrap: anywhere; margin-right: 6px; color: #586b83; font-size: 13px; }
main { flex: 1; width: min(1240px, 100%); margin: auto; padding: 32px; }
footer { padding: 16px 24px 24px; color: #60728a; text-align: center; font-size: 12px; line-height: 1.7; }
footer span { margin: 0 8px; }
@media (max-width: 1000px) {
  .header-content { gap: 20px; }
  .user-name { display: none; }
}
@media (max-width: 700px) {
  .header-content { flex-wrap: wrap; gap: 14px; padding: 16px; }
  :deep(.brand-mark) { gap: 8px; }
  :deep(.brand-mark span) { font-size: 22px; }
  :deep(.brand-mark svg) { width: 28px; height: 28px; }
  .session-controls { gap: 6px; }
  .session-controls .el-button { padding: 8px 10px; font-size: 12px; }
  nav { width: 100%; order: 3; gap: 24px; }
  main { padding: 24px 16px; }
}
</style>
