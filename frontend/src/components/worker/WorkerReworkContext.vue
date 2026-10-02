<script setup lang="ts">
import { computed } from 'vue'
import type { OrderDetail } from '../../types/repair'
import { formatTime } from '../../utils/format'
const props=defineProps<{ detail: OrderDetail }>()
const reason=computed(()=>[...props.detail.timeline].sort((a,b)=>b.id-a.id).find(event=>event.action==='ACCEPTANCE_FAIL'))
</script>

<template>
  <aside v-if="detail.order.repairRound>1||detail.order.status==='REWORK_PENDING'" class="worker-rework-context"><p class="eyebrow">{{ detail.order.status==='REWORK_PENDING'?'返工待安排':'返工任务' }} · 第 {{ detail.order.repairRound||1 }} 次维修</p><h2>结合上一次反馈，继续解决问题。</h2><p v-if="reason" class="pre-wrap">{{ reason.content||'学生验收未通过，具体情况请结合工单沟通确认。' }}</p><p v-else>暂无返工原因记录，请结合原问题与历史维修记录确认。</p><small v-if="reason">反馈时间：{{ formatTime(reason.createTime) }}</small><p class="field-hint">原问题与各轮维修记录均在下方保留。</p></aside>
</template>
