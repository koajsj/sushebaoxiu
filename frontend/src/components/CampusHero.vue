<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import entranceWide from '../assets/campus/plaza-1440.webp'
import entranceSmall from '../assets/campus/plaza-768.webp'
import courtyardWide from '../assets/campus/dormitory-1440.webp'
import courtyardSmall from '../assets/campus/dormitory-768.webp'
import workerWide from '../assets/campus/maintenance-1440.webp'
import workerSmall from '../assets/campus/maintenance-768.webp'
import serviceWide from '../assets/campus/service-space-1440.webp'
import serviceSmall from '../assets/campus/service-space-768.webp'

const props = withDefaults(defineProps<{
  variant?: 'login' | 'banner' | 'support'
  asset?: 'entrance' | 'courtyard' | 'worker' | 'service'
  label: string
}>(), { variant: 'banner', asset: 'courtyard' })
const failed = ref(false)
const images = {
  entrance: { wide: entranceWide, small: entranceSmall },
  courtyard: { wide: courtyardWide, small: courtyardSmall },
  worker: { wide: workerWide, small: workerSmall },
  service: { wide: serviceWide, small: serviceSmall },
}
const image = computed(() => images[props.asset])
watch(() => props.asset, () => { failed.value = false })
</script>

<template>
  <section class="campus-hero" :class="[`campus-hero--${variant}`, `campus-hero--${asset}`, { 'campus-hero--fallback': failed }]" :aria-label="label">
    <picture v-if="!failed" class="campus-hero-media" aria-hidden="true">
      <img :src="image.wide" :srcset="`${image.small} 768w, ${image.wide} 1440w`"
        :sizes="variant === 'login' ? '(max-width: 740px) 100vw, 55vw' : variant === 'support' ? '(max-width: 740px) 180px, 280px' : '(max-width: 740px) 100vw, (max-width: 1366px) 75vw, 1280px'"
        alt="" width="1440" height="810" decoding="async"
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
.campus-hero-media img { object-fit: cover; object-position: 50% 55%; }
.campus-hero::after { content: ''; background: var(--hero-banner-overlay); pointer-events: none; }
.campus-hero-content { position: relative; z-index: 1; width: 100%; padding: var(--space-7); }
.campus-hero--banner { min-height: 176px; --hero-ink: var(--ink); --hero-muted: var(--muted); background: var(--surface); border: 1px solid var(--surface-line); }
.campus-hero--banner .campus-hero-media { left: auto; width: 52%; }
.campus-hero--banner::after { background: linear-gradient(90deg, var(--surface) 48%, rgb(255 255 255 / 96%) 55%, rgb(255 255 255 / 10%) 75%, transparent); }
.campus-hero--banner .campus-hero-content { max-width: 64%; padding: 28px var(--space-6); }
.campus-hero--courtyard .campus-hero-media img { object-position: 44% 58%; }
.campus-hero--service .campus-hero-media img { object-position: 70% 52%; }
.campus-hero--login { min-height: clamp(440px, 65dvh, 520px); align-items: flex-end; }
.campus-hero--login .campus-hero-media img { object-position: 49% 52%; }
.campus-hero--login::after { background: var(--hero-login-overlay); }
.campus-hero--login .campus-hero-content { padding: clamp(28px, 4vw, 52px); }
.campus-hero--fallback::after { background: transparent; }
.campus-hero--support { width: 280px; aspect-ratio: 16 / 10; flex-shrink: 0; align-self: center; }
.campus-hero--support .campus-hero-media img { object-position: 64% 52%; }
.campus-hero--support::after { display: none; }
.campus-hero--support .campus-hero-content { padding: var(--space-4); }
.campus-hero--support:not(.campus-hero--fallback) .campus-hero-content { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
@media (max-width: 1100px) { .campus-hero--support { width: 220px; } }
@media (max-width: 1000px) {
  .campus-hero--login { min-height: 440px; }
}
@media (max-width: 740px) {
  .campus-hero--login { min-height: 280px; }
  .campus-hero--banner { min-height: 160px; }
  .campus-hero--support { width: 180px; aspect-ratio: 16 / 9; }
  .campus-hero-content { padding: var(--space-5); }
  .campus-hero--banner .campus-hero-content { max-width: 100%; }
  .campus-hero--banner { --hero-ink: var(--ink); --hero-muted: var(--muted); }
  .campus-hero--banner .campus-hero-media { width: 48%; opacity: .35; }
  .campus-hero--banner::after { background: linear-gradient(90deg, var(--surface) 45%, rgb(255 255 255 / 85%) 65%, rgb(255 255 255 / 65%)); }
}
.campus-hero--banner.campus-hero--fallback { --hero-ink: var(--ink); --hero-muted: var(--muted); }
.campus-hero--banner.campus-hero--fallback::after { background: transparent; }
</style>
