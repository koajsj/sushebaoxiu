import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { AxiosError } from 'axios'

// Controlled HTTP ordering exercises production state/interceptors, no browser or real sessions.
const storage = new Map()
globalThis.localStorage = { getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)), removeItem: (key) => storage.delete(key) }
const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
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
    return { auth: useAuthStore(pinia), router }
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
} finally { delete globalThis.localStorage; await server.close() }
