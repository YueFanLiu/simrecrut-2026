<!-- Compose the application shell and provide one shared workspace state. -->
<script>
import { provide } from "vue";
import { createWorkspace, workspaceKey } from "./state/workspace.js";
import { pageComponents } from "./router/pageComponents.js";
import WorkspaceNavigation from "./components/WorkspaceNavigation.vue";
import RecordDrawer from "./components/RecordDrawer.vue";
export default {
  components: { WorkspaceNavigation, RecordDrawer },
  setup() {
    const workspace = createWorkspace();
    provide(workspaceKey, workspace);
    return { ...workspace, pageComponents };
  },
};
</script>
<template>
  <WorkspaceNavigation />
  <main>
    <div class="breadcrumb">
      SimRecrut <span>/</span> {{ page[1] }} <span>/</span> {{ page[4] }}
    </div>
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ page[0] }} / {{ page[1] }} WORKSPACE</div>
        <h1>{{ page[4] }}</h1>
        <p>{{ page[5] }}</p>
      </div>
      <button
        v-if="page[0] === 'R01'"
        class="primary"
        @click="go('/research/experiments/new')"
      >
        ＋ Create experiment
      </button>
      <button
        v-else
        class="secondary"
        @click="notify('Demo view refreshed. No production API is connected.')"
      >
        ↻ Refresh
      </button>
    </div>
    <div class="notice">
      <span>ⓘ</span>
      <div>
        <strong>Demonstration workspace</strong> Synthetic records and
        session-only interactions. Production authorization, persistence and
        metrics are not connected.
      </div>
    </div>
    <div v-if="missingRecord" class="panel panel-body">
      <h2>Record not found</h2>
      <p>This identifier does not match an available demo record.</p>
      <button
        class="secondary"
        @click="
          go(page[0] === 'V02' ? '/reviewer/tasks' : '/research/experiments')
        "
      >
        Return to list
      </button>
    </div>

    <component v-else :is="pageComponents[page[0]]" />
    <footer>
      SimRecrut <span>Research simulation · Not a real hiring system</span
      ><span>{{ page[0] }} · First-round prototype</span>
    </footer>
  </main>
  <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  <RecordDrawer />
</template>
