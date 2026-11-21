<script setup>
import { t, useLocalizedValidation } from '../../i18n/index.js'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput, ElRadioGroup, ElRadioButton } from 'element-plus'
import AuthLayout from '../../layouts/AuthLayout.vue'
import FieldIcon from '../../components/simrecrut/FieldIcon.vue'
import PasswordInput from '../../components/simrecrut/PasswordInput.vue'
import { registrationEnabled as demoRegistrationEnabled } from '../../config/demo.js'
import { demoMode } from '../../config/runtime.js'
import { sendRegistrationCode, register } from '../../api/login.js'
import { registerDemo } from '../../mock/auth.js'
import {
  validateUsername, validateEmail, validateRegistrationPassword,
  validatePasswordConfirmation, validateAgreement, validateConnectedPassword, validateConnectedEmail,
} from '../../utils/registration-validation.js'

const router = useRouter()
const formRef = ref()
const form = reactive({ username: '', email: '', password: '', confirmation: '', agreedToTerms: false })
const loading = ref(false)
const registrationError = ref('')
const createdUsername = ref('')
const notice = ref('')
const registrationEnabled = !demoMode || demoRegistrationEnabled
const sendingCode = ref(false)
const codeNotice = ref('')
const challenge = ref(null)
const now = ref(Date.now())
const resendAt = ref(0)
const createdRole = ref('Candidate')
form.accountType = 'CANDIDATE'
form.emailCode = ''
const normalizedEmail = computed(() => form.email.trim().toLowerCase())
const cooldown = computed(() => Math.max(0, Math.ceil((resendAt.value - now.value) / 1000)))
const challengeValid = computed(() => challenge.value?.email === normalizedEmail.value
  && challenge.value.expiresAt > now.value)
const timer = setInterval(() => { now.value = Date.now() }, 1000)
onUnmounted(() => clearInterval(timer))
watch(normalizedEmail, () => {
  challenge.value = null
  form.emailCode = ''
  codeNotice.value = ''
})
async function requestCode() {
  if (sendingCode.value || cooldown.value || loading.value) return
  if (!await formRef.value.validateField('email').then(() => true).catch(() => false)) return
  if (sendingCode.value || cooldown.value || loading.value) return
  sendingCode.value = true
  registrationError.value = ''
  const email = normalizedEmail.value
  try {
    const { data } = await sendRegistrationCode({ email })
    const receivedAt = Date.now()
    now.value = receivedAt
    resendAt.value = receivedAt + data.resendAfterSeconds * 1000
    if (email !== normalizedEmail.value) return
    challenge.value = { id: data.challengeId, email,
      expiresAt: receivedAt + data.expiresInSeconds * 1000 }
    form.emailCode = ''
    codeNotice.value = 'Code sent. Check your inbox and spam folder.'
  } catch (error) {
    const retryAfter = Number(error.response?.headers?.['retry-after'])
    if (retryAfter > 0) resendAt.value = Date.now() + retryAfter * 1000
    registrationError.value = error.message || 'Unable to send a verification code. Please retry.'
  } finally { sendingCode.value = false }
}
const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  email: [{ validator: demoMode ? validateEmail : validateConnectedEmail, trigger: 'blur' }],
  password: [{ validator: demoMode ? validateRegistrationPassword : validateConnectedPassword, trigger: 'blur' }],
  confirmation: [{
    validator: (_rule, value, callback) => {
      const error = validatePasswordConfirmation(form.password, value)
      callback(error ? new Error(error) : undefined)
    },
    trigger: 'blur',
  }],
  emailCode: [{ validator: (_rule, value, callback) => {
    callback(demoMode || (challengeValid.value && /^[0-9]{6}$/.test(value)) ? undefined
      : new Error(t('Request a code for this email and enter its six digits before it expires.')))
  }, trigger: 'blur' }],
  agreedToTerms: [{ validator: validateAgreement, trigger: 'change' }],
}

function passwordChanged() {
  registrationError.value = ''
  if (form.confirmation) formRef.value.validateField('confirmation').catch(() => {})
}

