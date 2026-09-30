<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { MapMarker } from '../types/dispatch'
import { statusLabels } from '../types/repair'
import { formatTime } from '../utils/format'
import { validLocation } from '../utils/map'
defineProps<{marker:MapMarker|null}>()
</script>
<template><aside class="location-card detail-surface">
  <template v-if="marker"><p class="eyebrow">{{ {building:'CAMPUS BUILDING',order:'REPAIR LOCATION',worker:'SERVICE LOCATION'}[marker.kind] }}</p><h2>{{ marker.title }}</h2>
    <template v-if="marker.order"><span class="status-pill" :class="`status-${marker.order.status.toLowerCase()}`">{{ statusLabels[marker.order.status] }}</span><dl><dt>报修地点</dt><dd>{{ marker.order.address }}</dd><dt>故障类型</dt><dd>{{ marker.order.typeName }}</dd><dt>提交时间</dt><dd>{{ formatTime(marker.order.createTime) }}</dd></dl><RouterLink class="text-button" :to="`/admin/orders/${marker.order.id}`">查看完整订单 →</RouterLink><RouterLink v-if="marker.order.status==='WAIT_ASSIGN'&&!marker.order.workerId" class="primary-button" :to="`/admin/dispatch?orderId=${marker.order.id}`">推荐维修人员</RouterLink></template>
    <template v-else-if="marker.worker"><span class="status-pill" :class="marker.worker.status==='BUSY'?'status-processing':'status-finished'">{{ marker.worker.status==='BUSY'?'有待处理任务':'暂无待处理任务' }}</span><dl><dt>技能领域</dt><dd>{{ marker.worker.skillType }}</dd><dt>当前任务</dt><dd>{{ marker.worker.activeTaskCount }} 项</dd><dt>位置来源</dt><dd>已配置的静态服务位置</dd></dl></template>
    <template v-else-if="marker.building"><dl><dt>建筑类型</dt><dd>{{ ({DORMITORY:'宿舍',TEACHING:'教学楼',LIBRARY:'图书馆'} as Record<string,string>)[marker.building.type]||marker.building.type }}</dd></dl></template>
    <div class="location-coordinate"><span>经纬度 · WGS84</span><p v-if="validLocation(marker.longitude,marker.latitude)">{{ marker.longitude?.toFixed(7) }}, {{ marker.latitude?.toFixed(7) }}</p><p v-else>尚未配置有效坐标</p></div>
  </template><template v-else><span class="location-placeholder" aria-hidden="true">⌖</span><h2>从一个地点开始</h2><p class="muted">选择地图标记或下方位置列表，查看建筑、维修任务和人员信息。</p></template>
</aside></template>
