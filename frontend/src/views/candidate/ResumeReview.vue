<!-- C04: Review five professional fact groups without editing identity fields. -->
<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { getResume, resumeAction } from "@/api/resumes";
import { demoMode } from "@/store/modules/user";
const route = useRoute(),
  record = ref(null),
  busy = ref(false),
  confirmed = ref(false);
const groups = ["skills", "experience", "education", "languages", "projects"];
const editors = ref({});
let timer,
  stopped = false;
async function refresh() {
  if (demoMode || stopped) return;
  try {
    record.value = (await getResume(route.params.id)).data;
    if (record.value.status === "PROCESSING") timer = setTimeout(refresh, 1500);
    else
      for (const group of groups)
        editors.value[group] = JSON.stringify(
          record.value.draft?.professional?.[group] || [],
          null,
          2,
        );
  } catch {
    ElMessage.error("Could not load this resume.");
  }
}
onMounted(refresh);
onUnmounted(() => {
  stopped = true;
  clearTimeout(timer);
});
async function publish() {
  busy.value = true;
  try {
    const professional = Object.fromEntries(
      groups.map((group) => [group, JSON.parse(editors.value[group])]),
    );
    await resumeAction(route.params.id, "save", {
      draft: { professional, warnings: record.value.draft?.warnings || [] },
      confirmed: true,
    });
    await resumeAction(route.params.id, "upload");
    await refresh();
    ElMessage.success("Confirmed JSON published. Temporary original removed.");
  } catch {
    ElMessage.error(
      "Publication failed. Check the facts and cloud configuration; the original is retained until publication succeeds.",
    );
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <el-card
    ><h2>Review professional facts</h2>
    <p v-if="!record">No connected resume is loaded.</p>
    <template v-else
      ><el-alert
        v-if="record.status === 'PROCESSING'"
        title="Extracting professional facts…"
        type="info"
        :closable="false"
      /><el-alert
        v-if="record.error"
        :title="record.error"
        type="error"
        :closable="false"
      /><el-tabs v-if="record.status !== 'PROCESSING'"
        ><el-tab-pane v-for="group in groups" :key="group" :label="group"
          ><p>
            Check codes and evidence. Each field contains a JSON array of facts.
          </p>
          <el-input
            v-model="editors[group]"
            type="textarea"
            :rows="15" /></el-tab-pane></el-tabs
      ><el-checkbox v-model="confirmed"
        >I have reviewed these professional facts.</el-checkbox
      ><el-button
        v-hasPermi="['simrecrut:resume:confirm']"
        type="primary"
        :disabled="!confirmed || record.status === 'PROCESSING'"
        :loading="busy"
        @click="publish"
        >Confirm and publish JSON</el-button
      ></template
    ></el-card
  >
</template>
