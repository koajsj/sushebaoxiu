<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { RepairOrder } from '../../types/repair'
import { priorityLabels } from '../../types/repair'
import { formatTime } from '../../utils/format'
import OrderStatusTag from '../OrderStatusTag.vue'
import ProtectedImage from '../ProtectedImage.vue'
defineProps<{ order: RepairOrder; previousRejection: string }>()
</script>

<template>
  <article class="order-summary">
    <header><span class="eyebrow">当前工单 #{{ order.id }}</span><OrderStatusTag :order="order" /></header>
    <h2>{{ order.title }}</h2><p class="order-category">{{ order.typeName }} <span :class="{ urgent: order.priority==='HIGH' }">{{ priorityLabels[order.priority] }}</span></p>
    <div class="location-context"><span aria-hidden="true">⌖</span><div><strong>{{ order.buildingName }} · {{ order.roomNo }}</strong><small>报修地点</small></div></div>
    <p class="problem-description">{{ order.description }}</p>
    <p v-if="previousRejection" class="rejection-context">上次拒单：{{ previousRejection }}</p>
    <details v-if="order.imageUrl" class="order-image"><summary>查看报修现场</summary><ProtectedImage :url="order.imageUrl" /></details>
    <footer><time :datetime="order.createTime">提交于 {{ formatTime(order.createTime) }}</time><RouterLink class="text-button" :to="`/admin/orders/${order.id}`">完整详情与人工派单 →</RouterLink></footer>
  </article>
</template>

<style scoped>
.order-summary { min-width: 0; padding: var(--space-5); border: 1px solid var(--surface-line); background: var(--surface); border-radius: var(--radius); animation: summary-enter var(--motion-enter) var(--ease-out); }
header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2); }
h2 { font-size: 24px; font-weight: 600; line-height: 1.45; letter-spacing: -.03em; overflow-wrap: anywhere; margin: var(--space-5) 0 var(--space-3); }
.order-category { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); font-size: var(--text-support); color: var(--muted); }
.order-category span { border-left: 1px solid var(--line); padding-left: var(--space-3); }
.order-category .urgent { color: var(--warning); }
.location-context { display: flex; align-items: center; gap: var(--space-3); margin: var(--space-5) 0; padding: var(--space-4); border-radius: var(--radius-sm); background: var(--surface-subtle); }
.location-context>span { font-size: 24px; color: var(--accent); }
.location-context div { min-width: 0; display: grid; gap: var(--space-1); }
.location-context strong { font-size: var(--text-body); font-weight: 500; overflow-wrap: anywhere; }
.location-context small, footer time { font-size: var(--text-caption); color: var(--muted); }
.problem-description, .rejection-context { font-size: var(--text-body); color: var(--ink-secondary); line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.rejection-context { margin-top: var(--space-4); padding: var(--space-3); background: var(--warning-soft); color: var(--warning); border-radius: var(--radius-sm); font-size: var(--text-support); }
.order-image { margin-top: var(--space-4); font-size: var(--text-support); color: var(--muted); }
.order-image summary { cursor: pointer; }
.order-image :deep(.repair-photo) { margin-top: var(--space-3); }
footer { display: grid; gap: var(--space-4); border-top: 1px solid var(--surface-line); margin-top: var(--space-5); padding-top: var(--space-4); }
@keyframes summary-enter { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } }
@media(prefers-reduced-motion:reduce) { .order-summary { animation: none; } }
</style>
