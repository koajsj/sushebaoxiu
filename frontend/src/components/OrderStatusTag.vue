<script setup lang="ts">
import { orderStatusLabel, type OrderStatusSummary } from '../types/repair'

defineProps<{ order: OrderStatusSummary }>()
</script>

<template>
  <span class="status-pill" :class="`status-${order.status.toLowerCase()}`">{{ orderStatusLabel(order) }}</span>
</template>
