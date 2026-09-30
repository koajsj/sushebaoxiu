import { createRouter, createWebHistory } from 'vue-router'
import type { RouterHistory, RouteRecordRaw } from 'vue-router'
import type { Pinia } from 'pinia'
import { useAuthStore } from '../store/auth'
import { setUnauthorizedHandler } from '../utils/request'
import type { Role } from '../types'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  ...(['student', 'worker', 'admin'] as Role[]).map((role) => ({
    path: `/${role}`,
    component: role === 'student' ? () => import('../layouts/StudentLayout.vue')
      : role === 'worker' ? () => import('../layouts/WorkerLayout.vue') : () => import('../layouts/AdminLayout.vue'),
    meta: { role },
    children: [
      { path: '', component: role === 'admin' ? () => import('../views/OrderListView.vue') : () => import('../views/WorkspaceHome.vue'), props: { role },
        meta: { title: { student: '学生服务', worker: '维修工作台', admin: '工单管理' }[role] } },
      { path: 'orders', component: () => import('../views/OrderListView.vue'), props: { role }, meta: { title: role === 'worker' ? '我的任务' : '订单列表' } },
      ...(role === 'student' ? [{ path: 'orders/new', component: () => import('../views/CreateOrderView.vue'), meta: { title: '提交报修' } }] : []),
      ...(role === 'admin' ? [
        { path: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '数据驾驶舱' } },
        { path: 'dispatch', component: () => import('../views/DispatchView.vue'), meta: { title: '智能派单' } },
        { path: 'map', component: () => import('../views/MapView.vue'), meta: { title: '校园任务地图' } },
      ] : []),
      { path: 'orders/:id(\\d+)', component: () => import('../views/OrderDetailView.vue'), props: { role }, meta: { title: '订单详情' } },
      { path: 'orders/:id(\\d+)/messages', component: () => import('../views/OrderChatView.vue'), props: { role }, meta: { title: '维修沟通' } },
    ],
  })),
  { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue'), meta: { title: '页面不存在' } },
]

export function createAppRouter(history: RouterHistory = createWebHistory(), pinia?: Pinia) {
  const router = createRouter({ history, routes })
  setUnauthorizedHandler(() => {
    const auth = useAuthStore(pinia)
    auth.clearSession()
    if (router.currentRoute.value.meta.role) void router.replace('/login')
  }, () => useAuthStore(pinia).token)
  router.beforeEach(async (to) => {
    const auth = useAuthStore(pinia)
    try { await auth.restore() }
    catch { return to.path === '/login' ? true : { path: '/login', query: { reason: 'connection' } } }
    if (to.meta.role && !auth.authenticated) return '/login'
    if (to.meta.role && to.meta.role !== auth.role) return auth.homePath
    if (to.path === '/login' && auth.authenticated) return auth.homePath
    return true
  })
  router.afterEach((to) => {
    if (typeof document !== 'undefined') document.title = `${String(to.meta.title || '首页')} · 校园智能报修`
  })
  return router
}
