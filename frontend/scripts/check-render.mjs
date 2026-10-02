import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { ID_INJECTION_KEY, ZINDEX_INJECTION_KEY } from 'element-plus'

// Component render check; authenticated fixtures are only used for SSR output.
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
const warnings = []
const originalWarn = console.warn
console.warn = (...args) => warnings.push(args.join(' '))
try {
  const { default: App } = await server.ssrLoadModule('/src/App.vue')
  const { createAppRouter } = await server.ssrLoadModule('/src/router/index.ts')
  const { useAuthStore } = await server.ssrLoadModule('/src/store/auth.ts')
  const cases = [
    ['/', '欢迎回来', null], ['/login', '登录你的校园服务账号', null],
    ['/student', '校园维修服务', 'STUDENT'], ['/worker', '维修任务工作台', 'WORKER'],
    ['/admin', '校园维修运营中心', 'ADMIN'], ['/admin/dispatch', '智能派单', 'ADMIN'], ['/admin/map', '校园任务地图', 'ADMIN'], ['/admin/dashboard', '校园维修运营中心', 'ADMIN'], ['/admin/manage', '基础资料维护', 'ADMIN'], ['/student/settings/password', '修改密码', 'STUDENT'], ['/student/orders/new', '选择故障类型', 'STUDENT'], ['/student/orders', '我的工单', 'STUDENT'],
    ['/worker/orders', '我的任务', 'WORKER'], ['/worker/orders?phase=FINISHED', '已完成任务', 'WORKER'], ['/admin/orders', '工单管理', 'ADMIN'], ['/student/orders/1', '订单详情', 'STUDENT'],
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
      const navigation=html.match(/<aside[^>]*aria-label="主导航"[^>]*>([\s\S]*?)<\/aside>/)?.[1]
      assert.ok(navigation, `${path}: role navigation is present`)
      for(const other of ['student','worker','admin'].filter(value=>value!==role.toLowerCase())) {
        assert.ok(!navigation.includes(`/${other}`), `${path}: unrelated role navigation is hidden`)
      }
      assert.ok(navigation.includes('消息通知'))
      assert.ok(navigation.includes('账号安全'))
      assert.equal((html.match(/class="notification-host /g)||[]).length,1,'One shared notification instance')
      if(path==='/admin') assert.ok(html.includes('operations-dashboard'),'Administrator lands on operations Dashboard')
      if(path==='/admin/orders') assert.ok(!html.includes('operations-dashboard'),'Order management remains a separate entry')
      if(path==='/worker/orders?phase=FINISHED') assert.match(navigation,/el-menu-item is-active[^>]*title="已完成任务"/,'Completed task filter owns its navigation highlight')
    }
    if (path.endsWith('/messages')) {
      assert.ok(!html.includes('当前没有负责人'), 'Loading must not claim the order has no assignee')
      assert.ok(!html.includes('历史记录 · 暂不可发送'), 'Loading must not claim confirmed read-only permissions')
    }
    console.log(`PASS ${path} (${role || 'anonymous'}): Vue SSR render`)
  }
  const { default: ChatComposer } = await server.ssrLoadModule('/src/components/chat/ChatComposer.vue')
  const composer = props => renderToString(createSSRApp(ChatComposer, {
    modelValue: '', canSend: false, busy: false, loading: false, role: 'student', ...props,
  }))
  const loadingComposer = await composer({ loading: true })
  assert.ok(loadingComposer.includes('role="status"'))
  assert.ok(!loadingComposer.includes('当前没有负责人'))
  assert.ok((await composer({})).includes('当前没有负责人'))
  assert.ok((await composer({ role: 'admin' })).includes('管理员可阅读'))
  assert.ok((await composer({ canSend: true })).includes('发送消息'))
  console.log('PASS chat loading is distinct from confirmed read-only and sendable states')
  const { default: ProtectedImage } = await server.ssrLoadModule('/src/components/ProtectedImage.vue')
  const imagePinia = createPinia(); setActivePinia(imagePinia)
  let imageState
  await renderToString(createSSRApp({ setup() {
    imageState = ProtectedImage.setup({ url: '/images/fixture', alt: '现场图片' }, { expose() {} })
    return () => null
  } }).use(imagePinia))
  assert.equal(typeof imageState.imageFailed, 'function', 'Decoded image failure must have a fallback handler')
  const url = URL.createObjectURL(new Blob(['undecodable-image']))
  imageState.source.value = url
  imageState.imageFailed({ target: { src: 'blob:previous-picture' } })
  assert.equal(imageState.source.value, url, 'Old picture errors must not hide the current picture')
  imageState.imageFailed({ target: { src: url } })
  assert.equal(imageState.source.value, '')
  assert.equal(imageState.failed.value, true)
  await assert.rejects(fetch(url), 'Failed preview object URL must be released')
  imageState.imageFailed({ target: { src: url } })
  console.log('PASS undecodable private image switches to the error state and releases its object URL')
  const {default:StudentHome}=await server.ssrLoadModule('/src/components/student/StudentHome.vue')
  const {default:WorkerHome}=await server.ssrLoadModule('/src/components/worker/WorkerHome.vue')
  const summary={today:2,total:6,pending:1,active:3,completed:2}
  const order={id:1,title:'空调不制冷',description:'运行后没有冷风',typeName:'空调维修',buildingName:'五号楼',roomNo:'302',priority:'NORMAL',workerName:'王师傅',workerId:1,status:'REWORK_PENDING',phase:'REWORK_PENDING',repairRound:2,overdueType:null,appointmentStatus:'NONE',createTime:'2026-10-01T09:00:00',updateTime:'2026-10-01T10:00:00'}
  for(const [Home,role] of [[StudentHome,'student'],[WorkerHome,'worker']]) {
    const pinia=createPinia();setActivePinia(pinia)
    const auth=useAuthStore(pinia);auth.initialized=true;auth.token='render-fixture';auth.user={id:1,realName:'校园用户',role:role.toUpperCase()}
    const router=createAppRouter(createMemoryHistory(),pinia);await router.push(`/${role}`);await router.isReady()
    const renderHome=props=>renderToString(createSSRApp(Home,{name:'校园用户',summary,orders:[],loading:false,error:'',...props}).use(pinia).use(router))
    const empty=await renderHome({})
    assert.ok(!empty.includes('undefined'))
    if(role==='student') {
      assert.ok(empty.includes('还有工单正在处理中'),'Recent four empty does not claim all orders are finished')
      assert.ok(empty.includes('我要报修'));assert.ok(empty.includes('维修进度与记录'))
    } else {
      assert.ok(!empty.includes('class="worker-attention"'),'No alarm panel without a known overdue task')
      const withRework=await renderHome({orders:[order]})
      const sections=withRework.split('<h2>维修、验收与返工</h2>')
      assert.ok(!sections[0].includes('空调不制冷'),'Rework is not presented as a pending acceptance action')
      assert.ok(sections[1].includes('空调不制冷'))
      const overdue=await renderHome({orders:[{...order,status:'PROCESSING',phase:'PROCESSING',overdueType:'REPAIR'}]})
      assert.ok(overdue.includes('class="worker-attention"'))
    }
    assert.ok((await renderHome({loading:true})).includes('role="status"'))
    assert.ok((await renderHome({error:'连接失败，输入不会丢失'})).includes('role="alert"'))
  }
  console.log('PASS role home empty/loading/error, rework grouping and contextual timeout reminder')
  const {default:OrderHeader}=await server.ssrLoadModule('/src/components/lifecycle/OrderHeader.vue')
  const {studentNextStep}=await server.ssrLoadModule('/src/components/student/presentation.ts')
  const {workerNextStep}=await server.ssrLoadModule('/src/components/worker/presentation.ts')
  const activeOrder={...order,status:'ASSIGNED',phase:'WAIT_START',acceptedTime:'2026-10-01T10:00:00'}
  for(const nextStep of [studentNextStep(activeOrder),workerNextStep(activeOrder),'维修员已接单，关注开工时限与上门预约。']) {
    const html=await renderToString(createSSRApp(OrderHeader,{order:activeOrder,nextStep}))
    assert.ok(html.includes(`<p class="lifecycle-guidance">${nextStep}</p>`),'Header preserves the page-provided next action for each role')
    assert.ok(html.includes('王师傅')&&html.includes('待开工'),'Task owner and current phase remain visible')
    assert.ok(html.includes('href="#order-actions-1"'),'Guidance still links to the current action panel')
  }
  console.log('PASS lifecycle header preserves role guidance, phase, owner and action anchor')
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
    ['worker','ASSIGNED',null,'NONE','无法接单 · 拒绝任务'],
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
  const {orderStatusLabel,isAwaitingAcceptance}=await server.ssrLoadModule('/src/types/repair.ts')
  assert.equal(orderStatusLabel({status:'WAIT_ASSIGN',workerId:null}),'待派单')
  assert.equal(orderStatusLabel({status:'WAIT_ASSIGN',workerId:1}),'待接单')
  console.log('PASS shared status text distinguishes pending assignment from worker response')
  for (const status of ['WAIT_ASSIGN', 'ASSIGNED', 'PROCESSING', 'WAIT_CONFIRM', 'FINISHED', 'REWORK_PENDING']) {
    for (const acceptedTime of [null, '2026-10-01T09:00:00']) {
      assert.equal(isAwaitingAcceptance({ status, acceptedTime }),
        !acceptedTime && ['WAIT_ASSIGN', 'ASSIGNED'].includes(status))
    }
  }
  const { default: OrderStatusTag } = await server.ssrLoadModule('/src/components/OrderStatusTag.vue')
  for (const [order, label] of [
    [{ status: 'WAIT_ASSIGN', workerId: null }, '待派单'],
    [{ status: 'WAIT_ASSIGN', workerId: 1 }, '待接单'],
    [{ status: 'ASSIGNED', workerId: 1, phase: 'WAIT_START' }, '待开工'],
    [{ status: 'WAIT_CONFIRM', workerId: 1 }, '待确认'],
  ]) {
    const html = await renderToString(createSSRApp(OrderStatusTag, { order }))
    assert.ok(html.includes(`class="status-pill status-${order.status.toLowerCase()}"`))
    assert.ok(html.includes(label))
  }
  console.log('PASS shared order status component preserves phase and legacy labels/classes')
  const { unwrap, ApiError } = await server.ssrLoadModule('/src/utils/request.ts')
  for (const data of [0, false, [], { id: 1 }]) {
    assert.equal(unwrap({ code: 0, message: '成功', data }), data)
  }
  assert.throws(() => unwrap({ code: 0, message: '成功', data: null }),
    error => error instanceof ApiError && error.message === '服务返回内容不完整')
  console.log('PASS shared API unwrap preserves empty values and missing-data errors')
  assert.deepEqual(warnings, [])
  console.log(`PASS ${cases.length} render cases; no console/Vue SSR warnings. Browser visuals remain unverified.`)
} finally { console.warn = originalWarn; await server.close() }
