import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

// Runs production Pinia/Axios/router code against the real backend, without a browser.
// Only the platform's localStorage is represented in memory.
const storage = new Map()
globalThis.localStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key),
}
const server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' })
const backend = process.env.CHECK_BACKEND_URL || 'http://127.0.0.1:8080/api'
const warnings = []
const originalWarn = console.warn
console.warn = (...args) => warnings.push(args.join(' '))
let checks = 0
function pass(name) { checks++; console.log(`PASS ${name}`) }
try {
  const { createAppRouter } = await server.ssrLoadModule('/src/router/index.ts')
  const { useAuthStore } = await server.ssrLoadModule('/src/store/auth.ts')
  const { http, ApiError } = await server.ssrLoadModule('/src/utils/request.ts')
  http.defaults.baseURL = backend
  function context() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory(), pinia)
    return { auth: useAuthStore(pinia), router }
  }
  storage.clear()
  let { auth, router } = context()
  await router.push('/student')
  await router.isReady()
  assert.equal(router.currentRoute.value.path, '/login')
  assert.equal(auth.authenticated, false)
  pass('anonymous route redirects to login')
  await assert.rejects(auth.signIn({ username: 'student001', password: 'incorrect' }), /账号或密码/)
  assert.equal(storage.size, 0)
  assert.equal(auth.authenticated, false)
  pass('wrong password does not persist a session')

  for (const role of ['student', 'worker', 'admin']) {
    storage.clear()
    ;({ auth, router } = context())
    await auth.signIn({ username: `${role}001`, password: '123456' })
    assert.equal(auth.role, role)
    assert.equal(auth.authenticated, true)
    assert.equal(storage.size, 1)
    assert.equal(storage.get('campus-repair.token'), auth.token)
    const oldToken = auth.token
    await router.push('/login')
    await router.isReady()
    assert.equal(router.currentRoute.value.path, `/${role}`)
    pass(`${role}: real login and automatic role landing`)
    for (const other of ['student', 'worker', 'admin'].filter((value) => value !== role)) {
      await router.push(`/${other}`)
      assert.equal(router.currentRoute.value.path, `/${role}`)
    }
    pass(`${role}: route isolation`)

    // A fresh Pinia + memory router represents a reload; trusted user data is fetched again.
    ;({ auth, router } = context())
    assert.equal(auth.user, null)
    await router.push(`/${role}`)
    await router.isReady()
    assert.equal(auth.role, role)
    assert.equal(auth.token, oldToken)
    assert.equal(router.currentRoute.value.path, `/${role}`)
    pass(`${role}: persisted token restores identity after reload`)
    await auth.signOut()
    await router.replace('/login')
    assert.equal(auth.authenticated, false)
    assert.equal(auth.token, '')
    assert.equal(storage.size, 0)
    assert.equal(router.currentRoute.value.path, '/login')
    const response = await fetch(`${backend}/users/me`, { headers: { Authorization: `Bearer ${oldToken}` } })
    assert.equal(response.status, 401)
    pass(`${role}: logout clears storage, returns login, revokes server token`)
  }

  storage.set('campus-repair.token', 'invalid.jwt.token')
  ;({ auth, router } = context())
  await router.push('/admin')
  await router.isReady()
  assert.equal(auth.authenticated, false)
  assert.equal(storage.size, 0)
  assert.equal(router.currentRoute.value.path, '/login')
  pass('invalid stored token is cleared and blocked')

  storage.clear()
  ;({ auth, router } = context())
  await auth.signIn({ username: 'student001', password: '123456' })
  ;({ auth, router } = context())
  const savedToken = auth.token
  http.defaults.baseURL = 'http://127.0.0.1:9/api'
  await assert.rejects(auth.restore(), ApiError)
  assert.equal(auth.token, savedToken)
  assert.equal(auth.initialized, false)
  http.defaults.baseURL = backend
  await Promise.all([auth.restore(), auth.restore()])
  assert.equal(auth.role, 'student')
  await auth.signOut()
  pass('temporary connection failure preserves token and restoration can retry')
  assert.deepEqual(warnings, [])
  pass('no console warnings during state/router flow')
  console.log(`PASS ${checks} frontend flow checks against real backend; browser UI remains unverified.`)
} finally {
  console.warn = originalWarn
  delete globalThis.localStorage
  await server.close()
}
