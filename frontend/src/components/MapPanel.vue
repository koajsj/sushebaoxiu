<script setup lang="ts">
import { computed,ref,watch } from 'vue'
import type { MapMarker } from '../types/dispatch'
import { constrainPan,projectMarkers } from '../utils/map'
const props=defineProps<{markers:MapMarker[];selectedKey:string|null}>()
const emit=defineEmits<{select:[key:string]}>()
const zoom=ref(1),pan=ref({x:0,y:0}),dragging=ref(false),svg=ref<SVGSVGElement|null>(null)
const points=computed(()=>projectMarkers(props.markers))
let origin={x:0,y:0,panX:0,panY:0}
const transform=computed(()=>`translate(${500+pan.value.x} ${320+pan.value.y}) scale(${zoom.value}) translate(-500 -320)`)
function setZoom(value:number){zoom.value=Math.max(1,Math.min(4,value));pan.value=constrainPan(pan.value.x,pan.value.y,zoom.value)}
function reset(){zoom.value=1;pan.value={x:0,y:0}}
function begin(event:PointerEvent){if(!svg.value||event.button!==0)return;const point=toSvg(event);if(!point)return;origin={x:point.x,y:point.y,panX:pan.value.x,panY:pan.value.y};dragging.value=true;svg.value.setPointerCapture(event.pointerId)}
function toSvg(event:PointerEvent){const matrix=svg.value?.getScreenCTM();if(!matrix)return null;return new DOMPoint(event.clientX,event.clientY).matrixTransform(matrix.inverse())}
function move(event:PointerEvent){if(!dragging.value)return;const point=toSvg(event);if(point)pan.value=constrainPan(origin.panX+point.x-origin.x,origin.panY+point.y-origin.y,zoom.value)}
function end(event:PointerEvent){dragging.value=false;if(svg.value?.hasPointerCapture(event.pointerId))svg.value.releasePointerCapture(event.pointerId)}
watch(()=>props.markers,reset)
</script>
<template><div class="map-panel">
  <div class="map-toolbar"><span>校园坐标地图<span class="map-subtitle">静态位置 · 同地点标记展开</span></span><div><button aria-label="缩小地图" :disabled="zoom===1" @click="setZoom(zoom-.5)">−</button><span>{{ zoom.toFixed(1) }}×</span><button aria-label="放大地图" :disabled="zoom===4" @click="setZoom(zoom+.5)">＋</button><button @click="reset">复位</button></div></div>
  <svg ref="svg" class="campus-map" :class="{dragging}" viewBox="0 0 1000 640" role="group" aria-label="校园建筑、报修点及维修人员坐标地图" @pointerdown="begin" @pointermove="move" @pointerup="end" @pointercancel="end">
    <defs><pattern id="campus-coordinate-grid" width="40" height="40" patternUnits="userSpaceOnUse"><path d="M 40 0 L 0 0 0 40" fill="none" stroke="#d9e2df" stroke-width=".65"/></pattern></defs>
    <rect width="1000" height="640" fill="#edf2ee"/><rect width="1000" height="640" fill="url(#campus-coordinate-grid)"/>
    <g :transform="transform"><template v-for="point in points" :key="point.key">
      <line v-if="point.x!==point.anchorX||point.y!==point.anchorY" :x1="point.anchorX" :y1="point.anchorY" :x2="point.x" :y2="point.y" stroke="#9baab2" stroke-width="1"/>
      <g class="map-marker" :class="[point.kind,{selected:selectedKey===point.key}]" :transform="`translate(${point.x} ${point.y})`" role="button" tabindex="0" :aria-label="`${{building:'建筑',order:'报修',worker:'维修员'}[point.kind]}：${point.title}`" :aria-pressed="selectedKey===point.key" @pointerdown.stop @click.stop="emit('select',point.key)" @keydown.enter.prevent="emit('select',point.key)" @keydown.space.prevent="emit('select',point.key)">
        <circle v-if="point.kind!=='building'" r="11"/><rect v-else x="-12" y="-10" width="24" height="20" rx="5"/>
        <text text-anchor="middle" dominant-baseline="central" class="marker-glyph">{{ {building:'▤',order:'!',worker:'人'}[point.kind] }}</text>
        <title>{{ point.title }} · {{ point.longitude?.toFixed(6) }}, {{ point.latitude?.toFixed(6) }}</title>
      </g>
    </template></g>
    <g transform="translate(949 45)" aria-hidden="true"><path d="M0 -17 L-6 4 L0 0 L6 4 Z" fill="#62776b"/><text y="20" text-anchor="middle" fill="#62776b" font-size="12">N</text></g>
  </svg>
  <p v-if="!points.length" class="map-empty">没有可显示的坐标，请先配置楼栋和人员静态位置。</p>
  <div class="map-legend"><span class="legend-building">校园建筑</span><span class="legend-order">报修点</span><span class="legend-worker">维修人员</span><small>点击标记查看详情 · 放大后拖动平移 · WGS84</small></div>
</div></template>
