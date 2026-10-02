import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { createSSRApp, h, nextTick } from 'vue'
import { renderToString } from 'vue/server-renderer'

// Targeted download contract checks without a browser or database mutations.
const server=await createServer({server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'})
try {
  const {http,ApiError}=await server.ssrLoadModule('/src/utils/request.ts')
  const {getReport}=await server.ssrLoadModule('/src/api/export.ts')
  const body=new Blob(['PK workbook'],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'})
  http.defaults.adapter=async config=>{
    assert.equal(config.responseType,'blob')
    assert.match(config.url,/^\/admin\/export\/(orders|workers|types)$/)
    return {data:body,status:200,statusText:'OK',config,headers:{'content-type':body.type,'content-disposition':"attachment; filename*=UTF-8''"+encodeURIComponent('工单统计_2026-10-01.xlsx')}}
  }
  for(const type of ['orders','workers','types']) {
    const file=await getReport(type)
    assert.equal(file.blob,body)
    assert.equal(file.filename,'工单统计_2026-10-01.xlsx')
  }
  console.log('PASS three report downloads request protected Blob endpoints with dated filenames')
  http.defaults.adapter=async config=>{throw new AxiosError('Forbidden','ERR_BAD_REQUEST',config,null,{
    status:403,config,headers:{},data:new Blob([JSON.stringify({code:40300,message:'没有访问权限',data:null})],{type:'application/json'})})}
  await assert.rejects(getReport('orders'),e=>e instanceof ApiError&&e.message==='没有访问权限')
  console.log('PASS JSON permission errors are surfaced instead of downloaded as Excel')
  http.defaults.adapter=async config=>({status:200,statusText:'OK',config,headers:{'content-type':'application/json'},data:new Blob(['{}'],{type:'application/json'})})
  await assert.rejects(getReport('orders'),ApiError)
  console.log('PASS invalid file responses are rejected with a visible error')
  const {default:ExportButton}=await server.ssrLoadModule('/src/components/ExportReportButton.vue')
  const {useAuthStore}=await server.ssrLoadModule('/src/store/auth.ts')
  const pinia=createPinia();setActivePinia(pinia)
  const auth=useAuthStore(pinia);auth.token='admin-fixture';auth.user={id:3,username:'admin001',realName:'管理员',role:'ADMIN',phone:null}
  let state
  await renderToString(createSSRApp({setup(){state=ExportButton.setup({},{expose(){}});return()=>h('div')}}).use(pinia))
  await state.download()
  assert.ok(state.error.value);assert.equal(state.busy.value,false)
  assert.match(await renderToString(createSSRApp(ExportButton).use(pinia)),/下载 Excel 报表/)
  console.log('PASS export component reports download failure and releases loading')
  let finish,started,requests=0
  const pending=new Promise(resolve=>{started=resolve})
  http.defaults.adapter=config=>new Promise(resolve=>{requests++;started();finish=()=>resolve({data:body,status:200,statusText:'OK',config,headers:{'content-type':body.type}})})
  const downloading=state.download();await pending
  await state.download();assert.equal(requests,1)
  auth.token='another-session';await nextTick();finish();await downloading
  assert.equal(state.busy.value,false);assert.equal(state.feedback.value,'');assert.equal(state.error.value,'')
  console.log('PASS duplicate clicks are blocked and session changes cancel stale downloads')
} finally {await server.close()}
