<script setup lang="ts">
import { RouterLink } from 'vue-router'
import OrderStatusTag from '../OrderStatusTag.vue'
import { overdueLabels, type RepairOrder } from '../../types/repair'
import { formatTime } from '../../utils/format'
import { studentActionLabel, studentNextStep } from './presentation'
defineProps<{ order: RepairOrder }>()
</script>

<template>
  <RouterLink class="student-order-card" :to="`/student/orders/${order.id}`">
    <header><span>{{ order.typeName }} · #{{ order.id }}</span><OrderStatusTag :order="order" /></header>
    <h2>{{ order.title }}</h2>
    <p class="student-order-place">{{ order.buildingName }} · {{ order.roomNo }}</p>
    <p class="student-order-next">{{ studentNextStep(order) }}</p>
    <div v-if="order.repairRound > 1 || order.overdueType" class="service-flags"><span v-if="order.repairRound > 1">第 {{ order.repairRound }} 轮维修</span><span v-if="order.overdueType">{{ overdueLabels[order.overdueType] }}</span></div>
    <div class="student-order-owner"><span class="student-avatar" aria-hidden="true">{{ order.workerName?.slice(0, 1) || '—' }}</span><span>{{ order.workerName || '维修人员待安排' }}</span></div>
    <footer><time :datetime="order.updateTime">更新于 {{ formatTime(order.updateTime) }}</time><strong>{{ studentActionLabel(order) }} <span aria-hidden="true">→</span></strong></footer>
  </RouterLink>
</template>