async function submitRegistration() {
  if (loading.value || !registrationEnabled || sendingCode.value || createdUsername.value) return
  registrationError.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value || sendingCode.value) return
  loading.value = true
  try {
    if (demoMode) createdUsername.value = (await registerDemo(form)).username
    else {
      const { data } = await register({ username: form.username.trim(), email: normalizedEmail.value,
        password: form.password, passwordConfirmation: form.confirmation,
        accountType: form.accountType, challengeId: challenge.value.id,
        emailCode: form.emailCode, agreedToTerms: form.agreedToTerms })
      createdUsername.value = data.username
      createdRole.value = data.accountType === 'HR' ? 'HR' : 'Candidate'
      challenge.value = null
      form.emailCode = ''
    }
    form.password = ''
    form.confirmation = ''
  } catch (error) {
    // Verification is consumed before account creation, including duplicate-account failures.
    if (!demoMode) { challenge.value = null; form.emailCode = ''; codeNotice.value = '' }
    registrationError.value = error.message || 'Unable to create your account. Please try again.'
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push({ name: 'Login' })
}

useLocalizedValidation(formRef)
</script>

<template>
  <AuthLayout title-id="register-title">
    <div v-if="!registrationEnabled" class="result-state">
      <div class="result-icon unavailable" aria-hidden="true">×</div>
      <h1 id="register-title">{{ $t("Registration unavailable") }}</h1>
      <p>{{ $t("Registration is currently disabled or unavailable.") }}</p>
      <p>{{ $t("Please use an existing account.") }}</p>
      <ElAlert v-if="registrationError" :title="$t(registrationError)" type="error" :closable="false" />
      <ElButton type="primary" size="large" @click="goToLogin">{{ $t("Back to login") }}</ElButton>
    </div>

    <div v-else-if="createdUsername" class="result-state" role="status">
      <div class="result-icon success" aria-hidden="true">✓</div>
      <h1 id="register-title">{{ $t("Account created successfully") }}</h1>
      <p>{{ $t('Your {role} account {name} is ready.', { role: $t(createdRole), name: createdUsername }) }}</p>
      <p>{{ $t("You can now sign in with your username or email.") }}</p>
      <p v-if="demoMode" class="result-note">{{ $t("This account exists only until you refresh or close this browser tab.") }}</p>
      <p v-else class="result-note">{{ $t("Your account is saved. Sign in to continue to your workspace.") }}</p>
      <ElButton type="primary" size="large" @click="goToLogin">{{ $t("Go to sign in") }}</ElButton>
    </div>

    <template v-else>
      <h1 id="register-title">{{ $t("Create an account") }}</h1>
      <p class="register-subtitle">{{ $t("Join SimRecrut and start your simulation journey") }}</p>
      <div class="feedback" aria-live="polite" aria-atomic="true">
        <ElAlert v-if="registrationError" :title="$t(registrationError)" type="error"
          show-icon :closable="false" />
      </div>

      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top"
        :disabled="loading" :aria-busy="loading || sendingCode" @submit.prevent="submitRegistration">
        <ElFormItem :label="$t(&quot;Username&quot;)" prop="username">
          <ElInput v-model="form.username" :placeholder="$t(&quot;Enter a username&quot;)" size="large"
            autocomplete="username" autocapitalize="none" :spellcheck="false"
            aria-describedby="username-hint" @input="registrationError = ''">
            <template #prefix><FieldIcon name="user" /></template>
          </ElInput>
        </ElFormItem>
        <p id="username-hint" class="field-hint">{{ $t("3-20 characters: letters, numbers or underscores.") }}</p>

        <ElFormItem :label="$t(&quot;Email address&quot;)" prop="email">
          <ElInput v-model="form.email" :placeholder="$t(&quot;Enter your email address&quot;)" size="large"
            autocomplete="email" inputmode="email" autocapitalize="none" :spellcheck="false"
            @input="registrationError = ''">
            <template #prefix><FieldIcon name="email" /></template>
          </ElInput>
        </ElFormItem>

        <template v-if="!demoMode">
          <ElFormItem :label="$t(&quot;Email verification code&quot;)" prop="emailCode">
            <div class="code-row">
              <ElInput v-model="form.emailCode" :placeholder="$t(&quot;6-digit code&quot;)" size="large"
                inputmode="numeric" autocomplete="one-time-code" maxlength="6" />
              <ElButton :loading="sendingCode" :disabled="loading || sendingCode || cooldown > 0"
                @click="requestCode">{{ cooldown ? $t('Resend in {seconds}s', { seconds: cooldown }) : $t('Send code') }}</ElButton>
            </div>
          </ElFormItem>
          <p v-if="sendingCode" class="field-hint" role="status">{{ $t("Sending your code. This may take a few seconds.") }}</p>
          <p v-else-if="codeNotice" class="field-hint" role="status">{{ $t(codeNotice) }}
            {{ $t(challenge && !challengeValid ? 'Code expired. Request a new code.' : '') }}</p>
          <ElFormItem :label="$t(&quot;Account type&quot;)">
            <ElRadioGroup v-model="form.accountType" :aria-label="$t(&quot;Account type&quot;)">
              <ElRadioButton value="CANDIDATE">{{ $t("Candidate") }}</ElRadioButton>
              <ElRadioButton value="HR">{{ $t("HR") }}</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
        </template>

        <ElFormItem :label="$t(&quot;Password&quot;)" prop="password">
          <PasswordInput v-model="form.password" :placeholder="$t(&quot;Create a password&quot;)" :disabled="loading"
            aria-describedby="password-hint" @input="passwordChanged" />
        </ElFormItem>
        <p id="password-hint" class="field-hint">{{ $t(demoMode ? '8-20 characters, including letters and numbers.' : 'Use at least 12 characters.') }}</p>

        <ElFormItem :label="$t(&quot;Confirm password&quot;)" prop="confirmation">
          <PasswordInput v-model="form.confirmation" :placeholder="$t(&quot;Confirm your password&quot;)"
            :disabled="loading" @input="registrationError = ''" />
        </ElFormItem>

        <ElFormItem prop="agreedToTerms" class="agreement-item">
          <div class="agreement-row">
            <ElCheckbox v-model="form.agreedToTerms" :aria-label="$t(&quot;Read registration and privacy notices&quot;)"> {{ $t("I have read the") }} </ElCheckbox>
            <button type="button" class="notice-link" :disabled="loading" @click="notice = 'terms'"> {{ $t("Terms of Use") }} </button>
            <span>{{ $t("and") }}</span>
            <button type="button" class="notice-link" :disabled="loading" @click="notice = 'privacy'"> {{ $t("Privacy Policy") }} </button>
          </div>
        </ElFormItem>

        <ElButton class="full-width" type="primary" size="large" native-type="submit"
          :loading="loading" :disabled="loading || sendingCode">
          {{ $t(loading ? 'Creating account...' : 'Create account') }}
        </ElButton>
      </ElForm>

      <div class="sign-in-divider"><span>{{ $t("Already have an account?") }}</span></div>
      <ElButton class="full-width" size="large" :disabled="loading" @click="goToLogin">{{ $t("Sign in") }}</ElButton>
      <aside v-if="demoMode" class="demo-note">
        <strong>{{ $t("Frontend demo only") }}</strong>
        <p>{{ $t("Creates a temporary Candidate account. No email is sent and no data is sent to a server.") }}</p>
        <p>{{ $t("The account is cleared when you refresh or close this tab. Use invented details and a demo password.") }}</p>
        <p>{{ $t("Validation rules are provisional for this prototype.") }}</p>
      </aside>
    </template>

    <ElDialog :model-value="Boolean(notice)" :title="$t(notice === 'terms' ? 'Registration notice' : 'Privacy notice')"
      width="min(480px, calc(100vw - 32px))" @close="notice = ''">
      <template v-if="!demoMode">
        <p v-if="notice === 'terms'">{{ $t("This development service creates a persistent Candidate or HR account for the SimRecrut project. This notice is not a finalized legal agreement.") }}</p>
        <template v-else><p>{{ $t("Your username, email and hashed password are stored in the configured project database. Your password is used for authentication and is not stored as readable text.") }}</p><p>{{ $t("A verification code is sent to confirm your email address. Remember Me stores only your sign-in identifier in this browser.") }}</p></template>
      </template>
      <template v-else-if="notice === 'terms'">
        <p>{{ $t("This is a frontend demonstration for reviewing the SimRecrut interface.") }}</p>
        <p>{{ $t("Registration creates a temporary Candidate demo account. It does not create a real account or grant real permissions.") }}</p>
        <p>{{ $t("These are prototype notices. Final terms must be supplied before a real service is enabled.") }}</p>
      </template>
      <template v-else>
        <p>{{ $t("Registration details and demo credentials are held in this tab's memory and cleared on refresh.") }}</p>
        <p>{{ $t("No registration data is sent to a server. Login's Remember Me option stores only the identifier in local storage.") }}</p>
        <p>{{ $t("Use invented details and a demo password. This prototype notice is not a finalized privacy policy.") }}</p>
      </template>
      <template #footer><ElButton type="primary" @click="notice = ''">{{ $t("Close") }}</ElButton></template>
    </ElDialog>
  </AuthLayout>
