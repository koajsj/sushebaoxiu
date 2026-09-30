<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <span class="brand-mark">
    <span class="brand-symbol" aria-hidden="true">C</span>
    <span v-if="!compact" class="brand-copy">
      <strong>校园智能报修</strong>
      <span>Campus Repair</span>
    </span>
  </span>
</template>
