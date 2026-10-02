<script setup lang="ts">
import { computed } from 'vue'
import type { TypeCount } from '../../types/phase5'
const props = defineProps<{ types: TypeCount[] }>()
const total = computed(() => props.types.reduce((sum, type) => sum + type.count, 0))
const colors = Array.from({ length: 6 }, (_, index) => `var(--chart-${index + 1})`)
const segments = computed(() => {
  let offset = 0
  return props.types.map((type, index) => {
    const length = total.value ? type.count / total.value * Math.PI * 120 : 0
    const segment = { ...type, length, offset, color: colors[index % colors.length] }
    offset += length
    return segment
  })
})
</script>

<template>
  <div v-if="total" class="fault-chart">
    <svg viewBox="0 0 180 180" role="img" aria-label="全量工单故障类型占比">
      <circle cx="90" cy="90" r="60" fill="none" stroke="var(--surface-line)" stroke-width="22" />
      <circle v-for="segment in segments.filter(item => item.count > 0)" :key="segment.typeId" cx="90" cy="90" r="60"
        fill="none" :stroke="segment.color" stroke-width="22" :stroke-dasharray="`${segment.length} ${Math.PI * 120 - segment.length}`"
        :stroke-dashoffset="-segment.offset" transform="rotate(-90 90 90)">
        <title>{{ segment.typeName }}：{{ segment.count }} 单，{{ segment.percentage === null ? '暂无占比' : `${segment.percentage}%` }}</title>
      </circle>
      <text x="90" y="88" text-anchor="middle" class="fault-total">{{ total }}</text>
      <text x="90" y="110" text-anchor="middle" class="fault-caption">工单总数</text>
    </svg>
    <ul class="fault-legend"><li v-for="(type, index) in types" :key="type.typeId">
      <span class="fault-name"><i :style="{ background: colors[index % colors.length] }" /><span>{{ type.typeName }}</span></span>
      <span class="fault-count">{{ type.count }} 单</span><strong>{{ type.percentage === null ? '暂无占比' : `${type.percentage}%` }}</strong>
    </li></ul>
  </div>
  <div v-else class="fault-empty"><span aria-hidden="true">◎</span><strong>还没有故障分布数据</strong><p>产生报修后，将按实际故障类型展示占比。</p></div>
</template>

<style scoped>
.fault-chart { display: grid; grid-template-columns: minmax(100px, 150px) minmax(0, 1fr); align-items: center; gap: var(--space-4); min-height: 235px; }
svg { width: 100%; max-width: 180px; justify-self: center; }.fault-total { fill: var(--ink); font-size: 27px; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -.04em; }.fault-caption { fill: var(--muted); font-size: 11px; }
.fault-legend { list-style: none; padding: 0; margin: 0; display: grid; gap: var(--space-4); }
.fault-legend li { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: var(--space-1) var(--space-2); font-size: var(--text-caption); }
.fault-name { display: flex; align-items: center; gap: var(--space-2); min-width: 0; grid-column: 1 / -1; color: var(--ink-secondary); }.fault-name span { overflow-wrap: anywhere; }.fault-name i { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.fault-count { color: var(--muted); padding-left: 15px; }.fault-legend strong { font-weight: 500; font-variant-numeric: tabular-nums; }
.fault-empty { display: grid; place-items: center; align-content: center; gap: var(--space-3); min-height: 235px; text-align: center; }.fault-empty > span { font-size: 34px; color: var(--accent); }.fault-empty strong { font-size: var(--text-body); font-weight: 500; }.fault-empty p { font-size: var(--text-caption); color: var(--muted); line-height: 1.7; }
@media (min-width: 741px) and (max-width: 1250px) { .fault-chart { grid-template-columns: 1fr; gap: var(--space-3); } svg { max-width: 135px; } .fault-legend { gap: var(--space-3); }.fault-legend li { grid-template-columns: minmax(0, 1fr) auto auto; }.fault-name { grid-column: auto; }.fault-count { padding-left: 0; } }
@media (max-width: 420px) { .fault-chart { grid-template-columns: 1fr; } .fault-legend li { grid-template-columns: minmax(0, 1fr) auto auto; }.fault-name { grid-column: auto; }.fault-count { padding-left: 0; } }
</style>
