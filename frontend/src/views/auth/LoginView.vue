<script setup>
import { useLocalizedValidation, t } from '../../i18n/index.js'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FieldIcon from '../../components/simrecrut/FieldIcon.vue'
import PasswordInput from '../../components/simrecrut/PasswordInput.vue'
import { registrationEnabled } from '../../config/demo.js'
import { useAuthStore } from '../../store/modules/auth.js'
import { demoMode } from '../../config/runtime.js'
import { demoAccount } from '../../mock/auth.js'
import { readRememberedIdentifier, saveRememberedIdentifier } from '../../utils/remembered-account.js'
import { validateIdentifier, validatePassword } from '../../utils/login-validation.js'
import { afterSignIn, authPageRoute, guestBrowseDestination, resolvedAuthReturnPath } from '../../utils/auth-navigation.js'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const returnTo = computed(() => resolvedAuthReturnPath(route.query.redirect, path => router.resolve(path)))
const formRef = ref()
const rememberedIdentifier = readRememberedIdentifier()
const form = reactive({
  identifier: rememberedIdentifier,
  password: '',
  rememberMe: Boolean(rememberedIdentifier),
})
const loading = ref(false)
const loginError = ref('')
const canRegister = !demoMode || registrationEnabled
const rules = {
  identifier: [{ validator: validateIdentifier, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
}

async function submitLogin() {
  if (loading.value) return
  loginError.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value) return

  loading.value = true
  try {
    await auth.login({ identifier: form.identifier, password: form.password })
    const saved = saveRememberedIdentifier(form.identifier, form.rememberMe)
    if (!saved) ElMessage.warning(t('Your browser could not save the Remember Me preference.'))
    form.password = ''
    await router.replace(afterSignIn(returnTo.value, auth, demoMode, path => router.resolve(path)))
  } catch (error) {
    loginError.value = error.message || 'Unable to sign in. Please try again.'
  } finally {
    loading.value = false
  }
}

useLocalizedValidation(formRef)
</script>

<template>
  <AuthLayout>
    <h1 id="login-title">{{ $t("Welcome back") }}</h1>
    <p class="login-subtitle">{{ $t("Sign in to your SimRecrut account") }}</p>
    <p v-if="returnTo" class="return-note">{{ $t('After signing in, you will return to your selected page. No simulation starts automatically.') }}</p>

    <div class="feedback" aria-live="polite" aria-atomic="true">
      <el-alert v-if="loginError" :title="$t(loginError)" type="error" show-icon :closable="false" />
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      :disabled="loading" :aria-busy="loading" @submit.prevent="submitLogin">
      <el-form-item :label="$t(&quot;Username or email&quot;)" prop="identifier">
        <el-input v-model="form.identifier" :placeholder="$t(&quot;Enter your username or email&quot;)" size="large"
          autocomplete="username" autocapitalize="none" :spellcheck="false"
          @input="loginError = ''">
          <template #prefix><FieldIcon name="user" /></template>
        </el-input>
      </el-form-item>

      <el-form-item :label="$t(&quot;Password&quot;)" prop="password">
        <PasswordInput v-model="form.password" autocomplete="current-password"
          :disabled="loading" @input="loginError = ''" />
      </el-form-item>

      <div class="remember-row">
        <label class="remember-choice"><input v-model="form.rememberMe" type="checkbox" :disabled="loading" aria-describedby="remember-identifier-hint">
          <span>{{ $t('Remember my username or email') }}</span></label>
        <p id="remember-identifier-hint">{{ $t('This option saves your username or email in this browser, never your password.') }}</p>
      </div>

      <el-button class="sign-in-button" type="primary" size="large" native-type="submit"
        :loading="loading" :disabled="loading">
        {{ $t(loading ? 'Signing in...' : 'Sign in') }}
      </el-button>
    </el-form>

    <RouterLink v-if="!demoMode" class="forgot-password" :to="authPageRoute('ForgotPassword', returnTo)">{{ $t("Forgot password?") }}</RouterLink>

    <div class="registration-divider"><span>{{ $t("or") }}</span></div>
    <el-button class="register-button" size="large" :disabled="loading || !canRegister"
      @click="router.push(authPageRoute('Register', returnTo))">{{ $t("Create a new account") }}</el-button>
    <p v-if="!canRegister" class="registration-note">{{ $t("Registration is not currently available.") }}</p>
    <RouterLink class="guest-link" :to="guestBrowseDestination(returnTo)">{{ $t("Browse jobs without signing in") }} →</RouterLink>

    <aside v-if="demoMode" class="demo-note" :aria-label="$t(&quot;Demo account&quot;)">
      <strong>{{ $t("Frontend demo only") }}</strong>
      <p>{{ $t("This is a simulated login, not real authentication.") }}</p>
      <dl>
        <div><dt>{{ $t("Username") }}</dt><dd>{{ demoAccount.username }}</dd></div>
        <div><dt>{{ $t("Password") }}</dt><dd>{{ demoAccount.password }}</dd></div>
      </dl>
      <p class="demo-email">{{ $t("You can also use") }} {{ demoAccount.email }}.</p>
    </aside>
  </AuthLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 30px; letter-spacing: -0.8px; color: var(--sr-ink); }
