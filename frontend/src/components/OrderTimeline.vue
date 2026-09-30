<script setup lang="ts">
import type { OrderEvent } from '../types/repair'
import { formatTime } from '../utils/format'
defineProps<{ events:OrderEvent[] }>()
const stages = [
  {label:'提交报修',actions:['SUBMIT']}, {label:'管理员审核',actions:['AUDIT']},
  {label:'派单与接单',actions:['ASSIGN','ACCEPT']}, {label:'维修处理',actions:['START']},
  {label:'完成确认',actions:['FINISH','CONFIRM']}, {label:'学生评价',actions:['EVALUATE']},
]
const labels:Record<string,string>={SUBMIT:'已提交',AUDIT:'审核通过',ASSIGN:'已指定维修人员',ACCEPT:'维修人员已接单',START:'开始维修',FINISH:'维修完成，等待确认',CONFIRM:'学生已确认完成',EVALUATE:'已提交评价'}
</script>
<template><ol class="order-timeline" aria-label="订单时间线"><li v-for="stage in stages" :key="stage.label" :class="{done:events.some(e=>stage.actions.includes(e.action))}">
  <span class="timeline-dot" aria-hidden="true"></span><div><h3>{{ stage.label }}</h3>
    <template v-for="event in events.filter(e=>stage.actions.includes(e.action))" :key="event.id"><p>{{ labels[event.action] }}<time>{{ formatTime(event.createTime) }}</time></p></template>
    <p v-if="!events.some(e=>stage.actions.includes(e.action))" class="muted">等待处理</p>
  </div></li></ol></template>
