<script setup lang="ts">
import { computed } from 'vue'
import type { OrderEvent } from '../types/repair'
import { formatTime } from '../utils/format'
const props=defineProps<{events:OrderEvent[];status:string;currentRound?:number}>()
const labels:Record<string,string>={SUBMIT:'提交报修',AUDIT:'审核通过',AUDIT_REJECT:'审核驳回',EDIT:'修改信息',RESUBMIT:'重新提交',ASSIGN:'安排维修人员',WORKER_REJECT:'维修人员拒单',RECALL:'管理员收回任务',ACCEPT:'接受任务',START:'开始维修',RECORD:'提交维修记录',FINISH:'维修完成，等待验收',CONFIRM:'验收通过',EVALUATE:'评价完成',ACCEPTANCE_FAIL:'验收未通过',REWORK_ORIGINAL:'原维修人员返工',REWORK_REDISPATCH:'返工重新派单',APPOINTMENT_PROPOSE:'提出预约',APPOINTMENT_ACCEPT:'预约已确认',APPOINTMENT_REJECT:'预约需调整',SLA_RESPONSE:'系统提醒 · 接单超时',SLA_START:'系统提醒 · 待开工超时',SLA_REPAIR:'系统提醒 · 维修超时'}
const rounds=computed(()=>{const grouped=new Map<number,OrderEvent[]>();for(const event of [...props.events].sort((a,b)=>a.id-b.id)){const round=event.roundNo||1;const rows=grouped.get(round)||[];rows.push(event);grouped.set(round,rows)}return [...grouped].map(([round,events])=>({round,events}))})
const latestId=computed(()=>props.events.reduce((latest,event)=>Math.max(latest,event.id),0))
const activeRound=computed(()=>rounds.value.some(group=>group.round===props.currentRound)?props.currentRound:rounds.value.at(-1)?.round)
</script>
<template><p v-if="!events.length" class="muted">暂无处理记录</p><details v-for="group in rounds" :key="group.round" class="timeline-round" :open="group.round===activeRound"><summary>第 {{ group.round }} 轮维修 <small>{{ group.events.length }} 条记录</small></summary><ol class="order-timeline" aria-label="订单真实事件时间线"><li v-for="event in group.events" :key="event.id" class="done" :aria-current="event.id===latestId?'step':undefined"><span class="timeline-dot" aria-hidden="true"/><div><h3>{{ labels[event.action]||'处理记录' }} <small v-if="event.id===latestId" class="timeline-latest-label">最新事件</small></h3><p v-if="event.content" class="pre-wrap">{{ event.content }}</p><time :datetime="event.createTime">{{ formatTime(event.createTime) }}</time></div></li></ol></details></template>