.login-subtitle { margin: 8px 0 26px; font-size: 14px; color: var(--sr-text-muted); }
.return-note { padding:12px; border-radius:8px; background:var(--sr-primary-50); color:var(--sr-text-secondary); font-size:13px; line-height:1.7; }
.feedback:has(.el-alert) { margin-bottom: 20px; }
:deep(.el-form-item) { margin-bottom: 23px; }
:deep(.el-form-item__label) { color: var(--sr-text); font-weight: 600; padding-bottom: 7px; }
:deep(.el-input__wrapper) { padding: 3px 12px; }
:deep(.el-input__prefix-inner) { margin-right: 4px; }
.remember-row { margin: -2px 0 20px; }
.remember-choice { display:flex; align-items:flex-start; gap:9px; color:var(--sr-text-secondary); font-size:13px; line-height:1.7; cursor:pointer; }
.remember-choice input { flex:none; width:16px; height:16px; margin:3px 0 0; accent-color:var(--sr-primary); }
.remember-choice input:focus-visible { outline:2px solid var(--sr-primary); outline-offset:3px; }
.remember-row p { margin:6px 0 0 25px; color:var(--sr-text-muted); font-size:12px; line-height:1.6; }
:deep(.el-checkbox__label) { color: var(--sr-text-secondary); font-size: 13px; }
.sign-in-button, .register-button { width: 100%; font-weight: 600; }
.forgot-password { display: block; width: fit-content; margin: 12px auto 0; color: var(--el-color-primary); font-size: 14px; }
.registration-divider { display: flex; align-items: center; gap: 14px; margin: 20px 0 16px; color: var(--sr-text-subtle); font-size: 13px; }
.registration-divider::before, .registration-divider::after { content: ''; height: 1px; background: var(--sr-border); flex: 1; }
.guest-link { display: block; width: fit-content; margin: 16px auto 0; color: var(--sr-primary); font-size: 14px; font-weight: 600; text-decoration: none; }
.guest-link:hover { text-decoration: underline; }
.registration-note { margin: 10px 0 0; text-align: center; font-size: 12px; color: var(--sr-text-muted); }
.demo-note { margin-top: 24px; padding: 16px; border: 1px solid var(--sr-primary-100); border-radius: 8px; background: var(--sr-surface-tint); font-size: 12px; color: var(--sr-text-muted); }
.demo-note strong { color: var(--sr-text-secondary); font-size: 13px; }
.demo-note p { margin: 7px 0 0; line-height: 1.6; }
.demo-note dl { margin: 12px 0 0; }
.demo-note dl div { display: flex; gap: 12px; margin-top: 5px; }
.demo-note dt { min-width: 65px; }
.demo-note dd { margin: 0; color: var(--sr-text); font-family: Consolas, monospace; overflow-wrap: anywhere; }
.demo-email { overflow-wrap: anywhere; }
</style>
