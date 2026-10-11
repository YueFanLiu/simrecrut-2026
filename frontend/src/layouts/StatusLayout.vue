<script setup>
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'

const auth = useAuthStore()
const router = useRouter()

function signOut() {
  auth.logout()
  router.replace({ name: 'Login' })
}
</script>

<template>
  <div class="status-layout">
    <header class="status-header">
      <div class="header-content">
        <BrandMark />
        <div v-if="auth.isDemoSignedIn" class="session-controls">
          <span class="user-name">{{ auth.demoUser?.displayName }}</span>
          <ElButton @click="signOut">Sign out</ElButton>
        </div>
        <ElButton v-else @click="router.replace({ name: 'Login' })">Sign in</ElButton>
      </div>
    </header>
    <main class="status-main">
      <slot />
    </main>
    <footer>SimRecrut <span>·</span> Frontend demonstration</footer>
  </div>
</template>

<style scoped>
.status-layout { display: flex; flex-direction: column; min-height: 100svh; }
.status-header { background: white; border-bottom: 1px solid #e1eaf5; }
.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  max-width: 1200px;
  margin: auto;
  padding: 20px 32px;
}
:deep(.brand-mark span) { font-size: 25px; letter-spacing: -0.8px; }
:deep(.brand-mark svg) { width: 32px; height: 32px; }
.session-controls { display: flex; align-items: center; gap: 20px; min-width: 0; }
.user-name { max-width: 220px; overflow-wrap: anywhere; font-size: 14px; color: #586b83; }
.status-main { display: flex; align-items: center; flex: 1; width: 100%; padding: 56px 32px; }
footer { padding: 16px 24px 24px; color: #60728a; text-align: center; font-size: 12px; }
footer span { margin: 0 8px; }
@media (max-width: 700px) {
  .header-content { padding: 16px; gap: 12px; }
  :deep(.brand-mark) { gap: 8px; }
  :deep(.brand-mark span) { font-size: 22px; }
  :deep(.brand-mark svg) { width: 28px; height: 28px; }
  .user-name { display: none; }
  .status-main { padding: 32px 20px; }
}
</style>
