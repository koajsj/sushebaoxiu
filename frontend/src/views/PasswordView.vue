<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '../api/manage'
import { useAuthStore } from '../store/auth'
const auth=useAuthStore(),router=useRouter()
const oldPassword=ref(''),newPassword=ref(''),confirmPassword=ref(''),busy=ref(false),error=ref('')
async function save(){if(busy.value)return
  if(newPassword.value!==confirmPassword.value){error.value='两次输入的新密码不一致';return}
  busy.value=true;error.value=''
  try{await changePassword(oldPassword.value,newPassword.value);auth.clearSession();await router.replace('/login')}
  catch(e){error.value=e instanceof Error?e.message:'修改失败，请重试'}finally{busy.value=false}
}
</script>
<template><section class="business-page password-page"><header class="page-heading"><div><p class="eyebrow">账号安全</p><h1>修改密码</h1><p>修改成功后，所有已有登录会话都会失效，请使用新密码重新登录。</p></div></header><form class="management-form" @submit.prevent="save"><label>当前密码<input v-model="oldPassword" type="password" required autocomplete="current-password" /></label><label>新密码<input v-model="newPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password" /></label><label>再次输入新密码<input v-model="confirmPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password" /></label><p v-if="error" class="notice error" role="alert">{{ error }}</p><button class="primary-button" :disabled="busy">{{ busy?'正在保存…':'确认修改' }}</button></form></section></template>
