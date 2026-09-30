<script setup lang="ts">
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute,RouterLink } from 'vue-router'
import { getCatalog, getOrders } from '../api/repair'
import { statusLabels, type Catalog, type RepairOrder } from '../types/repair'
import type { Role } from '../types'
import OrderCard from '../components/OrderCard.vue'
const props=defineProps<{role:Role}>()
const route=useRoute()
const filters=reactive({status:'',typeId:'',from:'',to:'',overdue:route.query.overdue==='true'})
const orders=ref<RepairOrder[]>([]), total=ref(0), page=ref(1), loading=ref(true), error=ref('')
const catalog=ref<Catalog>({types:[],buildings:[]}), catalogError=ref('')
let revision=0
async function load(reset=false) {
  if(reset)page.value=1
  const current=++revision; loading.value=true; error.value=''
  try {
    const result=await getOrders(props.role,{page:page.value,size:12,status:filters.status||undefined,typeId:filters.typeId?Number(filters.typeId):undefined,from:filters.from||undefined,to:filters.to||undefined,overdue:filters.overdue?true:undefined})
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
onBeforeUnmount(()=>{revision++})
</script>
<template><section class="business-page">
  <header class="page-heading"><div><p class="eyebrow">{{ role==='admin'?'CAMPUS OPERATIONS':'YOUR CAMPUS SERVICE' }}</p><h1>{{ {student:'我的报修',worker:'我的任务',admin:'工单管理'}[role] }}</h1><p>{{ role==='admin'?'审核需求，指定维修人员，关注处理进度。':'每一份订单的进展，都在这里。' }}</p></div><RouterLink v-if="role==='student'" class="primary-button" to="/student/orders/new">提交报修 ↗</RouterLink></header>
  <div v-if="catalogError" class="notice error" role="alert">故障类型加载失败：{{ catalogError }} <button class="text-button" @click="loadCatalog">重试</button></div>
  <form class="filter-bar" @submit.prevent="load(true)">
    <label>订单状态<select v-model="filters.status" @change="load(true)"><option value="">全部状态</option><option v-for="(label,key) in statusLabels" :key="key" :value="key">{{ label }}</option></select></label>
    <template v-if="role==='admin'"><label>故障类型<select v-model="filters.typeId" @change="load(true)"><option value="">全部类型</option><option v-for="type in catalog.types" :key="type.id" :value="type.id">{{ type.name }}</option></select></label><label class="overdue-filter"><span>超时工单</span><input v-model="filters.overdue" type="checkbox" @change="load(true)" /></label><label>开始日期<input v-model="filters.from" type="date" /></label><label>结束日期<input v-model="filters.to" type="date" /></label><button class="secondary-button" type="submit">筛选</button></template>
  </form>
  <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load()">重试</button></div>
  <p class="list-count">{{ total }} 份{{ role==='worker'?'任务':'订单' }} <span v-if="loading">· 正在加载…</span></p>
  <div v-if="orders.length" class="order-grid" :aria-busy="loading"><OrderCard v-for="order in orders" :key="order.id" :role="role" :order="order" /></div>
  <div v-else-if="!loading&&!error" class="empty-state"><span class="empty-orbit" aria-hidden="true">○</span><h2>暂时没有符合条件的订单</h2><p>试试其他状态，或稍后再来看看。</p></div>
  <nav v-if="total>12" class="pagination" aria-label="订单分页"><button class="secondary-button" :disabled="page===1||loading" @click="turn(-1)">上一页</button><span>{{ page }} / {{ Math.ceil(total/12) }}</span><button class="secondary-button" :disabled="page*12>=total||loading" @click="turn(1)">下一页</button></nav>
</section></template>
