<script setup lang="ts">
import { computed,reactive,ref,onBeforeUnmount } from 'vue'
import { getCatalog,workflowAction } from '../api/repair'
import type { Catalog,CreateOrder,OrderDetail } from '../types/repair'
import type { Role } from '../types'
import { formatTime } from '../utils/format'
import ImageUpload from './ImageUpload.vue'
const props=defineProps<{detail:OrderDetail;role:Role;disabled:boolean}>()
const emit=defineEmits<{pending:[value:boolean];changed:[]}>()
const order=computed(()=>props.detail.order),busy=ref(false),uploading=ref(false),error=ref(''),feedback=ref(''),opened=ref(''),reason=ref('')
const catalog=ref<Catalog>({types:[],buildings:[]})
const edit=reactive<CreateOrder>({typeId:0,title:'',description:'',buildingId:0,roomNo:'',priority:'NORMAL',imageUrl:''})
const start=ref(''),end=ref('')
let alive=true
onBeforeUnmount(()=>{alive=false})
const locked=computed(()=>props.disabled||busy.value||uploading.value)
const latestReason=computed(()=>[...props.detail.timeline].reverse().find(e=>['AUDIT_REJECT','WORKER_REJECT','ACCEPTANCE_FAIL'].includes(e.action)))
const relevant=computed(()=>['REJECTED','REWORK_PENDING'].includes(order.value.status)||order.value.appointmentStatus!=='NONE'||
  (props.role==='admin'&&order.value.status==='WAIT_AUDIT')||(props.role==='student'&&order.value.status==='WAIT_CONFIRM')||
  (props.role==='worker'&&['WAIT_ASSIGN','ASSIGNED','PROCESSING'].includes(order.value.status)))
