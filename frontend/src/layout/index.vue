<!-- Compose the application shell and provide one shared workspace state. -->
<script>
import { demoMode } from "../store/modules/user";
import { provide } from "vue";
import { createWorkspace, workspaceKey } from "../state/workspace.js";
import { pageComponents } from "../router/pageComponents.js";
import WorkspaceNavigation from "../components/WorkspaceNavigation.vue";
import RecordDrawer from "../components/RecordDrawer.vue";
export default {
  components: { WorkspaceNavigation, RecordDrawer },
  setup() {
    const workspace = createWorkspace();
    provide(workspaceKey, workspace);
    return { ...workspace, pageComponents, demoMode };
  },
};
</script>
<template>
  <WorkspaceNavigation />
  <main>
    <div class="breadcrumb"> {{ $t("SimRecrut") }} <span>/</span> {{ page[1] }} <span>/</span> {{ page[4] }}
    </div>
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ page[0] }} / {{ page[1] }} {{ $t("WORKSPACE") }}</div>
        <h1>{{ page[4] }}</h1>
        <p>{{ page[5] }}</p>
      </div>
      <button
        v-if="page[0] === 'R01'"
        class="primary"
        @click="go('/research/experiments/new')"
      > {{ $t("＋ Create experiment") }} </button>
      <button
        v-else
        class="secondary"
        @click="notify('Demo view refreshed. No production API is connected.')"
      > {{ $t("↻ Refresh") }} </button>
    </div>
    <div class="notice" v-if="demoMode">
      <span>ⓘ</span>
      <div>
        <strong>{{ $t("Demonstration workspace") }}</strong> {{ $t("Synthetic records and session-only interactions. Production authorization, persistence and metrics are not connected in this preview.") }} </div>
    </div>
    <div class="notice" v-else> {{ $t("Connected workspace. Feature availability depends on the configured backend APIs.") }} </div>
    <div v-if="missingRecord" class="panel panel-body">
      <h2>{{ $t("Record not found") }}</h2>
      <p>{{ $t("This identifier does not match an available demo record.") }}</p>
      <button
        class="secondary"
        @click="
          go(page[0] === 'V02' ? '/reviewer/tasks' : '/research/experiments')
        "
      > {{ $t("Return to list") }} </button>
    </div>

    <RouterView v-else />
    <footer> {{ $t("SimRecrut") }} <span>{{ $t("Research simulation · Not a real hiring system") }}</span
      ><span>{{ page[0] }} {{ $t("· First-round prototype") }}</span>
    </footer>
  </main>
  <div v-if="toast" class="toast" role="status">{{ $t(toast) }}</div>
  <RecordDrawer />
</template>
