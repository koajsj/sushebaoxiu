<script setup lang="ts">
import { RouterLink } from 'vue-router'
import OrderStatusTag from '../OrderStatusTag.vue'
import { overdueLabels, priorityLabels, type RepairOrder } from '../../types/repair'
import { formatTime } from '../../utils/format'
import { hasActiveAppointment, workerActionLabel, workerDeadline, workerNextStep } from './presentation'
defineProps<{ order: RepairOrder; featured?: boolean }>()
</script>

<template>
  <RouterLink class="worker-task-card" :class="{'worker-task-featured':featured}" :to="`/worker/orders/${order.id}`">
    <header><span class="worker-task-kind">{{ order.typeName }} · #{{ order.id }}</span><OrderStatusTag :order="order" /></header>
    <h3>{{ order.title }}</h3><p class="worker-task-place">{{ order.buildingName }} · {{ order.roomNo }}</p>
    <div class="worker-task-signals"><span :class="{'worker-priority-high':order.priority==='HIGH'}">{{ priorityLabels[order.priority] }}</span><span v-if="order.repairRound>1">返工 · 第 {{ order.repairRound }} 次维修</span><span v-if="order.overdueType" class="worker-attention-label">{{ overdueLabels[order.overdueType] }}</span></div>
    <p class="worker-task-next">{{ workerNextStep(order) }}</p>
    <div class="worker-task-schedule"><p v-if="hasActiveAppointment(order)"><span>{{ order.appointmentStatus==='ACCEPTED'?'已确认预约':'预约待学生确认' }}</span><strong>{{ formatTime(order.appointmentStart!) }} — {{ formatTime(order.appointmentEnd!) }}</strong></p><p v-else><span>上门预约</span><strong>{{ order.appointmentStatus==='REJECTED'?'需重新协商时间':'尚无有效预约' }}</strong></p><p v-if="workerDeadline(order)"><span>{{ workerDeadline(order)!.label }}</span><strong>{{ formatTime(workerDeadline(order)!.value) }}</strong></p></div>
    <footer><time>更新于 {{ formatTime(order.updateTime) }}</time><strong>{{ workerActionLabel(order) }} <span aria-hidden="true">→</span></strong></footer>
  </RouterLink>
</template>
