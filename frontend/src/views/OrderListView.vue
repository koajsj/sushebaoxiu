<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute,RouterLink } from 'vue-router'
import { getCatalog, getOrders } from '../api/repair'
import { phaseLabels, type Catalog, type RepairOrder } from '../types/repair'
import type { Role } from '../types'
import OrderCard from '../components/OrderCard.vue'
import RepairTaskCard from '../components/worker/RepairTaskCard.vue'
import StudentRepairCard from '../components/student/StudentRepairCard.vue'
const props=defineProps<{role:Role}>()
const route=useRoute()
const copy=computed(()=>({student:{title:'我的工单',description:'跟进维修进度，确认维修结果，留下服务评价。',empty:'你还没有报修工单',hint:'提交一次报修后，维修进度和历史记录会显示在这里。',loading:'正在读取你的维修进度…'},worker:{title:route.query.phase==='FINISHED'?'已完成任务':'我的任务',description:'确认待接任务，按时开工；维修完成后保存记录并提交验收。',empty:'暂无分配给你的任务',hint:'当前没有维修安排。新任务分配后会显示在这里。',loading:'正在读取分配给你的任务…'},admin:{title:'工单管理',description:'审核报修、分配维修人员，跟进超时与返工。',empty:'尚未收到报修工单',hint:'学生提交报修后，可在这里审核并安排维修。',loading:'正在读取报修与派单进展…'}}[props.role]))
const filters=reactive({phase:typeof route.query.phase==='string'?route.query.phase:'',typeId:'',from:'',to:'',overdue:route.query.overdue==='true'})
const orders=ref<RepairOrder[]>([]), total=ref(0), page=ref(1), loading=ref(true), error=ref('')
const catalog=ref<Catalog>({types:[],buildings:[]}), catalogError=ref('')
const filtered=computed(()=>!!(filters.phase||filters.typeId||filters.from||filters.to||filters.overdue))
let revision=0
async function load(reset=false) {
  if(reset)page.value=1
  const current=++revision; loading.value=true; error.value=''
  try {
    const result=await getOrders(props.role,{page:page.value,size:12,phase:filters.phase||undefined,typeId:filters.typeId?Number(filters.typeId):undefined,from:filters.from||undefined,to:filters.to||undefined,overdue:filters.overdue?true:undefined})
    if(current===revision){orders.value=result.records;total.value=result.total}
  } catch(e){if(current===revision){orders.value=[];total.value=0;error.value=e instanceof Error?e.message:'加载失败'}}
  finally {if(current===revision)loading.value=false}
}
function turn(delta:number){page.value+=delta;void load()}
async function loadCatalog() {
  catalogError.value=''
  try { catalog.value=await getCatalog() }
  catch(e) { catalogError.value=e instanceof Error?e.message:'故障类型加载失败，请重试' }
}
onMounted(()=>{void load();if(props.role==='admin')void loadCatalog()})
watch(()=>[route.query.phase,route.query.overdue],()=>{filters.phase=typeof route.query.phase==='string'?route.query.phase:'';filters.overdue=route.query.overdue==='true';void load(true)})
onBeforeUnmount(()=>{revision++})
</script>
<template><section class="business-page order-list-page" :class="{'student-order-center':role==='student','worker-task-center':role==='worker'}">
  <header class="page-heading"><div><p class="eyebrow">{{ role==='admin'?'校园服务 · 工单':role==='worker'?'校园维修 · 我的任务':'校园服务 · 我的工单' }}</p><h1>{{ copy.title }}</h1><p>{{ copy.description }}</p></div><RouterLink v-if="role==='student'" class="primary-button" to="/student/orders/new">我要报修 ↗</RouterLink></header>
  <div v-if="catalogError" class="notice error" role="alert">故障类型加载失败：{{ catalogError }} <button class="text-button" @click="loadCatalog">重新加载故障类型</button></div>
  <form class="filter-bar" @submit.prevent="load(true)">
    <label>维修阶段<select v-model="filters.phase" @change="load(true)"><option value="">全部阶段</option><option v-for="(label,key) in phaseLabels" :key="key" :value="key">{{ label }}</option></select></label>
    <template v-if="role==='admin'"><label>故障类型<select v-model="filters.typeId" @change="load(true)"><option value="">全部类型</option><option v-for="type in catalog.types" :key="type.id" :value="type.id">{{ type.name }}</option></select></label><label class="overdue-filter"><span>超时工单</span><input v-model="filters.overdue" type="checkbox" @change="load(true)" /></label><label>开始日期<input v-model="filters.from" type="date" /></label><label>结束日期<input v-model="filters.to" type="date" /></label><button class="secondary-button" type="submit">应用筛选</button></template>
  </form>
  <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load()">重新加载{{ role==='worker'?'任务':'工单' }}</button></div>
  <p class="list-count">{{ total }} 份{{ role==='worker'?'任务':'工单' }} <span v-if="loading">· 正在加载…</span></p>
  <p v-if="role==='student'" class="student-list-guide">查看当前阶段与下一步，也可以按处理阶段筛选记录。</p>
  <p v-if="role==='worker'&&filters.phase==='FINISHED'" class="student-list-guide">当前筛选已完成、尚未评价的任务；已评价任务可切换阶段查找，维修记录保存在任务详情中。</p>
  <p v-if="role==='worker'&&filters.overdue" class="worker-filter-notice">当前仅展示需要关注的超时任务。</p>
  <div v-if="orders.length" class="order-grid" :aria-busy="loading"><template v-for="order in orders" :key="order.id"><StudentRepairCard v-if="role==='student'" :order="order" /><RepairTaskCard v-else-if="role==='worker'" :order="order" /><OrderCard v-else :role="role" :order="order" /></template></div>
  <p v-else-if="loading" :class="role==='worker'?'worker-loading':'student-loading'" role="status">{{ copy.loading }}</p>
  <div v-else-if="!loading&&!error" class="empty-state"><span class="empty-orbit" aria-hidden="true">○</span><h2>{{ filtered?'没有符合筛选条件的'+(role==='worker'?'任务':'工单'):copy.empty }}</h2><p>{{ filtered?'调整维修阶段或筛选条件，继续查找需要的记录。':copy.hint }}</p><RouterLink v-if="role==='student'&&!filtered" class="primary-button" to="/student/orders/new">提交第一次报修</RouterLink></div>
  <nav v-if="total>12" class="pagination" :aria-label="role==='worker'?'任务分页':'工单分页'"><button class="secondary-button" :disabled="page===1||loading" @click="turn(-1)">上一页</button><span>{{ page }} / {{ Math.ceil(total/12) }}</span><button class="secondary-button" :disabled="page*12>=total||loading" @click="turn(1)">下一页</button></nav>
</section></template>
