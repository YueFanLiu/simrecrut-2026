<script setup>
import { ElConfigProvider } from 'element-plus'
import en from 'element-plus/es/locale/lang/en'
</script>

<template>
  <ElConfigProvider :locale="en">
    <!-- Remount pages when their route changes to avoid stale review state. -->
    <RouterView :key="$route.path" />
  </ElConfigProvider>
</template>
