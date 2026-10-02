<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { TrendPoint } from '../../types/phase5'
const props = defineProps<{ points: TrendPoint[] }>()
const selected = ref<number | null>(null)
const maximum = computed(() => Math.max(1, ...props.points.flatMap(point => [point.created, point.finished])))
const x = (index: number) => props.points.length < 2 ? 322 : 42 + index * 560 / (props.points.length - 1)
const y = (value: number) => 192 - value / maximum.value * 152
const path = (key: 'created' | 'finished') => props.points.map((point, index) => `${index ? 'L' : 'M'} ${x(index)} ${y(point[key])}`).join(' ')
const current = computed(() => selected.value === null ? null : props.points[selected.value] ?? null)
const tooltipX = computed(() => Math.min(462, Math.max(42, x(selected.value ?? 0) - 70)))
const hasActivity = computed(() => props.points.some(point => point.created || point.finished))
watch(() => props.points, () => { selected.value = null })
</script>

<template>
  <div class="trend-chart">
    <div class="trend-legend"><span><i />新报修</span><span><i />维修结果提交</span><small>单位：次</small></div>
    <p v-if="!points.length" class="chart-empty">暂无趋势数据，请稍后刷新。</p>
    <template v-else>
      <svg viewBox="0 0 640 240" role="group" aria-label="最近14天报修与维修结果提交趋势" @mouseleave="selected = null">
        <g v-for="level in [0, 1, 2]" :key="level" class="axis">
          <line x1="42" :y1="40 + level * 76" x2="602" :y2="40 + level * 76" />
          <text x="30" :y="44 + level * 76" text-anchor="end">{{ Number((maximum * (1 - level / 2)).toFixed(1)) }}</text>
        </g>
        <path :d="path('created')" class="trend-created" /><path :d="path('finished')" class="trend-finished" />
        <text x="42" y="225" class="date-label">{{ points[0]?.date.slice(5) }}</text>
        <text x="602" y="225" text-anchor="end" class="date-label">{{ points.at(-1)?.date.slice(5) }}</text>
        <g v-for="(point, index) in points" :key="point.date" class="trend-point" tabindex="0" role="button" :aria-pressed="selected === index"
          :aria-label="`${point.date}：新报修 ${point.created}，维修结果提交 ${point.finished}`"
          @mouseenter="selected = index" @focus="selected = index" @blur="selected = null" @click="selected = index"
          @keydown.enter.prevent="selected = index" @keydown.space.prevent="selected = index">
          <rect :x="x(index) - 13" y="32" width="26" height="166" fill="transparent" />
          <circle :cx="x(index)" :cy="y(point.created)" r="3" fill="var(--chart-1)" />
          <circle :cx="x(index)" :cy="y(point.finished)" r="3" fill="var(--chart-3)" />
          <title>{{ point.date }} · 新报修 {{ point.created }} · 维修结果提交 {{ point.finished }}</title>
        </g>
        <g v-if="current && selected !== null" class="trend-tooltip" aria-hidden="true">
          <line :x1="x(selected)" y1="32" :x2="x(selected)" y2="198" />
          <rect :x="tooltipX" y="2" width="140" height="64" rx="10" />
          <text :x="tooltipX + 12" y="20">{{ current.date }}</text>
          <text :x="tooltipX + 12" y="38">新报修 {{ current.created }} 次</text>
          <text :x="tooltipX + 12" y="55">结果提交 {{ current.finished }} 次</text>
        </g>
      </svg>
      <p class="trend-note">{{ hasActivity ? '悬停或用 Tab 选择日期查看数量；结果提交按维修轮次计数。' : '最近14天暂无报修或维修结果提交。' }}</p>
    </template>
  </div>
</template>

<style scoped>
.trend-legend { display: flex; flex-wrap: wrap; gap: var(--space-4); color: var(--ink-secondary); font-size: var(--text-caption); margin-bottom: var(--space-4); }
.trend-legend span { display: inline-flex; align-items: center; gap: var(--space-2); }
.trend-legend i { width: 14px; height: 3px; border-radius: 2px; background: var(--chart-1); }
.trend-legend span:nth-child(2) i { background: var(--chart-3); }
.trend-legend small { margin-left: auto; color: var(--muted); }
svg { display: block; width: 100%; }
.axis line { stroke: var(--surface-line); }
.axis text, .date-label { fill: var(--muted); font-size: 11px; }
.trend-created, .trend-finished { fill: none; stroke-width: 2.8; stroke-linecap: round; stroke-linejoin: round; }
.trend-created { stroke: var(--chart-1); }.trend-finished { stroke: var(--chart-3); }
.trend-point { outline: none; }.trend-point circle { opacity: 0; transition: opacity var(--motion-fast); }
.trend-point:hover circle, .trend-point:focus circle { opacity: 1; }
.trend-point:focus-visible rect { stroke: var(--accent); stroke-width: 1; rx: 5px; }
.trend-tooltip { pointer-events: none; }.trend-tooltip line { stroke: var(--line); stroke-dasharray: 3 4; }
.trend-tooltip rect { fill: var(--surface); stroke: var(--line); }.trend-tooltip text { fill: var(--ink); font-size: 11px; }
.trend-note { color: var(--muted); font-size: var(--text-caption); line-height: 1.65; margin-top: var(--space-3); }
.chart-empty { padding: var(--space-8) var(--space-4); color: var(--muted); text-align: center; font-size: var(--text-support); }
@media (prefers-reduced-motion: reduce) { .trend-point circle { transition: none; } }
</style>
