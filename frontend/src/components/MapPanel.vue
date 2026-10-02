<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import type { MapMarker } from '../types/dispatch'
import { constrainPan,layoutMarkerLabels } from '../utils/map'
import { campusLandmarks,projectCampusMarkers } from '../utils/campusMap'
import campusPlan from '../assets/campus/campus-plan-v1.jpg'
const props=defineProps<{markers:MapMarker[];selectedKey:string|null;locations?:MapMarker[];compact?:boolean}>()
const emit=defineEmits<{select:[key:string]}>()
const zoom=ref(1),pan=ref({x:0,y:0}),dragging=ref(false),svg=ref<SVGSVGElement|null>(null)
const points=computed(()=>projectCampusMarkers(props.markers,props.locations||props.markers))
const hoveredKey=ref<string|null>(null),focusedKey=ref<string|null>(null)
const activeKey=computed(()=>hoveredKey.value||focusedKey.value||props.selectedKey)
const imageFailed=ref(false)
const landmarkLabels=computed(()=>campusLandmarks.map(landmark=>{
  const text=props.compact?landmark.name.replace('学生','').replace('校园','').replace('教学楼','教学').replace('号楼','栋'):landmark.name
  const fontSize=props.compact?26:16
  const width=Array.from(text).reduce((size,char)=>size+(/^[\x00-\x7f]$/.test(char)?fontSize*.6:fontSize),24)
  const height=props.compact?38:28
  return {...landmark,text,fontSize,width,height,labelX:landmark.x-width/2,labelY:landmark.y+27}
}))
const landmarkBounds=computed(()=>landmarkLabels.value.map(landmark=>({x:landmark.labelX,y:landmark.labelY,width:landmark.width,height:landmark.height})))
const labels=computed(()=>layoutMarkerLabels(points.value.filter(point=>point.kind!=='building'),activeKey.value,landmarkBounds.value))
let origin={x:0,y:0,panX:0,panY:0}
const transform=computed(()=>`translate(${500+pan.value.x} ${320+pan.value.y}) scale(${zoom.value}) translate(-500 -320)`)
function setZoom(value:number){zoom.value=Math.max(1,Math.min(4,value));pan.value=constrainPan(pan.value.x,pan.value.y,zoom.value)}
function reset(){zoom.value=1;pan.value={x:0,y:0}}
function begin(event:PointerEvent){if(!svg.value||event.button!==0)return;const point=toSvg(event);if(!point)return;origin={x:point.x,y:point.y,panX:pan.value.x,panY:pan.value.y};dragging.value=true;svg.value.setPointerCapture(event.pointerId)}
function toSvg(event:PointerEvent){const matrix=svg.value?.getScreenCTM();if(!matrix)return null;return new DOMPoint(event.clientX,event.clientY).matrixTransform(matrix.inverse())}
function move(event:PointerEvent){if(!dragging.value)return;const point=toSvg(event);if(point)pan.value=constrainPan(origin.panX+point.x-origin.x,origin.panY+point.y-origin.y,zoom.value)}
function end(event:PointerEvent){dragging.value=false;if(svg.value?.hasPointerCapture(event.pointerId))svg.value.releasePointerCapture(event.pointerId)}
watch(()=>props.markers,()=>{reset();hoveredKey.value=null;focusedKey.value=null})
</script>
<template><div class="map-panel">
  <div class="map-toolbar"><span>校园导览图<span class="map-subtitle">虚构校园 · 楼栋示意 · 非实时定位</span></span><div><button aria-label="缩小地图" :disabled="zoom===1" @click="setZoom(zoom-.5)">−</button><span>{{ zoom.toFixed(1) }}×</span><button aria-label="放大地图" :disabled="zoom===4" @click="setZoom(zoom+.5)">＋</button><button @click="reset">复位</button></div></div>
  <svg ref="svg" class="campus-map" :class="{dragging}" viewBox="0 0 1000 640" role="group" aria-label="虚构校园导览图，展示教学楼、宿舍、食堂及维修任务" @pointerdown="begin" @pointermove="move" @pointerup="end" @pointercancel="end">
    <rect width="1000" height="640" fill="var(--map-canvas)"/>
    <g :transform="transform">
    <image v-if="!imageFailed" :href="campusPlan" width="1000" height="640" preserveAspectRatio="xMidYMid meet" aria-hidden="true" @error="imageFailed=true"/>
    <g v-for="landmark in landmarkLabels" :key="landmark.id" class="campus-landmark" :class="{active:points.some(point=>point.key===activeKey&&point.anchorX===landmark.x&&point.anchorY===landmark.y)}">
      <rect :x="landmark.labelX" :y="landmark.labelY" :width="landmark.width" :height="landmark.height" rx="8"/>
      <text :x="landmark.x" :y="landmark.labelY+landmark.height/2" text-anchor="middle" dominant-baseline="central" class="map-marker-label" :style="{fontSize:`${landmark.fontSize}px`}">{{ landmark.text }}</text>
    </g>
    <template v-for="point in points" :key="point.key">
      <line v-if="point.x!==point.anchorX||point.y!==point.anchorY" :x1="point.anchorX" :y1="point.anchorY" :x2="point.x" :y2="point.y" stroke="var(--map-connector)" stroke-width="1"/>
      <g class="map-marker" :class="[point.kind,{selected:selectedKey===point.key}]" :transform="`translate(${point.x} ${point.y})`" role="button" tabindex="0" :aria-label="`${{building:'建筑',order:'报修',worker:'维修员'}[point.kind]}：${point.title}`" :aria-pressed="selectedKey===point.key" @pointerenter="hoveredKey=point.key" @pointerleave="hoveredKey=null" @focus="focusedKey=point.key" @blur="focusedKey=null" @pointerdown.stop @click.stop="emit('select',point.key)" @keydown.enter.prevent="emit('select',point.key)" @keydown.space.prevent="emit('select',point.key)">
        <circle r="20" fill="transparent"/>
        <circle v-if="point.kind!=='building'" class="marker-body" r="16"/><rect v-else class="marker-body" x="-16" y="-15" width="32" height="30" rx="6"/>
        <g class="marker-icon" aria-hidden="true">
          <template v-if="point.kind==='building'"><path d="M-8 9V-9H8V9ZM-3 9V3H3V9M-4-5H-3M3-5H4M-4-1H-3M3-1H4"/></template>
          <template v-else-if="point.kind==='worker'"><circle cy="-5" r="4"/><path d="M-8 9V7a8 8 0 0 1 16 0v2"/></template>
          <template v-else><path d="M0-8V2"/><circle cy="8" r="1"/></template>
        </g>
        <title>{{ point.title }} · 校园示意位置</title>
      </g>
    </template>
    <g v-for="label in labels" :key="label.key" class="map-label" :class="{active:activeKey===label.key}" aria-hidden="true">
      <rect :x="label.x" :y="label.y" :width="label.width" :height="label.height" rx="6"/>
      <text :x="label.x+label.width/2" :y="label.y+label.height/2" text-anchor="middle" dominant-baseline="central" class="map-marker-label">{{ label.text }}</text>
    </g></g>
    <g transform="translate(949 45)" aria-hidden="true"><path d="M0 -17 L-6 4 L0 0 L6 4 Z" fill="var(--map-compass)"/><text y="20" text-anchor="middle" fill="var(--map-compass)" font-size="14">北</text></g>
  </svg>
  <p v-if="imageFailed" class="map-asset-error" role="alert">校园底图加载失败，楼栋名称和任务标记仍可查看。</p>
  <div class="map-legend"><span class="legend-building">校园建筑</span><span class="legend-order">报修点</span><span class="legend-worker">维修人员</span><small>点击标记查看业务详情 · 放大后可拖动 · 未标注位置请看列表</small></div>
</div></template>
