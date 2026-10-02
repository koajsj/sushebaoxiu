<script setup lang="ts">
import { RouterLink } from 'vue-router'
import OrderStatusTag from '../OrderStatusTag.vue'
import type { RepairOrder } from '../../types/repair'
import { formatTime } from '../../utils/format'
import { studentActionLabel, studentNextStep } from './presentation'
defineProps<{ order: RepairOrder }>()
</script>

<template>
  <article class="student-current-card">
    <header><p class="eyebrow">最近工单进展 · #{{ order.id }}</p><OrderStatusTag :order="order" /></header>
    <h2>{{ order.title }}</h2><p class="student-current-place">{{ order.buildingName }} · {{ order.roomNo }}</p>
    <p class="student-current-next">{{ studentNextStep(order) }}</p>
    <dl class="student-current-facts"><div><dt>负责维修人员</dt><dd>{{ order.workerName || '等待安排' }}</dd></div><div><dt>{{ order.appointmentStatus === 'ACCEPTED' ? '已确认上门时间' : order.appointmentStatus === 'PROPOSED' ? '待确认上门时间' : '上门时间' }}</dt><dd>{{ ['ACCEPTED', 'PROPOSED'].includes(order.appointmentStatus) ? `${formatTime(order.appointmentStart || '')} — ${formatTime(order.appointmentEnd || '')}` : '尚无有效预约' }}</dd></div></dl>
    <footer><span>以实际处理进展为准，不预估完成时间。</span><RouterLink class="primary-button" :to="`/student/orders/${order.id}`">{{ studentActionLabel(order) }} <span aria-hidden="true">→</span></RouterLink></footer>
  </article>
</template>
