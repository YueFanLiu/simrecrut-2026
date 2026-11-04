<script setup>
import { ElAlert } from 'element-plus'
import { useRoute } from 'vue-router'
import AdminLayout from '../../layouts/AdminLayout.vue'

const route = useRoute()
</script>

<template>
  <AdminLayout>
    <section aria-labelledby="admin-unavailable-title">
      <h1 id="admin-unavailable-title">{{ route.meta.title }}</h1>
      <ElAlert
        title="This page is currently available in demo mode. Its backend API is not implemented."
        type="info"
        :closable="false"
      />
    </section>
  </AdminLayout>
</template>
