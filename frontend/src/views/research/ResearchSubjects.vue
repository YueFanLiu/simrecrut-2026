<!-- R04: List eligible subjects without allowing revoked or expired selections. -->
<script>
import { useWorkspace } from "../../composables/useResearchWorkspace.js";
import RecordTable from "../../components/research/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
