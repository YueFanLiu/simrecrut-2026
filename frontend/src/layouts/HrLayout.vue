<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
function signOut() { auth.logout(); router.replace({ name: 'Login' }) }
</script>

<template>
  <div class="hr-layout">
    <header><div class="header-content"><BrandMark /><span class="workspace-label">HR workspace</span>
      <div class="session-controls"><span class="user-name">{{ auth.demoUser?.displayName }}</span>
        <ElButton @click="router.push({ name: 'DemoDashboard' })">Dashboard</ElButton><ElButton @click="signOut">Sign out</ElButton></div></div></header>
    <div class="workspace"><aside class="sidebar"><nav aria-label="HR navigation">
      <RouterLink :to="{ name: 'HrJobs' }" :aria-current="!['HrJobNew', 'HrCases', 'HrCaseDetail', 'MyReviewTasks', 'ReviewTaskDetail'].includes(route.name) ? 'page' : undefined">Job Management</RouterLink>
      <RouterLink :to="{ name: 'HrJobNew' }" :aria-current="route.name === 'HrJobNew' ? 'page' : undefined">Create Job</RouterLink>
      <RouterLink :to="{ name: 'HrCases' }" :aria-current="['HrCases', 'HrCaseDetail'].includes(route.name) ? 'page' : undefined">Case Results</RouterLink>
      <RouterLink :to="{ name: 'MyReviewTasks' }" :aria-current="['MyReviewTasks', 'ReviewTaskDetail'].includes(route.name) ? 'page' : undefined">My Review Tasks</RouterLink>
    </nav><p>Manage your demo job drafts and published versions.</p></aside><main><slot /></main></div>
    <footer>SimRecrut · HR workspace · Frontend demonstration</footer>
  </div>
</template>

<style scoped>
.hr-layout { min-height: 100svh; display: flex; flex-direction: column; }
header { background: white; border-bottom: 1px solid #e1eaf5; }
.header-content { max-width: 1440px; margin: auto; padding: 18px 32px; display: flex; align-items: center; gap: 28px; }
:deep(.brand-mark span) { font-size: 25px; } :deep(.brand-mark svg) { width: 32px; height: 32px; }
.workspace-label, .user-name { color: #586b83; font-size: 13px; }
.session-controls { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.session-controls .el-button { margin: 0; }
.workspace { flex: 1; width: min(1440px, 100%); margin: auto; display: grid; grid-template-columns: 210px minmax(0, 1fr); }
.sidebar { padding: 28px 18px; border-right: 1px solid #e1eaf5; background: #f8fbff; }
nav { display: flex; flex-direction: column; gap: 12px; }
nav a, nav button { border: 0; padding: 12px; text-align: left; border-radius: 6px; font-size: 13px; text-decoration: none; }
nav a { color: #586b83; background: transparent; }
nav a[aria-current="page"] { color: #176bdf; background: #e7f0ff; font-weight: 600; }
nav button { color: #8491a3; background: transparent; }
.sidebar p { color: #8491a3; font-size: 12px; line-height: 1.8; padding: 12px; }
main { padding: 32px; min-width: 0; }
footer { text-align: center; color: #586b83; padding: 20px; font-size: 12px; }
@media (max-width: 1000px) { .workspace-label, .user-name { display: none; } .workspace { grid-template-columns: 180px minmax(0, 1fr); } main { padding: 24px; } }
@media (max-width: 760px) { .workspace { grid-template-columns: minmax(0, 1fr); } .sidebar { border-right: 0; border-bottom: 1px solid #e1eaf5; padding: 14px 16px; } nav { flex-direction: row; flex-wrap: wrap; gap: 6px; } .sidebar p { display: none; } main { padding: 24px 16px; } .header-content { flex-wrap: wrap; gap: 14px; padding: 16px; } .session-controls { gap: 6px; } .session-controls .el-button { padding: 8px 10px; font-size: 12px; } }
</style>
