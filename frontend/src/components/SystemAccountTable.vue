<!-- Read users and roles from RuoYi rather than prototype fixture records. -->
<script setup>
import { ref, onMounted } from "vue";
import request from "@/utils/request";
const props = defineProps({ resource: { type: String, required: true } });
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  search = ref(""),
  busy = ref(false);
async function load() {
  busy.value = true;
  try {
    const result = await request({
      url: "/system/" + props.resource + "/list",
      params: {
        pageNum: page.value,
        pageSize: 20,
        [props.resource === "user" ? "userName" : "roleName"]: search.value,
      },
    });
    rows.value = result.rows || [];
    total.value = result.total || 0;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <el-card
    ><el-input
      v-model="search"
      :placeholder="$t(&quot;Search&quot;)"
      @keyup.enter="
        page = 1;
        load();
      " /><el-button
      @click="
        page = 1;
        load();
      "
      >{{ $t("Search") }}</el-button
    ><el-table :data="rows" v-loading="busy"
      ><el-table-column
        :prop="resource === 'user' ? 'userId' : 'roleId'"
        :label="$t(&quot;ID&quot;)"
      /><el-table-column
        :prop="resource === 'user' ? 'userName' : 'roleName'"
        :label="$t(&quot;Name&quot;)"
      /><el-table-column
        :prop="resource === 'user' ? 'nickName' : 'roleKey'"
        :label="$t(&quot;Display name / role key&quot;)"
      /><el-table-column prop="status" :label="$t(&quot;Status&quot;)"
        ><template #default="scope">{{ $t(scope.row.status === "0" ? "Active" : "Disabled") }}</template></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
  /></el-card>
</template>
