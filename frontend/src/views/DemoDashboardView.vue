<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { demoMode } from '../config/runtime.js'
import StatusLayout from '../layouts/StatusLayout.vue'
import { useAuthStore } from '../store/modules/auth.js'

const auth = useAuthStore()
const router = useRouter()

// Stroke icons drawn on a 24px grid; decorative only.
const icons = {
  briefcase: 'M4 8h16v11H4zM9 8V5h6v3M4 13h16',
  flow: 'M5 6h6v5H5zM13 13h6v5h-6zM8 11v4h5M16 13V9h-5',
  file: 'M7 3h7l4 4v14H7zM14 3v4h4M10 12h5M10 16h5',
  plus: 'M12 5v14M5 12h14',
  chart: 'M5 19V5M5 19h14M9 16v-5M13 16V8M17 16v-3',
  review: 'M4 5h16v11H8l-4 4zM8 9h8M8 12h5',
  flask: 'M10 3h4M11 3v6l-5 9a2 2 0 0 0 2 3h8a2 2 0 0 0 2-3l-5-9V3M8 15h8',
  people: 'M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20c0-3 3-5 6-5s6 2 6 5M16 5a3 3 0 0 1 0 6M18 15c2 1 3 3 3 5',
  model: 'M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5',
  shield: 'M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6zM9 12l2 2 4-4',
  pulse: 'M3 12h4l2-5 4 10 2-5h6',
  list: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
}

const workspaces = [
  { role: 'Candidate', label: 'Candidate workspace',
    summary: 'Explore published jobs, upload your CV and follow each simulation to its results.',
    links: [
      { to: 'PublishedJobs', title: 'Browse published jobs', text: 'See open positions and start a simulation.', icon: 'briefcase' },
      { to: 'MySimulations', title: 'My simulations', text: 'Track progress and read your five results.', icon: 'flow' },
      { to: 'ConnectedResumes', title: 'My CVs', text: 'Upload a PDF and confirm the extracted facts.', icon: 'file' },
    ] },
  { role: 'HR', label: 'HR workspace',
    summary: 'Publish job versions, review authorised cases and complete assigned human reviews.',
    links: [
      { to: 'HrJobs', title: 'Manage HR jobs', text: 'Drafts, published versions and history.', icon: 'briefcase' },
      { to: 'HrJobNew', title: 'Create Job', text: 'Set requirements and criterion weights.', icon: 'plus' },
      { to: 'HrCases', title: 'Case Results', text: 'Read-only results for your managed jobs.', icon: 'chart' },
      { to: 'MyReviewTasks', title: 'My Review Tasks', text: 'Blind human reviews assigned to you.', icon: 'review' },
    ] },
  { role: 'Researcher', label: 'Research workspace',
    summary: 'Design controlled experiments and compare methods on frozen configurations.',
    links: demoMode ? [
      { to: 'ResearchExperiments', title: 'Browse research experiments', text: 'Experiments and their frozen versions.', icon: 'flask' },
      { to: 'ResearchExperimentCreate', title: 'Create Experiment', text: 'Define settings and select subjects.', icon: 'plus' },
      { to: 'ResearchSubjects', title: 'Research Subjects', text: 'Eligible profiles with research grants.', icon: 'people' },
      { to: 'ResearchModels', title: 'Research Models', text: 'Neutral and Biased model packages.', icon: 'model' },
      { to: 'MyReviewTasks', title: 'My Review Tasks', text: 'Blind human reviews assigned to you.', icon: 'review' },
    ] : [
      { to: 'AssignPrimaryReviews', title: 'Assign Primary Reviews', text: 'Assign primary human reviews to reviewers.', icon: 'people' },
      { to: 'MyReviewTasks', title: 'My Review Tasks', text: 'Blind human reviews assigned to you.', icon: 'review' },
    ] },
  { role: 'Admin', label: 'Administration',
    summary: 'Manage accounts, roles, model releases and service health.',
    links: [
      { to: 'AdminUsers', title: 'Manage users', text: 'Account status and role assignments.', icon: 'people' },
      { to: 'AdminRoles', title: 'Roles and Permissions', text: 'Module permissions by role.', icon: 'shield' },
      { to: 'AdminModels', title: 'Model Versions and Release', text: 'Test, approve and activate model versions.', icon: 'model' },
      { to: 'AdminHealth', title: 'Service Health', text: 'Service availability and file cleanup.', icon: 'pulse' },
      { to: 'AdminAudit', title: 'Audit Logs', text: 'Operation metadata without content.', icon: 'list' },
    ] },
]