</template>

<style scoped>
h1 { margin: 0; font-size: 27px; letter-spacing: -0.7px; color: var(--sr-ink); }
.register-subtitle { margin: 8px 0 24px; font-size: 14px; line-height: 1.5; color: var(--sr-text-muted); }
.feedback:has(.el-alert) { margin-bottom: 20px; }
:deep(.el-form-item) { margin-bottom: 23px; }
:deep(.el-form-item__label) { color: var(--sr-text); font-weight: 600; padding-bottom: 7px; }
:deep(.el-input__wrapper) { padding: 3px 12px; }
:deep(.el-input__prefix-inner) { margin-right: 4px; }
:deep(.el-form-item__error) { position: static; width: 100%; padding-top: 6px; }
.field-hint { margin: -16px 0 17px; color: var(--sr-text-muted); font-size: 12px; line-height: 1.5; }
.agreement-row { display: flex; flex-wrap: wrap; align-items: center; gap: 0 5px; font-size: 12px; }
:deep(.el-checkbox__label) { color: var(--sr-text-secondary); font-size: 12px; }
.notice-link { padding: 0; border: 0; background: transparent; color: var(--sr-primary); font: inherit; cursor: pointer; }
.notice-link:focus-visible { outline: 2px solid var(--sr-primary); outline-offset: 2px; }
.notice-link:disabled { cursor: default; color: var(--sr-text-subtle); }
.code-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.code-row .el-input { flex: 1; min-width: 0; }
.code-row .el-button { margin: 0; }
.full-width { width: 100%; font-weight: 600; }
.sign-in-divider { display: flex; align-items: center; gap: 10px; margin: 20px 0 16px; color: var(--sr-text-muted); font-size: 12px; }
.sign-in-divider::before, .sign-in-divider::after { content: ''; height: 1px; flex: 1; background: var(--sr-border); }
.demo-note { margin-top: 20px; padding: 14px; border: 1px solid var(--sr-primary-100); border-radius: 8px; background: var(--sr-surface-tint); color: var(--sr-text-muted); font-size: 12px; }
.demo-note strong { color: var(--sr-text-secondary); font-size: 13px; }
.demo-note p { margin: 7px 0 0; line-height: 1.6; }
.result-state { text-align: center; padding: 24px 0; }
.result-state p { color: var(--sr-text-muted); line-height: 1.7; font-size: 14px; overflow-wrap: anywhere; }
.result-state h1 { font-size: 25px; }
.result-icon { display: grid; place-items: center; width: 64px; height: 64px; margin: 0 auto 24px; border-radius: 50%; font-size: 38px; }
.success { background: #e5f7ec; color: #20854b; }
.unavailable { background: var(--sr-surface-sunken); color: var(--sr-text-subtle); }
.result-note { margin-top: 24px; }
.result-state .el-button { width: 100%; margin-top: 16px; }
:deep(.el-dialog__body p) { line-height: 1.7; }
</style>
