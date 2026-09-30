<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { actOnOrder, addRepairRecord, evaluateOrder, getOrder, getWorkers } from '../api/repair'
import { statusLabels, type OrderDetail, type WorkerOption } from '../types/repair'
import type { Role } from '../types'
import { formatTime } from '../utils/format'
import OrderTimeline from '../components/OrderTimeline.vue'
import ProtectedImage from '../components/ProtectedImage.vue'
import ImageUpload from '../components/ImageUpload.vue'
const props=defineProps<{role:Role}>()
const route=useRoute(),id=Number(route.params.id)
const detail=ref<OrderDetail|null>(null),workers=ref<WorkerOption[]>([]),loading=ref(true),busy=ref(false),uploading=ref(false),error=ref(''),feedback=ref('')
const workerId=ref(0),content=ref(''),imageUrl=ref(''),score=ref(5),comment=ref('')
const order=computed(()=>detail.value?.order)
async function load(){
  loading.value=true;error.value=''
  try{detail.value=await getOrder(id);if(props.role==='admin')workers.value=await getWorkers();return true}
  catch(e){detail.value=null;error.value=e instanceof Error?e.message:'加载失败';return false}
  finally{loading.value=false}
}
onMounted(load)
async function perform(action:()=>Promise<void>,message:string){
  if(busy.value||uploading.value)return
  busy.value=true;error.value='';feedback.value=''
  try{await action();content.value='';imageUrl.value='';const refreshed=await load();feedback.value=refreshed?message:`${message} 详情刷新失败，请重试。`}
  catch(e){error.value=e instanceof Error?e.message:'操作失败，请重试'}finally{busy.value=false}
}
function action(name:'audit'|'assign'|'accept'|'start'|'finish'|'confirm'){
  if(name==='assign'&&!workerId.value){error.value='请先选择维修人员';return}
  void perform(()=>actOnOrder(props.role,id,name,workerId.value),{audit:'审核已通过，请指定维修人员。',assign:'已指定维修人员，等待接单。',accept:'接单成功。',start:'维修已开始。',finish:'维修结果已提交，等待学生确认。',confirm:'已确认维修完成，欢迎留下评价。'}[name])
}
function saveRecord(){if(!content.value.trim()){error.value='请填写维修结果';return}void perform(()=>addRepairRecord(id,content.value.trim(),imageUrl.value||undefined),'维修记录已保存，可继续补充或完成维修。')}
function evaluate(){void perform(()=>evaluateOrder(id,score.value,comment.value.trim()),'评价已提交，谢谢你的反馈。')}
</script>
<template><section class="business-page detail-page">
  <header class="page-heading"><div><p class="eyebrow">REPAIR JOURNEY · #{{ id }}</p><h1>订单详情</h1><p>让每一步处理，都有清晰的回应。</p></div><RouterLink class="text-button" :to="`/${role}/orders`">← 返回{{ role==='worker'?'任务':'订单' }}列表</RouterLink></header>
  <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" :disabled="busy" @click="load">刷新</button></div><p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
  <p v-if="loading&&!detail" class="empty-state" role="status">正在加载订单详情…</p>
  <div v-if="detail&&order" class="detail-grid">
    <div class="detail-main"><article class="detail-surface"><div class="order-card-top"><span class="order-kind">{{ order.typeName }}</span><span class="status-pill" :class="`status-${order.status.toLowerCase()}`">{{ statusLabels[order.status] }}</span></div><h2 class="detail-title">{{ order.title }}</h2><p class="detail-description pre-wrap">{{ order.description }}</p><RouterLink v-if="order.workerId" class="secondary-button chat-entry" :to="`/${role}/orders/${id}/messages`">维修沟通 →</RouterLink>
      <dl class="order-facts"><div><dt>报修地点</dt><dd>{{ order.buildingName }} · {{ order.roomNo }}</dd></div><div><dt>报修学生</dt><dd>{{ order.studentName }}</dd></div><div><dt>维修人员</dt><dd>{{ order.workerName||'等待安排' }}</dd></div><div><dt>紧急程度</dt><dd>{{ {LOW:'不紧急',NORMAL:'普通',HIGH:'较紧急'}[order.priority] }}</dd></div><div><dt>提交时间</dt><dd>{{ formatTime(order.createTime) }}</dd></div></dl><ProtectedImage v-if="order.imageUrl" :url="order.imageUrl" />
    </article>
    <section v-if="detail.records.length" class="detail-surface"><h2>维修记录</h2><article v-for="record in detail.records" :key="record.id" class="repair-record"><p class="pre-wrap">{{ record.content }}</p><ProtectedImage v-if="record.imageUrl" :url="record.imageUrl" alt="维修结果图片" /><p class="record-time">开始：{{ formatTime(record.startTime) }} · 完成：{{ record.finishTime?formatTime(record.finishTime):'维修中' }}</p></article></section>
    <section v-if="detail.evaluation" class="detail-surface evaluation-result"><h2>学生评价</h2><p class="rating-display" :aria-label="`${detail.evaluation.score}分，满分5分`">{{ '★'.repeat(detail.evaluation.score) }}<span>{{ '☆'.repeat(5-detail.evaluation.score) }}</span></p><p class="pre-wrap">{{ detail.evaluation.content||'学生已评分' }}</p><small>{{ formatTime(detail.evaluation.createTime) }}</small></section>
    <section v-if="role==='admin'&&['WAIT_AUDIT','WAIT_ASSIGN'].includes(order.status)" class="detail-surface action-surface"><h2>{{ order.status==='WAIT_AUDIT'?'审核报修':'指定维修人员' }}</h2><p>{{ order.status==='WAIT_AUDIT'?'确认问题描述和地点完整后，通过审核。':'根据故障情况人工选择维修人员，等待对方接单。' }}</p><button v-if="order.status==='WAIT_AUDIT'" class="primary-button" :disabled="busy||loading" @click="action('audit')">{{ busy?'处理中…':'通过审核' }}</button><template v-else-if="!order.workerId"><RouterLink class="text-button" :to="`/admin/dispatch?orderId=${order.id}`">查看智能推荐 →</RouterLink><label class="form-field">维修人员<select v-model="workerId"><option :value="0">请选择维修人员</option><option v-for="worker in workers" :key="worker.id" :value="worker.id">{{ worker.name }} · {{ worker.skillType }}</option></select></label><button class="primary-button" :disabled="busy||loading||!workerId" @click="action('assign')">确认指定</button><p v-if="!workers.length" class="field-hint">暂无可用维修人员，请联系账号维护人员。</p></template><p v-else class="field-hint">已指定 {{ order.workerName }}，等待接单。</p></section>
    <section v-if="role==='worker'&&['WAIT_ASSIGN','ASSIGNED','PROCESSING'].includes(order.status)" class="detail-surface action-surface"><h2>{{ order.status==='PROCESSING'?'记录维修结果':'处理任务' }}</h2><button v-if="order.status==='WAIT_ASSIGN'" class="primary-button" :disabled="busy||loading" @click="action('accept')">接受任务</button><button v-else-if="order.status==='ASSIGNED'" class="primary-button" :disabled="busy||loading" @click="action('start')">开始维修</button><template v-else><form class="form-fields" @submit.prevent="saveRecord"><label>维修结果<textarea v-model="content" rows="4" required maxlength="2000" placeholder="记录处理过程、维修结果及注意事项"></textarea></label><ImageUpload v-model="imageUrl" @pending="uploading=$event" /><button class="secondary-button" type="submit" :disabled="busy||loading||uploading">保存维修记录</button></form><div class="finish-action"><p>{{ detail.records.length?'维修记录已保存，确认处理完成后提交结果。':'请先保存一条维修记录，再完成维修。' }}</p><button class="primary-button" :disabled="busy||loading||uploading||!detail.records.length||!!content.trim()||!!imageUrl" @click="action('finish')">完成维修</button><small v-if="content.trim()||imageUrl" class="field-hint">先保存当前填写的记录，再完成维修。</small></div></template></section>
    <section v-if="role==='student'&&order.status==='WAIT_CONFIRM'" class="detail-surface action-surface"><h2>确认维修完成</h2><p>查看维修结果，确认故障已解决后完成本次报修。</p><button class="primary-button" :disabled="busy||loading" @click="action('confirm')">确认完成</button></section>
    <section v-if="role==='student'&&order.status==='FINISHED'" class="detail-surface"><h2>这次维修体验如何？</h2><form class="form-fields" @submit.prevent="evaluate"><fieldset class="rating-input"><legend>满意度</legend><label v-for="value in 5" :key="value" :class="{selected:score>=value}"><input v-model="score" type="radio" name="score" :value="value" :aria-label="`${value}分`" />★</label><span>{{ score }} / 5</span></fieldset><label>评价内容<textarea v-model="comment" maxlength="1000" rows="3" placeholder="分享你的维修体验（可选）"></textarea></label><button class="primary-button" type="submit" :disabled="busy||loading">提交评价</button></form></section>
    </div><aside class="timeline-surface"><p class="eyebrow">EVERY STEP MATTERS</p><h2>处理进度</h2><OrderTimeline :events="detail.timeline" /></aside>
  </div>
</section></template>
