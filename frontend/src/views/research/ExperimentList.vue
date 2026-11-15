<!-- R01: List authorized demo experiments and summarize their status. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
import RecordTable from "../../components/research/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <div class="stats">
    <article>
      <small>{{ $t("AUTHORIZED EXPERIMENTS") }}</small><b>{{ data.experiments.length }}</b
      ><span>{{ $t("Visible to the demo researcher") }}</span>
    </article>
    <article>
      <small>{{ $t("COMPLETED") }}</small
      ><b>{{ $t(data.experiments.filter((e) => e.status === "Completed").length) }}</b
      ><span>{{ $t("Stored examples; outputs may be unavailable") }}</span>
    </article>
    <article>
      <small>{{ $t("DRAFTS") }}</small
      ><b>{{ $t(data.experiments.filter((e) => e.status === "Draft").length) }}</b
      ><span>{{ $t("Awaiting subject selection") }}</span>
    </article>
    <article>
      <small>{{ $t("PROTOCOL") }}</small><b class="small-number">P-2026.01</b
      ><span>{{ $t("Versioned and immutable") }}</span>
    </article>
  </div>
  <RecordTable />
</template>
