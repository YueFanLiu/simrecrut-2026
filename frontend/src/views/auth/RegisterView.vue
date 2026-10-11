<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FieldIcon from '../../components/simrecrut/FieldIcon.vue'
import PasswordInput from '../../components/simrecrut/PasswordInput.vue'
import { registrationEnabled } from '../../config/demo.js'
import { registerDemo } from '../../mock/auth.js'
import {
  validateUsername, validateEmail, validateRegistrationPassword,
  validatePasswordConfirmation, validateAgreement,
} from '../../utils/registration-validation.js'

const router = useRouter()
const formRef = ref()
const form = reactive({ username: '', email: '', password: '', confirmation: '', agreedToTerms: false })
const loading = ref(false)
const registrationError = ref('')
const createdUsername = ref('')
const notice = ref('')
const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  email: [{ validator: validateEmail, trigger: 'blur' }],
  password: [{ validator: validateRegistrationPassword, trigger: 'blur' }],
  confirmation: [{
    validator: (_rule, value, callback) => {
      const error = validatePasswordConfirmation(form.password, value)
      callback(error ? new Error(error) : undefined)
    },
    trigger: 'blur',
  }],
  agreedToTerms: [{ validator: validateAgreement, trigger: 'change' }],
}

function passwordChanged() {
  registrationError.value = ''
  if (form.confirmation) formRef.value.validateField('confirmation').catch(() => {})
}

async function submitRegistration() {
  if (loading.value || !registrationEnabled || createdUsername.value) return
  registrationError.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value) return
  loading.value = true
  try {
    const account = await registerDemo(form)
    createdUsername.value = account.username
    form.password = ''
    form.confirmation = ''
  } catch (error) {
    registrationError.value = error.message || 'Unable to create your demo account. Please try again.'
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push({ name: 'Login' })
}
</script>

<template>
  <AuthLayout title-id="register-title">
    <div v-if="!registrationEnabled" class="result-state">
      <div class="result-icon unavailable" aria-hidden="true">×</div>
      <h1 id="register-title">Registration unavailable</h1>
      <p>Demo registration is currently disabled.</p>
      <p>Please use the existing demo account.</p>
      <ElButton type="primary" size="large" @click="goToLogin">Back to login</ElButton>
    </div>

    <div v-else-if="createdUsername" class="result-state" role="status">
      <div class="result-icon success" aria-hidden="true">✓</div>
      <h1 id="register-title">Account created successfully</h1>
      <p>Your Candidate demo account <strong>{{ createdUsername }}</strong> is ready.</p>
      <p>You can now sign in with your username or email.</p>
      <p class="result-note">This account exists only until you refresh or close this browser tab.</p>
      <ElButton type="primary" size="large" @click="goToLogin">Go to sign in</ElButton>
    </div>

    <template v-else>
      <h1 id="register-title">Create an account</h1>
      <p class="register-subtitle">Join SimRecrut and start your simulation journey</p>
      <div class="feedback" aria-live="polite" aria-atomic="true">
        <ElAlert v-if="registrationError" :title="registrationError" type="error"
          show-icon :closable="false" />
      </div>

      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top"
        :disabled="loading" :aria-busy="loading" @submit.prevent="submitRegistration">
        <ElFormItem label="Username" prop="username">
          <ElInput v-model="form.username" placeholder="Enter a username" size="large"
            autocomplete="username" autocapitalize="none" :spellcheck="false"
            aria-describedby="username-hint" @input="registrationError = ''">
            <template #prefix><FieldIcon name="user" /></template>
          </ElInput>
        </ElFormItem>
        <p id="username-hint" class="field-hint">3-20 characters: letters, numbers or underscores.</p>

        <ElFormItem label="Email address" prop="email">
          <ElInput v-model="form.email" placeholder="Enter your email address" size="large"
            autocomplete="email" inputmode="email" autocapitalize="none" :spellcheck="false"
            @input="registrationError = ''">
            <template #prefix><FieldIcon name="email" /></template>
          </ElInput>
        </ElFormItem>

        <ElFormItem label="Password" prop="password">
          <PasswordInput v-model="form.password" placeholder="Create a password" :disabled="loading"
            aria-describedby="password-hint" @input="passwordChanged" />
        </ElFormItem>
        <p id="password-hint" class="field-hint">At least 8 characters, including letters and numbers.</p>

        <ElFormItem label="Confirm password" prop="confirmation">
          <PasswordInput v-model="form.confirmation" placeholder="Confirm your password"
            :disabled="loading" @input="registrationError = ''" />
        </ElFormItem>

        <ElFormItem prop="agreedToTerms" class="agreement-item">
          <div class="agreement-row">
            <ElCheckbox v-model="form.agreedToTerms" aria-label="Agree to demo terms and privacy notice">
              I agree to the
            </ElCheckbox>
            <button type="button" class="notice-link" :disabled="loading" @click="notice = 'terms'">
              Terms of Use
            </button>
            <span>and</span>
            <button type="button" class="notice-link" :disabled="loading" @click="notice = 'privacy'">
              Privacy Policy
            </button>
          </div>
        </ElFormItem>

        <ElButton class="full-width" type="primary" size="large" native-type="submit"
          :loading="loading" :disabled="loading">
          {{ loading ? 'Creating account...' : 'Create account' }}
        </ElButton>
      </ElForm>

      <div class="sign-in-divider"><span>Already have an account?</span></div>
      <ElButton class="full-width" size="large" :disabled="loading" @click="goToLogin">Sign in</ElButton>
      <aside class="demo-note">
        <strong>Frontend demo only</strong>
        <p>Creates a temporary Candidate account. No email is sent and no data is sent to a server.</p>
        <p>The account is cleared when you refresh or close this tab. Use invented details and a demo password.</p>
        <p>Validation rules are provisional for this prototype.</p>
      </aside>
    </template>

    <ElDialog :model-value="Boolean(notice)" :title="notice === 'terms' ? 'Demo Terms of Use' : 'Demo Privacy Policy'"
      width="min(480px, calc(100vw - 32px))" @close="notice = ''">
      <template v-if="notice === 'terms'">
        <p>This is a frontend demonstration for reviewing the SimRecrut interface.</p>
        <p>Registration creates a temporary Candidate demo account. It does not create a real account or grant real permissions.</p>
        <p>These are prototype notices. Final terms must be supplied before a real service is enabled.</p>
      </template>
      <template v-else>
        <p>Registration details and demo credentials are held in this tab's memory and cleared on refresh.</p>
        <p>No registration data is sent to a server. Login's Remember Me option stores only the identifier in local storage.</p>
        <p>Use invented details and a demo password. This prototype notice is not a finalized privacy policy.</p>
      </template>
      <template #footer><ElButton type="primary" @click="notice = ''">Close</ElButton></template>
    </ElDialog>
  </AuthLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 27px; letter-spacing: -0.7px; color: #132941; }