const available = computed(() => workspaces.filter(workspace => auth.hasDemoRole(workspace.role)))
const primary = computed(() => available.value[0])
// A user holding several roles sees each shared destination once.
const links = computed(() => {
  const seen = new Set()
  return available.value.flatMap(workspace => workspace.links).filter(link => !seen.has(link.to) && seen.add(link.to))
})
</script>

<template>
  <StatusLayout>
    <div class="dashboard">
      <section class="hero">
        <div class="hero-copy">
          <span class="role-badge">{{ $t(primary?.label || (demoMode ? 'Frontend demo only' : 'Signed in')) }}</span>
          <h1>{{ $t('Welcome, {name}', { name: auth.demoUser?.displayName || '' }) }}</h1>
          <p>{{ $t(primary?.summary || 'You are signed in to SimRecrut. Choose a workspace to continue.') }}</p>
        </div>
        <svg class="hero-art" viewBox="0 0 220 160" aria-hidden="true">
          <rect x="18" y="22" width="150" height="104" rx="12" class="art-panel" />
          <rect x="34" y="42" width="60" height="8" rx="4" class="art-line" />
          <rect x="34" y="60" width="96" height="6" rx="3" class="art-soft" />
          <rect x="34" y="74" width="80" height="6" rx="3" class="art-soft" />
          <rect x="34" y="96" width="18" height="16" rx="3" class="art-bar" />
          <rect x="58" y="86" width="18" height="26" rx="3" class="art-bar" />
          <rect x="82" y="78" width="18" height="34" rx="3" class="art-accent" />
          <circle cx="172" cy="104" r="30" class="art-ring" />
          <path d="M160 104l9 9 17-18" class="art-check" />
        </svg>
      </section>

      <section class="quick" aria-labelledby="quick-access-title">
        <h2 id="quick-access-title">{{ $t('Quick access') }}</h2>
        <div class="cards">
          <button v-for="link in links" :key="link.to" type="button" class="card" @click="router.push({ name: link.to })">
            <span class="card-icon">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="icons[link.icon]" /></svg>
            </span>
            <span class="card-body">
              <strong>{{ $t(link.title) }}</strong>
              <span>{{ $t(link.text) }}</span>
            </span>
            <svg class="card-arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6l6 6-6 6" /></svg>
          </button>
        </div>
      </section>

      <aside v-if="demoMode" class="demo-note">
        <div>
          <strong>{{ $t('Frontend demo only') }}</strong>
          <p>{{ $t('Built-in demo sessions and confirmed case checkpoints survive reloads in this tab. Sign out clears private case state. No real authentication is performed.') }}</p>
        </div>
        <div class="demo-links">
          <span>{{ $t('Demo shortcuts') }}</span>
          <button type="button" @click="router.push({ name: 'Forbidden' })">{{ $t('Preview access denied') }}</button>
          <button type="button" @click="router.push({ name: 'NotFound' })">{{ $t('Preview page not found') }}</button>
        </div>
      </aside>
    </div>
  </StatusLayout>
</template>

