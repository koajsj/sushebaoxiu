import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

// Real frontend API/Pinia/router -> real HTTP. No browser/GUI or mocked responses.
const storage=new Map()
globalThis.localStorage={getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,String(value)),removeItem:key=>storage.delete(key)}
const server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'})
const warnings=[], originalWarn=console.warn
console.warn=(...args)=>warnings.push(args.join(' '))
let count=0
function pass(label){count++;console.log('PASS',label)}
try{
 const {http}=await server.ssrLoadModule('/src/utils/request.ts')
 // Fetch adapter matches browser Blob/FormData semantics in this Node-only check.
 http.defaults.adapter='fetch'
 http.interceptors.request.use(config=>{if(config.data instanceof FormData)config.headers.setContentType(false);return config})
 http.defaults.baseURL=process.env.CHECK_BACKEND_URL || 'http://127.0.0.1:18085/api'
 assert.equal(http.defaults.baseURL,'http://127.0.0.1:18085/api','isolated endpoint required before mutations')
 const api=await server.ssrLoadModule('/src/api/repair.ts')
 const dispatch=await server.ssrLoadModule('/src/api/dispatch.ts')
 const phase5=await server.ssrLoadModule('/src/api/phase5.ts')
 const {useAuthStore}=await server.ssrLoadModule('/src/store/auth.ts')
 const {createAppRouter}=await server.ssrLoadModule('/src/router/index.ts')
 async function session(role){
   const pinia=createPinia();setActivePinia(pinia)
   const router=createAppRouter(createMemoryHistory(),pinia), auth=useAuthStore(pinia)
   await auth.signIn({username:role+'001',password:'123456'})
   await router.push('/'+role);await router.isReady()
   assert.equal(router.currentRoute.value.path,'/'+role)
   return {auth,router}
 }
 let {router}=await session('student')
 const catalog=await api.getCatalog()
 assert.ok(catalog.types.length>=4&&catalog.buildings.length>=2);pass('real catalog loads')
 const png=Uint8Array.from(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jZ1kAAAAASUVORK5CYII=','base64'))
 const imageUrl=await api.uploadImage(new File([png],'photo.png',{type:'image/png'}))
 assert.match(imageUrl,/^\/api\/images\//)
 const blob=await api.loadImage(imageUrl);assert.ok(blob.size>0);pass('frontend uploads and privately reads a real image')
 const order=await api.createOrder({typeId:catalog.types[0].id,title:'前端验收：水龙头漏水',description:'连接处持续滴水，请安排检修。',buildingId:catalog.buildings[0].id,roomNo:'301',priority:'NORMAL',imageUrl})
 assert.equal(order.status,'WAIT_AUDIT');pass('frontend submits WAIT_AUDIT order')
 await router.push('/student/orders/'+order.id)
 assert.equal(router.currentRoute.value.meta.role,'student')
 assert.equal((await api.getOrders('student',{status:'WAIT_AUDIT'})).records.some(o=>o.id===order.id),true);pass('student detail route and filtered list')
 await session('admin')
 await api.actOnOrder('admin',order.id,'audit')
 const workers=await api.getWorkers(),worker=workers.find(w=>w.username==='worker001')
 assert.ok(worker)
 const recommendations=await dispatch.getRecommendations(order.id)
 const selected=recommendations.find(r=>r.workerId===worker.id);assert.ok(selected)
 await dispatch.confirmDispatch(order.id,selected)
 assert.equal((await api.getOrder(order.id)).order.workerId,worker.id);assert.equal((await api.getOrder(order.id)).order.status,'ASSIGNED');pass('admin audit and explainable smart assignment')
 const overview=await phase5.getOverview();assert.ok(overview.totalCount>=1)
 assert.equal((await phase5.getTrend()).length,14)
 assert.ok((await phase5.getTypeStats()).some(t=>t.count>0))
 assert.ok((await phase5.getWorkerStats()).some(w=>w.workerId===worker.id))
 assert.ok((await dispatch.getMapOrders()).records.some(o=>o.id===order.id))
 const restoredPinia=createPinia();setActivePinia(restoredPinia)
 const restoredRouter=createAppRouter(createMemoryHistory(),restoredPinia),restoredAuth=useAuthStore(restoredPinia)
 await restoredRouter.push('/admin/dashboard');await restoredRouter.isReady()
 assert.equal(restoredAuth.user.role,'ADMIN');assert.equal(restoredRouter.currentRoute.value.path,'/admin/dashboard')
 pass('real statistics, map and refreshed-session identity load')
 await session('worker')
 const notifications=await phase5.getNotifications();const unread=notifications.records.find(n=>n.readStatus===0);assert.ok(unread)
 await phase5.markNotificationRead(unread.id)
 assert.equal((await phase5.getNotifications()).records.find(n=>n.id===unread.id).readStatus,1);pass('task notification read persists')
 await phase5.sendMessage(order.id,'准备检查灯具。')
 assert.ok((await phase5.getMessages(order.id)).some(m=>m.content==='准备检查灯具。'));pass('assigned worker uses order chat')
 await api.actOnOrder('worker',order.id,'accept')
 await api.actOnOrder('worker',order.id,'start')
 const repairImage=await api.uploadImage(new File([png],'result.png',{type:'image/png'}))
 await api.addRepairRecord(order.id,'已更换水龙头密封垫，确认无漏水。',repairImage)
 await api.actOnOrder('worker',order.id,'finish')
 assert.equal((await api.getOrder(order.id)).order.status,'WAIT_CONFIRM');pass('worker starts assigned task, records with photo, finishes')
 const {auth}=await session('student')
 await phase5.sendMessage(order.id,'维修已解决，谢谢。')
 assert.ok((await phase5.getMessages(order.id)).some(m=>m.senderId===auth.user.id));pass('student uses same order conversation')
 assert.ok((await api.loadImage(repairImage)).size>0);pass('student loads worker result photo')
 await api.actOnOrder('student',order.id,'confirm')
 await api.evaluateOrder(order.id,5,'处理认真，感谢。')
 const detail=await api.getOrder(order.id)
 assert.equal(detail.order.status,'COMMENTED');assert.equal(detail.evaluation.score,5)
 assert.equal(detail.timeline.length,9);pass('student confirms and evaluates; full timeline persists')
 assert.ok((await api.getSummary('student')).completed>=1);pass('home counters reflect completed repair')
 const oldToken=auth.token
 await auth.signOut();assert.equal(storage.size,0)
 await assert.rejects(http.get('/users/me',{headers:{Authorization:`Bearer ${oldToken}`}}),e=>e.code===40100)
 pass('logout clears local token and revokes old server token')
 assert.deepEqual(warnings,[]);pass('no console/Vue warnings during API and router checks')
 console.log('PASS',count,'frontend real HTTP business checks; order',order.id,'browser interaction remains unverified.')
}finally{console.warn=originalWarn;await server.close()}
