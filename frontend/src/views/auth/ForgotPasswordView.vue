<!-- Reuse the existing authentication layout for mailbox-based password recovery. -->
<script setup>
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
  emailCode:[{pattern:/^\d{6}$/,required:true,message:'Enter the six-digit email code.',trigger:'blur'}] }
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
</script>

<template>
  <AuthLayout title-id="reset-title">
    <h1 id="reset-title">{{ done ? 'Password reset successfully' : 'Forgot password' }}</h1>
    <p v-if="done">Sign in again with your new password. Previous sessions have been signed out.</p>
    <p v-else>Verify your registered email address to set a new password.</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <p v-if="checking" role="status">Loading email settings...</p>
    <ElAlert v-else-if="!enabled" :title="demoMode ? 'Password recovery is unavailable in the frontend demo. Use a built-in demo account to sign in.' : 'Email verification is not configured. Contact the administrator.'" type="info" :closable="false" />
    <template v-else-if="!done">
      <ElAlert v-if="feedback" :title="feedback" type="success" :closable="false" />
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="loading" @submit.prevent="submit">
        <ElFormItem label="Registered email" prop="email"><ElInput v-model="form.email" autocomplete="email" placeholder="Enter your registered email" @input="emailChanged" /></ElFormItem>
        <ElButton :loading="loading" :disabled="loading||cooldown>0" @click="send">{{ cooldown>0 ? `Send again in ${cooldown}s` : 'Send email code' }}</ElButton>
        <p>The email code expires in 10 minutes. Check your spam folder too.</p>
        <ElFormItem label="Email verification code" prop="emailCode"><ElInput v-model="form.emailCode" maxlength="6" inputmode="numeric" autocomplete="one-time-code" placeholder="Six-digit code" /></ElFormItem>
        <ElFormItem label="New password" prop="password"><PasswordInput v-model="form.password" :disabled="loading" /></ElFormItem>
        <p>Use a password of 15-128 characters.</p>
        <ElFormItem label="Confirm new password" prop="confirmation"><PasswordInput v-model="form.confirmation" :disabled="loading" /></ElFormItem>
        <ElButton type="primary" native-type="submit" class="wide" :loading="loading" :disabled="loading||!form.challengeId">Reset password</ElButton>
      </ElForm>
    </template>
    <ElButton class="wide back" :disabled="loading" @click="router.push({name:'Login'})">Back to sign in</ElButton>
  </AuthLayout>
</template>

<style scoped>
h1 {margin:0;font-size:27px;color:#132941} p {font-size:14px;color:#627086;line-height:1.6} .wide {width:100%} .back {margin-top:18px} .el-alert {margin-bottom:16px} :deep(.el-form-item__label) {font-weight:600;color:#22344b} :deep(.el-form-item__error) {position:static} .el-form {margin-top:22px}
</style>
