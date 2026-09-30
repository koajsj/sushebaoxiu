<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getOrders, getSummary } from '../api/repair'
import { useAuthStore } from '../store/auth'
import { orderStatusLabel, type RepairOrder, type Summary } from '../types/repair'
import OrderCard from '../components/OrderCard.vue'
const props = defineProps<{role:'student'|'worker'}>()
const auth=useAuthStore()
const summary=ref<Summary|null>(null), orders=ref<RepairOrder[]>([]), loading=ref(true), error=ref('')
const ongoing=computed(()=>orders.value.find(order=>!['FINISHED','COMMENTED'].includes(order.status)))
let revision=0
async function load() {
  const current=++revision;loading.value=true; error.value=''
  try { const [counts, page]=await Promise.all([getSummary(props.role),getOrders(props.role,{size:4})]);if(current!==revision)return;summary.value=counts;orders.value=page.records }
  catch(e){if(current===revision){summary.value=null;orders.value=[];error.value=e instanceof Error?e.message:'加载失败'}}
  finally {if(current===revision)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{revision++})
</script>
<template><section class="business-page home-page">
  <header class="page-heading"><div><p class="eyebrow">{{ role==='student'?'CAMPUS CARE':'CAMPUS SERVICE' }} · {{ auth.user?.realName }}</p><h1>{{ role==='student'?'今天，让校园生活更顺畅。':'维修工作台' }}</h1><p>{{ role==='student'?'从提交到完成，每一步都清晰可见。':'每一项维修，都让校园更好一点。' }}</p></div>
    <RouterLink class="primary-button" :to="role==='student'?'/student/orders/new':'/worker/orders'">{{ role==='student'?'提交报修':'查看我的任务' }} <span aria-hidden="true">↗</span></RouterLink>
  </header>
  <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重新加载</button></div>
  <div class="widget-grid" :aria-busy="loading">
    <article class="summary-widget widget-feature"><span>{{ role==='student'?'我的报修':'今日任务' }}</span><strong>{{ summary?(role==='student'?summary.total:summary.today):'—' }}</strong><p>{{ role==='student'?'每一份需求都有回应':'今天分配给你的维修任务' }}</p></article>
    <article class="summary-widget"><span>{{ role==='student'?'待处理':'待接单 / 待开始' }}</span><strong>{{ summary?.pending ?? '—' }}</strong><p>等待下一步处理</p></article>
    <article class="summary-widget"><span>{{ role==='student'?'处理中 / 待确认':'维修中 / 待确认' }}</span><strong>{{ summary?.active ?? '—' }}</strong><p>进展正在发生</p></article>
    <article class="summary-widget"><span>已确认完成</span><strong>{{ summary?.completed ?? '—' }}</strong><p>校园服务的每一次落实</p></article>
  </div>
  <RouterLink v-if="ongoing&&!loading" class="current-repair" :to="`/${role}/orders/${ongoing.id}`"><div><span>最近订单进展 · #{{ ongoing.id }}</span><strong>{{ ongoing.title }}</strong></div><span class="status-pill" :class="`status-${ongoing.status.toLowerCase()}`">{{ orderStatusLabel(ongoing) }}</span><span class="current-repair-link">查看进度 →</span></RouterLink>
  <section class="recent-section"><div class="section-heading"><h2>{{ role==='student'?'最近报修':'最近任务' }}</h2><RouterLink class="text-button" :to="`/${role}/orders`">查看全部 →</RouterLink></div>
    <p v-if="loading" class="empty-state" role="status">正在加载订单…</p>
    <div v-else-if="orders.length" class="order-grid"><OrderCard v-for="order in orders" :key="order.id" :order="order" :role="role" /></div>
    <div v-else-if="!error" class="empty-state"><span class="empty-orbit" aria-hidden="true">✓</span><h3>{{ role==='student'?'你的校园报修，从这里开始。':'暂时没有分配给你的任务。' }}</h3><p>{{ role==='student'?'遇到设施故障？提交一份报修，我们会认真处理。':'有新任务时，可以在这里接单并查看处理进度。' }}</p></div>
  </section>
</section></template>
