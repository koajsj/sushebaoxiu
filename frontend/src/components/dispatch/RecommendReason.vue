<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ reason: string }>()
// Keep explanations from the server; do not infer new matching rules in the UI.
const highlights = computed(() => props.reason.replace(/（[^）]*）/g,'').split('；').map(part => part.trim()).filter(part => /^(技能|距离|坐标缺失|当前任务|历史评价|无历史评价)/.test(part)).slice(0,4))
</script>

<template>
  <section class="recommend-reason" aria-label="推荐依据"><p class="reason-label">为什么推荐</p><ul v-if="highlights.length"><li v-for="(line,index) in highlights" :key="index"><span aria-hidden="true">·</span>{{ line }}</li></ul><p v-else class="reason-summary">{{ reason || '该批次未提供推荐说明。' }}</p><details v-if="highlights.length"><summary>完整评分依据与坐标来源</summary><p>{{ reason || '该批次未提供推荐说明。' }}</p></details></section>
</template>

<style scoped>
.recommend-reason { min-width: 0; padding-top: var(--space-4); border-top: 1px solid var(--surface-line); }
.reason-label { font-size: var(--text-support); font-weight: 600; color: var(--ink); margin-bottom: var(--space-2); }
ul { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: var(--space-2) var(--space-4); list-style: none; padding: 0; }
li { display: flex; gap: var(--space-2); font-size: var(--text-support); color: var(--ink-secondary); line-height: 1.6; overflow-wrap: anywhere; }
li span { color: var(--accent); font-weight: 600; }
details { font-size: var(--text-caption); color: var(--muted); line-height: 1.8; margin-top: var(--space-3); }
summary { cursor: pointer; }
.reason-summary { color: var(--ink-secondary); font-size: var(--text-support); line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
details p { margin-top: var(--space-3); white-space: pre-wrap; overflow-wrap: anywhere; }
@media(max-width:520px) { ul { grid-template-columns: minmax(0,1fr); } }
</style>
