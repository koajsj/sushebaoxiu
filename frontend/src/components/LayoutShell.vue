<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { ElAlert, ElButton, ElMenu, ElMenuItem } from 'element-plus'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-menu.css'
import 'element-plus/theme-chalk/el-menu-item.css'
import BrandMark from './BrandMark.vue'
import NotificationPanel from './NotificationPanel.vue'
import { useUiStore } from '../store/ui'
import { useAuthStore } from '../store/auth'
import type { Role } from '../types'

const props = defineProps<{ role: Role }>()
const ui = useUiStore()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const exiting = ref(false)
const errorMessage = ref('')
const names: Record<Role, string> = { student: '学生服务', worker: '维修工作台', admin: '后勤管理' }
const roleNames: Record<Role, string> = { student: '学生', worker: '维修人员', admin: '管理员' }
const activeMenu = computed(() => {
  const root = `/${props.role}`
  if (route.path === '/student/orders/new') return route.path
  if (route.path.startsWith(`${root}/orders`)) return props.role === 'admin' ? root : `${root}/orders`
  return route.path
})

async function signOut() {
  if (exiting.value) return
  exiting.value = true
  errorMessage.value = ''
  try { await auth.signOut(); await router.replace('/login') }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '退出失败，请重试' }
  finally { exiting.value = false }
}
</script>

<template>
  <div class="workspace" :class="{ 'navigation-collapsed': ui.navigationCollapsed }">
    <a class="skip-link" href="#workspace-content">跳到主要内容</a>
    <aside id="workspace-navigation" class="workspace-sidebar" aria-label="主导航">
      <RouterLink :to="`/${props.role}`" class="sidebar-brand" aria-label="校园智能报修首页">
        <BrandMark :compact="ui.navigationCollapsed" />
      </RouterLink>
      <p class="sidebar-label">{{ ui.navigationCollapsed ? '导航' : names[props.role] }}</p>
      <ElMenu class="workspace-menu" :default-active="activeMenu" router>
        <ElMenuItem :index="`/${props.role}`">{{ ui.navigationCollapsed ? '首页' : props.role==='admin'?'工单管理':'工作台首页' }}</ElMenuItem>
        <ElMenuItem v-if="props.role !== 'admin'" :index="`/${props.role}/orders`" :title="props.role==='student'?'我的报修':'我的任务'">{{ ui.navigationCollapsed?'工单':props.role==='student'?'我的报修':'我的任务' }}</ElMenuItem>
        <ElMenuItem v-if="props.role === 'student'" index="/student/orders/new" title="提交报修">{{ ui.navigationCollapsed?'报修':'提交报修' }}</ElMenuItem>
        <ElMenuItem v-if="props.role === 'admin'" index="/admin/dispatch" title="智能派单">{{ ui.navigationCollapsed?'派单':'智能派单' }}</ElMenuItem>
        <ElMenuItem v-if="props.role === 'admin'" index="/admin/dashboard" title="数据驾驶舱">{{ ui.navigationCollapsed?'概览':'数据驾驶舱' }}</ElMenuItem>
        <ElMenuItem v-if="props.role === 'admin'" index="/admin/map" title="校园任务地图">{{ ui.navigationCollapsed?'地图':'校园任务地图' }}</ElMenuItem>
        <ElMenuItem v-if="props.role === 'admin'" index="/admin/manage" title="基础资料维护">{{ ui.navigationCollapsed?'维护':'基础资料维护' }}</ElMenuItem>
      </ElMenu>
      <p class="sidebar-note">{{ ui.navigationCollapsed ? '校园' : '让每一份校园关怀，都有回应。' }}</p>
    </aside>
    <div class="workspace-main">
      <header class="workspace-header">
        <div class="header-title">
          <ElButton class="navigation-toggle" :aria-expanded="!ui.navigationCollapsed"
            aria-controls="workspace-navigation" @click="ui.toggleNavigation">
            {{ ui.navigationCollapsed ? '展开导航' : '收起导航' }}
          </ElButton><span>{{ names[props.role] }}</span>
        </div>
        <div class="user-menu">
          <NotificationPanel />
          <span class="user-avatar" aria-hidden="true">{{ auth.user?.realName.slice(0, 1) || 'C' }}</span>
          <div class="user-copy"><strong>{{ auth.user?.realName }}</strong><span>{{ roleNames[props.role] }}</span></div>
          <RouterLink class="text-button account-settings-link" :to="`/${props.role}/settings/password`">修改密码</RouterLink>
          <ElButton class="signout-button" text :loading="exiting" :disabled="exiting || !auth.authenticated" @click="signOut">退出登录</ElButton>
        </div>
      </header>
      <main id="workspace-content" class="workspace-content" tabindex="-1">
        <ElAlert v-if="errorMessage" class="logout-error" :title="errorMessage" type="error" :closable="false" show-icon />
        <RouterView v-slot="{ Component, route }">
          <Transition name="page" mode="out-in"><component :is="Component" :key="route.path" /></Transition>
        </RouterView>
      </main>
      <footer class="workspace-footer">校园智能报修管理系统</footer>
    </div>
  </div>
</template>
