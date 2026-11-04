<script setup>
import { useRouter } from 'vue-router'
import { ElButton } from 'element-plus'
import StatusLayout from '../../layouts/StatusLayout.vue'
import illustration from '../../assets/access-denied-illustration.svg'

const router = useRouter()

function returnToPreviousPage() {
  // Keep the approved recovery destination instead of following arbitrary history or query URLs.
  router.replace({ name: 'DemoDashboard' })
}
</script>

<template>
  <StatusLayout>
    <section class="forbidden-content" aria-labelledby="forbidden-title">
      <img class="forbidden-illustration" :src="illustration" alt="" />
      <div class="forbidden-message">
        <p class="status-code">403</p>
        <h1 id="forbidden-title">Access Denied</h1>
        <p class="description">You do not have permission to access this page or perform this action.</p>

        <aside class="reason-panel" aria-labelledby="reasons-title">
          <h2 id="reasons-title">Possible reasons</h2>
          <ul>
            <li>Your account does not have the required role.</li>
            <li>You are not authorized for this resource.</li>
            <li>The resource is not available in your current workspace.</li>
          </ul>
        </aside>

        <div class="workspace-note">
          <span>Available workspace</span>
          <strong>Demo dashboard</strong>
          <p>This frontend demo has no real permission checks.</p>
        </div>

        <div class="navigation-actions">
          <ElButton type="primary" size="large" @click="returnToPreviousPage">
            Back to previous page
          </ElButton>
          <ElButton size="large" @click="router.replace({ name: 'DemoDashboard' })">
            Go to dashboard
          </ElButton>
        </div>
      </div>
    </section>
  </StatusLayout>
</template>

<style scoped>
.forbidden-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: center;
  width: min(1100px, 100%);
  margin: auto;
}
.forbidden-illustration { width: 100%; display: block; }
.forbidden-message { min-width: 0; }
.status-code { margin: 0 0 4px; font-size: 80px; font-weight: 750; line-height: 1.05; color: #102b50; }
h1 { margin: 0; font-size: 34px; letter-spacing: -0.8px; color: #132941; }
.description { margin: 16px 0 22px; color: #425269; line-height: 1.7; font-size: 15px; }
.reason-panel { padding: 20px; border: 1px solid #e1eaf5; border-radius: 10px; background: #edf5ff; }
.reason-panel h2 { margin: 0; color: #274a75; font-size: 14px; }
.reason-panel ul { margin: 12px 0 0; padding-left: 20px; color: #586b83; font-size: 13px; line-height: 1.7; }
.reason-panel li + li { margin-top: 5px; }
.workspace-note { margin: 20px 0 24px; color: #586b83; font-size: 12px; }
.workspace-note > span { margin-right: 12px; }
.workspace-note strong { color: #31557f; font-size: 13px; }
.workspace-note p { margin: 8px 0 0; line-height: 1.6; }
.navigation-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.navigation-actions .el-button { margin: 0; font-weight: 600; }
@media (max-width: 900px) {
  .forbidden-content { gap: 24px; }
  .status-code { font-size: 68px; }
  h1 { font-size: 29px; }
}
@media (max-width: 700px) {
  .forbidden-content { max-width: 440px; grid-template-columns: 1fr; gap: 16px; }
  .forbidden-illustration { max-width: 240px; margin: auto; }
  .forbidden-message { text-align: center; }
  .reason-panel { text-align: left; padding: 16px; }
  .description { margin-top: 12px; font-size: 14px; }
  .navigation-actions { flex-direction: column; }
  .navigation-actions .el-button { width: 100%; }
}
</style>
