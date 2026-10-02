<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { createOrder, getCatalog } from '../api/repair'
import { priorityLabels, type Catalog, type RepairOrder } from '../types/repair'
import ImageUpload from '../components/ImageUpload.vue'
import FaultSelector from '../components/student/FaultSelector.vue'
import SubmitSummary from '../components/student/SubmitSummary.vue'
const steps=['选择故障类型','描述问题','选择地点','添加图片','确认提交']
const step=ref(0),catalog=ref<Catalog>({types:[],buildings:[]}),loading=ref(true),busy=ref(false),uploading=ref(false),error=ref(''),success=ref<RepairOrder|null>(null)
const form=reactive({typeId:0,title:'',description:'',buildingId:0,roomNo:'',priority:'NORMAL' as RepairOrder['priority'],imageUrl:''})
const requestKey=ref(crypto.randomUUID())
watch(form,()=>{requestKey.value=crypto.randomUUID()},{deep:true,flush:'sync'})
const selectedType=computed(()=>catalog.value.types.find(t=>t.id===form.typeId))
const selectedBuilding=computed(()=>catalog.value.buildings.find(b=>b.id===form.buildingId))
const stepHeading=ref<HTMLElement|null>(null)
watch([step,success],async()=>{await nextTick();stepHeading.value?.focus()})
async function load(){loading.value=true;error.value='';try{catalog.value=await getCatalog()}catch(e){error.value=e instanceof Error?e.message:'加载失败'}finally{loading.value=false}}
onMounted(load)
function next(){
  error.value=''
  if(step.value===0&&!form.typeId)error.value='请先选择一种故障类型'
  if(step.value===1&&(!form.title.trim()||!form.description.trim()))error.value='请填写问题标题和具体描述'
  if(step.value===2&&(!form.buildingId||!form.roomNo.trim()))error.value='请选择楼栋并填写房间或具体位置'
  if(!error.value)step.value++
}
async function submit(){
  if(busy.value||uploading.value)return
  busy.value=true;error.value=''
  try{success.value=await createOrder({...form,title:form.title.trim(),description:form.description.trim(),roomNo:form.roomNo.trim(),imageUrl:form.imageUrl||undefined,requestKey:requestKey.value})}
  catch(e){error.value=e instanceof Error?e.message:'提交失败，请重试'}finally{busy.value=false}
}
</script>
<template><section class="business-page create-page student-create">
  <header class="page-heading"><div><p class="eyebrow">校园服务 · 提交报修</p><h1>提交报修</h1><p>选择故障、说明问题和地点，确认后提交报修。</p></div><RouterLink class="text-button" to="/student/orders">我的工单 →</RouterLink></header>
  <div v-if="success" class="submission-success" role="status"><span class="success-symbol" aria-hidden="true">✓</span><h2 ref="stepHeading" tabindex="-1">报修已提交</h2><p>订单 #{{ success.id }} 已进入审核。<br />审核与维修进度会显示在工单中。</p><RouterLink class="primary-button" :to="`/student/orders/${success.id}`">查看维修进度</RouterLink></div>
  <template v-else><ol class="wizard-progress" aria-label="报修步骤"><li v-for="(label,index) in steps" :key="label" :class="{current:index===step,completed:index<step}" :aria-current="index===step?'step':undefined"><span>{{ index<step?'✓':index+1 }}</span><small>{{ label }}</small></li></ol>
    <form class="wizard-surface" :aria-busy="busy||uploading" :aria-describedby="error?'student-create-error':undefined" @submit.prevent="step===4?submit():next()">
      <div :key="step" class="wizard-step-heading"><p class="eyebrow">第 {{ step+1 }} 步 · 共 5 步</p><h2 ref="stepHeading" tabindex="-1">{{ steps[step] }}</h2><p>{{ ['选择最贴近问题的类型，方便后勤人员了解情况。','清楚描述现象，帮助维修人员更快定位问题。','告诉我们需要到哪里。','一张现场照片，会让问题更容易理解。也可以跳过。','检查以下信息，确认后交由管理员审核。'][step] }}</p></div>
      <p v-if="loading" role="status">正在加载校园信息…</p>
      <FaultSelector v-else-if="step===0" v-model="form.typeId" :types="catalog.types" />
      <div v-else-if="step===1" class="form-fields student-description-fields"><label>问题标题<input v-model="form.title" :aria-invalid="!!error&&!form.title.trim()" maxlength="120" placeholder="例如：宿舍顶灯无法点亮" required /><small>{{ form.title.length }} / 120</small></label><label>具体描述<textarea v-model="form.description" :aria-invalid="!!error&&!form.description.trim()" maxlength="2000" rows="5" placeholder="在哪里、发生了什么、从什么时候开始？" required></textarea><small>{{ form.description.length }} / 2000</small></label><fieldset class="priority-options"><legend>紧急程度</legend><label v-for="(label,value) in priorityLabels" :key="value" :class="{selected:form.priority===value}"><input v-model="form.priority" type="radio" name="priority" :value="value" />{{ label }}</label></fieldset></div>
      <div v-else-if="step===2" class="form-fields student-location-fields"><label>校园楼栋<select v-model="form.buildingId" required><option :value="0" disabled>请选择楼栋</option><option v-for="building in catalog.buildings" :key="building.id" :value="building.id">{{ building.name }}</option></select></label><label>房间 / 具体位置<input v-model="form.roomNo" maxlength="30" placeholder="例如：301室 / 三楼走廊" required /></label><div class="student-location-preview"><span class="field-hint">维修人员将前往</span><strong>{{ selectedBuilding?.name||'请选择校园楼栋' }}<span v-if="form.roomNo.trim()"> · {{ form.roomNo }}</span></strong><p class="field-hint">请填写能够准确找到故障的具体位置。</p></div></div>
      <div v-else-if="step===3" class="student-upload-step"><ImageUpload v-model="form.imageUrl" @pending="uploading=$event" /><p class="field-hint">最多一张现场图片。上传成功后可更换或移除，不添加图片也可以继续。</p><p v-if="uploading" class="student-upload-feedback" role="status">图片正在上传，请稍候再继续。</p></div>
      <SubmitSummary v-else :type-name="selectedType?.name||''" :title="form.title" :description="form.description" :location="`${selectedBuilding?.name||''} · ${form.roomNo}`" :priority="form.priority" :image-url="form.imageUrl" />
      <div v-if="error" id="student-create-error" class="notice error" role="alert">{{ error }} <button v-if="!catalog.types.length" class="text-button" type="button" @click="load">重新加载</button></div>
      <div class="wizard-actions"><button v-if="step>0" class="secondary-button" type="button" :disabled="busy||uploading" @click="step--;error=''">上一步</button><span v-else class="field-hint">简单五步，轻松提交。</span><button class="primary-button" type="submit" :disabled="loading||busy||uploading||!catalog.types.length">{{ busy?'正在提交…':step===4?'提交报修':step===3&&!form.imageUrl?'跳过，下一步':'下一步' }} <span aria-hidden="true">→</span></button></div>
    </form>
  </template>
</section></template>
