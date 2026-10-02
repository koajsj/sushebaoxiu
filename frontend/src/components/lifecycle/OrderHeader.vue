<script setup lang="ts">
import { computed } from 'vue'
import OrderStatusTag from '../OrderStatusTag.vue'
import { orderStatusLabel, overdueLabels, priorityLabels, type RepairOrder } from '../../types/repair'
import { formatTime } from '../../utils/format'
import { hasActiveAppointment, workerDeadline } from '../worker/presentation'
const props=defineProps<{ order: RepairOrder; nextStep: string }>()
const deadline=computed(()=>workerDeadline(props.order))
</script>

<template>
  <section class="lifecycle-header" aria-label="当前维修阶段与下一步"><header><span>{{ order.typeName }} · 工单 #{{ order.id }}</span><OrderStatusTag :order="order" /></header><p class="lifecycle-fault-title">{{ order.title }}</p><h2>{{ orderStatusLabel(order) }}</h2><div class="lifecycle-guidance-row"><p class="lifecycle-guidance">{{ nextStep }}</p><a class="text-button" :href="`#order-actions-${order.id}`">查看当前事项 ↓</a></div>
    <dl><div><dt>维修地点</dt><dd>{{ order.buildingName }} · {{ order.roomNo }}</dd></div><div><dt>当前负责人</dt><dd>{{ order.workerName || (order.workerId?'姓名暂未提供':'维修人员待安排') }}</dd></div><div><dt>优先级</dt><dd>{{ priorityLabels[order.priority] }}</dd></div><div><dt>{{ hasActiveAppointment(order)?order.appointmentStatus==='ACCEPTED'?'已确认上门':'等待确认的预约':'上门安排' }}</dt><dd>{{ hasActiveAppointment(order)?`${formatTime(order.appointmentStart!)} — ${formatTime(order.appointmentEnd!)}`:order.appointmentStatus==='REJECTED'?'需要重新协商时间':'尚无有效预约' }}</dd></div></dl>
    <footer><span v-if="order.repairRound>1">返工 · 第 {{ order.repairRound }} 轮维修</span><span v-if="deadline">{{ deadline.label }}：{{ formatTime(deadline.value) }}</span><strong v-if="order.overdueType">{{ overdueLabels[order.overdueType] }}</strong><span>更新于 {{ formatTime(order.updateTime) }}</span></footer>
  </section>
</template>
