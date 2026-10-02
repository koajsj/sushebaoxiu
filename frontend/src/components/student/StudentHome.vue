<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import CampusHero from '../CampusHero.vue'
import CurrentRepairCard from './CurrentRepairCard.vue'
import StudentRepairCard from './StudentRepairCard.vue'
import type { RepairOrder, Summary } from '../../types/repair'
const props = defineProps<{ name: string; summary: Summary | null; orders: RepairOrder[]; loading: boolean; error: string }>()
defineEmits<{ retry: [] }>()
const current = computed(() => props.orders.find(order => !['FINISHED', 'COMMENTED'].includes(order.status)))
</script>

<template>
  <section class="business-page student-home">
    <CampusHero class="student-welcome" label="学生校园服务欢迎区域" asset="courtyard"><p>你好，{{ name }}</p><h1>校园维修服务</h1><p>提交设施问题，跟进维修进度，确认维修结果。</p></CampusHero>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" :disabled="loading" @click="$emit('retry')">重新读取维修进度</button></div>
    <section class="student-current-section" :aria-busy="loading"><div class="section-heading"><h2>我的维修进展</h2><span v-if="!loading && summary" class="muted">{{ summary.pending }} 份待审核 / 接单 / 开工 · {{ summary.active }} 份维修 / 验收 / 返工中</span></div>
      <div v-if="loading" class="student-loading" role="status"><span class="student-skeleton" aria-hidden="true"/><p>正在读取最新进展…</p></div>
      <CurrentRepairCard v-else-if="current" :order="current" />
      <div v-else-if="!error" class="student-calm-state"><h3>{{ summary && summary.pending + summary.active > 0 ? '还有工单正在处理中' : '近期暂无待处理工单' }}</h3><p>{{ summary && summary.pending + summary.active > 0 ? '首页展示最近四份工单，前往我的工单查看全部进展。' : '有新问题时随时提交，已完成的维修可在记录中查看。' }}</p></div>
    </section>
    <nav class="student-quick-actions" aria-label="校园维修快捷操作"><RouterLink class="student-quick-primary" to="/student/orders/new"><span class="student-quick-symbol" aria-hidden="true">＋</span><div><strong>我要报修</strong><span>简单五步，告诉我们需要什么帮助。</span></div><span aria-hidden="true">→</span></RouterLink><RouterLink class="student-quick-secondary" to="/student/orders"><div><strong>维修进度与记录</strong><span>报修进度、维修结果与服务评价</span></div><span aria-hidden="true">→</span></RouterLink></nav>
    <section><div class="section-heading"><h2>最近工单</h2><RouterLink class="text-button" to="/student/orders">全部报修记录 →</RouterLink></div><p v-if="summary&&!loading" class="student-recent-count">共 {{ summary.total }} 份报修 · {{ summary.completed }} 份已确认完成</p><div v-if="!loading && orders.length" class="order-grid"><StudentRepairCard v-for="order in orders" :key="order.id" :order="order" /></div><p v-else-if="loading" class="muted" role="status">正在加载报修记录…</p><div v-else-if="!error" class="student-calm-state"><h3>你的校园报修，从这里开始。</h3><p>提交的问题、处理进展与结果会保存在这里。</p></div></section>
    <aside class="student-notice-guide"><strong>审核与维修消息</strong><p>审核、派单与维修进展会同步至「消息通知」。在那里查看未读消息与历史通知。</p></aside>
  </section>
</template>
