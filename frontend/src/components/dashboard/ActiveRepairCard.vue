<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { RepairOrder } from '../../types/repair'
import { formatTime } from '../../utils/format'
import OrderStatusTag from '../OrderStatusTag.vue'
defineProps<{ order: RepairOrder }>()
</script>

<template>
  <RouterLink class="active-repair" :to="`/admin/orders/${order.id}`">
    <div class="active-repair-top"><span>工单 #{{ order.id }}</span><OrderStatusTag :order="order" /></div>
    <h3>{{ order.title }}</h3>
    <p class="repair-location">{{ order.buildingName }} · {{ order.roomNo }}</p>
    <div class="repair-owner"><span class="worker-avatar" aria-hidden="true">{{ (order.workerName || '未')[0] }}</span><span>{{ order.workerName || '尚未分配维修员' }}<small>{{ order.typeName }} · 第 {{ order.repairRound }} 轮维修</small></span><span class="repair-arrow" aria-hidden="true">↗</span></div>
    <p v-if="order.overdueType" class="repair-overdue">已超时，请优先跟进</p>
    <p v-else-if="order.appointmentStatus === 'ACCEPTED' && order.appointmentStart">已确认预约 · {{ formatTime(order.appointmentStart) }}</p>
    <p v-else-if="order.repairDueTime">处理时限 · {{ formatTime(order.repairDueTime) }}</p>
    <p v-else>已开始维修 · 暂无处理时限</p>
  </RouterLink>
</template>

<style scoped>
.active-repair { display: block; min-width: 0; padding: var(--space-5); border: 1px solid var(--surface-line); border-radius: var(--radius); background: var(--surface); color: var(--ink); transition: border-color var(--motion-fast), transform var(--motion-fast); }
.active-repair:hover { border-color: var(--accent-line); transform: translateY(-2px); }
.active-repair-top { display: flex; align-items: center; flex-wrap: wrap; justify-content: space-between; gap: var(--space-2); font-size: var(--text-caption); color: var(--muted); }
h3 { font-size: var(--text-body); font-weight: 600; line-height: 1.6; margin-top: var(--space-4); overflow-wrap: anywhere; }
.repair-location { color: var(--ink-secondary); font-size: var(--text-support); margin-top: var(--space-1); line-height: 1.65; overflow-wrap: anywhere; }
.repair-owner { display: flex; align-items: center; gap: var(--space-3); margin-top: var(--space-5); font-size: var(--text-support); }
.worker-avatar { display: grid; place-items: center; width: 34px; height: 34px; flex-shrink: 0; background: var(--accent-soft); color: var(--accent); border-radius: 50%; }
.repair-owner > span:nth-child(2) { flex: 1; min-width: 0; overflow-wrap: anywhere; }.repair-owner small { display: block; color: var(--muted); font-size: var(--text-caption); margin-top: var(--space-1); }.repair-arrow { color: var(--muted); }
.active-repair > p:last-child { font-size: var(--text-caption); margin-top: var(--space-4); color: var(--muted); line-height: 1.65; }.active-repair > p.repair-overdue { color: var(--danger); }
@media (prefers-reduced-motion: reduce) { .active-repair { transition: none; }.active-repair:hover { transform: none; } }
</style>
