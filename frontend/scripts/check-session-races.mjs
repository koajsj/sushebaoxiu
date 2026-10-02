import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { AxiosError } from 'axios'
import { createSSRApp, h, nextTick } from 'vue'
import { renderToString } from 'vue/server-renderer'

// Controlled HTTP ordering exercises production state/interceptors, no browser or real sessions.
const storage = new Map()
globalThis.localStorage = { getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)), removeItem: (key) => storage.delete(key) }
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
try {
  const { createAppRouter } = await server.ssrLoadModule('/src/router/index.ts')
  const { useAuthStore } = await server.ssrLoadModule('/src/store/auth.ts')
  const { http } = await server.ssrLoadModule('/src/utils/request.ts')
  function user(role) { return { id: role === 'STUDENT' ? 1 : 3, username: role.toLowerCase() + '001', realName: role, role, phone: null } }
  const oldToken = 'old-student-token', newToken = 'new-admin-token'
  function response(config, data) { return { data: { code: 0, message: '成功', data }, status: 200, statusText: 'OK', headers: {}, config } }
  function context() {
    const pinia = createPinia(); setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory(), pinia)
    return { pinia, auth: useAuthStore(pinia), router }
  }
  storage.set('campus-repair.token', oldToken)
  let { auth } = context()
  auth.user = user('STUDENT'); auth.initialized = true
  let rejectOld, signalStarted
  const started = new Promise((resolve) => { signalStarted = resolve })
  http.defaults.adapter = async (config) => {
    if (config.url === '/auth/login') return response(config, { token: newToken, user: user('ADMIN'), role: 'ADMIN', expiresAt: '' })
    if (config.url === '/users/me') return new Promise((_resolve, reject) => {
      rejectOld = () => reject(new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null,
        { ...response(config, null), status: 401, data: { code: 40100, message: '登录失效', data: null } }))
      signalStarted()
    })
    return response(config, null)
  }
  const oldRequest = http.get('/users/me').catch(() => {})
  await started
  await auth.signIn({ username: 'admin001', password: 'example' })
  rejectOld(); await oldRequest
  assert.equal(auth.token, newToken, 'Late 401 from old session must not erase new session')
  assert.equal(auth.user.role, 'ADMIN')
  assert.equal(storage.get('campus-repair.token'), newToken)
  console.log('PASS late old-session 401 preserves new login')

  storage.clear(); storage.set('campus-repair.token', oldToken)
  ;({ auth } = context()); auth.user = user('STUDENT'); auth.initialized = true
  let sentToken
  http.defaults.adapter = async (config) => { sentToken = config.headers.Authorization; return response(config, null) }
  storage.set('campus-repair.token', newToken)
  await http.get('/users/me')
  assert.equal(sentToken, `Bearer ${oldToken}`, 'Request identity must match displayed session, not another tab token')
  console.log('PASS request token and displayed user use same session snapshot')
  auth.syncFromStorage()
  assert.equal(auth.user, null)
  assert.equal(auth.initialized, false)
  http.defaults.adapter = async (config) => response(config, user('ADMIN'))
  await auth.restore()
  assert.equal(auth.token, newToken); assert.equal(auth.user.role, 'ADMIN')
  console.log('PASS external token change restores trusted new identity')

  let finishLogout, signalLogout
  const logoutStarted = new Promise((resolve) => { signalLogout = resolve })
  http.defaults.adapter = async (config) => {
    if (config.url === '/auth/logout') return new Promise((resolve) => { finishLogout = () => resolve(response(config, null)); signalLogout() })
    return response(config, { token: 'newer-student-token', user: user('STUDENT'), role: 'STUDENT', expiresAt: '' })
  }
  const signingOut = auth.signOut()
  await logoutStarted
  await auth.signIn({ username: 'student001', password: 'example' })
  finishLogout(); await signingOut
  assert.equal(auth.token, 'newer-student-token', 'Old logout completion must not clear a newer session')
  assert.equal(auth.user.role, 'STUDENT')
  console.log('PASS delayed old-session logout preserves new login')

  // Exercise the actual login handler while asynchronous form validation is still pending.
  storage.clear()
  const loginContext = context()
  loginContext.auth.initialized = true
  await loginContext.router.push('/login')
  const { default: Login } = await server.ssrLoadModule('/src/views/LoginView.vue')
  let loginState
  await renderToString(createSSRApp({ setup() {
    loginState = Login.setup({}, { expose() {} })
    return () => h('div')
  } }).use(loginContext.pinia).use(loginContext.router))
  let validated, loginRequests = 0
  const validation = new Promise((resolve) => { validated = resolve })
  loginState.formRef.value = { validate: () => validation }
  loginState.form.username = 'student001'
  loginState.form.password = 'example'
  http.defaults.adapter = async (config) => {
    if (config.url === '/auth/login') loginRequests++
    return response(config, { token: 'single-login-token', user: user('STUDENT'), role: 'STUDENT', expiresAt: '' })
  }
  const firstClick = loginState.submit(), secondClick = loginState.submit()
  validated(true)
  await Promise.all([firstClick, secondClick])
  assert.equal(loginRequests, 1, 'Double click during validation must send only one login request')
  assert.equal(loginState.loading.value, false, 'Login loading must be released')
  console.log('PASS login double click during validation sends one request')

  await loginContext.router.push('/student/orders/99/messages')
  const { default: Chat } = await server.ssrLoadModule('/src/views/OrderChatView.vue')
  let chatState, sentMessages = 0
  await renderToString(createSSRApp({ setup() {
    chatState = Chat.setup({ role: 'student' }, { expose() {} })
    return () => h('div')
  } }).use(loginContext.pinia).use(loginContext.router))
  chatState.context.value = { orderId: 99, title: '隔离测试', workerId: 2, workerName: '原维修员' }
  chatState.draft.value = '发送给原维修人员的草稿'
  await nextTick()
  http.defaults.adapter = async (config) => {
    if (config.url === '/orders/99/messages/context') return response(config, { orderId: 99, title: '隔离测试', workerId: 3, workerName: '新维修员' })
    if (config.method === 'post') { sentMessages++; return response(config, { id: 1 }) }
    return response(config, [])
  }
  await chatState.load()
  await chatState.send()
  assert.equal(sentMessages, 0, 'Refresh after reassignment must not silently send an old draft to new staff')
  assert.equal(chatState.draft.value, '', 'Recipient change must clear the old-context draft')
  console.log('PASS refreshed chat recipient change clears old-context draft')

  const workerContext = context()
  workerContext.auth.initialized = true
  workerContext.auth.token = 'worker-fixture'
  workerContext.auth.user = user('WORKER')
  await workerContext.router.push('/worker/orders/99')
  const { default: Detail } = await server.ssrLoadModule('/src/views/OrderDetailView.vue')
  let detailState, finishRecord, recordStarted
  const pendingRecord = new Promise((resolve) => { recordStarted = resolve })
  await renderToString(createSSRApp({ setup() {
    detailState = Detail.setup({ role: 'worker' }, { expose() {} })
    return () => h('div')
  } }).use(workerContext.pinia).use(workerContext.router))
  detailState.loading.value = false
  detailState.content.value = '正在保存的原草稿'
  await nextTick()
  http.defaults.adapter = async (config) => {
    if (config.url === '/worker/repair-record') return new Promise((resolve) => {
      finishRecord = () => resolve(response(config, null)); recordStarted()
    })
    if (config.url === '/orders/99') return response(config, { order: { id: 99, repairRound: 1 }, records: [], dispatchHistory: [] })
    return response(config, null)
  }
  const saving = detailState.saveRecord()
  await pendingRecord
  detailState.content.value = '保存期间继续输入的新草稿'
  await nextTick()
  finishRecord(); await saving
  assert.equal(detailState.content.value, '保存期间继续输入的新草稿')
  console.log('PASS record save keeps text entered while request was pending')

  http.defaults.adapter = async (config) => {
    throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, {
      ...response(config, null), status: 403,
      data: new Blob([JSON.stringify({ code: 40300, message: '无权访问此图片', data: null })], { type: 'application/json' }),
    })
  }
  await assert.rejects(http.get('/images/example', { responseType: 'blob' }),
    (error) => error.code === 40300 && error.message === '无权访问此图片')
  console.log('PASS protected image errors preserve permission message')
} finally { delete globalThis.localStorage; await server.close() }
