<!-- Authenticate against the configured RuoYi-compatible identity adapter. -->
<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import useUserStore from "@/store/modules/user";
import { pages } from "@/router/pages";
import { permissionFor } from "@/router/permissions";
import { getCaptcha } from "@/api/login";
const captcha = ref(""),
  captchaEnabled = ref(true);
const form = reactive({ username: "", password: "", code: "", uuid: "" });
async function refreshCaptcha() {
  try {
    const result = await getCaptcha();
    captchaEnabled.value = result.captchaEnabled !== false;
    captcha.value = result.img ? "data:image/png;base64," + result.img : "";
    form.uuid = result.uuid || "";
  } catch {
    message.value = "Identity service is unavailable.";
  }
}
onMounted(refreshCaptcha);
const busy = ref(false),
  message = ref("");
const route = useRoute(),
  router = useRouter(),
  user = useUserStore();
async function submit() {
  busy.value = true;
  message.value = "";
  try {
    await user.Login(form);
    form.password = "";
    const redirect = String(
      route.query.redirect ||
        pages.find(
          (page) =>
            user.permissions.includes("*:*:*") ||
            user.permissions.includes(permissionFor(page[0])),
        )?.[3] ||
        "/forbidden",
    );
    await router.replace(
      redirect.startsWith("/") && !redirect.startsWith("//")
        ? redirect
        : "/research/experiments",
    );
  } catch {
    refreshCaptcha();
    message.value =
      "Sign-in failed. Verify your credentials and the configured identity service.";
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <el-card class="login-card"
      ><h1>SimRecrut</h1>
      <p>Sign in to your workspace</p>
      <el-form @submit.prevent="submit" label-position="top"
        ><el-form-item label="Username"
          ><el-input
            v-model="form.username"
            autocomplete="username"
            required /></el-form-item
        ><el-form-item label="Password"
          ><el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            required /></el-form-item
        ><el-form-item v-if="captchaEnabled" label="Verification code"
          ><el-input v-model="form.code" required /><img
            v-if="captcha"
            :src="captcha"
            alt="Verification challenge"
            @click="refreshCaptcha" /></el-form-item
        ><el-alert
          v-if="message"
          :title="message"
          type="error"
          :closable="false"
        /><el-button native-type="submit" type="primary" :loading="busy"
          >Sign in</el-button
        ></el-form
      >
      <p class="muted">
        Requires the configured identity service. This screen does not simulate
        authentication.
      </p></el-card
    >
  </div>
</template>

<style scoped>
/* Keep authentication outside the workspace sidebar layout. */
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f3f6fb;
  padding: 24px;
}
.login-card {
  width: min(420px, 100%);
}
.login-card h1 {
  color: #2456a6;
}
.login-card img {
  max-height: 60px;
  cursor: pointer;
  margin-top: 8px;
}
.login-card .el-button {
  width: 100%;
  margin-top: 16px;
}
.muted {
  color: #64748b;
  font-size: 13px;
}
</style>
