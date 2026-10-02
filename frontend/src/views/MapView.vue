<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getMapBuildings,getMapOrders,getMapWorkers } from '../api/dispatch'
import type { CampusBuilding,MapOrder,MapWorker } from '../types/dispatch'
import { statusLabels } from '../types/repair'
import { createMapMarkers,findMarker } from '../utils/map'
import { projectCampusMarkers,campusLandmarkForMarker } from '../utils/campusMap'
import MapPanel from '../components/MapPanel.vue'
import LocationCard from '../components/LocationCard.vue'
const buildings=ref<CampusBuilding[]>([]),orders=ref<MapOrder[]>([]),workers=ref<MapWorker[]>([])
const loading=ref(true),error=ref(''),status=ref(''),total=ref(0),selectedKey=ref<string|null>(null)
const showBuildings=ref(true),showOrders=ref(true),showWorkers=ref(true)
let revision=0
const allMarkers=computed(()=>createMapMarkers(buildings.value,orders.value,workers.value))
const markers=computed(()=>allMarkers.value.filter(m=>({building:showBuildings.value,order:showOrders.value,worker:showWorkers.value})[m.kind]))
const selected=computed(()=>selectedKey.value?findMarker(markers.value,selectedKey.value):null)
const missing=computed(()=>markers.value.length-projectCampusMarkers(markers.value,allMarkers.value).length)
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
<template><section class="business-page map-page"><header class="page-heading"><div><p class="eyebrow">校园服务 · 空间分布</p><h1>校园任务地图</h1><p>教学区、生活区与维修任务一图呈现。虚构校园导览，不使用真实地址。</p></div><RouterLink class="text-button" to="/admin/dispatch">前往智能派单 →</RouterLink></header>
  <div class="map-filter-bar"><label>订单状态<select v-model="status" :disabled="loading" @change="load"><option value="">全部状态</option><option v-for="(label,value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label><div class="map-layer-controls"><label><input v-model="showBuildings" type="checkbox"/>建筑</label><label><input v-model="showOrders" type="checkbox"/>报修点</label><label><input v-model="showWorkers" type="checkbox"/>维修员</label></div><button class="secondary-button" :disabled="loading" @click="load">{{ loading?'加载中…':'刷新任务' }}</button></div>
  <div v-if="error" class="notice error" role="alert">{{ error }}<button class="text-button" @click="load">重试</button></div>
  <p v-if="loading&&!allMarkers.length" class="empty-state" role="status">正在读取校园楼栋与维修任务…</p>
  <template v-else-if="!error"><div class="map-layout" :class="{'has-selection':!!selected}"><MapPanel :markers="markers" :locations="allMarkers" :selected-key="selected?.key||null" @select="selectedKey=$event"/><LocationCard v-if="selected" :marker="selected"/></div>
    <div class="map-footnote"><span>{{ buildings.length }} 栋已建档建筑 · {{ orders.length }} 个报修点 · {{ workers.length }} 位维修员</span><span v-if="missing">{{ missing }} 项尚未匹配示意楼栋，可在位置列表查看</span><span v-if="total>orders.length">当前显示最新 {{ orders.length }} / {{ total }} 条订单，可按状态筛选</span></div>
    <details class="map-location-list"><summary>业务位置列表 · 包含未匹配楼栋的项目</summary><div><button v-for="marker in markers" :key="marker.key" :class="{selected:selected?.key===marker.key}" @click="selectedKey=marker.key"><span>{{ {building:'建筑',order:'报修',worker:'维修员'}[marker.kind] }}</span><strong>{{ marker.title }}</strong><small>{{ campusLandmarkForMarker(marker,allMarkers)?'查看示意位置':'未匹配示意楼栋' }}</small></button></div></details>
    <p class="recommend-disclaimer">底图为虚构校园示意图，楼栋名称用于展示分区。任务来自真实业务接口；未建档的示意建筑不代表数据库已有楼栋。维修员按静态配置就近匹配楼栋，不代表实时位置，不用于导航。</p>
  </template>
</section></template>
