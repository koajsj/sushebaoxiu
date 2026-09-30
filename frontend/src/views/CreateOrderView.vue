<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { createOrder, getCatalog } from '../api/repair'
import type { Catalog, RepairOrder } from '../types/repair'
import ImageUpload from '../components/ImageUpload.vue'
const steps=['选择故障类型','描述问题','选择地点','添加图片','确认提交']
const step=ref(0),catalog=ref<Catalog>({types:[],buildings:[]}),loading=ref(true),busy=ref(false),uploading=ref(false),error=ref(''),success=ref<RepairOrder|null>(null)
const form=reactive({typeId:0,title:'',description:'',buildingId:0,roomNo:'',priority:'NORMAL' as RepairOrder['priority'],imageUrl:''})
const selectedType=computed(()=>catalog.value.types.find(t=>t.id===form.typeId))
const selectedBuilding=computed(()=>catalog.value.buildings.find(b=>b.id===form.buildingId))
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
  try{success.value=await createOrder({...form,title:form.title.trim(),description:form.description.trim(),roomNo:form.roomNo.trim(),imageUrl:form.imageUrl||undefined})}
  catch(e){error.value=e instanceof Error?e.message:'提交失败，请重试'}finally{busy.value=false}
}
</script>
<template><section class="business-page create-page">
  <header class="page-heading"><div><p class="eyebrow">A LITTLE CARE, A BETTER CAMPUS</p><h1>提交报修</h1><p>把问题交给我们，把时间留给校园生活。</p></div><RouterLink class="text-button" to="/student/orders">我的报修 →</RouterLink></header>
  <div v-if="success" class="submission-success" role="status"><span class="success-symbol" aria-hidden="true">✓</span><h2>报修已提交</h2><p>订单 #{{ success.id }} 已进入审核。<br />你可以随时查看处理进度。</p><RouterLink class="primary-button" :to="`/student/orders/${success.id}`">查看订单进度</RouterLink></div>
  <template v-else><ol class="wizard-progress" aria-label="报修步骤"><li v-for="(label,index) in steps" :key="label" :class="{current:index===step,completed:index<step}" :aria-current="index===step?'step':undefined"><span>{{ index<step?'✓':index+1 }}</span><small>{{ label }}</small></li></ol>
    <form class="wizard-surface" @submit.prevent="step===4?submit():next()">
      <div class="wizard-step-heading"><p class="eyebrow">STEP {{ String(step+1).padStart(2,'0') }} / 05</p><h2>{{ steps[step] }}</h2><p>{{ ['选择最贴近问题的类型，方便后勤人员了解情况。','清楚描述现象，帮助维修人员更快定位问题。','告诉我们需要到哪里。','一张现场照片，会让问题更容易理解。也可以跳过。','检查以下信息，确认后交由管理员审核。'][step] }}</p></div>
      <p v-if="loading" role="status">正在加载校园信息…</p>
      <div v-else-if="step===0" class="type-grid"><button v-for="(type,index) in catalog.types" :key="type.id" type="button" class="type-choice" :class="{selected:form.typeId===type.id}" :aria-pressed="form.typeId===type.id" @click="form.typeId=type.id"><span class="type-icon" aria-hidden="true">{{ ['☀','◌','⌑','⌘','＋'][index%5] }}</span><strong>{{ type.name }}</strong><small>{{ type.description }}</small><span class="choice-check" aria-hidden="true">{{ form.typeId===type.id?'✓':'○' }}</span></button></div>
      <div v-else-if="step===1" class="form-fields"><label>问题标题<input v-model="form.title" maxlength="120" placeholder="例如：宿舍顶灯无法点亮" required /></label><label>具体描述<textarea v-model="form.description" maxlength="2000" rows="5" placeholder="在哪里、发生了什么、从什么时候开始？" required></textarea><small>{{ form.description.length }} / 2000</small></label><fieldset class="priority-options"><legend>紧急程度</legend><label v-for="option in [{value:'LOW',label:'不紧急'},{value:'NORMAL',label:'普通'},{value:'HIGH',label:'较紧急'}]" :key="option.value"><input v-model="form.priority" type="radio" name="priority" :value="option.value" />{{ option.label }}</label></fieldset></div>
      <div v-else-if="step===2" class="form-fields"><label>校园楼栋<select v-model="form.buildingId" required><option :value="0" disabled>请选择楼栋</option><option v-for="building in catalog.buildings" :key="building.id" :value="building.id">{{ building.name }}</option></select></label><label>房间 / 具体位置<input v-model="form.roomNo" maxlength="30" placeholder="例如：301室 / 三楼走廊" required /></label><p class="field-hint">请填写能够准确找到故障的具体位置。</p></div>
      <ImageUpload v-else-if="step===3" v-model="form.imageUrl" @pending="uploading=$event" />
      <dl v-else class="confirm-details"><div><dt>故障类型</dt><dd>{{ selectedType?.name }}</dd></div><div><dt>问题标题</dt><dd>{{ form.title }}</dd></div><div><dt>具体描述</dt><dd class="pre-wrap">{{ form.description }}</dd></div><div><dt>报修地点</dt><dd>{{ selectedBuilding?.name }} · {{ form.roomNo }}</dd></div><div><dt>紧急程度</dt><dd>{{ {LOW:'不紧急',NORMAL:'普通',HIGH:'较紧急'}[form.priority] }}</dd></div><div><dt>现场图片</dt><dd>{{ form.imageUrl?'已上传1张图片':'未添加图片' }}</dd></div></dl>
      <div v-if="error" class="notice error" role="alert">{{ error }} <button v-if="!catalog.types.length" class="text-button" type="button" @click="load">重新加载</button></div>
      <div class="wizard-actions"><button v-if="step>0" class="secondary-button" type="button" :disabled="busy||uploading" @click="step--;error=''">上一步</button><span v-else class="field-hint">简单五步，轻松提交。</span><button class="primary-button" type="submit" :disabled="loading||busy||uploading||!catalog.types.length">{{ busy?'正在提交…':step===4?'确认提交':step===3&&!form.imageUrl?'跳过，下一步':'下一步' }} <span aria-hidden="true">→</span></button></div>
    </form>
  </template>
</section></template>
