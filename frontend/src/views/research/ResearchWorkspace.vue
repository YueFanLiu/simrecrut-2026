<script setup>
import { provide } from 'vue'
import { ElAlert, ElButton, ElDialog, ElSkeleton } from 'element-plus'
import ResearchLayout from '../../layouts/ResearchLayout.vue'
import { createResearchWorkspace, researchWorkspaceKey } from '../../composables/useResearchWorkspace.js'
const workspace = createResearchWorkspace()
provide(researchWorkspaceKey, workspace)
const { loading, loadError, missingRecord, drawer, drawerRecord, error, toast, refresh, go, reviewer, condition } = workspace
</script>

<template>
  <ResearchLayout>
    <div class="research-module">
      <div class="module-heading"><h1>{{ $route.meta.title }}</h1><div class="module-actions"><ElButton :loading="loading" @click="refresh">Refresh</ElButton><ElButton v-if="$route.name === 'ResearchExperiments'" type="primary" @click="go('/research/experiments/new')">Create New Experiment</ElButton><ElButton v-else-if="['ResearchExperimentDetail', 'ResearchExperimentCreate'].includes($route.name)" @click="go('/research/experiments')">Back to Experiments</ElButton><ElButton v-if="$route.name === 'ResearchMetrics' && !missingRecord" @click="go('/research/experiments/' + workspace.experiment.value.id)">Back to Experiment</ElButton></div></div>
      <ElAlert type="info" :closable="false" title="Frontend demonstration only. Research subjects are fixed examples independent of Candidate uploads. Experiment configuration and synthetic outputs survive reload in this tab; review assignments do not. No real API, model execution, training, authentication or reviewer notification is performed." />
      <p v-if="toast" role="status">{{ toast }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="loading" class="panel panel-body" role="status">Loading Research demo data...<ElSkeleton :rows="5" animated /></div>
      <div v-else-if="loadError" class="panel panel-body" role="alert"><h2>Research Data Unavailable</h2><p>{{ loadError }}</p><ElButton @click="refresh">Try Again</ElButton></div>
      <div v-else-if="missingRecord" class="panel panel-body" role="alert"><h2>Experiment Unavailable</h2><p>This experiment is unknown or no longer authorised. Select an accessible experiment from the list.</p><ElButton @click="go('/research/experiments')">Back to Experiments</ElButton></div>
      <RouterView v-else />
      <ElDialog :model-value="Boolean(drawer && drawerRecord)" title="Read-only Record Details" width="min(600px, 94vw)" @close="drawer = null">
        <template v-if="drawerRecord"><dl class="record-details"><template v-for="(value, key) in drawerRecord" :key="key"><dt>{{ key }}</dt><dd v-if="key === 'professional'"><p v-for="(fact, category) in value" :key="category">{{ category }}: {{ fact }}</p><p>Evidence: Fixed professional mock summary; no original CV is available.</p></dd><dd v-else>{{ value }}</dd></template></dl><ElButton v-if="drawer.kind === 'model'" :disabled="drawerRecord.status !== 'Active'" @click="workspace.useModel(drawerRecord)">Use in New Experiment</ElButton><div v-if="drawer.kind === 'subject' && drawerRecord.source === 'Authorized Candidate Cases'"><p>Primary review assignment for this authorised demo case.</p><label>Eligible demo reviewer<select v-model="reviewer"><option value="reviewer-hr">Demo HR Reviewer</option><option value="reviewer-researcher">Demo Research Reviewer</option></select></label><label>Review condition<select v-model="condition"><option>Standard</option><option>Instructed-Bias</option></select></label><ElButton @click="workspace.assignPrimary">Assign Primary Demo Review</ElButton><p v-if="error" role="alert">{{ error }}</p><p v-if="toast" role="status">{{ toast }}</p></div></template>
      </ElDialog>
    </div>
  </ResearchLayout>
</template>

<style src="../../assets/research-workspace.css"></style>
