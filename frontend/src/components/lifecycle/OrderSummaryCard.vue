<script setup lang="ts">
import ProtectedImage from '../ProtectedImage.vue'
import { formatTime } from '../../utils/format'
import type { RepairOrder } from '../../types/repair'
defineProps<{ order: RepairOrder }>()
</script>

<template>
  <article class="detail-surface lifecycle-summary"><div class="section-heading"><h2>工单摘要</h2><span>{{ order.typeName }}</span></div><p class="pre-wrap lifecycle-description">{{ order.description }}</p><ProtectedImage v-if="order.imageUrl" :url="order.imageUrl" /><footer><span>{{ order.buildingName }} · {{ order.roomNo }}</span><span>报修人：{{ order.studentName }}</span><time :datetime="order.createTime">提交于 {{ formatTime(order.createTime) }}</time></footer></article>
</template>
