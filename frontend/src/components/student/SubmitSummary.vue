<script setup lang="ts">
import ProtectedImage from '../ProtectedImage.vue'
import { priorityLabels, type RepairOrder } from '../../types/repair'
defineProps<{ typeName: string; title: string; description: string; location: string; priority: RepairOrder['priority']; imageUrl: string }>()
</script>

<template>
  <section class="student-submit-summary" aria-label="报修提交前摘要">
    <h3>{{ title }}</h3><p class="pre-wrap student-summary-description">{{ description }}</p>
    <dl><div><dt>故障类型</dt><dd>{{ typeName }}</dd></div><div><dt>报修地点</dt><dd>{{ location }}</dd></div><div><dt>紧急程度</dt><dd>{{ priorityLabels[priority] }}</dd></div><div><dt>现场图片</dt><dd>{{ imageUrl ? '1 张' : '未添加' }}</dd></div></dl>
    <ProtectedImage v-if="imageUrl" :url="imageUrl" alt="即将提交的现场照片" />
    <p class="field-hint">提交后进入管理员审核，处理进展会保存到这份工单。</p>
  </section>
</template>
