<!-- R05: Display the model registry through the shared read-only list. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
import RecordTable from "../../components/research/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
