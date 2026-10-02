<script setup lang="ts">
import type { WorkerRecommendation } from '../types/dispatch'
import ScoreDisplay from './ScoreDisplay.vue'
import ScoreBreakdown from './dispatch/ScoreBreakdown.vue'
import RecommendReason from './dispatch/RecommendReason.vue'
defineProps<{ worker: WorkerRecommendation; index: number; topScore: number; tied: boolean; busy: boolean; confirming: boolean; disabled: boolean }>()
defineEmits<{ confirm: [worker: WorkerRecommendation] }>()
</script>

<template>
  <article class="decision-card" :class="{ 'is-top-choice': index===0 }">
    <header><div class="candidate-identity"><span class="candidate-avatar" aria-hidden="true">{{ worker.workerName.slice(0,1) }}</span><div><p class="candidate-rank">{{ index===0 ? tied?'评分并列首位':'综合评分最高' : `候选 ${index+1}` }}<span v-if="index>0">{{ topScore===worker.totalScore?'与首位同分':`距首位 ${(topScore-worker.totalScore).toFixed(1)} 分` }}</span></p><h3>{{ worker.workerName }}</h3><p class="candidate-skill">{{ worker.skillType || '技能资料未填写' }}</p></div></div><ScoreDisplay :value="worker.totalScore" label="综合匹配度" large /></header>
    <RecommendReason :reason="worker.reason" />
    <div class="candidate-facts"><div><span>当前任务</span><strong>{{ worker.activeTaskCount }} <small>项</small></strong></div><div><span>静态坐标距离</span><strong>{{ worker.distanceKm===null?'位置未配置':`${worker.distanceKm.toFixed(2)} km` }}</strong></div><div><span>历史评价</span><strong>{{ worker.rating===0?'暂无评价':`${worker.rating.toFixed(2)} / 5` }}</strong></div></div>
    <ScoreBreakdown :worker="worker" />
    <footer><span>评分来自本次推荐快照，确认时重新校验</span><button :class="index===0?'primary-button':'secondary-button'" :disabled="busy||disabled" :aria-label="`选择${worker.workerName}并确认派单`" @click="$emit('confirm',worker)">{{ confirming?'正在派单…':`选择${worker.workerName}` }}</button></footer>
  </article>
</template>

<style scoped>
.decision-card { min-width: 0; padding: var(--space-5); background: var(--surface); border: 1px solid var(--surface-line); border-radius: var(--radius); transition: border-color var(--motion-fast); }
.decision-card.is-top-choice { border-color: var(--accent-line); box-shadow: inset 3px 0 0 var(--accent); }
.decision-card:hover { border-color: var(--accent-line); }
header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-5); }
.candidate-identity { display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
.candidate-identity>div { min-width: 0; }
.candidate-avatar { display: grid; place-items: center; width: 48px; height: 48px; flex-shrink: 0; border-radius: var(--radius-sm); color: var(--ink-secondary); background: var(--surface-subtle); font-size: 22px; }
.is-top-choice .candidate-avatar { background: var(--accent-soft); color: var(--accent); }
.candidate-rank { display: flex; flex-wrap: wrap; gap: var(--space-2); color: var(--muted); font-size: var(--text-caption); margin-bottom: var(--space-1); }
.is-top-choice .candidate-rank { color: var(--accent); font-weight: 600; }
.candidate-rank span { color: var(--muted); }
h3 { font-size: 22px; font-weight: 600; letter-spacing: -.025em; line-height: 1.5; overflow-wrap: anywhere; }
.candidate-skill { font-size: var(--text-support); color: var(--muted); overflow-wrap: anywhere; }
.candidate-facts { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: var(--space-4); margin-block: var(--space-5) var(--space-3); }
.candidate-facts>div { display: grid; gap: var(--space-2); min-width: 0; }
.candidate-facts span { color: var(--muted); font-size: var(--text-caption); }
.candidate-facts strong { color: var(--ink-secondary); font-weight: 500; font-size: var(--text-body); font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.candidate-facts small { font-weight: 400; font-size: var(--text-caption); color: var(--muted); }
footer { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--space-3); margin-top: var(--space-5); }
footer>span { font-size: var(--text-caption); color: var(--muted); max-width: 32ch; }
footer button { min-width: 132px; }
@media(max-width:520px) { header { flex-wrap: wrap; } .candidate-facts { gap: var(--space-3); } footer button { width: 100%; } }
@media(min-width:861px) and (max-width:1366px) { header { flex-wrap: wrap; } }
@media(prefers-reduced-motion:reduce) { .decision-card { animation: none; transition: none; } }
</style>
