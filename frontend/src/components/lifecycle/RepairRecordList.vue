<script setup lang="ts">
import { computed } from 'vue'
import ProtectedImage from '../ProtectedImage.vue'
import { formatTime } from '../../utils/format'
import type { RepairRecord } from '../../types/repair'
const props=defineProps<{ records: RepairRecord[]; currentRound: number; workerNames: Map<number,string|null> }>()
const rounds=computed(()=>{
  const groups=new Map<number,RepairRecord[]>()
  for(const record of [...props.records].sort((a,b)=>a.id-b.id)){
    const round=record.roundNo||1, rows=groups.get(round)||[]
    rows.push(record);groups.set(round,rows)
  }
  return [...groups].sort(([a],[b])=>b-a)
})
</script>

<template>
  <section class="detail-surface lifecycle-records"><div class="section-heading"><h2>维修记录</h2><span>{{ records.length }} 条记录</span></div><p v-if="!records.length" class="lifecycle-empty">暂无维修记录，开始维修后，处理过程与结果会保存在这里。</p><template v-else><p class="field-hint">当前轮次优先展开；开始时间为该轮开工时间，历史记录完整保留。</p><details v-for="[round,rows] in rounds" :key="round" :open="round===currentRound"><summary>第 {{ round }} 次{{ round>1?'返工维修':'维修' }} <span>{{ rows.length }} 条记录</span></summary><ol aria-label="维修过程记录"><li v-for="record in rows" :key="record.id"><div class="lifecycle-record-heading"><h3>{{ workerNames.get(record.workerId)||`维修员 #${record.workerId}` }}</h3><span>维修结果记录</span></div><p class="pre-wrap">{{ record.content }}</p><ProtectedImage v-if="record.imageUrl" :url="record.imageUrl" alt="维修结果现场图片" /><p class="lifecycle-record-time">本轮开始：{{ formatTime(record.startTime) }}<br />{{ record.finishTime?`本轮结束：${formatTime(record.finishTime)}`:'本轮尚未结束' }}</p></li></ol></details></template></section>
</template>
