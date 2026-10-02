<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getOrder,getOrders } from '../api/repair'
import { confirmDispatch,getRecommendations,generateRecommendations } from '../api/dispatch'
import type { RepairOrder } from '../types/repair'
import type { WorkerRecommendation } from '../types/dispatch'
import WorkerRecommendCard from '../components/WorkerRecommendCard.vue'
import DispatchHero from '../components/dispatch/DispatchHero.vue'
import OrderSummaryCard from '../components/dispatch/OrderSummaryCard.vue'
import DispatchConfirmDialog from '../components/dispatch/DispatchConfirmDialog.vue'
const route=useRoute()
const orders=ref<RepairOrder[]>([]),order=ref<RepairOrder|null>(null),recommendations=ref<WorkerRecommendation[]>([])
const previousRejection=ref('')
const pendingWorker=ref<WorkerRecommendation|null>(null)
const selectionLocked=computed(()=>busy.value||!!pendingWorker.value)
const topScore=computed(()=>recommendations.value[0]?.totalScore??0)
const tied=computed(()=>recommendations.value.length>1&&recommendations.value[1]?.totalScore===topScore.value)
const page=ref(1),total=ref(0),queueLoading=ref(false),loading=ref(false),busy=ref(false),confirmingId=ref<number|null>(null)
const error=ref(''),queueError=ref(''),feedback=ref(''),expired=ref(false)
let revision=0,queueRevision=0,expiryTimer:ReturnType<typeof setTimeout>|undefined
const eligible=computed(()=>order.value?.status==='WAIT_ASSIGN'&&!order.value.workerId)
function clearRecommendations(){recommendations.value=[];expired.value=false;clearTimeout(expiryTimer)}
function showRecommendations(rows:WorkerRecommendation[]){recommendations.value=rows;clearTimeout(expiryTimer)
  const expiry=rows.length?Math.min(...rows.map(row=>Date.parse(row.expiresAt))):0
  expired.value=!!expiry&&expiry<=Date.now()
  if(expiry>Date.now())expiryTimer=setTimeout(()=>{expired.value=true},Math.max(0,expiry-Date.now()))
}
async function loadQueue(selectFirst=false) {
  const current=++queueRevision;queueLoading.value=true;queueError.value=''
  try {let result=await getOrders('admin',{phase:'WAIT_DISPATCH',page:page.value,size:8});if(current!==queueRevision)return
    const lastPage=Math.max(1,Math.ceil(result.total/8))
    if(page.value>lastPage){page.value=lastPage;result=await getOrders('admin',{phase:'WAIT_DISPATCH',page:page.value,size:8});if(current!==queueRevision)return}
    orders.value=result.records;total.value=result.total
    if(selectFirst){const query=Number(route.query.orderId);const id=Number.isSafeInteger(query)&&query>0?query:orders.value.find(o=>!o.workerId)?.id;if(id)await selectOrder(id)}
  } catch(e){if(current===queueRevision){orders.value=[];queueError.value=e instanceof Error?e.message:'订单列表加载失败'}}
  finally{if(current===queueRevision)queueLoading.value=false}
}
async function selectOrder(id:number) {
  if(busy.value)return
  pendingWorker.value=null;feedback.value=''
  const current=++revision;loading.value=true;error.value='';order.value=null;previousRejection.value='';clearRecommendations()
  try {const result=await getOrder(id);if(current!==revision)return;order.value=result.order
    const rejected=[...(result.dispatchHistory||[])].reverse().find(row=>row.decision==='REJECTED');previousRejection.value=rejected?`${rejected.workerName||'原维修员'} · ${rejected.rejectReason}`:''
    if(result.order.status==='WAIT_ASSIGN'&&!result.order.workerId){const rows=await getRecommendations(id);if(current!==revision)return;showRecommendations(rows)}
  }catch(e){if(current===revision){clearRecommendations();error.value=e instanceof Error?e.message:'推荐加载失败'}}
  finally{if(current===revision)loading.value=false}
}
async function generate(refresh=false){if(!order.value||busy.value||loading.value||!eligible.value)return
  const current=revision,id=order.value.id;loading.value=true;error.value=''
  try{const rows=await generateRecommendations(id,refresh);if(current!==revision)return;showRecommendations(rows)}
  catch(e){if(current===revision)error.value=e instanceof Error?e.message:'推荐生成失败'}
  finally{if(current===revision)loading.value=false}
}
function chooseWorker(worker:WorkerRecommendation) {
  if(selectionLocked.value||loading.value||!eligible.value||expired.value||!order.value)return
  pendingWorker.value=worker
}
async function confirm() {
  const worker=pendingWorker.value
  if(!worker||busy.value||loading.value||!eligible.value||expired.value||!order.value||!recommendations.value.some(row=>row.recommendationId===worker.recommendationId))return
  const current=revision,id=order.value.id;busy.value=true;confirmingId.value=worker.workerId;error.value='';feedback.value=''
  try {await confirmDispatch(id,worker);if(current!==revision)return;clearRecommendations();feedback.value=`已确认派单给 ${worker.workerName}。等待维修人员响应任务。`
    try{const result=await getOrder(id);if(current!==revision)return;order.value=result.order}catch{if(current!==revision)return;order.value=null;error.value='派单已成功，订单详情刷新失败，请重新加载。'}
    await loadQueue()
  }catch(e){if(current===revision){clearRecommendations();error.value=e instanceof Error?e.message:'派单结果未确认，请刷新订单后重试'}}
  finally{if(current===revision){busy.value=false;confirmingId.value=null;pendingWorker.value=null}}
}
function changePage(value:number){if(selectionLocked.value)return;page.value=value;void loadQueue()}
onMounted(()=>loadQueue(true))
watch(()=>route.query.orderId,(value)=>{const id=Number(value);if(Number.isSafeInteger(id)&&id>0)void selectOrder(id)})
onBeforeUnmount(()=>{revision++;queueRevision++;clearTimeout(expiryTimer)})
</script>
<template>
  <section class="business-page dispatch-center">
    <DispatchHero :total="total" :loading="queueLoading" :failed="!!queueError" />
    <p v-if="feedback" class="notice success dispatch-success" role="status">{{ feedback }}<RouterLink v-if="order" class="text-button" :to="`/admin/orders/${order.id}`">查看工单进展 →</RouterLink></p>
    <div class="decision-grid">
      <aside class="order-context">
        <section class="queue-panel" aria-label="待派单工单" :aria-busy="queueLoading">
          <header class="queue-heading"><h2>选择待派单工单</h2><button class="text-button" :disabled="queueLoading||selectionLocked" @click="loadQueue(!order)">{{ queueLoading?'更新中…':'刷新' }}</button></header>
          <p v-if="queueError" class="notice error" role="alert">{{ queueError }}</p>
          <p v-if="queueLoading&&!orders.length" class="queue-placeholder" role="status">正在读取待派单队列…</p>
          <p v-else-if="!orders.length&&!queueError" class="queue-placeholder">当前没有待派单工单。审核通过的报修会出现在这里。</p>
          <div class="queue-items"><button v-for="item in orders" :key="item.id" class="queue-item" :class="{selected:order?.id===item.id}" :aria-pressed="order?.id===item.id" :disabled="selectionLocked" @click="selectOrder(item.id)"><span>#{{ item.id }} · {{ item.typeName }}</span><strong>{{ item.title }}</strong><small>{{ item.buildingName }} · {{ item.roomNo }}</small></button></div>
          <nav v-if="total>8&&!queueError" class="queue-pagination" aria-label="待派单队列分页"><button class="text-button" :disabled="page<=1||queueLoading||selectionLocked" @click="changePage(page-1)">上一页</button><span>{{ page }} / {{ Math.ceil(total/8) }}</span><button class="text-button" :disabled="page*8>=total||queueLoading||selectionLocked" @click="changePage(page+1)">下一页</button></nav>
        </section>
        <OrderSummaryCard v-if="order" :key="order.id" :order="order" :previous-rejection="previousRejection" />
      </aside>
      <section class="candidates-area" aria-label="维修人员推荐" :aria-busy="loading||busy">
        <header class="candidates-heading"><div><p class="eyebrow">理解依据，再做选择</p><h2>推荐维修人员 <span v-if="eligible&&recommendations.length">{{ recommendations.length }} 位候选</span></h2><p>技能 40% · 距离 30% · 负载 20% · 评价 10%</p></div><button v-if="order&&eligible" class="secondary-button" :disabled="loading||selectionLocked" @click="generate(true)">重新计算</button></header>
        <div v-if="error" class="notice error" role="alert">{{ error }}<button v-if="order" class="text-button" :disabled="busy||loading" @click="selectOrder(order.id)">重新加载</button><button v-else class="text-button" :disabled="busy||loading" @click="loadQueue(true)">重试</button></div>
        <p v-if="expired" class="notice error" role="status">推荐已过期，请重新计算后派单。</p>
        <div v-if="loading" class="candidate-loading" role="status"><span class="loading-line" /><span class="loading-line short" /><p>正在读取工单与推荐快照…</p></div>
        <template v-else-if="eligible&&recommendations.length">
          <p class="comparison-caption">{{ tied?'最高评分存在并列，可结合技能资料与实际任务量比较。':'候选按后端综合评分排序，首位突出显示供你优先比较。' }}评分为规则匹配度，不代表成功概率。</p>
          <div class="candidate-list"><WorkerRecommendCard v-for="(worker,index) in recommendations" :key="worker.recommendationId" :worker="worker" :index="index" :top-score="topScore" :tied="tied" :busy="selectionLocked" :confirming="confirmingId===worker.workerId" :disabled="expired" @confirm="chooseWorker" /></div>
          <p class="candidate-disclaimer">任务量、距离和评价为读取时的资料；四维评分与推荐原因保留本批次快照，变化后需重新计算。距离来自静态服务坐标，不是实时位置。坐标缺失时距离项计 0；暂无评价时评价项计 50。</p>
        </template>
        <div v-else-if="!error" class="decision-empty"><span class="empty-symbol" aria-hidden="true">↗</span><h2>{{ !order?'从一份工单开始':!eligible?'工单已有处理安排':'准备一次有依据的选择' }}</h2><p>{{ !order?'选择待派单工单，先了解故障，再比较合适的维修人员。':!eligible?'当前工单不再等待派单。可查看完整详情与后续处理进展。':'尚无有效推荐批次。生成推荐后，系统会保存当前真实资料的评分快照；若暂无可用人员，可前往工单详情检查人工安排。' }}</p><button v-if="eligible" class="primary-button" :disabled="loading||busy" @click="generate()">生成推荐</button><RouterLink v-else-if="order" class="text-button" :to="`/admin/orders/${order.id}`">查看工单详情 →</RouterLink></div>
      </section>
    </div>
    <DispatchConfirmDialog :order="order" :worker="pendingWorker" :busy="busy" :disabled="loading||expired||!eligible" :expired="expired" @close="pendingWorker=null" @confirm="confirm" />
  </section>
