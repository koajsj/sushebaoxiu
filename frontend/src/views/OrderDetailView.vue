<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { actOnOrder, addRepairRecord, evaluateOrder, getOrder, getWorkers } from '../api/repair'
import { orderStatusLabel, type OrderDetail, type WorkerOption } from '../types/repair'
import type { Role } from '../types'
import { formatTime } from '../utils/format'
import OrderWorkflowPanel from '../components/OrderWorkflowPanel.vue'
import OrderTimeline from '../components/OrderTimeline.vue'
import ProtectedImage from '../components/ProtectedImage.vue'
import ImageUpload from '../components/ImageUpload.vue'
const props=defineProps<{role:Role}>()
const route=useRoute(),id=computed(()=>Number(route.params.id))
const detail=ref<OrderDetail|null>(null),workers=ref<WorkerOption[]>([]),loading=ref(true),busy=ref(false),uploading=ref(false),error=ref(''),feedback=ref('')
const workflowBusy=ref(false)
const working=computed(()=>busy.value||workflowBusy.value)
const currentRecords=computed(()=>detail.value?.records.filter(r=>(r.roundNo||1)===(order.value?.repairRound||1))||[])
const workerId=ref(0),content=ref(''),imageUrl=ref(''),score=ref(5),comment=ref('')
const recordKey=ref(crypto.randomUUID())
let draftVersion=0
watch([content,imageUrl],()=>{draftVersion++;recordKey.value=crypto.randomUUID()},{flush:'sync'})
const workersError=ref('')
let revision=0
const order=computed(()=>detail.value?.order)
const workerNames=computed(()=>new Map((detail.value?.dispatchHistory||[]).map(row=>[row.workerId,row.workerName])))
const nextStep=computed(()=>({WAIT_AUDIT:'等待管理员审核',WAIT_DISPATCH:'等待管理员派单',WAIT_ACCEPT:'等待维修人员接单',WAIT_START:'等待维修人员开始维修',PROCESSING:'维修进行中',WAIT_CONFIRM:'等待学生验收',REWORK_PENDING:'等待管理员安排返工',REJECTED:'请学生修改后重新提交',FINISHED:'可以提交本次评价',COMMENTED:'本单已结束',CREATED:'尚未提交'}[order.value?.phase||'CREATED']))
async function load(){
  const current=++revision;loading.value=true;error.value='';workersError.value=''
  try{
    const result=await getOrder(id.value);if(current!==revision)return false;detail.value=result;workers.value=[]
    if(props.role==='admin'&&result.order.status==='WAIT_ASSIGN'&&!result.order.workerId){
      try{const candidates=await getWorkers();if(current===revision)workers.value=candidates}
      catch(e){if(current===revision)workersError.value=e instanceof Error?e.message:'维修人员加载失败'}
    }
    return current===revision
  }
  catch(e){if(current===revision){detail.value=null;error.value=e instanceof Error?e.message:'加载失败'}return false}
  finally{if(current===revision)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{revision++})
watch(id,()=>{revision++;detail.value=null;workers.value=[];workerId.value=0;content.value='';imageUrl.value='';comment.value='';score.value=5;busy.value=false;workflowBusy.value=false;error.value='';feedback.value='';void load()})
async function perform(action:()=>Promise<void>,message:string){
  if(working.value||loading.value||uploading.value)return
  const contextId=id.value
  busy.value=true;error.value='';feedback.value=''
  try{await action();if(contextId!==id.value)return;const refreshed=await load();if(contextId===id.value)feedback.value=refreshed?message:`${message} 详情刷新失败，请重试。`}
  catch(e){if(contextId===id.value)error.value=e instanceof Error?e.message:'操作失败，请重试'}finally{if(contextId===id.value)busy.value=false}
}
function action(name:'audit'|'assign'|'accept'|'start'|'finish'|'confirm'){
  if(name==='assign'&&!workerId.value){error.value='请先选择维修人员';return}
  if(name==='finish'&&!window.confirm('确认维修记录已完整，提交后将进入学生验收？'))return
  void perform(()=>actOnOrder(props.role,id.value,name,workerId.value),{audit:'审核已通过，请指定维修人员。',assign:'已指定维修人员，等待接单。',accept:'接单成功。',start:'维修已开始。',finish:'维修结果已提交，等待学生确认。',confirm:'已确认维修完成，欢迎留下评价。'}[name])
}
async function saveRecord(){if(!content.value.trim()||working.value||loading.value||uploading.value){if(!content.value.trim())error.value='请填写维修结果';return}
  const snapshot={content:content.value.trim(),imageUrl:imageUrl.value,key:recordKey.value,version:draftVersion}
  const contextId=id.value
  busy.value=true;error.value='';feedback.value=''
  try{await addRepairRecord(contextId,snapshot.content,snapshot.key,snapshot.imageUrl||undefined)
    if(contextId!==id.value)return
    if(snapshot.version===draftVersion&&content.value.trim()===snapshot.content&&imageUrl.value===snapshot.imageUrl){content.value='';imageUrl.value=''}
    const refreshed=await load();if(contextId===id.value)feedback.value=refreshed?'维修记录已保存，可继续补充或完成维修。':'维修记录已保存，详情刷新失败，请重试。'
  }catch(e){if(contextId===id.value)error.value=e instanceof Error?e.message:'保存失败，输入内容已保留'}finally{if(contextId===id.value)busy.value=false}
}
function evaluate(){void perform(()=>evaluateOrder(id.value,score.value,comment.value.trim()),'评价已提交，谢谢你的反馈。')}
</script>
<template><section class="business-page detail-page">
  <header class="page-heading"><div><p class="eyebrow">工单 #{{ id }} · 处理详情</p><h1>订单详情</h1><p>让每一步处理，都有清晰的回应。</p></div><RouterLink class="text-button" :to="`/${role}/orders`">← 返回{{ role==='worker'?'任务':'订单' }}列表</RouterLink></header>
  <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" :disabled="working||loading" @click="load">刷新</button></div><p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
  <p v-if="loading&&!detail" class="empty-state" role="status">正在加载订单详情…</p>
  <div v-if="detail&&order" class="detail-grid">
    <div class="detail-main"><article class="detail-surface"><div class="order-card-top"><span class="order-kind">{{ order.typeName }}</span><span class="status-pill" :class="`status-${order.status.toLowerCase()}`">{{ orderStatusLabel(order) }}</span></div><h2 class="detail-title">{{ order.title }}</h2><p class="detail-description pre-wrap">{{ order.description }}</p><RouterLink v-if="order.workerId||role!=='worker'" class="secondary-button chat-entry" :to="`/${role}/orders/${id}/messages`">维修沟通 →</RouterLink>
      <p class="detail-next-step">当前：{{ orderStatusLabel(order) }} · {{ nextStep }}</p><p class="service-flags"><span v-if="order.repairRound>1">返工 · 第 {{ order.repairRound }} 次维修</span><span v-if="order.overdueType">{{ {RESPONSE:'接单超时',START:'待开工超时',REPAIR:'维修超时'}[order.overdueType] }}</span></p><dl class="order-facts"><div><dt>报修地点</dt><dd>{{ order.buildingName }} · {{ order.roomNo }}</dd></div><div><dt>报修学生</dt><dd>{{ order.studentName }}</dd></div><div><dt>维修人员</dt><dd>{{ order.workerName||'等待安排' }}</dd></div><div><dt>紧急程度</dt><dd>{{ {LOW:'不紧急',NORMAL:'普通',HIGH:'较紧急'}[order.priority] }}</dd></div><div><dt>提交时间</dt><dd>{{ formatTime(order.createTime) }}</dd></div></dl><ProtectedImage v-if="order.imageUrl" :url="order.imageUrl" />
    </article>
    <OrderWorkflowPanel :detail="detail" :role="role" :disabled="busy||loading||uploading" @pending="workflowBusy=$event" @changed="load"/><section v-if="detail.dispatchHistory?.length" class="detail-surface"><details><summary>派单历史 · {{ detail.dispatchHistory.length }} 轮</summary><article v-for="row in detail.dispatchHistory" :key="row.id" class="repair-record"><h3>第 {{ row.roundNo }} 轮 · {{ row.workerName||'维修人员' }}</h3><p>{{ row.method==='SMART'?'智能推荐':'人工指定' }} · {{ {ASSIGNED:'等待响应',ACCEPTED:'已接受',REJECTED:'已拒单',RECALLED:'管理员收回',REWORK:'进入返工'}[row.decision]||row.decision }}<span v-if="row.totalScore!==null"> · {{ row.totalScore }} 分</span></p><p class="pre-wrap">{{ row.reason }}</p><p v-if="row.rejectReason" class="pre-wrap">拒单原因：{{ row.rejectReason }}</p><small>{{ formatTime(row.createTime) }}</small></article></details></section><section v-if="detail.records.length" class="detail-surface"><h2>维修记录</h2><article v-for="record in detail.records" :key="record.id" class="repair-record"><h3>第 {{ record.roundNo||1 }} 次维修 · {{ workerNames.get(record.workerId)||`维修员 #${record.workerId}` }}</h3><p class="pre-wrap">{{ record.content }}</p><ProtectedImage v-if="record.imageUrl" :url="record.imageUrl" alt="维修结果图片" /><p class="record-time">开始：{{ formatTime(record.startTime) }} · 完成：{{ record.finishTime?formatTime(record.finishTime):'维修中' }}</p></article></section>
    <section v-if="detail.evaluation" class="detail-surface evaluation-result"><h2>学生评价</h2><p class="rating-display" :aria-label="`${detail.evaluation.score}分，满分5分`">{{ '★'.repeat(detail.evaluation.score) }}<span>{{ '☆'.repeat(5-detail.evaluation.score) }}</span></p><p class="pre-wrap">{{ detail.evaluation.content||'学生已评分' }}</p><small>{{ formatTime(detail.evaluation.createTime) }}</small></section>
    <section v-if="role==='admin'&&['WAIT_AUDIT','WAIT_ASSIGN'].includes(order.status)" class="detail-surface action-surface"><h2>{{ order.status==='WAIT_AUDIT'?'审核报修':'指定维修人员' }}</h2><p>{{ order.status==='WAIT_AUDIT'?'确认问题描述和地点完整后，通过审核。':'根据故障情况人工选择维修人员，等待对方接单。' }}</p><button v-if="order.status==='WAIT_AUDIT'" class="primary-button" :disabled="working||loading" @click="action('audit')">{{ busy?'处理中…':'通过审核' }}</button><template v-else-if="!order.workerId"><RouterLink class="text-button" :to="`/admin/dispatch?orderId=${order.id}`">查看智能推荐 →</RouterLink><label class="form-field">维修人员<select v-model="workerId"><option :value="0">请选择维修人员</option><option v-for="worker in workers" :key="worker.id" :value="worker.id">{{ worker.name }} · {{ worker.skillType }}</option></select></label><button class="primary-button" :disabled="working||loading||!workerId" @click="action('assign')">确认指定</button><p v-if="workersError" class="notice error" role="alert">{{ workersError }} <button class="text-button" :disabled="working||loading" @click="load">重新加载人员</button></p><p v-else-if="!workers.length" class="field-hint">暂无可用维修人员，请联系账号维护人员。</p></template><p v-else class="field-hint">已指定 {{ order.workerName }}，等待接单。</p></section>
    <section v-if="role==='worker'&&['WAIT_ASSIGN','ASSIGNED','PROCESSING'].includes(order.status)" class="detail-surface action-surface"><h2>{{ order.status==='PROCESSING'?'记录维修结果':'处理任务' }}</h2><button v-if="!order.acceptedTime&&['WAIT_ASSIGN','ASSIGNED'].includes(order.status)" class="primary-button" :disabled="working||loading" @click="action('accept')">接受任务</button><button v-else-if="order.status==='ASSIGNED'" class="primary-button" :disabled="working||loading" @click="action('start')">开始维修</button><template v-else><form class="form-fields" @submit.prevent="saveRecord"><label>维修结果<textarea v-model="content" rows="4" required maxlength="2000" placeholder="记录处理过程、维修结果及注意事项"></textarea></label><ImageUpload :disabled="working||loading" v-model="imageUrl" @pending="uploading=$event" /><button class="secondary-button" type="submit" :disabled="working||loading||uploading">保存维修记录</button></form><div class="finish-action"><p>{{ currentRecords.length?'本轮维修记录已保存，确认处理完成后提交结果。':'请先保存一条维修记录，再完成维修。' }}</p><button class="primary-button" :disabled="working||loading||uploading||!currentRecords.length||!!content.trim()||!!imageUrl" @click="action('finish')">完成维修</button><small v-if="content.trim()||imageUrl" class="field-hint">先保存当前填写的记录，再完成维修。</small></div></template></section>
    <section v-if="role==='student'&&order.status==='WAIT_CONFIRM'" class="detail-surface action-surface"><h2>确认维修完成</h2><p>查看维修结果，确认故障已解决后完成本次报修。</p><button class="primary-button" :disabled="working||loading" @click="action('confirm')">已解决 · 确认完成</button></section>
    <section v-if="role==='student'&&order.status==='FINISHED'" class="detail-surface"><h2>这次维修体验如何？</h2><form class="form-fields" @submit.prevent="evaluate"><fieldset class="rating-input"><legend>满意度</legend><label v-for="value in 5" :key="value" :class="{selected:score>=value}"><input v-model="score" type="radio" name="score" :value="value" :aria-label="`${value}分`" />★</label><span>{{ score }} / 5</span></fieldset><label>评价内容<textarea v-model="comment" maxlength="1000" rows="3" placeholder="分享你的维修体验（可选）"></textarea></label><button class="primary-button" type="submit" :disabled="working||loading">提交评价</button></form></section>
    </div><aside class="timeline-surface"><p class="eyebrow">真实处理记录</p><h2>处理进度</h2><OrderTimeline :events="detail.timeline" :status="order.status" /></aside>
  </div>
</section></template>
