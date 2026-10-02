import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
import { createSSRApp, createRenderer, h, reactive, ssrContextKey } from 'vue'
import { renderToString } from 'vue/server-renderer'

// Calls actual component handlers with controlled response ordering; no browser/DOM acceptance.
const storage = new Map()
globalThis.localStorage = { getItem: key => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)), removeItem: key => storage.delete(key) }
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
let failures = 0
try {
  const { http } = await server.ssrLoadModule('/src/utils/request.ts')
  const { useAuthStore } = await server.ssrLoadModule('/src/store/auth.ts')
  const { createAppRouter } = await server.ssrLoadModule('/src/router/index.ts')
  const response = (config, data) => ({ data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config })
  const context = { orderId: 99, title: '竞态验证工单', workerId: 2, workerName: '维修员' }
  const message = { id: 10, orderId: 99, senderId: 1, receiverId: 2, content: '已保存的消息', read: false }
  function deferred() { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
  async function state(path, route = '/student/orders/99/messages', role = 'student', initialize) {
    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(pinia)
    auth.user = { id: 1, username: 'fixture', realName: '验证账号', role: role.toUpperCase() }
    auth.token = 'fixture'; auth.initialized = true
    const router = createAppRouter(createMemoryHistory(), pinia)
    await router.push(route)
    http.defaults.adapter = async config => response(config, config.url.endsWith('/context') ? context : [])
    const { default: Component } = await server.ssrLoadModule(path)
    let result
    await renderToString(createSSRApp({ setup() {
      result = Component.setup({ role }, { expose() {} }); initialize?.(result); return () => h('div')
    } }).use(pinia).use(router))
    if (result.load) await result.load()
    return result
  }
  async function check(name, run) {
    try { await run(); console.log('PASS', name) }
    catch (error) { failures++; console.error('FAIL', name, error.message) }
    finally { delete globalThis.document }
  }
  const renderer = createRenderer({
    createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
    insert() {}, remove() {}, setElementText() {}, setText() {}, setComment() {}, patchProp() {},
    parentNode: () => null, nextSibling: () => null,
  })
  await check('late upload cannot attach an old-session photo to the current draft', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(pinia)
    auth.user = { id: 1, username: 'fixture', realName: '验证账号', role: 'WORKER' }
    auth.token = 'old-session'; auth.initialized = true
    const { default: Component } = await server.ssrLoadModule('/src/components/ImageUpload.vue')
    const props = reactive({ modelValue: '', contextKey: 99 }), events = []
    let upload
    const app = renderer.createApp({ setup() {
      upload = Component.setup(props, { expose() {}, emit: (name, value) => events.push([name, value]) })
      return () => h('div')
    } }).use(pinia)
    app.provide(ssrContextKey, { modules: new Set() })
    app.mount({})
    const gate = deferred(), started = deferred()
    http.defaults.adapter = async config => {
      started.resolve(); await gate.promise
      return response(config, { url: '/api/images/11111111-1111-1111-1111-111111111111' })
    }
    const pending = upload.choose({ target: { files: [new File(['fixture'], 'photo.png', { type: 'image/png' })], value: 'photo.png' } })
    await started.promise
    auth.token = 'new-session'
    gate.resolve(); await pending
    app.unmount()
    assert.equal(events.some(([name]) => name === 'update:modelValue'), false, 'old identity must not update the draft')
    assert.equal(upload.uploading.value, false)
    assert.equal(upload.error.value, '')
  })
  await check('late upload cannot attach a photo after the order context changes', async () => {
    const pinia = createPinia(); setActivePinia(pinia)
    const auth = useAuthStore(pinia)
    auth.user = { id: 1, username: 'fixture', realName: '验证账号', role: 'WORKER' }
    auth.token = 'fixture'; auth.initialized = true
    const { default: Component } = await server.ssrLoadModule('/src/components/ImageUpload.vue')
    const props = reactive({ modelValue: '', contextKey: '99:1' }), events = []
    let upload
    const app = renderer.createApp({ setup() {
      upload = Component.setup(props, { expose() {}, emit: (name, value) => events.push([name, value]) })
      return () => h('div')
    } }).use(pinia)
    app.provide(ssrContextKey, { modules: new Set() })
    app.mount({})
    const gate = deferred(), started = deferred()
    http.defaults.adapter = async config => {
      started.resolve(); await gate.promise
      return response(config, { url: '/api/images/11111111-1111-1111-1111-111111111111' })
    }
    const pending = upload.choose({ target: { files: [new File(['fixture'], 'photo.png', { type: 'image/png' })], value: 'photo.png' } })
    await started.promise
    props.contextKey = '100:2'
    gate.resolve(); await pending
    app.unmount()
    assert.equal(events.some(([name]) => name === 'update:modelValue'), false, 'old order/round must not update the draft')
    assert.equal(upload.uploading.value, false)
  })
  await check('manual reload cannot permanently block chat polling', async () => {
    const chat = await state('/src/views/OrderChatView.vue')
    globalThis.document = { hidden: false }
    const gate = deferred(), started = deferred()
    let blocked = true, requests = 0
    http.defaults.adapter = async config => {
      requests++
      if (blocked && config.url.endsWith('/context')) { blocked = false; started.resolve(); await gate.promise }
      return response(config, config.url.endsWith('/context') ? context : [])
    }
    const pending = chat.refresh(); await started.promise
    await chat.load(); gate.resolve(); await pending
    const before = requests; await chat.refresh()
    assert.ok(requests > before, 'polling must resume after reload invalidates an old poll')
    chat.reset()
  })
  await check('successful POST remains successful when message synchronization fails', async () => {
    const chat = await state('/src/views/OrderChatView.vue', '/student/orders/99/messages', 'student', result => {
      result.context.value = context; result.draft.value = message.content
    })
    let posts = 0
    http.defaults.adapter = async config => {
      if (config.url.endsWith('/context')) return response(config, context)
      if (config.method === 'post') { posts++; return response(config, message) }
      throw new Error('controlled synchronization failure')
    }
    await chat.send(); await chat.send()
    assert.equal(posts, 1, 'already committed message must not be resent')
    assert.equal(chat.draft.value, '')
    assert.match(chat.feedback.value, /已发送/)
    assert.match(chat.error.value, /同步|刷新/)
    chat.reset()
  })
  await check('read receipts update without a new message and without losing history', async () => {
    const chat = await state('/src/views/OrderChatView.vue')
    chat.messages.value = [{ ...message }]
    globalThis.document = { hidden: false }
    http.defaults.adapter = async config => response(config, config.url.endsWith('/context') ? context :
      config.params?.afterId === message.id ? [] : [{ ...message, read: true }])
    await chat.refresh()
    assert.equal(chat.messages.value.length, 1)
    assert.equal(chat.messages.value[0].read, true)
    chat.reset()
  })
  await check('notification last-page removal returns remaining unread messages', async () => {
    const panel = await state('/src/components/NotificationPanel.vue')
    panel.page.value = 2; panel.unreadOnly.value = true
    http.defaults.adapter = async config => response(config, config.method === 'put' ? null : {
      records: config.params.page === 1 ? [{ id: 1, readStatus: 0 }] : [], total: 20, unreadCount: 20,
      page: config.params.page, size: 20, latestId: 21,
    })
    await panel.read(21)
    assert.equal(panel.page.value, 1)
    assert.ok(panel.data.value.records.length)
  })
  await check('permanently unread old-round messages cannot starve later read receipts', async () => {
    for (const firstId of [1, 10]) {
      const chat = await state('/src/views/OrderChatView.vue')
      chat.messages.value = Array.from({ length: 121 }, (_, offset) => ({ ...message, id: firstId + offset,
        senderId: offset === 0 || offset === 120 ? 1 : 2 }))
      globalThis.document = { hidden: false }
      http.defaults.adapter = async config => {
        if (config.url.endsWith('/context')) return response(config, context)
        const { afterId, beforeId, size } = config.params
        return response(config, chat.messages.value.filter(row => afterId ? row.id > afterId : row.id < beforeId)
          .slice(0, size).map(row => ({ ...row, read: row.id === firstId + 120 })))
      }
      await chat.refresh(); await chat.refresh()
      assert.equal(chat.messages.value.at(-1).read, true)
      assert.equal(chat.messages.value[0].read, false)
      assert.equal(chat.messages.value.length, 121)
      chat.reset()
    }
  })
  await check('shrinking dispatch queue stays on a valid page', async () => {
    const dispatch = await state('/src/views/DispatchView.vue', '/admin/dispatch', 'admin')
    dispatch.page.value = 2
    http.defaults.adapter = async config => response(config, { records: config.params.page === 1 ? [{ id: 1 }] : [], total: 8, page: config.params.page, size: 8 })
    await dispatch.loadQueue()
    assert.equal(dispatch.page.value, 1)
    assert.equal(dispatch.orders.value.length, 1)
  })
  await check('catalog save does not clear a newer draft', async () => {
    const management = await state('/src/views/ManagementView.vue', '/admin/manage', 'admin')
    management.buildingId.value = 1; management.building.name = '原楼栋'
    const gate = deferred(), started = deferred()
    http.defaults.adapter = async config => {
      if (config.method === 'put') { started.resolve(); await gate.promise; return response(config, 1) }
      return response(config, { buildings: [], types: [] })
    }
    const saving = management.submitCatalog('building'); await started.promise
    management.editBuilding({ id: 2, name: '新楼栋', type: '教学', longitude: null, latitude: null })
    management.building.name = '保存期间输入的新内容'
    gate.resolve(); await saving
    assert.equal(management.buildingId.value, 2)
    assert.equal(management.building.name, '保存期间输入的新内容')
    assert.equal(management.busy.value, false)
  })
  await check('account save does not clear text entered while saving', async () => {
    const management = await state('/src/views/ManagementView.vue', '/admin/manage', 'admin')
    management.editingId.value = 1; management.form.realName = '原姓名'
    const gate = deferred(), started = deferred()
    http.defaults.adapter = async config => {
      if (config.method === 'put') { started.resolve(); await gate.promise; return response(config, null) }
      return response(config, { records: [], total: 0 })
    }
    const saving = management.submitAccount(); await started.promise
    management.form.realName = '继续填写的新姓名'
    gate.resolve(); await saving
    assert.equal(management.form.realName, '继续填写的新姓名')
    assert.equal(management.editingId.value, 1)
  })
} finally { delete globalThis.document; delete globalThis.localStorage; await server.close() }
if (failures) process.exitCode = 1
