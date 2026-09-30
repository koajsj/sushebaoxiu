<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getOverview,getTrend,getTypeStats,getWorkerStats } from '../api/phase5'
import { getMapBuildings,getMapOrders,getMapWorkers } from '../api/dispatch'
import type { Overview,TrendPoint,TypeCount,WorkerCount } from '../types/phase5'
import type { CampusBuilding,MapOrder,MapWorker,MapMarker } from '../types/dispatch'
import { findMarker } from '../utils/map'
import DashboardCard from '../components/DashboardCard.vue'
import ChartPanel from '../components/ChartPanel.vue'
import MapPanel from '../components/MapPanel.vue'
import LocationCard from '../components/LocationCard.vue'
const overview=ref<Overview|null>(null),trend=ref<TrendPoint[]>([]),types=ref<TypeCount[]>([]),workers=ref<WorkerCount[]>([])
const buildings=ref<CampusBuilding[]>([]),orders=ref<MapOrder[]>([]),people=ref<MapWorker[]>([])
const loading=ref(true),error=ref(''),selectedKey=ref<string|null>(null)
const markers=computed<MapMarker[]>(()=>[
  ...buildings.value.map(building=>({key:`building-${building.id}`,kind:'building' as const,title:building.name,longitude:building.longitude,latitude:building.latitude,building})),
  ...orders.value.map(order=>({key:`order-${order.id}`,kind:'order' as const,title:order.title,longitude:order.longitude,latitude:order.latitude,order})),
  ...people.value.map(worker=>({key:`worker-${worker.id}`,kind:'worker' as const,title:worker.name,longitude:worker.longitude,latitude:worker.latitude,worker})),
])
const selected=computed(()=>selectedKey.value?findMarker(markers.value,selectedKey.value):null)
const maxTrend=computed(()=>Math.max(1,...trend.value.flatMap(p=>[p.created,p.finished])))
const trendPath=(key:'created'|'finished')=>trend.value.map((point,index)=>`${index?'L':'M'} ${32+index*44} ${202-point[key]/maxTrend.value*155}`).join(' ')
const colors=['#3778c6','#7ba8df','#85aeb0','#c0c9df','#d8e4f1','#99a78e']
const typeTotal=computed(()=>types.value.reduce((sum,item)=>sum+item.count,0))
const pieSegments=computed(()=>{let offset=0;return types.value.map((item,index)=>{const length=typeTotal.value?item.count/typeTotal.value*376.99:0,segment={...item,color:colors[index%colors.length],length,offset};offset+=length;return segment}).filter(item=>item.count>0)})
const maxCompleted=computed(()=>Math.max(1,...workers.value.map(w=>w.completedCount)))
let revision=0
async function load(){const current=++revision;loading.value=true;error.value=''
  try{const [summary,points,kinds,staff,locations,tasks,positions]=await Promise.all([
    getOverview(),getTrend(),getTypeStats(),getWorkerStats(),getMapBuildings(),getMapOrders(),getMapWorkers(),
  ])
    if(current!==revision)return
    overview.value=summary;trend.value=points;types.value=kinds;workers.value=staff
    buildings.value=locations;orders.value=tasks.records;people.value=positions
  }catch(e){if(current===revision){overview.value=null;trend.value=[];types.value=[];workers.value=[];buildings.value=[];orders.value=[];people.value=[];selectedKey.value=null
    error.value=e instanceof Error?e.message:'数据加载失败'}}finally{if(current===revision)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{revision++})
</script>
<template><section class="business-page dashboard-page"><header class="page-heading"><div><p class="eyebrow">CAMPUS OPERATIONS · LIVE DATA</p><h1>校园服务驾驶舱</h1><p>从报修到完成，了解每一次校园服务的进展。</p></div><button class="secondary-button" :disabled="loading" @click="load">{{ loading?'更新中…':'刷新数据' }}</button></header>
  <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
  <p v-if="loading&&!overview" class="empty-state" role="status">正在汇总真实工单数据…</p>
  <template v-if="overview"><div class="dashboard-cards"><DashboardCard label="今日报修" :value="overview.todayCount" hint="今日新提交的工单" :index="0"/><DashboardCard label="当前处理中" :value="overview.activeCount" hint="已分配、维修中、待确认与返工待处理" :index="1"/><DashboardCard label="完成率" :value="overview.completionRate" suffix="%" hint="学生已确认 / 全部工单" :index="2"/><DashboardCard label="平均维修时间" :value="overview.averageRepairHours" suffix="小时" hint="每轮开始维修至提交结果" :index="3"/></div>
    <div class="dashboard-attention"><RouterLink to="/admin/orders?overdue=true">当前超时工单 <strong>{{ overview.overdueCount }}</strong> →</RouterLink><span>返工工单 <strong>{{ overview.reworkCount }}</strong> <small>含已返工与待处理</small></span></div><div class="dashboard-chart-grid"><ChartPanel title="报修趋势" description="最近14天 · 提交与维修结果提交"><svg viewBox="0 0 640 245" class="dashboard-line" role="img" aria-label="最近14天报修提交与完成趋势折线图"><line x1="32" y1="202" x2="606" y2="202" stroke="#d8e2ea"/><line x1="32" y1="47" x2="606" y2="47" stroke="#eef2f6"/><text x="0" y="51">{{ maxTrend }}</text><text x="0" y="205">0</text><path :d="trendPath('created')" fill="none" stroke="#3778c6" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><path :d="trendPath('finished')" fill="none" stroke="#82adb0" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><text x="32" y="230">{{ trend[0]?.date.slice(5) }}</text><text x="565" y="230">{{ trend.at(-1)?.date.slice(5) }}</text></svg><div class="chart-legend"><span class="legend-blue">新报修</span><span class="legend-teal">维修完成</span></div></ChartPanel>
      <ChartPanel title="故障类型" description="全量工单 · 按故障类型占比"><div class="donut-layout"><svg viewBox="0 0 180 180" role="img" aria-label="故障类型占比饼图"><circle cx="90" cy="90" r="60" fill="none" stroke="#edf1f5" stroke-width="32"/><circle v-for="segment in pieSegments" :key="segment.typeId" cx="90" cy="90" r="60" fill="none" :stroke="segment.color" stroke-width="32" :stroke-dasharray="`${segment.length} ${376.99-segment.length}`" :stroke-dashoffset="-segment.offset" transform="rotate(-90 90 90)"/><text x="90" y="86" text-anchor="middle" class="donut-value">{{ typeTotal }}</text><text x="90" y="105" text-anchor="middle" class="donut-caption">工单总数</text></svg><ul class="type-legend"><li v-for="(item,index) in types" :key="item.typeId"><span :style="{background:colors[index%colors.length]}"/><strong>{{ item.typeName }}</strong><small>{{ item.count }} · {{ item.percentage }}%</small></li></ul></div></ChartPanel></div>
    <ChartPanel title="维修效率" description="维修员完成工单数 · 评分来自学生真实评价"><p v-if="!workers.length" class="dashboard-empty">暂无维修人员数据</p><div v-else class="worker-bars"><div v-for="worker in workers" :key="worker.workerId" class="worker-bar"><span>{{ worker.workerName }}</span><div><i :style="{width:`${worker.completedCount?Math.max(3,worker.completedCount/maxCompleted*100):0}%`}"/></div><strong>{{ worker.completedCount }} 单</strong><small>评分 {{ worker.averageRating?`${worker.averageRating}/5`:'暂无' }} · 当前 {{ worker.activeCount }} 单</small></div></div></ChartPanel>
    <ChartPanel title="校园任务分布" description="复用校园坐标地图 · 静态位置，非实时定位"><template #action><RouterLink class="text-button" to="/admin/map">查看完整地图 →</RouterLink></template><div class="dashboard-map" :class="{'has-selection':!!selected}"><MapPanel :markers="markers" :selected-key="selected?.key||null" @select="selectedKey=$event"/><LocationCard v-if="selected" :marker="selected"/></div><p class="dashboard-map-note">开发环境使用示例坐标；地图显示最新 {{ orders.length }} 个报修点。</p></ChartPanel>
  </template>
</section></template>
