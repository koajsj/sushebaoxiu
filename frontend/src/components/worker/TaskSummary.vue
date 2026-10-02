<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Summary } from '../../types/repair'
defineProps<{ summary: Summary | null; loading: boolean }>()
</script>

<template>
  <section class="worker-task-summary" aria-label="维修任务概览" :aria-busy="loading">
    <RouterLink to="/worker/orders"><span>今日派单</span><strong>{{ !loading&&summary?summary.today:'—' }}</strong><small>今日分配、当前由你负责的任务</small></RouterLink>
    <RouterLink to="/worker/orders?phase=WAIT_ACCEPT"><span>待接单与待开工</span><strong>{{ !loading&&summary?summary.pending:'—' }}</strong><small>待处理汇总 · 点击查看待接单</small></RouterLink>
    <RouterLink to="/worker/orders?phase=PROCESSING"><span>在途任务</span><strong>{{ !loading&&summary?summary.active:'—' }}</strong><small>维修、待验收与待返工 · 点击筛选维修中</small></RouterLink>
    <RouterLink to="/worker/orders?phase=FINISHED"><span>累计完成</span><strong>{{ !loading&&summary?summary.completed:'—' }}</strong><small>累计完成及评价 · 点击查看已完成</small></RouterLink>
  </section>
</template>