<style scoped>
.dashboard { display: grid; gap: 32px; width: 100%; max-width: 1136px; margin: 0 auto; align-self: flex-start; }
.hero {
  position: relative; overflow: hidden; display: flex; align-items: center; justify-content: space-between; gap: 32px;
  padding: 40px 44px; color: #fff; border-radius: var(--sr-radius-lg); box-shadow: var(--sr-shadow-lg);
  background: radial-gradient(circle at 85% 20%, rgb(255 255 255 / 10%), transparent 40%), linear-gradient(120deg, var(--isep-navy) 0%, var(--sr-primary-dark) 55%, var(--sr-primary) 100%);
}
.hero::after { content: ''; position: absolute; inset: auto 0 0; height: 5px; background: var(--sr-brand-gradient); }
.hero-copy { max-width: 620px; }
.role-badge {
  display: inline-block; padding: 5px 12px; font-size: 13px; font-weight: 600; color: var(--sr-accent-yellow);
  background: rgb(255 255 255 / 10%); border: 1px solid rgb(255 255 255 / 18%); border-radius: 999px;
}
h1 { margin: 16px 0 10px; font-size: 34px; line-height: 1.2; letter-spacing: -0.6px; color: #fff; }
.hero p { margin: 0; font-size: 16px; line-height: 1.7; color: rgb(255 255 255 / 80%); }
.hero-art { flex: none; width: 220px; height: 160px; }
.art-panel { fill: rgb(255 255 255 / 10%); stroke: rgb(255 255 255 / 22%); }
.art-line { fill: rgb(255 255 255 / 85%); }
.art-soft { fill: rgb(255 255 255 / 30%); }
.art-bar { fill: var(--sr-primary-200); }
.art-accent { fill: var(--sr-accent); }
.art-ring { fill: var(--isep-white); stroke: var(--sr-accent-yellow); stroke-width: 4; }
.art-check { fill: none; stroke: var(--sr-primary); stroke-width: 6; stroke-linecap: round; stroke-linejoin: round; }

h2 { margin: 0 0 16px; font-size: 18px; color: var(--sr-ink); }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.card {
  display: flex; align-items: flex-start; gap: 16px; padding: 22px 20px; text-align: left; font: inherit; cursor: pointer;
  background: var(--sr-surface); border: 1px solid var(--sr-border); border-radius: var(--sr-radius-lg); box-shadow: var(--sr-shadow-sm);
  transition: border-color .15s, box-shadow .15s, transform .15s;
}
.card:hover, .card:focus-visible { border-color: var(--sr-primary-200); box-shadow: var(--sr-shadow); transform: translateY(-2px); outline: none; }
.card:focus-visible { box-shadow: 0 0 0 3px var(--sr-primary-100), var(--sr-shadow); }
.card-icon { display: grid; place-items: center; flex: none; width: 44px; height: 44px; color: var(--sr-primary); background: var(--sr-primary-50); border-radius: 12px; }
.card:nth-child(4n + 2) .card-icon { color: var(--sr-accent-strong); background: var(--sr-accent-50); }
.card-icon svg, .card-arrow { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.card-body { display: grid; flex: 1; gap: 6px; min-width: 0; }
.card-body strong { font-size: 16px; color: var(--sr-ink); }
.card-body span { font-size: 14px; line-height: 1.5; color: var(--sr-text-muted); }
.card-arrow { flex: none; margin-top: 12px; color: var(--sr-text-subtle); transition: color .15s, transform .15s; }
.card:hover .card-arrow { color: var(--sr-primary); transform: translateX(3px); }

.demo-note {
  display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 18px 22px;
  background: var(--sr-surface-tint); border: 1px dashed var(--sr-border-strong); border-radius: var(--sr-radius);
}
.demo-note strong { font-size: 13px; color: var(--sr-text-secondary); }
.demo-note p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; color: var(--sr-text-muted); }
.demo-links { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 6px 14px; flex: none; font-size: 13px; }
.demo-links span { color: var(--sr-text-subtle); }
.demo-links button { padding: 0; font: inherit; color: var(--sr-primary); background: none; border: 0; cursor: pointer; }
.demo-links button:hover { text-decoration: underline; }

@media (max-width: 760px) {
  .hero { padding: 28px 24px 32px; }
  .hero-art { display: none; }
  h1 { font-size: 26px; }
  .demo-note { flex-direction: column; align-items: flex-start; }
  .demo-links { justify-content: flex-start; }
}
</style>
