<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getOrder,getOrders } from '../api/repair'
import { confirmDispatch,getRecommendations } from '../api/dispatch'
import { orderStatusLabel,type RepairOrder } from '../types/repair'
import type { WorkerRecommendation } from '../types/dispatch'
import { formatTime } from '../utils/format'
import WorkerRecommendCard from '../components/WorkerRecommendCard.vue'
import ProtectedImage from '../components/ProtectedImage.vue'
const route=useRoute()
const orders=ref<RepairOrder[]>([]),order=ref<RepairOrder|null>(null),recommendations=ref<WorkerRecommendation[]>([])
const previousRejection=ref('')
const page=ref(1),total=ref(0),queueLoading=ref(false),loading=ref(false),busy=ref(false),confirmingId=ref<number|null>(null)
const error=ref(''),queueError=ref(''),feedback=ref(''),expired=ref(false)
let revision=0,queueRevision=0,expiryTimer:ReturnType<typeof setTimeout>|undefined
const eligible=computed(()=>order.value?.status==='WAIT_ASSIGN'&&!order.value.workerId)
function clearRecommendations(){recommendations.value=[];expired.value=false;clearTimeout(expiryTimer)}
async function loadQueue(selectFirst=false) {
  const current=++queueRevision;queueLoading.value=true;queueError.value=''
  try {const result=await getOrders('admin',{status:'WAIT_ASSIGN',page:page.value,size:8});if(current!==queueRevision)return
    orders.value=result.records;total.value=result.total
    if(selectFirst){const query=Number(route.query.orderId);const id=Number.isSafeInteger(query)&&query>0?query:orders.value.find(o=>!o.workerId)?.id;if(id)await selectOrder(id)}
  } catch(e){if(current===queueRevision){orders.value=[];queueError.value=e instanceof Error?e.message:'订单列表加载失败'}}
  finally{if(current===queueRevision)queueLoading.value=false}
}
async function selectOrder(id:number) {
  if(busy.value)return
  const current=++revision;loading.value=true;error.value='';order.value=null;previousRejection.value='';clearRecommendations()
  try {const result=await getOrder(id);if(current!==revision)return;order.value=result.order
    const rejected=[...(result.dispatchHistory||[])].reverse().find(row=>row.decision==='REJECTED');previousRejection.value=rejected?`${rejected.workerName||'原维修员'} · ${rejected.rejectReason}`:''
    if(result.order.status==='WAIT_ASSIGN'&&!result.order.workerId){const rows=await getRecommendations(id);if(current!==revision)return;recommendations.value=rows;expiryTimer=setTimeout(()=>{expired.value=true},10*60*1000)}
  }catch(e){if(current===revision){clearRecommendations();error.value=e instanceof Error?e.message:'推荐加载失败'}}
  finally{if(current===revision)loading.value=false}
}
async function confirm(worker:WorkerRecommendation) {
  if(busy.value||loading.value||!eligible.value||expired.value||!order.value)return
  const id=order.value.id;busy.value=true;confirmingId.value=worker.workerId;error.value='';feedback.value=''
  try {await confirmDispatch(id,worker);clearRecommendations();feedback.value=`已确认派单给 ${worker.workerName}。等待维修人员响应任务。`
    try{order.value=(await getOrder(id)).order}catch{order.value=null;error.value='派单已成功，订单详情刷新失败，请重新加载。'}
    await loadQueue()
  }catch(e){clearRecommendations();error.value=e instanceof Error?e.message:'派单结果未确认，请刷新订单后重试'}
  finally{busy.value=false;confirmingId.value=null}
}
function changePage(value:number){if(busy.value)return;page.value=value;void loadQueue()}
onMounted(()=>loadQueue(true))
watch(()=>route.query.orderId,(value)=>{const id=Number(value);if(Number.isSafeInteger(id)&&id>0)void selectOrder(id)})
onBeforeUnmount(()=>{revision++;queueRevision++;clearTimeout(expiryTimer)})
</script>
<template><section class="business-page dispatch-page">
  <header class="page-heading"><div><p class="eyebrow">A BETTER MATCH</p><h1>智能派单</h1><p>根据真实技能、静态距离、当前负载与历史评价，找到合适的人。</p></div><RouterLink class="text-button" to="/admin/map">在地图中查看 →</RouterLink></header>
  <p v-if="feedback" class="notice success dispatch-feedback" role="status">{{ feedback }}</p>
  <div class="dispatch-grid"><aside class="dispatch-context">
    <section class="dispatch-queue detail-surface"><div class="section-heading"><h2>待派单订单</h2><button class="text-button" :disabled="queueLoading||busy" @click="loadQueue(!order)">刷新</button></div>
      <p v-if="queueError" class="notice error" role="alert">{{ queueError }}</p><p v-if="queueLoading&&!orders.length" class="muted" role="status">正在读取订单…</p>
      <p v-else-if="!orders.length&&!queueError" class="muted">目前没有待派单订单。</p>
      <button v-for="item in orders" :key="item.id" class="dispatch-queue-item" :class="{selected:order?.id===item.id}" :disabled="busy" @click="selectOrder(item.id)"><span>#{{ item.id }} · {{ item.typeName }}</span><strong>{{ item.title }}</strong><small>{{ item.buildingName }} · {{ item.roomNo }}<span v-if="item.workerId"> · 已人工指定</span></small></button>
      <div v-if="total>8" class="dispatch-pagination"><button class="text-button" :disabled="page<=1||queueLoading||busy" @click="changePage(page-1)">上一页</button><span>{{ page }} / {{ Math.ceil(total/8) }}</span><button class="text-button" :disabled="page*8>=total||queueLoading||busy" @click="changePage(page+1)">下一页</button></div>
    </section>
    <article v-if="order" class="detail-surface dispatch-order"><p class="eyebrow">ORDER · #{{ order.id }}</p><span class="status-pill" :class="`status-${order.status.toLowerCase()}`">{{ orderStatusLabel(order) }}</span><h2>{{ order.title }}</h2><p v-if="previousRejection" class="workflow-context">上次拒单：{{ previousRejection }}</p><p class="pre-wrap">{{ order.description }}</p><dl><dt>故障类型</dt><dd>{{ order.typeName }}</dd><dt>位置</dt><dd>{{ order.buildingName }} · {{ order.roomNo }}</dd><dt>提交时间</dt><dd>{{ formatTime(order.createTime) }}</dd></dl><ProtectedImage v-if="order.imageUrl" :url="order.imageUrl"/><RouterLink class="text-button" :to="`/admin/orders/${order.id}`">完整详情与人工派单 →</RouterLink></article>
  </aside><div class="recommendation-area">
    <div class="recommendation-heading"><div><h2>推荐维修人员</h2><p>技能 40% · 距离 30% · 负载 20% · 评价 10%</p></div><button v-if="order&&eligible" class="secondary-button" :disabled="loading||busy" @click="selectOrder(order.id)">重新计算</button></div>
    <div v-if="error" class="notice error" role="alert">{{ error }}<button v-if="order" class="text-button" :disabled="busy||loading" @click="selectOrder(order.id)">重新加载</button><button v-else class="text-button" :disabled="busy||loading" @click="loadQueue(true)">重试</button></div>
    <p v-if="expired" class="notice error" role="status">推荐已过期，请重新计算后派单。</p>
    <p v-if="loading" class="empty-state" role="status">正在读取当前任务与位置，计算推荐…</p>
    <template v-else-if="eligible&&recommendations.length"><WorkerRecommendCard v-for="(worker,index) in recommendations" :key="worker.recommendationId" :worker="worker" :index="index" :busy="busy" :confirming="confirmingId===worker.workerId" :disabled="expired" @confirm="confirm"/><p class="recommend-disclaimer">位置为静态服务坐标。没有坐标的距离项计 0；暂无评价的评价项计 50。推荐仅供管理员确认。</p></template>
    <div v-else-if="!error" class="empty-state"><span class="empty-orbit" aria-hidden="true">↗</span><h2>{{ !order?'从一份订单开始':!eligible?'订单已有处理安排':'暂无可用维修人员' }}</h2><p>{{ !order?'选择左侧待派单订单，查看评分与推荐理由。':!eligible?'原有人工指定需维修人员接单；智能确认后可先接受任务再开始维修。':'请检查维修人员账号及档案是否启用。' }}</p></div>
  </div></div>
</section></template>
