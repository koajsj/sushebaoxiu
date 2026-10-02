<script setup lang="ts">
import type { WorkerRecommendation } from '../../types/dispatch'
defineProps<{ worker: WorkerRecommendation }>()
const factors = [
  { key: 'skillScore', label: '技能匹配', weight: 40 },
  { key: 'distanceScore', label: '距离因素', weight: 30 },
  { key: 'loadScore', label: '当前负载', weight: 20 },
  { key: 'ratingScore', label: '历史评价', weight: 10 },
] as const
</script>

<template>
  <div class="score-breakdown" aria-label="推荐批次四维评分">
    <div v-for="factor in factors" :key="factor.key" class="score-factor"><div><span>{{ factor.label }}<small>{{ factor.weight }}%</small></span><strong>{{ worker[factor.key].toFixed(1) }}</strong></div><meter min="0" max="100" :value="worker[factor.key]" :aria-label="`${factor.label} ${worker[factor.key].toFixed(1)}分，权重${factor.weight}%`" /></div>
  </div>
</template>

<style scoped>
.score-breakdown { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: var(--space-4); padding-top: var(--space-4); }
.score-factor { min-width: 0; }
.score-factor>div { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-2); color: var(--ink-secondary); font-size: var(--text-support); }
.score-factor span { display: grid; gap: var(--space-1); }
.score-factor small { font-size: 11px; color: var(--muted); }
.score-factor strong { font-size: 16px; font-weight: 500; color: var(--ink); font-variant-numeric: tabular-nums; }
meter { display: block; width: 100%; height: 5px; border: 0; border-radius: 4px; margin-top: var(--space-3); background: var(--surface-line); }
meter::-webkit-meter-bar { background: var(--surface-line); border: 0; border-radius: 4px; }
meter::-webkit-meter-optimum-value { background: var(--chart-1); border-radius: 4px; }
meter::-moz-meter-bar { background: var(--chart-1); border-radius: 4px; }
@media(max-width:520px) { .score-breakdown { grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--space-5); } }
@media(min-width:861px) and (max-width:1366px) { .score-breakdown { grid-template-columns: repeat(2,minmax(0,1fr)); } }
</style>
