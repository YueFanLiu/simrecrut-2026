<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FieldIcon from '../../components/simrecrut/FieldIcon.vue'
import PasswordInput from '../../components/simrecrut/PasswordInput.vue'
import { registrationEnabled } from '../../config/demo.js'
import { useAuthStore } from '../../store/modules/auth.js'
import { demoAccount } from '../../mock/auth.js'
import { readRememberedIdentifier, saveRememberedIdentifier } from '../../utils/remembered-account.js'
import { validateIdentifier, validatePassword } from '../../utils/login-validation.js'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const rememberedIdentifier = readRememberedIdentifier()
const form = reactive({
  identifier: rememberedIdentifier,
  password: '',
  rememberMe: Boolean(rememberedIdentifier),
})
const loading = ref(false)
const loginError = ref('')
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
    if (!saved) ElMessage.warning('Your browser could not save the Remember Me preference.')
    form.password = ''
    await router.replace({ name: 'DemoDashboard' })
  } catch (error) {
    loginError.value = error.message || 'Unable to sign in. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h1 id="login-title">Welcome back</h1>
    <p class="login-subtitle">Sign in to your SimRecrut account</p>

    <div class="feedback" aria-live="polite" aria-atomic="true">
      <el-alert v-if="loginError" :title="loginError" type="error" show-icon :closable="false" />
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      :disabled="loading" :aria-busy="loading" @submit.prevent="submitLogin">
      <el-form-item label="Username or email" prop="identifier">
        <el-input v-model="form.identifier" placeholder="Enter your username or email" size="large"
          autocomplete="username" autocapitalize="none" :spellcheck="false"
          @input="loginError = ''">
          <template #prefix><FieldIcon name="user" /></template>
        </el-input>
      </el-form-item>

      <el-form-item label="Password" prop="password">
        <PasswordInput v-model="form.password" autocomplete="current-password"
          :disabled="loading" @input="loginError = ''" />
      </el-form-item>

      <div class="remember-row">
        <el-checkbox v-model="form.rememberMe">Remember Me</el-checkbox>
        <span>Username only</span>
      </div>

      <el-button class="sign-in-button" type="primary" size="large" native-type="submit"
        :loading="loading" :disabled="loading">
        {{ loading ? 'Signing in...' : 'Sign in' }}
      </el-button>
    </el-form>

    <div class="registration-divider"><span>or</span></div>
    <el-button class="register-button" size="large" :disabled="loading || !registrationEnabled"
      @click="router.push({ name: 'Register' })">Create a new account</el-button>
    <p v-if="!registrationEnabled" class="registration-note">Registration is not available in this demo.</p>

    <aside class="demo-note" aria-label="Demo account">
      <strong>Frontend demo only</strong>
      <p>This is a simulated login, not real authentication.</p>
      <dl>
        <div><dt>Username</dt><dd>{{ demoAccount.username }}</dd></div>
        <div><dt>Password</dt><dd>{{ demoAccount.password }}</dd></div>
      </dl>
      <p class="demo-email">You can also use {{ demoAccount.email }}.</p>
    </aside>
  </AuthLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 28px; letter-spacing: -0.8px; color: #132941; }
.login-subtitle { margin: 8px 0 26px; font-size: 14px; color: #627086; }
.feedback:has(.el-alert) { margin-bottom: 20px; }
:deep(.el-form-item) { margin-bottom: 23px; }
:deep(.el-form-item__label) { color: #22344b; font-weight: 600; padding-bottom: 7px; }
:deep(.el-input__wrapper) { padding: 3px 12px; }
:deep(.el-input__prefix-inner) { margin-right: 4px; }
.remember-row { display: flex; align-items: center; justify-content: space-between; margin: -2px 0 20px; }
.remember-row > span { color: #68798c; font-size: 12px; }
:deep(.el-checkbox__label) { color: #425269; font-size: 13px; }
.sign-in-button, .register-button { width: 100%; font-weight: 600; }
.registration-divider { display: flex; align-items: center; gap: 14px; margin: 20px 0 16px; color: #8491a3; font-size: 13px; }
.registration-divider::before, .registration-divider::after { content: ''; height: 1px; background: #e8edf4; flex: 1; }
.registration-note { margin: 10px 0 0; text-align: center; font-size: 12px; color: #68798c; }
.demo-note { margin-top: 24px; padding: 16px; border: 1px solid #deebfa; border-radius: 8px; background: #f5f9ff; font-size: 12px; color: #4a607b; }
.demo-note strong { color: #31557f; font-size: 13px; }
.demo-note p { margin: 7px 0 0; line-height: 1.6; }
.demo-note dl { margin: 12px 0 0; }
.demo-note dl div { display: flex; gap: 12px; margin-top: 5px; }
.demo-note dt { min-width: 65px; }
.demo-note dd { margin: 0; color: #223f61; font-family: Consolas, monospace; overflow-wrap: anywhere; }
.demo-email { overflow-wrap: anywhere; }
</style>
