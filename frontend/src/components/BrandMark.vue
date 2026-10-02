<script setup lang="ts">
import BrandIcon from './BrandIcon.vue'
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <span class="brand-mark">
    <BrandIcon />
    <span v-if="!compact" class="brand-copy">
      <strong>校园智能报修</strong>
      <span>Campus Repair</span>
    </span>
  </span>
</template>
