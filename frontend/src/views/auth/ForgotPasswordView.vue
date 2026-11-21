<!-- Reuse the existing authentication layout for mailbox-based password recovery. -->
<script setup>
import { useLocalizedValidation, t } from '../../i18n/index.js'
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import AuthLayout from '../../layouts/AuthLayout.vue'
import PasswordInput from '../../components/simrecrut/PasswordInput.vue'
import { passwordResetOptions, sendPasswordCode, resetPassword } from '../../api/login.js'
import { validateConnectedEmail, validateConnectedPassword, validatePasswordConfirmation } from '../../utils/registration-validation.js'
import { removeToken } from '../../utils/auth.js'
import { demoMode } from '../../config/runtime.js'

const router=useRouter(), formRef=ref()
const form=reactive({ email:'', challengeId:'', emailCode:'', password:'', confirmation:'' })
const loading=ref(false), checking=ref(true), enabled=ref(false)
const error=ref(''), feedback=ref(''), done=ref(false), cooldown=ref(0)
let active=true, timer
const rules={ email:[{validator:validateConnectedEmail,trigger:'blur'}], password:[{validator:validateConnectedPassword,trigger:'blur'}],
  confirmation:[{validator:(_rule,value,callback)=>{ const message=validatePasswordConfirmation(form.password,value); callback(message?new Error(message):undefined) },trigger:'blur'}],
  emailCode:[{pattern:/^\d{6}$/,required:true,get message() { return t('Enter the six-digit email code.') },trigger:'blur'}] }
onMounted(async()=>{
  if (demoMode) { checking.value=false; return }
  try { const response=await passwordResetOptions(); if(active) enabled.value=response.data?.emailEnabled===true }
  catch { error.value='Unable to load email verification settings.' }
  finally { checking.value=false }
})
function emailChanged() { form.challengeId=''; form.emailCode=''; feedback.value=''; error.value='' }
async function send() {
  if(loading.value||cooldown.value||!enabled.value) return
  if(!await formRef.value.validateField('email').then(()=>true).catch(()=>false)) return
  loading.value=true; error.value=''
  try {
    const response=await sendPasswordCode({email:form.email.trim()})
    if(!active) return
    if(!response.data?.challengeId) throw new Error('The identity service did not return a verification challenge.')
    form.challengeId=response.data.challengeId; form.emailCode=''; feedback.value='If this email address is eligible, a verification code has been sent.'
    cooldown.value=response.data.resendAfterSeconds||60; clearInterval(timer); timer=setInterval(()=>{ cooldown.value-=1; if(cooldown.value<=0) clearInterval(timer) },1000)
  } catch(failure) { if(active) error.value=failure.message||'Unable to request an email code.' }
  finally { if(active) loading.value=false }
}
async function submit() {
  if(loading.value||!form.challengeId) return
  if(!await formRef.value.validate().catch(()=>false)) return
  loading.value=true; error.value=''
  try { await resetPassword({email:form.email.trim(),challengeId:form.challengeId,emailCode:form.emailCode,password:form.password,passwordConfirmation:form.confirmation}); if(!active)return; form.password=''; form.confirmation=''; form.emailCode=''; removeToken(); done.value=true }
  catch(failure) { if(active) error.value=failure.message||'Unable to reset your password. Request a new code if it expired.' }
  finally { if(active) loading.value=false }
}
onBeforeUnmount(()=>{active=false;clearInterval(timer);form.password='';form.confirmation='';form.emailCode=''})

useLocalizedValidation(formRef)
</script>

<template>
  <AuthLayout title-id="reset-title">
    <h1 id="reset-title">{{ $t(done ? 'Password reset successfully' : 'Forgot password') }}</h1>
    <p v-if="done">{{ $t("Sign in again with your new password. Previous sessions have been signed out.") }}</p>
    <p v-else>{{ $t("Verify your registered email address to set a new password.") }}</p>
    <ElAlert v-if="error" :title="$t(error)" type="error" :closable="false" />
    <p v-if="checking" role="status">{{ $t("Loading email settings...") }}</p>
    <ElAlert v-else-if="!enabled" :title="$t(demoMode ? 'Password recovery is unavailable in the frontend demo. Use a built-in demo account to sign in.' : 'Email verification is not configured. Contact the administrator.')" type="info" :closable="false" />
    <template v-else-if="!done">
      <ElAlert v-if="feedback" :title="$t(feedback)" type="success" :closable="false" />
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="loading" @submit.prevent="submit">
        <ElFormItem :label="$t(&quot;Registered email&quot;)" prop="email"><ElInput v-model="form.email" autocomplete="email" :placeholder="$t(&quot;Enter your registered email&quot;)" @input="emailChanged" /></ElFormItem>
        <ElButton :loading="loading" :disabled="loading||cooldown>0" @click="send">{{ cooldown>0 ? $t('Send again in {seconds}s', { seconds: cooldown }) : $t('Send email code') }}</ElButton>
        <p>{{ $t("The email code expires in 10 minutes. Check your spam folder too.") }}</p>
        <ElFormItem :label="$t(&quot;Email verification code&quot;)" prop="emailCode"><ElInput v-model="form.emailCode" maxlength="6" inputmode="numeric" autocomplete="one-time-code" :placeholder="$t(&quot;Six-digit code&quot;)" /></ElFormItem>
        <ElFormItem :label="$t(&quot;New password&quot;)" prop="password"><PasswordInput v-model="form.password" :disabled="loading" /></ElFormItem>
        <p>{{ $t("Use at least 12 characters.") }}</p>
        <ElFormItem :label="$t(&quot;Confirm new password&quot;)" prop="confirmation"><PasswordInput v-model="form.confirmation" :disabled="loading" /></ElFormItem>
        <ElButton type="primary" native-type="submit" class="wide" :loading="loading" :disabled="loading||!form.challengeId">{{ $t("Reset password") }}</ElButton>
      </ElForm>
    </template>
    <ElButton class="wide back" :disabled="loading" @click="router.push({name:'Login'})">{{ $t("Back to sign in") }}</ElButton>
  </AuthLayout>
</template>

<style scoped>
h1 {margin:0;font-size:27px;color:var(--sr-ink)} p {font-size:14px;color:var(--sr-text-muted);line-height:1.6} .wide {width:100%} .back {margin-top:18px} .el-alert {margin-bottom:16px} :deep(.el-form-item__label) {font-weight:600;color:var(--sr-text)} :deep(.el-form-item__error) {position:static} .el-form {margin-top:22px}
</style>
