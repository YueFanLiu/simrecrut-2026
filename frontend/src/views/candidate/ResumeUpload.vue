<!-- C03: Keep uploads temporary until the user confirms the professional facts. -->
<script setup>
import CandidateLayout from "../../layouts/CandidateLayout.vue";
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { listResumes, importResume, resumeAction, getResume } from "@/api/resumes";
import { demoMode } from "@/store/modules/user";
const records = ref([]),
  busy = ref(false),
  router = useRouter();
let active = true;
const controller = new AbortController();
async function refresh() {
  if (!demoMode) {
    const result = await listResumes(controller.signal);
    if (active) records.value = result.data;
  }
}
onMounted(refresh);
onUnmounted(() => { active = false; controller.abort(); records.value = []; });
async function upload(file) {
  if (demoMode)
    return ElMessage.info(
      "Sign in to the connected backend to process a real PDF.",
    );
  if (
    !file.name.toLowerCase().endsWith(".pdf") ||
    file.size > 10 * 1024 * 1024 ||
    !file.size
  )
    return ElMessage.error("Choose a PDF between 1 byte and 10 MB.");
  busy.value = true;
  try {
    const content = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result.split(",")[1]);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
    if (!active) return false;
    const result = await importResume({ filename: file.name, content }, controller.signal);
    if (!active) return false;
    const existing = (await getResume(result.data.id, controller.signal)).data;
    // Reopening an existing review must preserve its manually corrected draft.
    if (existing.status === "UPLOADED") await resumeAction(result.data.id, "clean", {}, { signal: controller.signal });
    if (!active) return false;
    await router.push("/candidate/resumes/" + result.data.id);
  } catch (error) {
    // HTTP errors are already shown by the shared request interceptor.
    if (active && error.code !== "ERR_CANCELED" && !error.response) ElMessage.error("Upload could not finish. Reload your CV list before retrying.");
  } finally {
    busy.value = false;
  }
  return false;
}
</script>
<template>
  <CandidateLayout>
  <el-card
    ><h2>Upload your CV</h2>
    <p>PDF only · Up to 10 MB · Up to 20 pages · Unencrypted</p>
    <el-upload
      drag
      :show-file-list="false"
      :before-upload="upload"
      :disabled="busy"
      accept=".pdf"
      ><p>
        {{
          busy
            ? "Importing and preparing extraction…"
            : "Drag a PDF here, or click to choose a file"
        }}
      </p></el-upload
    >
    <p>
      Unconfirmed CVs expire 24 hours after import. Confirmed CVs expire after 30 days.
      Temporary originals are deleted after successful JSON publication.
    </p>
    <el-table :data="records"
      ><el-table-column prop="filename" label="CV" /><el-table-column
        prop="status"
        label="Status"
      /><el-table-column label="Action"
        ><template #default="scope"
          ><el-button @click="router.push('/candidate/resumes/' + scope.row.id)"
            >Review facts</el-button
          ></template
        ></el-table-column
      ></el-table
    ></el-card
  >
  </CandidateLayout>
</template>
