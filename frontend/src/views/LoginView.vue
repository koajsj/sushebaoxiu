<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-input.css'
import BrandMark from '../components/BrandMark.vue'
import CampusHero from '../components/CampusHero.vue'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref(route.query.reason === 'connection' ? '无法恢复登录状态，请检查网络连接。' : '')
const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (loading.value) return
  loading.value = true
  try {
    const valid = await formRef.value?.validate().catch(() => false)
    if (!valid) return
    errorMessage.value = ''
    await auth.signIn({ username: form.username.trim(), password: form.password })
    form.password = ''
    await router.replace(auth.homePath)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <header class="login-brand"><BrandMark /></header>
    <div class="login-composition">
      <CampusHero class="login-story" variant="login" asset="entrance" label="校园服务介绍">
        <div class="story-copy">
          <p class="story-label">为校园，留一份安心。</p>
          <h1>美好校园，<br />从用心照顾开始。</h1>
          <p class="story-description">一个熟悉的入口，连接每一份校园关怀。</p>
        </div>
      </CampusHero>
      <section class="login-card" aria-labelledby="login-title">
        <div class="login-card-heading"><span class="login-monogram" aria-hidden="true">C</span>
          <h2 id="login-title">欢迎回来</h2><p>登录你的校园服务账号</p></div>
        <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <ElFormItem label="账号" prop="username">
            <ElInput v-model="form.username" name="username" placeholder="请输入学号或工号"
              autocomplete="username" autocapitalize="none" :spellcheck="false" :maxlength="64" :disabled="loading" size="large" />
          </ElFormItem>
          <ElFormItem label="密码" prop="password">
            <ElInput v-model="form.password" name="password" type="password" show-password
              placeholder="请输入密码" autocomplete="current-password" :maxlength="72"
              :disabled="loading" size="large" />
          </ElFormItem>
          <ElButton class="login-submit" type="primary" native-type="submit" :loading="loading" :disabled="loading">
            {{ loading ? '正在登录' : '登录' }}
          </ElButton>
        </ElForm>
        <div class="login-feedback" aria-live="polite">
          <ElAlert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
          <p v-else>使用学校分配的账号，进入你的专属工作空间。</p>
        </div>
      </section>
    </div>
    <footer class="login-footer">校园智能报修管理系统</footer>
  </main>
</template>
