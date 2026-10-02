<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import MapPanel from '../MapPanel.vue'
import LocationCard from '../LocationCard.vue'
import type { MapMarker } from '../../types/dispatch'
import { findMarker } from '../../utils/map'
const props = defineProps<{ markers: MapMarker[]; orderCount: number }>()
const selectedKey = ref<string | null>(null)
const selected = computed(() => selectedKey.value ? findMarker(props.markers, selectedKey.value) : null)
</script>

<template>
  <section class="dashboard-campus-map">
    <header><div><h2>校园任务分布</h2><p>虚构校园导览 · 任务来自业务数据</p></div><RouterLink class="text-button" to="/admin/map">大图 ↗</RouterLink></header>
    <MapPanel compact :markers="markers" :locations="markers" :selected-key="selected?.key || null" @select="selectedKey = $event" />
    <div v-if="selected" class="map-detail"><button class="text-button" @click="selectedKey = null">收起详情</button><LocationCard :marker="selected" /></div>
    <p v-else class="map-guidance">点击任务标记查看详情；可放大地图阅读楼栋名称。</p>
    <p class="map-source">已读取接口返回的最新 {{ orderCount }} 个报修点；未匹配楼栋的任务可前往大图的位置列表查看。</p>
  </section>
</template>

<style scoped>
.dashboard-campus-map { min-width: 0; padding: var(--space-5); border: 1px solid var(--surface-line); border-radius: var(--radius-lg); background: var(--surface); box-shadow: var(--surface-shadow); }
header { display: flex; gap: var(--space-3); justify-content: space-between; align-items: flex-start; margin-bottom: var(--space-5); }h2 { font-size: var(--text-section); font-weight: 600; letter-spacing: -.025em; }header p { font-size: var(--text-caption); color: var(--muted); line-height: 1.65; margin-top: var(--space-2); }header a { flex-shrink: 0; font-size: var(--text-caption); }
:deep(.map-panel) { min-width: 0; border: 0; border-radius: var(--radius-sm); box-shadow: none; }:deep(.campus-map) { height: auto; min-height: 0; max-height: none; }:deep(.map-toolbar) { flex-wrap: wrap; padding: var(--space-3); font-size: var(--text-caption); gap: var(--space-2); }:deep(.map-toolbar > div) { margin-left: auto; }:deep(.map-legend) { flex-wrap: wrap; padding: var(--space-3); gap: var(--space-2) var(--space-3); }:deep(.map-legend small) { width: 100%; line-height: 1.6; }
.map-guidance,.map-source { color: var(--muted); font-size: var(--text-caption); line-height: 1.7; margin-top: var(--space-3); }.map-source { padding-top: var(--space-3); border-top: 1px solid var(--surface-line); }
.map-detail { padding-top: var(--space-3); }.map-detail > button { display: block; margin-left: auto; margin-bottom: var(--space-2); }:deep(.location-card) { padding: var(--space-4); background: var(--surface-subtle); border: 0; box-shadow: none; }:deep(.location-card h2) { font-size: 17px; }:deep(.location-card dd) { overflow-wrap: anywhere; }
@media (max-width: 740px) { .dashboard-campus-map { padding: var(--space-5); }:deep(.campus-map) { min-height: 0; } }
</style>
