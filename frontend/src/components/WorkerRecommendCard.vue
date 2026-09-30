<script setup lang="ts">
import type { WorkerRecommendation } from '../types/dispatch'
import ScoreDisplay from './ScoreDisplay.vue'
defineProps<{worker:WorkerRecommendation;index:number;busy:boolean;confirming:boolean;disabled:boolean}>()
defineEmits<{confirm:[worker:WorkerRecommendation]}>()
const factors=[{key:'skillScore',label:'技能',weight:40},{key:'distanceScore',label:'距离',weight:30},{key:'loadScore',label:'负载',weight:20},{key:'ratingScore',label:'评价',weight:10}] as const
</script>
<template><article class="recommend-card" :style="{'--card-delay':`${Math.min(index,5)*55}ms`}">
  <header><div class="worker-identity"><span class="worker-monogram" aria-hidden="true">{{ worker.workerName.slice(0,1) }}</span><div><span v-if="index===0" class="recommended-label">综合得分最高</span><h3>{{ worker.workerName }}</h3><p>{{ worker.skillType }}</p></div></div><ScoreDisplay :value="worker.totalScore" large /></header>
  <div class="score-factors"><div v-for="factor in factors" :key="factor.key"><ScoreDisplay :value="worker[factor.key]" :label="`${factor.label} · ${factor.weight}%`"/><meter min="0" max="100" :value="worker[factor.key]" :aria-label="`${factor.label}评分`"/></div></div>
  <p class="recommend-context">{{ worker.distanceKm===null?'位置未配置':`${worker.distanceKm.toFixed(2)} km` }}<span>当前 {{ worker.activeTaskCount }} 项任务</span><span>{{ worker.rating===0?'暂无评价':`${worker.rating.toFixed(2)} / 5` }}</span></p>
  <details class="score-explanation"><summary>为什么推荐这位维修人员？</summary><p>{{ worker.reason }}</p></details>
  <footer><span>由管理员确认后派单</span><button class="primary-button" :disabled="busy||disabled" @click="$emit('confirm',worker)">{{ confirming?'正在派单…':'确认派单' }}</button></footer>
</article></template>
