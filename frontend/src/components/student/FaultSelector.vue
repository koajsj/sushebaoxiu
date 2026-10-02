<script setup lang="ts">
import type { Catalog } from '../../types/repair'
defineProps<{ types: Catalog['types']; modelValue: number }>()
defineEmits<{ 'update:modelValue': [value: number] }>()
</script>

<template>
  <div v-if="types.length" class="student-fault-grid" role="group" aria-label="选择故障类型">
    <button v-for="type in types" :key="type.id" class="student-fault-choice" type="button" :class="{ selected: modelValue === type.id }" :aria-pressed="modelValue === type.id" @click="$emit('update:modelValue', type.id)">
      <span class="student-fault-symbol" aria-hidden="true">{{ type.name.slice(0, 1) }}</span><span class="student-fault-copy"><strong>{{ type.name }}</strong><small>{{ type.description || '选择此类故障，继续填写具体问题。' }}</small></span><span class="student-fault-check" aria-hidden="true">{{ modelValue === type.id ? '✓' : '○' }}</span>
    </button>
  </div>
  <p v-else class="student-calm-state">暂无可选故障类型，请稍后重试。</p>
</template>
