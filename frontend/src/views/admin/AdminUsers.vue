<!-- Use official RuoYi identity data outside explicit demo mode. -->
<script setup>
import { demoMode } from "@/store/modules/user";
import Demo from "./AdminUsersDemo.vue";
import SystemAccountTable from "@/components/SystemAccountTable.vue";
</script>
<template>
  <Demo v-if="demoMode" /><SystemAccountTable v-else resource="user" />
</template>
