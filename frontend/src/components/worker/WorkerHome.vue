<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { RepairOrder, Summary } from '../../types/repair'
import RepairTaskCard from './RepairTaskCard.vue'
import TaskSummary from './TaskSummary.vue'
import CampusHero from '../CampusHero.vue'
const props = defineProps<{ name: string; summary: Summary | null; orders: RepairOrder[]; loading: boolean; error: string }>()
defineEmits<{ retry: [] }>()
const pending = computed(() => props.orders.filter(order => ['WAIT_ASSIGN', 'ASSIGNED'].includes(order.status)))
const active = computed(() => props.orders.filter(order => ['PROCESSING', 'WAIT_CONFIRM', 'REWORK_PENDING'].includes(order.status)))
const completed = computed(() => props.orders.filter(order => ['FINISHED', 'COMMENTED'].includes(order.status)))
const attention = computed(() => props.orders.filter(order => order.overdueType && !['FINISHED', 'COMMENTED'].includes(order.status)))
</script>

<template>
  <section class="business-page worker-home">
    <header class="worker-welcome"><div class="worker-welcome-copy"><p class="eyebrow">校园维修 · 执行中心</p><h1>维修任务工作台</h1><p>{{ name }}，欢迎回来。<template v-if="summary&&!loading">今日有 {{ summary.today }} 份派单任务，{{ summary.pending }} 份等待响应或开工。</template><template v-else>先接单与开工，再记录维修结果并提交验收。</template></p><RouterLink class="secondary-button" to="/worker/orders">进入我的任务 <span aria-hidden="true">→</span></RouterLink></div><CampusHero class="worker-scene" variant="support" asset="worker" label="校园维修人员协作场景"><span>校园维修服务</span></CampusHero></header>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" :disabled="loading" @click="$emit('retry')">重新读取任务</button></div>
    <TaskSummary :summary="summary" :loading="loading" />
    <aside v-if="!loading&&attention.length" class="worker-attention"><div><strong>需要关注</strong><p>以下最近任务已超时，请进入详情确认时限与下一步。</p></div><RouterLink class="text-button" to="/worker/orders?overdue=true">查看超时任务 →</RouterLink><ul v-if="!loading&&attention.length"><li v-for="order in attention" :key="order.id"><RouterLink :to="`/worker/orders/${order.id}`">{{ order.buildingName }} · {{ order.roomNo }} <span>{{ order.title }}</span><span aria-hidden="true">→</span></RouterLink></li></ul></aside>
    <div class="worker-task-scope"><p class="worker-data-scope">以下按阶段整理最近 4 份任务；完整任务与筛选结果请前往任务列表。</p><RouterLink class="text-button" to="/worker/orders?overdue=true">筛选超时任务 →</RouterLink></div>
    <div v-if="loading" class="worker-loading" role="status"><span aria-hidden="true"/><p>正在读取任务进展…</p></div>
    <template v-else-if="!error">
      <section><div class="section-heading"><h2>等待接单与开工</h2><RouterLink class="text-button" to="/worker/orders?phase=WAIT_START">查看待开工 →</RouterLink></div><div v-if="pending.length" class="order-grid"><RepairTaskCard v-for="order in pending" :key="order.id" :order="order" /></div><p v-else class="worker-quiet-state">最近任务中暂无待处理项。全部待接单与待开工任务可在列表中查看。</p></section>
      <section><div class="section-heading"><h2>维修、验收与返工</h2><RouterLink class="text-button" to="/worker/orders?phase=PROCESSING">查看维修中 →</RouterLink></div><div v-if="active.length" class="order-grid"><RepairTaskCard v-for="order in active" :key="order.id" :order="order" :featured="order.status==='PROCESSING'" /></div><p v-else class="worker-quiet-state">最近任务中暂无维修、验收或待返工项。新的进展会显示在这里。</p></section>
      <section><div class="section-heading"><h2>最近完成</h2><RouterLink class="text-button" to="/worker/orders?phase=FINISHED">已完成任务与记录 →</RouterLink></div><div v-if="completed.length" class="order-grid"><RepairTaskCard v-for="order in completed" :key="order.id" :order="order" /></div><p v-else class="worker-quiet-state">最近任务中暂无已确认完成项。每轮维修结果都会保留在工单详情中。</p></section>
    </template>
  </section>
</template>
