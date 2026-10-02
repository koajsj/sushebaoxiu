<script setup lang="ts">
import OrderStatusTag from './OrderStatusTag.vue'
import { RouterLink } from 'vue-router'
import type { Role } from '../types'
import { isAwaitingAcceptance, overdueLabels, type RepairOrder } from '../types/repair'
import { formatTime } from '../utils/format'
defineProps<{ order: RepairOrder; role: Role }>()
</script>
<template><RouterLink class="order-card" :to="`/${role}/orders/${order.id}`">
  <div class="order-card-top"><span class="order-kind">{{ order.typeName }}</span><OrderStatusTag :order="order" /></div>
  <h2>{{ order.title }}</h2><div class="service-flags"><span v-if="order.repairRound>1">返工 · 第{{ order.repairRound }}次维修</span><span v-if="order.overdueType">{{ overdueLabels[order.overdueType] }}</span></div><p class="order-excerpt">{{ order.description }}</p>
  <div class="order-location">{{ order.buildingName }} · {{ order.roomNo }}</div>
  <div class="order-card-footer"><time>{{ formatTime(order.createTime) }}</time><span>{{ order.workerName || '等待安排维修人员' }}</span></div>
  <div v-if="role==='worker'" class="order-card-action">{{ isAwaitingAcceptance(order)?'查看并响应':{REJECTED:'查看详情',REWORK_PENDING:'等待返工安排',WAIT_ASSIGN:'查看并接单',ASSIGNED:'开始维修',PROCESSING:'填写维修记录',WAIT_CONFIRM:'查看维修结果',FINISHED:'查看完成情况',COMMENTED:'查看学生评价',WAIT_AUDIT:'查看详情',CREATED:'查看详情'}[order.status] }} <span aria-hidden="true">↗</span></div>
  <div v-if="role==='admin'" class="order-card-action">{{ order.status==='WAIT_AUDIT'?'审核报修':order.status==='WAIT_ASSIGN'&&!order.workerId?'分配维修人员':order.status==='REWORK_PENDING'?'安排下一轮维修':order.overdueType?'跟进超时工单':'查看维修进展' }} <span aria-hidden="true">↗</span></div>
</RouterLink></template>
