<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getMapBuildings,getMapOrders,getMapWorkers } from '../api/dispatch'
import type { CampusBuilding,MapMarker,MapOrder,MapWorker } from '../types/dispatch'
import { statusLabels } from '../types/repair'
import { findMarker,validLocation } from '../utils/map'
import MapPanel from '../components/MapPanel.vue'
import LocationCard from '../components/LocationCard.vue'
const buildings=ref<CampusBuilding[]>([]),orders=ref<MapOrder[]>([]),workers=ref<MapWorker[]>([])
const loading=ref(true),error=ref(''),status=ref(''),total=ref(0),selectedKey=ref<string|null>(null)
const showBuildings=ref(true),showOrders=ref(true),showWorkers=ref(true)
let revision=0
const allMarkers=computed<MapMarker[]>(()=>[
  ...buildings.value.map(building=>({key:`building-${building.id}`,kind:'building' as const,title:building.name,longitude:building.longitude,latitude:building.latitude,building})),
  ...orders.value.map(order=>({key:`order-${order.id}`,kind:'order' as const,title:order.title,longitude:order.longitude,latitude:order.latitude,order})),
  ...workers.value.map(worker=>({key:`worker-${worker.id}`,kind:'worker' as const,title:worker.name,longitude:worker.longitude,latitude:worker.latitude,worker})),
])
const markers=computed(()=>allMarkers.value.filter(m=>({building:showBuildings.value,order:showOrders.value,worker:showWorkers.value})[m.kind]))
const selected=computed(()=>selectedKey.value?findMarker(markers.value,selectedKey.value):null)
const missing=computed(()=>markers.value.filter(m=>!validLocation(m.longitude,m.latitude)).length)
async function load(){const current=++revision;loading.value=true;error.value=''
  try{const [locations,tasks,people]=await Promise.all([getMapBuildings(),getMapOrders(status.value),getMapWorkers()]);if(current!==revision)return
    buildings.value=locations;orders.value=tasks.records;workers.value=people;total.value=tasks.total
    if(selectedKey.value&&!findMarker(allMarkers.value,selectedKey.value))selectedKey.value=null
  }catch(e){if(current===revision){buildings.value=[];orders.value=[];workers.value=[];total.value=0;selectedKey.value=null;error.value=e instanceof Error?e.message:'地图数据加载失败'}}
  finally{if(current===revision)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{revision++})
</script>
<template><section class="business-page map-page"><header class="page-heading"><div><p class="eyebrow">校园服务 · 空间分布</p><h1>校园任务地图</h1><p>从地点看见任务，从任务了解进度。静态服务坐标，无实时定位。</p></div><RouterLink class="text-button" to="/admin/dispatch">前往智能派单 →</RouterLink></header>
  <div class="map-filter-bar"><label>订单状态<select v-model="status" :disabled="loading" @change="load"><option value="">全部状态</option><option v-for="(label,value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label><div class="map-layer-controls"><label><input v-model="showBuildings" type="checkbox"/>建筑</label><label><input v-model="showOrders" type="checkbox"/>报修点</label><label><input v-model="showWorkers" type="checkbox"/>维修员</label></div><button class="secondary-button" :disabled="loading" @click="load">{{ loading?'加载中…':'刷新位置' }}</button></div>
  <div v-if="error" class="notice error" role="alert">{{ error }}<button class="text-button" @click="load">重试</button></div>
  <p v-if="loading&&!allMarkers.length" class="empty-state" role="status">正在读取校园坐标与任务…</p>
  <template v-else-if="!error"><div class="map-layout" :class="{'has-selection':!!selected}"><MapPanel :markers="markers" :selected-key="selected?.key||null" @select="selectedKey=$event"/><LocationCard v-if="selected" :marker="selected"/></div>
    <div class="map-footnote"><span>{{ buildings.length }} 栋建筑 · {{ orders.length }} 个报修点 · {{ workers.length }} 位维修员</span><span v-if="missing">{{ missing }} 个位置未配置有效坐标，未生成标记</span><span v-if="total>orders.length">当前显示最新 {{ orders.length }} / {{ total }} 条订单，可按状态筛选</span></div>
    <details class="map-location-list"><summary>位置列表 · 包含未配置坐标的项目</summary><div><button v-for="marker in markers" :key="marker.key" :class="{selected:selected?.key===marker.key}" @click="selectedKey=marker.key"><span>{{ {building:'建筑',order:'报修',worker:'维修员'}[marker.kind] }}</span><strong>{{ marker.title }}</strong><small>{{ validLocation(marker.longitude,marker.latitude)?'查看位置':'待配置坐标' }}</small></button></div></details>
    <p class="recommend-disclaimer">开发初始化使用示例校园坐标；用于展示空间关系，不代表实际校园边界。正式使用前请配置实际 WGS84 坐标。</p>
  </template>
</section></template>
