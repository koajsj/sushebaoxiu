<script setup lang="ts">
import { ElDialog } from 'element-plus'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-overlay.css'
import type { RepairOrder } from '../../types/repair'
import type { WorkerRecommendation } from '../../types/dispatch'
defineProps<{ order: RepairOrder | null; worker: WorkerRecommendation | null; busy: boolean; disabled: boolean; expired: boolean }>()
const emit = defineEmits<{ close: []; confirm: [] }>()
</script>

<template>
  <ElDialog class="dispatch-confirm-dialog" :model-value="!!worker" title="确认维修安排" width="480px" align-center destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @update:model-value="value=>{if(!value&&!busy)emit('close')}">
    <div v-if="order&&worker" class="dispatch-confirm-content"><p class="dialog-intro">请确认工单与维修人员，派单后将等待对方响应任务。</p><div class="confirmation-order"><span>工单 #{{ order.id }}</span><h3>{{ order.title }}</h3><p>{{ order.buildingName }} · {{ order.roomNo }} · {{ order.typeName }}</p></div><div class="confirmation-worker"><span class="candidate-avatar" aria-hidden="true">{{ worker.workerName.slice(0,1) }}</span><div><strong>{{ worker.workerName }}</strong><p>{{ worker.skillType || '技能资料未填写' }}</p></div><span class="confirmation-score">{{ worker.totalScore.toFixed(1) }}<small>综合匹配度 / 100</small></span></div><p v-if="expired" class="notice error" role="alert">推荐已过期，请返回页面重新计算。</p><p class="dialog-hint">确认时系统会重新校验工单与人员资料。评分是规则匹配度，不代表维修成功概率。</p></div>
    <template #footer><div class="confirmation-actions"><button class="secondary-button" :disabled="busy" @click="emit('close')">返回比较</button><button class="primary-button" :disabled="busy||disabled" :aria-busy="busy" @click="emit('confirm')">{{ busy?'正在确认派单…':'确认派单' }}</button></div></template>
  </ElDialog>
</template>

<style scoped>
.dialog-intro, .dialog-hint { color: var(--muted); font-size: var(--text-support); line-height: 1.8; }
.confirmation-order { border-block: 1px solid var(--surface-line); padding-block: var(--space-4); margin: var(--space-5) 0; }
.confirmation-order span { color: var(--accent); font-size: var(--text-caption); }
.confirmation-order h3 { color: var(--ink); font-size: 18px; line-height: 1.5; overflow-wrap: anywhere; margin-block: var(--space-2); }
.confirmation-order p { color: var(--muted); font-size: var(--text-support); overflow-wrap: anywhere; }
.confirmation-worker { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-5); }
.candidate-avatar { display: grid; place-items: center; width: 44px; height: 44px; flex-shrink: 0; border-radius: var(--radius-sm); background: var(--accent-soft); color: var(--accent); font-size: 20px; }
.confirmation-worker>div { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.confirmation-worker strong { color: var(--ink); font-weight: 600; }
.confirmation-worker p { font-size: var(--text-caption); color: var(--muted); }
.confirmation-score { font-size: 24px; color: var(--ink); font-weight: 500; text-align: right; font-variant-numeric: tabular-nums; }
.confirmation-score small { display: block; font-size: var(--text-caption); font-weight: 400; color: var(--muted); }
.confirmation-actions { display: flex; justify-content: flex-end; }
@media(max-width:420px) { .confirmation-worker { flex-wrap: wrap; } .confirmation-score { margin-left: auto; } .confirmation-actions button { flex: 1; } }
</style>
