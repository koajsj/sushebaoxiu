<script setup lang="ts">
import { ref } from 'vue'
import icon from '../assets/campus/brand-icon.svg'
const failed = ref(false)
</script>

<template>
  <span class="brand-icon-frame" aria-hidden="true">
    <img v-if="!failed" class="brand-icon" :src="icon" alt="" width="40" height="40" decoding="async" @error="failed = true" />
    <svg v-else class="brand-icon-fallback" viewBox="0 0 40 40" fill="none">
      <path d="M8 18 20 9l12 9M11 18v14h18V18M17 32v-9h6v9" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
      <path d="M26 26c6 0 8-5 8-9-6 0-10 4-10 9" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
    </svg>
  </span>
</template>

<style scoped>
.brand-icon-frame { display: inline-flex; flex-shrink: 0; width: 40px; height: 40px; border-radius: 10px; overflow: hidden; vertical-align: middle; }
.brand-icon, .brand-icon-fallback { display: block; width: 100%; height: 100%; object-fit: contain; }
.brand-icon-fallback { color: var(--accent); background: var(--accent-soft); }
</style>
