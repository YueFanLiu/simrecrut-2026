<script setup>
import { useRouter } from 'vue-router'
import { ElButton, ElTag } from 'element-plus'
import { demoMode } from '../config/runtime.js'
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { useAuthStore } from '../store/modules/auth.js'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  try { await auth.logout() }
  finally { await router.replace({ name: 'Login' }) }
}
</script>

<template>
  <main class="demo-dashboard">
    <BrandMark />
    <section>
      <el-tag type="info">{{ $t(demoMode ? 'Frontend demo only' : 'Signed in') }}</el-tag>
      <h1>{{ $t('Welcome, {name}', { name: auth.demoUser?.displayName || '' }) }}</h1>
      <p v-if="!demoMode">{{ $t("You are signed in to SimRecrut. Choose a workspace to continue.") }}</p>
      <template v-if="demoMode"><p>{{ $t("The simulated login was successful.") }}</p>
      <p>{{ $t("This temporary dashboard only verifies navigation. The full dashboard is not implemented.") }}</p>
      <p>{{ $t("Built-in demo sessions and confirmed case checkpoints survive reloads in this tab. Sign out clears private case state. No real authentication is performed.") }}</p></template>
      <el-button type="primary" @click="logout">{{ $t("Sign out") }}</el-button>
      <el-button v-if="auth.hasDemoRole('Admin')" @click="router.push({ name: 'AdminUsers' })">{{ $t("Manage users") }}</el-button>
      <el-button v-if="!demoMode && auth.hasDemoRole('Candidate')" type="primary" @click="router.push({ name: 'ConnectedResumes' })">{{ $t("My CVs") }}</el-button>
      <el-button v-if="auth.hasDemoRole('Candidate')" @click="router.push({ name: 'PublishedJobs' })">{{ $t("Browse published jobs") }}</el-button>
      <el-button v-if="auth.hasDemoRole('HR')" @click="router.push({ name: 'HrJobs' })">{{ $t("Manage HR jobs") }}</el-button>
      <el-button v-if="!demoMode && auth.hasDemoRole('HR')" @click="router.push({ name: 'HrCases' })">{{ $t("Case Results") }}</el-button>
      <el-button v-if="!demoMode && auth.hasDemoRole('Researcher')" @click="router.push({ name: 'AssignPrimaryReviews' })">{{ $t("Assign Primary Reviews") }}</el-button>
      <el-button v-if="demoMode && auth.hasDemoRole('Researcher')" @click="router.push({ name: 'ResearchExperiments' })">{{ $t("Browse research experiments") }}</el-button>
      <el-button v-if="auth.hasDemoRole('HR') || auth.hasDemoRole('Researcher')" @click="router.push({ name: 'MyReviewTasks' })">{{ $t("My Review Tasks") }}</el-button>
      <el-button @click="router.push({ name: 'Forbidden' })">{{ $t("Preview access denied") }}</el-button>
      <el-button @click="router.push({ name: 'NotFound' })">{{ $t("Preview page not found") }}</el-button>
    </section>
  </main>
</template>

<style scoped>
.demo-dashboard { max-width: 800px; padding: 48px 24px; margin: auto; }
section { margin-top: 32px; padding: 28px; background: white; border: 1px solid #e1eaf5; border-radius: 12px; }
h1 { font-size: 26px; }
p { color: #586b83; line-height: 1.7; }
.el-button { margin-top: 12px; }
</style>
