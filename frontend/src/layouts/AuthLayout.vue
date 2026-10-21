<script setup>
import BrandMark from '../components/simrecrut/BrandMark.vue'
import { demoMode } from '../config/runtime.js'
import illustration from '../assets/recruitment-illustration.svg'

defineProps({ titleId: { type: String, default: 'login-title' } })
</script>

<template>
  <main class="auth-layout">
    <div class="auth-content">
      <section class="brand-panel" aria-label="About SimRecrut">
        <div class="brand-intro">
          <BrandMark />
          <p>A recruitment simulator for<br class="desktop-break" /> fairness research</p>
        </div>
        <img class="recruitment-illustration" :src="illustration" alt="" />
        <p class="brand-caption">Compare <span>·</span> Understand <span>·</span> Support Fairer Decisions</p>
      </section>
      <section class="auth-card" :aria-labelledby="titleId">
        <slot />
      </section>
    </div>
    <footer>SimRecrut <span>·</span> {{ demoMode ? 'Frontend demonstration' : 'Recruitment and fairness research' }}</footer>
  </main>
</template>

<style scoped>
.auth-layout {
  min-height: 100svh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 32px 24px;
  background: radial-gradient(ellipse at 15% 55%, #deeeff 0, transparent 58%), #f3f8fe;
}
.auth-content {
  width: min(1060px, 100%);
  margin: auto;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  align-items: center;
  gap: 48px;
}
.brand-panel { min-width: 0; }
.brand-intro { padding: 0 40px; }
.brand-intro p { margin: 16px 0 0 54px; color: #344963; font-size: 18px; line-height: 1.55; }
.recruitment-illustration { display: block; width: 100%; margin-top: 32px; }
.brand-caption { text-align: center; font-size: 13px; color: #4a617a; margin: 20px 0 0; }
.brand-caption span { margin: 0 9px; color: #8aa0ba; }
.auth-card {
  padding: 40px;
  border: 1px solid #e7eef8;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 16px 48px #173e6a0a;
}
footer { margin-top: 40px; text-align: center; color: #60728a; font-size: 12px; }
footer span { margin: 0 8px; }
@media (max-width: 900px) {
  .auth-content { gap: 24px; grid-template-columns: 1fr 1fr; }
  .brand-intro { padding: 0; }
  .brand-intro p { margin-left: 0; }
  .auth-card { padding: 30px; }
  .brand-caption { line-height: 1.8; }
}
@media (max-width: 700px) {
  .auth-layout { padding: 28px 16px 20px; }
  .auth-content { max-width: 440px; grid-template-columns: 1fr; gap: 24px; }
  .brand-intro { display: flex; flex-direction: column; align-items: center; text-align: center; }
  .brand-intro p { font-size: 14px; margin-top: 8px; }
  .desktop-break, .recruitment-illustration, .brand-caption { display: none; }
  .auth-card { padding: 28px 24px; border-radius: 12px; }
  footer { margin-top: 24px; }
}
</style>
