<script setup lang="ts">
import { computed } from 'vue'
import type { WorkerCount } from '../../types/phase5'
const props = defineProps<{ workers: WorkerCount[] }>()
const ranked = computed(() => [...props.workers].sort((a, b) => b.completedCount - a.completedCount || a.workerId - b.workerId))
const maximum = computed(() => Math.max(1, ...ranked.value.map(worker => worker.completedCount)))
</script>

<template>
  <div v-if="ranked.length" class="worker-ranking">
    <div class="ranking-labels"><span>维修人员</span><span>完成 / 评分</span></div>
    <ol tabindex="0" aria-label="维修人员效率排名，可滚动查看全部人员"><li v-for="(worker, index) in ranked" :key="worker.workerId">
      <span class="rank-number" :class="{ 'rank-leading': index === 0 }">{{ index + 1 }}</span>
      <div class="rank-person"><strong>{{ worker.workerName }}</strong><span>当前任务 {{ worker.activeCount }} 单</span><div class="rank-bar" aria-hidden="true"><i :style="{ width: `${worker.completedCount / maximum * 100}%` }" /></div></div>
      <div class="rank-result"><strong>{{ worker.completedCount }} <small>单</small></strong><span>{{ worker.averageRating === null ? '暂无评分' : `${worker.averageRating.toFixed(1)} / 5` }}</span></div>
    </li></ol>
  </div>
  <div v-else class="ranking-empty"><strong>暂无维修人员数据</strong><p>维修与评价完成后，效率信息将呈现在这里。</p></div>
</template>

<style scoped>
.ranking-labels { display: flex; justify-content: space-between; font-size: var(--text-caption); color: var(--muted); margin-bottom: var(--space-2); }
ol { list-style: none; padding: 0; margin: 0; max-height: 360px; overflow-y: auto; scrollbar-width: thin; }
li { display: grid; grid-template-columns: 20px minmax(0, 1fr) auto; gap: var(--space-3); align-items: center; padding: var(--space-4) 0; border-bottom: 1px solid var(--surface-line); }li:last-child { border: 0; }
.rank-number { color: var(--muted); font-size: var(--text-caption); font-variant-numeric: tabular-nums; }.rank-leading { color: var(--accent); font-weight: 600; }
.rank-person strong { font-size: var(--text-support); font-weight: 500; overflow-wrap: anywhere; }.rank-person > span { display: block; color: var(--muted); font-size: var(--text-caption); margin-top: var(--space-1); }
.rank-bar { height: 3px; border-radius: 3px; background: var(--surface-subtle); overflow: hidden; margin-top: var(--space-2); }.rank-bar i { display: block; height: 100%; background: var(--chart-1); border-radius: inherit; transition: width var(--motion); }
.rank-result { display: grid; gap: var(--space-1); text-align: right; font-variant-numeric: tabular-nums; }.rank-result strong { font-size: var(--text-body); font-weight: 600; }.rank-result small { color: var(--muted); font-size: var(--text-caption); font-weight: 400; }.rank-result > span { color: var(--muted); font-size: var(--text-caption); }
.ranking-empty { padding: var(--space-8) 0; text-align: center; }.ranking-empty strong { font-size: var(--text-body); font-weight: 500; }.ranking-empty p { color: var(--muted); font-size: var(--text-caption); line-height: 1.7; margin-top: var(--space-3); }
@media (prefers-reduced-motion: reduce) { .rank-bar i { transition: none; } }
</style>
