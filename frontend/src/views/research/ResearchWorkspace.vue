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
      <div class="module-heading"><h1>{{ $t($route.meta.title) }}</h1><div class="module-actions"><ElButton :loading="loading" @click="refresh">{{ $t("Refresh") }}</ElButton><ElButton v-if="$route.name === 'ResearchExperiments'" type="primary" @click="go('/research/experiments/new')">{{ $t("Create New Experiment") }}</ElButton><ElButton v-else-if="['ResearchExperimentDetail', 'ResearchExperimentCreate'].includes($route.name)" @click="go('/research/experiments')">{{ $t("Back to Experiments") }}</ElButton><ElButton v-if="$route.name === 'ResearchMetrics' && !missingRecord" @click="go('/research/experiments/' + workspace.experiment.value.id)">{{ $t("Back to Experiment") }}</ElButton></div></div>
      <ElAlert type="info" :closable="false" :title="$t(&quot;Frontend demonstration only. Research subjects are fixed examples independent of Candidate uploads. Experiment configuration and synthetic outputs survive reload in this tab; review assignments do not. No real API, model execution, training, authentication or reviewer notification is performed.&quot;)" />
      <p v-if="toast" role="status">{{ $t(toast) }}</p>
      <p v-if="error" class="error" role="alert">{{ $t(error) }}</p>
      <div v-if="loading" class="panel panel-body" role="status">{{ $t("Loading Research demo data...") }}<ElSkeleton :rows="5" animated /></div>
      <div v-else-if="loadError" class="panel panel-body" role="alert"><h2>{{ $t("Research Data Unavailable") }}</h2><p>{{ $t(loadError) }}</p><ElButton @click="refresh">{{ $t("Try Again") }}</ElButton></div>
      <div v-else-if="missingRecord" class="panel panel-body" role="alert"><h2>{{ $t("Experiment Unavailable") }}</h2><p>{{ $t("This experiment is unknown or no longer authorised. Select an accessible experiment from the list.") }}</p><ElButton @click="go('/research/experiments')">{{ $t("Back to Experiments") }}</ElButton></div>
      <RouterView v-else />
      <ElDialog :model-value="Boolean(drawer && drawerRecord)" :title="$t(&quot;Read-only Record Details&quot;)" width="min(600px, 94vw)" @close="drawer = null">
        <template v-if="drawerRecord"><dl class="record-details"><template v-for="(value, key) in drawerRecord" :key="key"><dt>{{ key }}</dt><dd v-if="key === 'professional'"><p v-for="(fact, category) in value" :key="category">{{ $t(category) }}: {{ fact }}</p><p>{{ $t("Evidence: Fixed professional mock summary; no original CV is available.") }}</p></dd><dd v-else>{{ value }}</dd></template></dl><ElButton v-if="drawer.kind === 'model'" :disabled="drawerRecord.status !== 'Active'" @click="workspace.useModel(drawerRecord)">{{ $t("Use in New Experiment") }}</ElButton><div v-if="drawer.kind === 'subject' && drawerRecord.source === 'Authorized Candidate Cases'"><p>{{ $t("Primary review assignment for this authorised demo case.") }}</p><label>{{ $t("Eligible demo reviewer") }}<select v-model="reviewer"><option value="reviewer-hr">{{ $t("Demo HR Reviewer") }}</option><option value="reviewer-researcher">{{ $t("Demo Research Reviewer") }}</option></select></label><label>{{ $t("Review condition") }}<select v-model="condition"><option value="Standard">{{ $t("Standard") }}</option><option value="Instructed-Bias">{{ $t("Instructed-Bias") }}</option></select></label><ElButton @click="workspace.assignPrimary">{{ $t("Assign Primary Demo Review") }}</ElButton><p v-if="error" role="alert">{{ $t(error) }}</p><p v-if="toast" role="status">{{ $t(toast) }}</p></div></template>
      </ElDialog>
    </div>
  </ResearchLayout>
</template>

<style src="../../assets/research-workspace.css"></style>