function pendingUpload(value:boolean){uploading.value=value;emit('pending',value||busy.value)}
async function open(name:string){if(locked.value)return;opened.value=opened.value===name?'':name;reason.value='';error.value=''
  if(name==='edit'){
    Object.assign(edit,{typeId:order.value.typeId,title:order.value.title,description:order.value.description,buildingId:order.value.buildingId,roomNo:order.value.roomNo,priority:order.value.priority,imageUrl:order.value.imageUrl||''})
    busy.value=true;emit('pending',true)
    try{const result=await getCatalog();if(alive)catalog.value=result}catch(e){if(alive)error.value=e instanceof Error?e.message:'选项加载失败'}finally{busy.value=false;emit('pending',uploading.value)}
  }
}
async function perform(action:string,input:unknown){if(locked.value)return;busy.value=true;emit('pending',true);error.value='';feedback.value=''
  try{await workflowAction(props.role,order.value.id,action,input);if(alive){opened.value='';feedback.value='操作已保存';emit('changed')}}
  catch(e){if(alive)error.value=e instanceof Error?e.message:'操作失败，请刷新后重试'}finally{busy.value=false;if(alive)emit('pending',uploading.value)}
}
function submitReason(action:string){if(!reason.value.trim()){error.value='请填写明确原因';return}void perform(action,{reason:reason.value.trim()})}
function propose(){if(!start.value||!end.value){error.value='请选择完整预约时间';return}void perform('appointment',{start:start.value+':00',end:end.value+':00',version:order.value.appointmentVersion})}
</script>
<template><section v-if="relevant" class="detail-surface workflow-panel"><p class="eyebrow">SERVICE COORDINATION</p><h2>{{ order.status==='REJECTED'?'补充信息后继续':order.status==='REWORK_PENDING'?'问题仍待解决':'协同与反馈' }}</h2>
  <div v-if="latestReason&&['REJECTED','REWORK_PENDING','WAIT_ASSIGN'].includes(order.status)" class="workflow-context"><p class="pre-wrap">{{ latestReason.content }}</p><small>{{ formatTime(latestReason.createTime) }}</small></div>
  <p v-if="order.status==='REWORK_PENDING'" class="field-hint">管理员将安排原维修人员返工或重新派单，既有维修记录会保留。</p>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
  <div class="workflow-actions">
    <button v-if="role==='admin'&&order.status==='WAIT_AUDIT'" class="text-button" :disabled="locked" @click="open('reject')">需要补充信息 · 驳回</button>
    <button v-if="role==='student'&&order.status==='REJECTED'" class="primary-button" :disabled="locked" @click="open('edit')">修改并重新提交</button>
    <button v-if="role==='worker'&&!order.acceptedTime&&['WAIT_ASSIGN','ASSIGNED'].includes(order.status)" class="text-button" :disabled="locked" @click="open('reject')">无法接受任务</button>
    <button v-if="role==='student'&&order.status==='WAIT_CONFIRM'" class="text-button" :disabled="locked" @click="open('acceptance-fail')">仍有问题 · 申请返工</button>
    <button v-if="role==='admin'&&order.status==='REWORK_PENDING'" class="secondary-button" :disabled="locked" @click="open('rework')">安排下一轮维修</button>
    <button v-if="role==='worker'&&order.acceptedTime&&['ASSIGNED','PROCESSING'].includes(order.status)" class="secondary-button" :disabled="locked" @click="open('appointment')">{{ order.appointmentStatus==='NONE'?'提出上门时间':'调整预约时间' }}</button>
  </div>
  <form v-if="['reject','acceptance-fail','appointment-reject'].includes(opened)" class="form-fields workflow-expanded" @submit.prevent="opened==='appointment-reject'?perform('appointment',{version:order.appointmentVersion,accepted:false,reason:reason.trim()}):submitReason(opened)"><label>原因<textarea v-model="reason" maxlength="500" rows="3" required placeholder="说明具体情况，帮助下一步处理" :disabled="locked" /></label><button class="secondary-button" type="submit" :disabled="locked||!reason.trim()">{{ busy?'保存中…':'确认提交原因' }}</button></form>
  <div v-if="opened==='rework'" class="workflow-expanded"><p class="field-hint">原维修人员继续处理无需再次接单；重新派单将释放当前负责人。</p><div class="workflow-actions"><button class="primary-button" :disabled="locked" @click="perform('rework',{mode:'ORIGINAL'})">原维修员继续</button><button class="secondary-button" :disabled="locked" @click="perform('rework',{mode:'REDISPATCH'})">重新派单</button></div></div>
  <form v-if="opened==='edit'" class="form-fields workflow-expanded" @submit.prevent="perform('resubmit',{...edit,imageUrl:edit.imageUrl||null})"><label>标题<input v-model="edit.title" required maxlength="120" :disabled="locked" /></label><label>故障类型<select v-model="edit.typeId" :disabled="locked"><option v-for="type in catalog.types" :key="type.id" :value="type.id">{{ type.name }}</option></select></label><label>问题描述<textarea v-model="edit.description" required maxlength="2000" rows="4" :disabled="locked" /></label><div class="workflow-columns"><label>楼栋<select v-model="edit.buildingId" :disabled="locked"><option v-for="building in catalog.buildings" :key="building.id" :value="building.id">{{ building.name }}</option></select></label><label>房间<input v-model="edit.roomNo" required maxlength="30" :disabled="locked" /></label></div><label>优先级<select v-model="edit.priority" :disabled="locked"><option value="LOW">不紧急</option><option value="NORMAL">普通</option><option value="HIGH">较紧急</option></select></label><ImageUpload :disabled="locked" :model-value="edit.imageUrl||''" @update:model-value="edit.imageUrl=$event" @pending="pendingUpload"/><button class="primary-button" type="submit" :disabled="locked||!catalog.types.length">{{ busy?'提交中…':'保存修改并重新提交' }}</button></form>
  <div v-if="order.appointmentStatus!=='NONE'" class="appointment-summary"><h3>预计上门时间</h3><p>{{ formatTime(order.appointmentStart||'') }} — {{ formatTime(order.appointmentEnd||'') }}</p><p class="field-hint">{{ {NONE:'尚未预约',PROPOSED:'等待学生确认',ACCEPTED:'学生已确认',REJECTED:'学生希望调整时间'}[order.appointmentStatus] }}</p><p v-if="order.appointmentReason" class="pre-wrap">{{ order.appointmentReason }}</p><div v-if="role==='student'&&order.appointmentStatus==='PROPOSED'&&['ASSIGNED','PROCESSING'].includes(order.status)" class="workflow-actions"><button class="secondary-button" :disabled="locked" @click="perform('appointment',{version:order.appointmentVersion,accepted:true})">确认预约</button><button class="text-button" :disabled="locked" @click="open('appointment-reject')">协商其他时间</button></div></div>
  <form v-if="opened==='appointment'" class="form-fields workflow-expanded" @submit.prevent="propose"><p class="field-hint">使用校园时间（北京时间），预约用于协商，不阻塞维修。</p><div class="workflow-columns"><label>开始时间<input v-model="start" type="datetime-local" required :disabled="locked" /></label><label>结束时间<input v-model="end" type="datetime-local" required :disabled="locked" /></label></div><button class="primary-button" type="submit" :disabled="locked">发送预约</button></form>
</section></template>
