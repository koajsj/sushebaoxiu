import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { ID_INJECTION_KEY, ZINDEX_INJECTION_KEY } from 'element-plus'

// Component render check; authenticated fixtures are only used for SSR output.
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
const warnings = []
const originalWarn = console.warn
console.warn = (...args) => warnings.push(args.join(' '))
try {
  const { default: App } = await server.ssrLoadModule('/src/App.vue')
  const { createAppRouter } = await server.ssrLoadModule('/src/router/index.ts')
  const { useAuthStore } = await server.ssrLoadModule('/src/store/auth.ts')
  const cases = [
    ['/', '欢迎回来', null], ['/login', '登录你的校园服务账号', null],
    ['/student', '今天，让校园生活更顺畅。', 'STUDENT'], ['/worker', '维修工作台', 'WORKER'],
    ['/admin', '工单管理', 'ADMIN'], ['/admin/dispatch', '智能派单', 'ADMIN'], ['/admin/map', '校园任务地图', 'ADMIN'], ['/admin/dashboard', '校园服务驾驶舱', 'ADMIN'], ['/student/orders/new', '选择故障类型', 'STUDENT'], ['/student/orders', '我的报修', 'STUDENT'],
    ['/worker/orders', '我的任务', 'WORKER'], ['/student/orders/1', '订单详情', 'STUDENT'],
    ['/worker/orders/1', '订单详情', 'WORKER'], ['/admin/orders/1', '订单详情', 'ADMIN'],
    ['/student/orders/1/messages', '维修沟通', 'STUDENT'], ['/worker/orders/1/messages', '维修沟通', 'WORKER'], ['/admin/orders/1/messages', '维修沟通', 'ADMIN'],
    ['/missing-page', '页面不存在', null],
    ['/admin', '欢迎回来', null],
  ]
  for (const [path, text, role] of cases) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore(pinia)
    auth.initialized = true
    if (role) {
      auth.token = 'render-fixture'
      auth.user = { id: 1, username: 'fixture', realName: '校园用户', phone: null, role }
    }
    const router = createAppRouter(createMemoryHistory(), pinia)
    const app = createSSRApp(App).use(pinia).use(router)
    app.provide(ID_INJECTION_KEY, { prefix: 1024, current: 0 })
    app.provide(ZINDEX_INJECTION_KEY, { current: 0 })
    app.config.warnHandler = (message) => warnings.push(message)
    await router.push(path)
    await router.isReady()
    const html = await renderToString(app)
    assert.ok(html.includes(text), `${path}: missing content ${text}`)
    assert.ok(!html.includes('[object Object]'), `${path}: invalid rendered text`)
    if (router.currentRoute.value.path === '/login') {
      assert.ok(html.includes('type="password"'))
      assert.ok(html.includes('autocomplete="username"'))
      assert.ok(html.includes('校园服务介绍'))
      assert.ok(!html.includes('href="/student"'), 'Public role preview must be removed')
    } else if (role) {
      assert.ok(html.includes('校园用户'))
      assert.ok(html.includes('退出登录'))
      assert.ok(!html.includes('其他界面预览'))
    }
    console.log(`PASS ${path} (${role || 'anonymous'}): Vue SSR render`)
  }
  assert.deepEqual(warnings, [])
  console.log(`PASS ${cases.length} render cases; no console/Vue SSR warnings. Browser visuals remain unverified.`)
} finally { console.warn = originalWarn; await server.close() }
