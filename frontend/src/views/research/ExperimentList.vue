<!-- R01: List authorized demo experiments and summarize their status. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
import RecordTable from "../../components/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <div class="stats">
    <article>
      <small>AUTHORIZED EXPERIMENTS</small><b>{{ data.experiments.length }}</b
      ><span>Visible to the demo researcher</span>
    </article>
    <article>
      <small>COMPLETED</small
      ><b>{{
        data.experiments.filter((e) => e.status === "Completed").length
      }}</b
      ><span>Frozen results available</span>
    </article>
    <article>
      <small>DRAFTS</small
      ><b>{{ data.experiments.filter((e) => e.status === "Draft").length }}</b
      ><span>Awaiting subject selection</span>
    </article>
    <article>
      <small>PROTOCOL</small><b class="small-number">P-2026.01</b
      ><span>Versioned and immutable</span>
    </article>
  </div>
  <RecordTable />
</template>
