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
import { roleExperience } from './roleExperience'
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
const experience = computed(() => roleExperience[props.role])
const notifications = ref<InstanceType<typeof NotificationPanel> | null>(null)
const roleNames: Record<Role, string> = { student: '学生', worker: '维修人员', admin: '管理员' }
const activeMenu = computed(() => {
  const root = `/${props.role}`
  if (route.path === '/student/orders/new') return route.path
  if (props.role === 'worker' && route.path === '/worker/orders' && route.query.phase === 'FINISHED') return '/worker/orders?phase=FINISHED'
  if (route.path.startsWith(`${root}/orders`)) return `${root}/orders`
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
  <div class="workspace" :class="{ 'navigation-collapsed': ui.navigationCollapsed }" :data-role="props.role">
    <a class="skip-link" href="#workspace-content">跳到主要内容</a>
    <aside id="workspace-navigation" class="workspace-sidebar" aria-label="主导航">
      <RouterLink :to="`/${props.role}`" class="sidebar-brand" aria-label="校园智能报修首页">
        <BrandMark :compact="ui.navigationCollapsed" />
      </RouterLink>
      <p class="sidebar-label">{{ ui.navigationCollapsed ? '导航' : experience.title }}</p>
      <ElMenu class="workspace-menu" :default-active="activeMenu" router>
        <ElMenuItem v-for="item in experience.menu" :key="item.path" :index="item.path" :title="item.label">{{ ui.navigationCollapsed ? item.compact : item.label }}</ElMenuItem>
      </ElMenu>
      <nav class="sidebar-utilities" aria-label="消息与账号">
        <button type="button" :aria-expanded="notifications?.opened ?? false" :aria-controls="notifications?.panelId" @click="notifications?.show()">{{ ui.navigationCollapsed ? '通知' : '消息通知' }}</button>
        <RouterLink :to="`/${props.role}/settings/password`" :aria-current="route.path.endsWith('/settings/password') ? 'page' : undefined">{{ ui.navigationCollapsed ? '账号' : '账号安全' }}</RouterLink>
      </nav>
      <p class="sidebar-note">{{ ui.navigationCollapsed ? '校园' : experience.note }}</p>
    </aside>
    <div class="workspace-main">
      <header class="workspace-header">
        <div class="header-title">
          <ElButton class="navigation-toggle" :aria-expanded="!ui.navigationCollapsed"
            aria-controls="workspace-navigation" @click="ui.toggleNavigation">
            {{ ui.navigationCollapsed ? '展开导航' : '收起导航' }}
          </ElButton><div class="workspace-location"><strong>{{ experience.title }}</strong><span>{{ route.meta.title }}<span class="workspace-purpose"> · {{ experience.purpose }}</span></span></div>
        </div>
        <div class="user-menu">
          <NotificationPanel ref="notifications" />
          <span class="user-avatar" aria-hidden="true">{{ auth.user?.realName.slice(0, 1) || 'C' }}</span>
          <div class="user-copy"><strong>{{ auth.user?.realName }}</strong><span>{{ roleNames[props.role] }}</span></div>
          <RouterLink class="text-button account-settings-link" :to="`/${props.role}/settings/password`">账号安全</RouterLink>
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

<style scoped>
.workspace-location { display: grid; gap: var(--space-1); min-width: 0; }
.workspace-location strong { font-size: var(--text-support); color: var(--ink); font-weight: 600; overflow-wrap: anywhere; }
.workspace-location > span { font-size: var(--text-caption); color: var(--muted); font-weight: 400; line-height: 1.6; overflow-wrap: anywhere; }
.sidebar-utilities { display: grid; gap: var(--space-1); padding-top: var(--space-4); margin-top: var(--space-4); border-top: 1px solid var(--surface-line); }
.sidebar-utilities > :is(button, a) { display: flex; align-items: center; min-height: var(--control-height); padding: var(--space-2) var(--space-4); border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--ink-secondary); font-size: var(--text-support); font-weight: 500; text-align: left; transition: background var(--motion-fast), color var(--motion-fast); }
.sidebar-utilities > :is(button, a):hover { color: var(--accent); background: var(--surface-subtle); }
.sidebar-utilities > a[aria-current="page"], .sidebar-utilities > button[aria-expanded="true"] { color: var(--accent); background: var(--accent-soft); }
.navigation-collapsed .sidebar-utilities > :is(button, a) { justify-content: center; padding-inline: var(--space-1); }
@media (max-width: 1100px) { .workspace-purpose { display: none; } }
@media (max-width: 740px) {
  .sidebar-utilities { display: flex; flex-wrap: wrap; padding: 0; margin: var(--space-1) 0 0; border: 0; }
  .navigation-collapsed .sidebar-utilities { display: none; }
  .sidebar-utilities > :is(button, a) { min-height: var(--control-height-sm); padding-inline: var(--space-3); }
}
@media (prefers-reduced-motion: reduce) { .sidebar-utilities > :is(button, a) { transition: none; } }
</style>
