import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { ID_INJECTION_KEY, ZINDEX_INJECTION_KEY } from 'element-plus'

// Component render check; authenticated fixtures are only used for SSR output.
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
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
  const { default: OrderTimeline } = await server.ssrLoadModule('/src/components/OrderTimeline.vue')
  const timelineHtml=await renderToString(createSSRApp(OrderTimeline,{status:'WAIT_CONFIRM',events:[
    {id:1,action:'SUBMIT',createTime:'2026-09-30T09:00:00'},
    {id:2,action:'FINISH',createTime:'2026-09-30T10:00:00'},
  ]}))
  assert.equal((timelineHtml.match(/class="done"/g)||[]).length,2)
  assert.ok(timelineHtml.includes('维修完成，等待验收'))
  assert.ok(!timelineHtml.includes('学生已确认完成'))
  console.log('PASS timeline shows only real events without fabricated confirmation')
  const {default: WorkflowPanel}=await server.ssrLoadModule('/src/components/OrderWorkflowPanel.vue')
  for(const [role,status,acceptedTime,appointmentStatus,label] of [
    ['student','REJECTED',null,'NONE','修改并重新提交'],
    ['admin','WAIT_AUDIT',null,'NONE','需要补充信息'],
    ['worker','ASSIGNED',null,'NONE','无法接受任务'],
    ['worker','ASSIGNED','2026-09-30T09:00:00','NONE','提出上门时间'],
    ['student','WAIT_CONFIRM','2026-09-30T09:00:00','NONE','申请返工'],
    ['admin','REWORK_PENDING','2026-09-30T09:00:00','NONE','安排下一轮维修'],
    ['student','ASSIGNED','2026-09-30T09:00:00','PROPOSED','确认预约'],
  ]) {
    const detail={order:{id:1,status,acceptedTime,appointmentStatus,appointmentStart:'2026-10-03T14:00:00',appointmentEnd:'2026-10-03T15:00:00'},timeline:[]}
    const html=await renderToString(createSSRApp(WorkflowPanel,{detail,role,disabled:false}))
    assert.ok(html.includes(label),`${role}/${status}: missing contextual action`)
  }
  console.log('PASS 7 enhancement contextual panels render without warnings')
  const {orderStatusLabel}=await server.ssrLoadModule('/src/types/repair.ts')
  assert.equal(orderStatusLabel({status:'WAIT_ASSIGN',workerId:null}),'待派单')
  assert.equal(orderStatusLabel({status:'WAIT_ASSIGN',workerId:1}),'待接单')
  console.log('PASS shared status text distinguishes pending assignment from worker response')
  assert.deepEqual(warnings, [])
  console.log(`PASS ${cases.length} render cases; no console/Vue SSR warnings. Browser visuals remain unverified.`)
} finally { console.warn = originalWarn; await server.close() }
