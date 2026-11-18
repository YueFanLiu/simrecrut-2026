<!-- A03: Compose the release registry and controlled model transitions. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
import RecordTable from "../../components/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
