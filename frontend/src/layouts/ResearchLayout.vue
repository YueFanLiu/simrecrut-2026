<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'
import { computed } from 'vue'
import { demoMode } from '../config/runtime.js'
import { useResearchModuleStore } from '../store/modules/research-module.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const research = useResearchModuleStore()
// Preserve the selected experiment instead of navigating to a fixed demo ID.
const metricsTarget = computed(() => ({ name: 'ResearchMetrics', query: {
  experimentId: route.params.experimentId || route.query.experimentId || research.experiments[0]?.id,
} }))
async function signOut() { await auth.logout(); router.replace({ name: 'Login' }) }
</script>

<template>
  <div class="research-layout">
    <header><div class="header-content"><BrandMark /><span class="workspace-label">{{ $t("Research workspace") }}</span>
      <div class="session-controls"><span class="user-name">{{ auth.demoUser?.displayName }}</span>
        <ElButton @click="router.push({ name: 'DemoDashboard' })">{{ $t("Dashboard") }}</ElButton><ElButton @click="signOut">{{ $t("Sign out") }}</ElButton></div></div></header>
    <div class="workspace"><aside class="sidebar"><nav :aria-label="$t(&quot;Research navigation&quot;)">
      <template v-if="demoMode">
        <RouterLink :to="{ name: 'ResearchExperiments' }" :aria-current="['ResearchExperiments', 'ResearchExperimentDetail'].includes(route.name) ? 'page' : undefined">{{ $t("Experiments") }}</RouterLink>
        <RouterLink :to="{ name: 'ResearchExperimentCreate' }" :aria-current="route.name === 'ResearchExperimentCreate' ? 'page' : undefined">{{ $t("Create Experiment") }}</RouterLink>
        <RouterLink :to="{ name: 'ResearchSubjects' }" :aria-current="route.name === 'ResearchSubjects' ? 'page' : undefined">{{ $t("Research Subjects") }}</RouterLink>
        <RouterLink :to="{ name: 'ResearchModels' }" :aria-current="route.name === 'ResearchModels' ? 'page' : undefined">{{ $t("Models") }}</RouterLink>
        <RouterLink :to="metricsTarget" :aria-current="route.name === 'ResearchMetrics' ? 'page' : undefined">{{ $t("Metrics") }}</RouterLink>
      </template>
      <RouterLink :to="{ name: 'MyReviewTasks' }" :aria-current="['MyReviewTasks', 'ReviewTaskDetail'].includes(route.name) ? 'page' : undefined">{{ $t("My Review Tasks") }}</RouterLink>
      <RouterLink v-if="!demoMode" :to="{ name: 'AssignPrimaryReviews' }" :aria-current="route.name === 'AssignPrimaryReviews' ? 'page' : undefined">{{ $t("Assign Primary Reviews") }}</RouterLink>
    </nav><p v-if="demoMode">{{ $t("Browse authorised demo experiments and their frozen versions.") }}</p></aside><main><slot /></main></div>
    <footer>{{ $t("SimRecrut - Research workspace") }}{{ $t(demoMode ? ' - Frontend demonstration' : '') }}</footer>
  </div>
</template>

<style scoped>
.research-layout { min-height: 100svh; display: flex; flex-direction: column; }
header { position: sticky; top: 0; z-index: 20; background: var(--sr-surface); border-bottom: 1px solid var(--sr-border); box-shadow: var(--sr-shadow-sm); }
.header-content { max-width: 1440px; margin: auto; padding: 18px 32px; display: flex; align-items: center; gap: 28px; }
:deep(.brand-mark .brand-name) { font-size: 25px; } :deep(.brand-mark svg) { width: 32px; height: 32px; }
.workspace-label, .user-name { color: var(--sr-text-muted); font-size: 13px; }
.workspace-label { padding: 4px 12px; border-radius: 999px; background: var(--sr-accent-50); color: var(--sr-accent-strong); font-weight: 650; border: 1px solid var(--sr-accent-100); }
.session-controls { margin-left: auto; display: flex; align-items: center; gap: 10px; }
.session-controls .el-button { margin: 0; }
.workspace { flex: 1; width: min(1440px, 100%); margin: auto; display: grid; grid-template-columns: 210px minmax(0, 1fr); }
.sidebar { padding: 28px 16px; border-right: 1px solid var(--sr-border); background: var(--sr-surface); }
nav { display: flex; flex-direction: column; gap: 4px; }
nav a, nav button { position: relative; border: 0; padding: 11px 14px; text-align: left; border-radius: 8px; font-size: 13.5px; text-decoration: none; transition: background-color .15s, color .15s; }
nav a { color: var(--sr-text-secondary); background: transparent; font-weight: 500; }
nav a:hover { color: var(--sr-ink); background: var(--sr-surface-tint); }
nav a[aria-current="page"] { color: var(--sr-ink); background: var(--sr-primary-50); font-weight: 650; }
nav a[aria-current="page"]::before { content: ''; position: absolute; left: 0; top: 8px; bottom: 8px; width: 3px; border-radius: 3px; background: var(--sr-accent); }
nav button { color: var(--sr-text-subtle); background: transparent; }
.sidebar p { color: var(--sr-text-subtle); font-size: 12px; line-height: 1.8; padding: 12px; }
main { padding: 32px 36px; min-width: 0; }
footer { text-align: center; color: var(--sr-text-muted); padding: 20px; font-size: 12px; border-top: 1px solid var(--sr-border); background: var(--sr-surface); }
@media (max-width: 1000px) { .workspace-label, .user-name { display: none; } .workspace { grid-template-columns: 180px minmax(0, 1fr); } main { padding: 24px; } }
@media (max-width: 760px) { .workspace { grid-template-columns: minmax(0, 1fr); } .sidebar { border-right: 0; border-bottom: 1px solid var(--sr-border); padding: 14px 16px; } nav { flex-direction: row; flex-wrap: wrap; gap: 6px; } .sidebar p { display: none; } main { padding: 24px 16px; } .header-content { flex-wrap: wrap; gap: 14px; padding: 16px; } .session-controls { gap: 6px; } .session-controls .el-button { padding: 8px 10px; font-size: 12px; } }
</style>
