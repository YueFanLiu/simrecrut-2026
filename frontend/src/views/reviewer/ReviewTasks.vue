<!-- V01: Show assigned human review tasks and their submission status. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
import RecordTable from "../../components/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