.register-subtitle { margin: 8px 0 24px; font-size: 14px; line-height: 1.5; color: #627086; }
.feedback:has(.el-alert) { margin-bottom: 20px; }
:deep(.el-form-item) { margin-bottom: 23px; }
:deep(.el-form-item__label) { color: #22344b; font-weight: 600; padding-bottom: 7px; }
:deep(.el-input__wrapper) { padding: 3px 12px; }
:deep(.el-input__prefix-inner) { margin-right: 4px; }
:deep(.el-form-item__error) { position: static; width: 100%; padding-top: 6px; }
.field-hint { margin: -16px 0 17px; color: #68798c; font-size: 12px; line-height: 1.5; }
.agreement-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0 5px; font-size: 12px; }
:deep(.el-checkbox__label) { color: #425269; font-size: 12px; }
.notice-link { padding: 0; border: 0; background: transparent; color: #176bdf; font: inherit; cursor: pointer; }
.notice-link:focus-visible { outline: 2px solid #176bdf; outline-offset: 2px; }
.notice-link:disabled { cursor: default; color: #8995a7; }
.full-width { width: 100%; font-weight: 600; }
.sign-in-divider { display: flex; align-items: center; gap: 10px; margin: 20px 0 16px; color: #68798c; font-size: 12px; }
.sign-in-divider::before, .sign-in-divider::after { content: ''; height: 1px; flex: 1; background: #e8edf4; }
.demo-note { margin-top: 20px; padding: 14px; border: 1px solid #deebfa; border-radius: 8px; background: #f5f9ff; color: #4a607b; font-size: 12px; }
.demo-note strong { color: #31557f; font-size: 13px; }
.demo-note p { margin: 7px 0 0; line-height: 1.6; }
.result-state { text-align: center; padding: 24px 0; }
.result-state p { color: #586b83; line-height: 1.7; font-size: 14px; overflow-wrap: anywhere; }
.result-state h1 { font-size: 25px; }
.result-icon { display: grid; place-items: center; width: 64px; height: 64px; margin: 0 auto 24px; border-radius: 50%; font-size: 38px; }
.success { background: #e5f7ec; color: #20854b; }
.unavailable { background: #eaf0f7; color: #6f8299; }
.result-note { margin-top: 24px; }
.result-state .el-button { width: 100%; margin-top: 16px; }
:deep(.el-dialog__body p) { line-height: 1.7; }
</style>
