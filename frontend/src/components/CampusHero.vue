<script setup lang="ts">
import { computed, ref } from 'vue'
import entranceWide from '../assets/campus/entrance-1440.jpg'
import entranceSmall from '../assets/campus/entrance-768.jpg'
import courtyardWide from '../assets/campus/courtyard-1440.jpg'
import courtyardSmall from '../assets/campus/courtyard-768.jpg'

const props = withDefaults(defineProps<{
  variant?: 'login' | 'banner'
  asset?: 'entrance' | 'courtyard'
  label: string
}>(), { variant: 'banner', asset: 'courtyard' })
const failed = ref(false)
const image = computed(() => props.asset === 'entrance'
  ? { wide: entranceWide, small: entranceSmall }
  : { wide: courtyardWide, small: courtyardSmall })
</script>

<template>
  <section class="campus-hero" :class="[`campus-hero--${variant}`, { 'campus-hero--fallback': failed }]" :aria-label="label">
    <picture v-if="!failed" class="campus-hero-media" aria-hidden="true">
      <source media="(max-width: 740px)" :srcset="image.small" />
      <img :src="image.wide" alt="" width="1440" height="810" decoding="async"
        :loading="variant === 'login' ? 'eager' : 'lazy'"
        :fetchpriority="variant === 'login' ? 'high' : 'auto'" @error="failed = true" />
    </picture>
    <div class="campus-hero-content"><slot /></div>
  </section>
</template>

<style scoped>
.campus-hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: flex;
  align-items: center;
  min-width: 0;
  border-radius: var(--radius-lg);
  background: var(--hero-base);
  color: var(--hero-ink);
}
.campus-hero-media, .campus-hero-media img, .campus-hero::after {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.campus-hero-media img { object-fit: cover; object-position: 58% 52%; }
.campus-hero::after { content: ''; background: var(--hero-banner-overlay); pointer-events: none; }
.campus-hero-content { position: relative; z-index: 1; width: 100%; padding: var(--space-7); }
.campus-hero--banner { min-height: 212px; }
.campus-hero--banner .campus-hero-content { max-width: 75%; }
.campus-hero--banner .campus-hero-media img { object-position: 58% 63%; }
.campus-hero--login { min-height: 548px; align-items: flex-end; }
.campus-hero--login::after { background: var(--hero-login-overlay); }
.campus-hero--login .campus-hero-content { padding: clamp(28px, 4vw, 52px); }
.campus-hero--fallback::after { background: transparent; }
@media (max-width: 1000px) {
  .campus-hero--login { min-height: 500px; }
}
@media (max-width: 740px) {
  .campus-hero--login { min-height: 280px; }
  .campus-hero--banner { min-height: 212px; }
  .campus-hero-content { padding: var(--space-5); }
  .campus-hero--banner .campus-hero-content { max-width: 100%; }
  .campus-hero--banner::after { background: var(--hero-login-overlay); }
}
</style>
