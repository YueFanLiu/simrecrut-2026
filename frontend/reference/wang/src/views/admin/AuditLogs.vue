<!-- A07: Display audit metadata without exposing resume content. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
import RecordTable from "../../components/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
