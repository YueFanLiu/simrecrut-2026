<script setup>
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import StatusLayout from '../../layouts/StatusLayout.vue'
import { useAuthStore } from '../../store/modules/auth.js'
import { registrationEnabled } from '../../config/demo.js'
import illustration from '../../assets/not-found-illustration.svg'

const router = useRouter()
const auth = useAuthStore()

function goToStart() {
  router.replace({ name: auth.isDemoSignedIn ? 'DemoDashboard' : 'Login' })
}

function goBack() {
  const previousPath = router.options.history.state.back
  const permittedNames = auth.isDemoSignedIn
    ? ['DemoDashboard', 'Forbidden', 'PublishedJobs']
    : ['Login', ...(registrationEnabled ? ['Register'] : [])]

  // Resolve only local paths and navigate by an approved route name, dropping query parameters.
  // This avoids leaving the app, returning to missing pages or entering another workspace.
  if (typeof previousPath === 'string' && previousPath.startsWith('/') &&
      !previousPath.startsWith('//')) {
    const previousRoute = router.resolve(previousPath)
    if (permittedNames.includes(previousRoute.name)) {
      router.replace({ name: previousRoute.name })
      return
    }
  }
  goToStart()
}
</script>

<template>
  <StatusLayout>
    <section class="not-found-content" aria-labelledby="not-found-title">
      <img class="not-found-illustration" :src="illustration" alt="" />
      <div class="not-found-message">
        <p class="status-code">404</p>
        <h1 id="not-found-title">Page Not Found</h1>
        <p class="description">Sorry, the page you are looking for does not exist or has been moved.</p>
        <div class="navigation-actions">
          <ElButton type="primary" size="large" @click="goToStart">
            {{ auth.isDemoSignedIn ? 'Go to dashboard' : 'Sign in' }}
          </ElButton>
          <ElButton size="large" @click="goBack">Go Back</ElButton>
        </div>
        <div v-if="auth.isDemoSignedIn" class="workspace-note">
          <span>Available workspace</span>
          <strong>Demo dashboard</strong>
        </div>
        <p v-else class="sign-in-note">Sign in to access your demo workspace.</p>
      </div>
    </section>
  </StatusLayout>
</template>

<style scoped>
.not-found-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: center;
  width: min(1100px, 100%);
  margin: auto;
}
.not-found-illustration { width: 100%; display: block; }
.not-found-message { min-width: 0; }
.status-code { margin: 0 0 4px; font-size: 80px; font-weight: 750; line-height: 1.05; color: #102b50; }
h1 { margin: 0; font-size: 34px; letter-spacing: -0.8px; color: #132941; }
.description { margin: 16px 0 26px; color: #425269; line-height: 1.7; font-size: 15px; }
.navigation-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.navigation-actions .el-button { margin: 0; min-width: 145px; font-weight: 600; }
.workspace-note, .sign-in-note { margin: 24px 0 0; color: #586b83; font-size: 12px; line-height: 1.7; }
.workspace-note > span { margin-right: 12px; }
.workspace-note strong { color: #31557f; font-size: 13px; }
@media (max-width: 900px) {
  .not-found-content { gap: 24px; }
  .status-code { font-size: 68px; }
  h1 { font-size: 29px; }
}
@media (max-width: 700px) {
  .not-found-content { max-width: 440px; grid-template-columns: 1fr; gap: 16px; }
  .not-found-illustration { max-width: 240px; margin: auto; }
  .not-found-message { text-align: center; }
  .description { margin-top: 12px; font-size: 14px; }
  .navigation-actions { flex-direction: column; }
  .navigation-actions .el-button { width: 100%; }
}
</style>
