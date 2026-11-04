<!-- A01: Compose the account list and shared account-detail actions. -->
<script>
import { useWorkspace } from "../../state/workspace.js";
import RecordTable from "../../components/RecordTable.vue";
export default { components: { RecordTable }, setup: useWorkspace };
</script>

<template>
  <RecordTable />
</template>
