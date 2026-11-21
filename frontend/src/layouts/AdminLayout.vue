<script setup>
import { useRoute, useRouter } from 'vue-router'
import { demoMode } from '../config/runtime.js'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
async function signOut() { await auth.logout(); router.replace({ name: 'Login' }) }
</script>

<template>
  <div class="admin-layout">
    <header><div class="header-content"><BrandMark /><span class="workspace-label">{{ $t("Administration") }}</span>
      <div class="session-controls"><span class="user-name">{{ auth.demoUser?.displayName }}</span>
        <ElButton @click="router.push({ name: 'DemoDashboard' })">{{ $t("Dashboard") }}</ElButton><ElButton @click="signOut">{{ $t("Sign out") }}</ElButton></div></div></header>
    <div class="workspace"><aside class="sidebar"><nav :aria-label="$t(&quot;Administration navigation&quot;)">
      <RouterLink :to="{ name: 'AdminUsers' }" :aria-current="route.name === 'AdminUsers' ? 'page' : undefined">{{ $t("User Management") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminRoles' }" :aria-current="route.name === 'AdminRoles' ? 'page' : undefined">{{ $t("Roles & Permissions") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminModels' }" :aria-current="route.name === 'AdminModels' ? 'page' : undefined">{{ $t("Model Versions") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminProtocols' }" :aria-current="route.name === 'AdminProtocols' ? 'page' : undefined">{{ $t("Frozen Protocols") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminHealth' }" :aria-current="route.name === 'AdminHealth' ? 'page' : undefined">{{ $t("Service Health") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminFailures' }" :aria-current="route.name === 'AdminFailures' ? 'page' : undefined">{{ $t("Failed Jobs & Errors") }}</RouterLink>
      <RouterLink :to="{ name: 'AdminAudit' }" :aria-current="route.name === 'AdminAudit' ? 'page' : undefined">{{ $t("Audit Logs") }}</RouterLink>
</nav><p v-if="demoMode">{{ $t("Administration uses synthetic frontend data.") }}</p></aside><main><slot /></main></div>
    <footer>{{ $t("SimRecrut · Administration") }} {{ $t(demoMode ? '· Frontend demonstration' : '') }}</footer>
  </div>
</template>

<style scoped>
.admin-layout { min-height: 100svh; display: flex; flex-direction: column; }
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
