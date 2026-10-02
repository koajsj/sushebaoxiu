<script setup lang="ts">
import { RouterLink } from 'vue-router'
defineProps<{ total: number; loading: boolean; failed: boolean }>()
</script>

<template>
  <header class="dispatch-hero">
    <div class="hero-copy"><p class="eyebrow">校园维修运营 · 调度决策</p><h1>智能派单中心</h1><p class="hero-description">基于技能、距离、负载和历史评价的维修人员推荐。<br />让每一次选择，都有清晰的依据。</p><RouterLink class="text-button" to="/admin/map">查看校园任务分布 →</RouterLink></div>
    <div class="queue-metric" role="status"><span>当前待派单</span><strong>{{ failed ? '—' : loading ? '…' : total }}<small v-if="!failed&&!loading">单</small></strong><p>{{ failed ? '数量暂不可用' : loading ? '正在更新待派单队列' : '已审核 · 等待安排维修人员' }}</p></div>
  </header>
</template>

<style scoped>
.dispatch-hero { display: flex; align-items: center; justify-content: space-between; gap: var(--space-6); padding: var(--space-7); background: var(--surface); border: 1px solid var(--surface-line); border-radius: var(--radius-lg); }
.hero-copy { min-width: 0; }
h1 { font-size: var(--text-page); font-weight: 600; line-height: 1.25; letter-spacing: -.04em; margin-top: var(--space-3); }
.hero-description { color: var(--muted); font-size: var(--text-body); line-height: 1.8; margin: var(--space-3) 0 var(--space-5); }
.queue-metric { min-width: 210px; padding-left: var(--space-7); border-left: 1px solid var(--surface-line); color: var(--muted); font-size: var(--text-caption); }
.queue-metric strong { display: block; color: var(--accent); font-size: var(--text-number); line-height: 1.25; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -.04em; margin: var(--space-2) 0; }
.queue-metric small { font-size: var(--text-body); font-weight: 500; margin-left: var(--space-2); color: var(--muted); }
@media(max-width:740px) { .dispatch-hero { padding: var(--space-5); align-items: flex-start; flex-direction: column; gap: var(--space-5); } .queue-metric { border-left: 0; border-top: 1px solid var(--surface-line); width: 100%; padding: var(--space-4) 0 0; } .queue-metric strong { font-size: 32px; } }
</style>
