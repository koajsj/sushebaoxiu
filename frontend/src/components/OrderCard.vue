<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Role } from '../types'
import { orderStatusLabel, type RepairOrder } from '../types/repair'
import { formatTime } from '../utils/format'
defineProps<{ order: RepairOrder; role: Role }>()
</script>
<template><RouterLink class="order-card" :to="`/${role}/orders/${order.id}`">
  <div class="order-card-top"><span class="order-kind">{{ order.typeName }}</span><span class="status-pill" :class="`status-${order.status.toLowerCase()}`">{{ orderStatusLabel(order) }}</span></div>
  <h2>{{ order.title }}</h2><div class="service-flags"><span v-if="order.repairRound>1">返工 · 第{{ order.repairRound }}次维修</span><span v-if="order.overdueType">{{ {RESPONSE:'接单超时',START:'待开工超时',REPAIR:'维修超时'}[order.overdueType] }}</span></div><p class="order-excerpt">{{ order.description }}</p>
  <div class="order-location">{{ order.buildingName }} · {{ order.roomNo }}</div>
  <div class="order-card-footer"><time>{{ formatTime(order.createTime) }}</time><span>{{ order.workerName || '等待安排维修人员' }}</span></div>
  <div v-if="role==='worker'" class="order-card-action">{{ !order.acceptedTime&&['WAIT_ASSIGN','ASSIGNED'].includes(order.status)?'查看并响应':{REJECTED:'查看详情',REWORK_PENDING:'等待返工安排',WAIT_ASSIGN:'查看并接单',ASSIGNED:'开始维修',PROCESSING:'填写维修记录',WAIT_CONFIRM:'查看维修结果',FINISHED:'查看完成情况',COMMENTED:'查看学生评价',WAIT_AUDIT:'查看详情',CREATED:'查看详情'}[order.status] }} <span aria-hidden="true">↗</span></div>
</RouterLink></template>
