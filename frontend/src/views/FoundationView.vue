<script setup lang="ts">
import { useAuthStore } from '../store/auth'
import type { Role } from '../types'

defineProps<{ role: Role }>()
const auth = useAuthStore()
const titles: Record<Role, string> = { student: '学生服务入口', worker: '维修工作台', admin: '后勤管理入口' }
</script>

<template>
  <section class="foundation-view">
    <div class="foundation-heading"><p class="welcome-label">欢迎回来，{{ auth.user?.realName }}</p>
      <h1>{{ titles[role] }}</h1><p>你的校园服务工作空间。</p></div>
    <div class="foundation-empty">
      <span class="empty-symbol" aria-hidden="true">C</span>
      <h2>准备就绪，静候下一步。</h2>
      <p>你已成功登录，当前身份为{{ { student: '学生', worker: '维修人员', admin: '管理员' }[role] }}。<br />校园报修业务将在后续阶段接入。</p>
    </div>
  </section>
</template>
