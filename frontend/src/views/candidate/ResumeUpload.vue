<!-- C03: Keep uploads temporary until the user confirms the professional facts. -->
<script setup>
import CandidateLayout from "../../layouts/CandidateLayout.vue";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { listResumes, importResume, resumeAction } from "@/api/resumes";
import { demoMode } from "@/store/modules/user";
const records = ref([]),
  busy = ref(false),
  router = useRouter();
async function refresh() {
  if (!demoMode) records.value = (await listResumes()).data;
}
onMounted(refresh);
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
    const result = await importResume({ filename: file.name, content });
    await resumeAction(result.data.id, "clean");
    await router.push("/candidate/resumes/" + result.data.id);
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
      Temporary originals remain on the server until confirmed JSON is
      successfully published.
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