</template>

<style scoped>
.dispatch-center { display: grid; gap: var(--space-5); }
.decision-grid { display: grid; grid-template-columns: minmax(240px,280px) minmax(0,1fr); gap: var(--space-6); align-items: start; }
.order-context { display: grid; gap: var(--space-5); min-width: 0; }
.queue-panel { padding: var(--space-5); border-radius: var(--radius); border: 1px solid var(--surface-line); background: var(--surface); }
.queue-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-3); }
.queue-heading h2 { font-size: var(--text-body); font-weight: 600; }
.queue-items { display: grid; gap: var(--space-1); max-height: 350px; overflow-y: auto; }
.queue-item { display: grid; gap: var(--space-1); width: 100%; min-width: 0; text-align: left; color: var(--ink); background: transparent; border: 1px solid transparent; border-radius: var(--radius-sm); padding: var(--space-3); transition: background var(--motion-fast), border-color var(--motion-fast); }
.queue-item:hover:not(:disabled) { background: var(--surface-subtle); }
.queue-item.selected { background: var(--accent-soft); border-color: var(--accent-line); }
.queue-item span, .queue-item small, .queue-pagination { font-size: var(--text-caption); color: var(--muted); }
.queue-item strong { font-size: var(--text-support); font-weight: 600; line-height: 1.6; overflow-wrap: anywhere; }
.queue-item small { overflow-wrap: anywhere; }
.queue-pagination { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); padding-top: var(--space-4); }
.queue-placeholder { font-size: var(--text-support); color: var(--muted); line-height: 1.8; padding: var(--space-3) 0; }
.candidates-area { min-width: 0; }
.candidates-heading { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: var(--space-4); margin-bottom: var(--space-4); }
.candidates-heading>div { min-width: 0; }
.candidates-heading h2 { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-3); font-size: var(--text-section); font-weight: 600; letter-spacing: -.025em; margin: var(--space-2) 0; }
.candidates-heading h2 span { font-size: var(--text-caption); font-weight: 400; color: var(--muted); }
.candidates-heading p:last-child, .comparison-caption, .candidate-disclaimer { color: var(--muted); font-size: var(--text-caption); line-height: 1.8; }
.comparison-caption { margin-bottom: var(--space-4); }
.candidate-list { display: grid; gap: var(--space-4); }
.candidate-disclaimer { margin-top: var(--space-5); }
.candidate-loading, .decision-empty { background: var(--surface); padding: var(--space-7); border: 1px solid var(--surface-line); border-radius: var(--radius); }
.decision-empty { display: grid; justify-items: start; gap: var(--space-4); }
.empty-symbol { display: grid; place-items: center; width: 48px; height: 48px; background: var(--accent-soft); border-radius: var(--radius-sm); font-size: 26px; color: var(--accent); }
.decision-empty h2 { font-size: var(--text-section); font-weight: 600; }
.decision-empty p, .candidate-loading p { font-size: var(--text-body); color: var(--muted); line-height: 1.8; }
.candidate-loading p { margin-top: var(--space-5); }
.loading-line { display: block; width: 65%; height: 24px; background: var(--surface-subtle); border-radius: var(--radius-sm); margin-bottom: var(--space-4); }
.loading-line.short { width: 40%; height: 14px; }
.dispatch-success { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-3); animation: context-enter var(--motion-enter) var(--ease-out); }
@keyframes context-enter { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media(max-width:1100px) { .decision-grid { grid-template-columns: minmax(220px,260px) minmax(0,1fr); gap: var(--space-5); } }
@media(max-width:860px) { .decision-grid { grid-template-columns: minmax(0,1fr); } .order-context { grid-template-columns: repeat(2,minmax(0,1fr)); align-items: start; } }
@media(max-width:620px) { .order-context { grid-template-columns: minmax(0,1fr); } .candidate-loading, .decision-empty { padding: var(--space-5); } .queue-items { max-height: 220px; } }
@media(prefers-reduced-motion:reduce) { .order-context, .dispatch-success { animation: none; } .queue-item { transition: none; } }
</style>
