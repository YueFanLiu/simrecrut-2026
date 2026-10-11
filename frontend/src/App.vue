<script setup>
import { ElConfigProvider } from 'element-plus'
import en from 'element-plus/es/locale/lang/en'
</script>

<template>
  <ElConfigProvider :locale="en">
    <RouterView />
  </ElConfigProvider>
</template>
