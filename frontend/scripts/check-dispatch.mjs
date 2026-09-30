import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createPinia,setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'
// Real existing frontend API + Pinia + router, isolated HTTP backend only.
const endpoint=process.env.CHECK_BACKEND_URL||'http://127.0.0.1:18084/api'
assert.equal(endpoint,'http://127.0.0.1:18084/api','isolated server required before mutations')
const storage=new Map()
globalThis.localStorage={getItem:key=>storage.get(key)??null,setItem:(key,value)=>storage.set(key,String(value)),removeItem:key=>storage.delete(key)}
const server=await createServer({server:{middlewareMode:true},appType:'custom'})
let count=0
const pass=label=>{count++;console.log('PASS',label)}
const warnings=[],original=console.warn
console.warn=(...args)=>warnings.push(args.join(' '))
try {
 const {http}=await server.ssrLoadModule('/src/utils/request.ts');http.defaults.adapter='fetch';http.defaults.baseURL=endpoint
 const api=await server.ssrLoadModule('/src/api/dispatch.ts')
 const repair=await server.ssrLoadModule('/src/api/repair.ts')
 const {useAuthStore}=await server.ssrLoadModule('/src/store/auth.ts')
 const {createAppRouter}=await server.ssrLoadModule('/src/router/index.ts')
 async function session(role){const pinia=createPinia();setActivePinia(pinia);const router=createAppRouter(createMemoryHistory(),pinia),auth=useAuthStore(pinia);await auth.signIn({username:role+'001',password:'123456'});return {auth,router}}
 let {router}=await session('student')
 await router.push('/admin/map');assert.equal(router.currentRoute.value.path,'/student')
 await assert.rejects(api.getMapWorkers(),e=>e.code===40300);pass('student route and API cannot enter admin map')
 const catalog=await repair.getCatalog()
 const order=await repair.createOrder({typeId:catalog.types[0].id,title:'前端智能派单验收',description:'宿舍灯具损坏，请更换。',buildingId:catalog.buildings[0].id,roomNo:'301',priority:'NORMAL'})
 const admin=await session('admin');router=admin.router
 await router.push('/admin/dispatch?orderId='+order.id);assert.equal(router.currentRoute.value.path,'/admin/dispatch')
 await repair.actOnOrder('admin',order.id,'audit')
 const rows=await api.getRecommendations(order.id);assert.ok(rows.length>=3&&rows.every(r=>r.recommendationId>0));pass('real admin page route and candidate scores load')
 const selected=rows.find(r=>r.workerId===1);assert.ok(selected)
 await api.confirmDispatch(order.id,selected);assert.equal((await repair.getOrder(order.id)).order.status,'ASSIGNED');pass('frontend confirms server-controlled assignment')
 await router.push('/admin/map');assert.equal(router.currentRoute.value.path,'/admin/map')
 const [buildings,orders,workers]=await Promise.all([api.getMapBuildings(),api.getMapOrders('ASSIGNED'),api.getMapWorkers()])
 const point=orders.records.find(o=>o.id===order.id);assert.ok(point)
 const building=buildings.find(b=>b.id===order.buildingId);assert.equal(point.longitude,building.longitude);assert.equal(point.latitude,building.latitude)
 assert.ok(workers.find(w=>w.id===1).activeTaskCount>=1);pass('map API consumes persisted order/building/worker data')
 const restoredPinia=createPinia();setActivePinia(restoredPinia);const restoredRouter=createAppRouter(createMemoryHistory(),restoredPinia),restoredAuth=useAuthStore(restoredPinia)
 await restoredRouter.push('/admin/map');assert.ok(restoredAuth.authenticated);assert.equal(restoredRouter.currentRoute.value.path,'/admin/map');pass('stored token restores admin map route after refresh')
 const worker=await session('worker');await worker.router.push('/admin/dispatch');assert.equal(worker.router.currentRoute.value.path,'/worker');pass('worker cannot enter admin dispatch route')
 assert.ok((await repair.getOrders('worker')).records.some(o=>o.id===order.id));await repair.actOnOrder('worker',order.id,'start');await repair.addRepairRecord(order.id,'已更换灯具，验收正常。');await repair.actOnOrder('worker',order.id,'finish');pass('assigned worker continues existing repair flow')
 const student=await session('student');await repair.actOnOrder('student',order.id,'confirm');await repair.evaluateOrder(order.id,5,'满意')
 const detail=await repair.getOrder(order.id);assert.equal(detail.order.status,'COMMENTED');assert.equal(detail.timeline.length,7);assert.equal(detail.evaluation.score,5);pass('student confirms and evaluates smart-dispatch order')
 await student.auth.signOut();await student.router.push('/admin/map');assert.equal(student.router.currentRoute.value.path,'/login');assert.equal(storage.size,0);pass('logout clears token and route guard returns login')
 assert.deepEqual(warnings,[]);pass('no frontend API/router console warnings')
 console.log('PASS',count,'frontend Phase4 HTTP checks; order',order.id)
} finally {console.warn=original;await server.close()}
