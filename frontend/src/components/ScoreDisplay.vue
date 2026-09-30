<script setup lang="ts">
import { onBeforeUnmount,onMounted,ref,watch } from 'vue'
const props=defineProps<{value:number;label?:string;large?:boolean}>()
const displayed=ref(props.value)
let frame=0,mounted=false
function animate() {
  if(!mounted) return
  cancelAnimationFrame(frame)
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches) {displayed.value=props.value;return}
  const from=displayed.value,target=props.value,start=performance.now()
  const tick=(time:number)=>{const progress=Math.min(1,(time-start)/480);displayed.value=from+(target-from)*(1-Math.pow(1-progress,3));if(progress<1)frame=requestAnimationFrame(tick)}
  frame=requestAnimationFrame(tick)
}
onMounted(()=>{mounted=true;displayed.value=0;animate()})
watch(()=>props.value,animate)
onBeforeUnmount(()=>{mounted=false;cancelAnimationFrame(frame)})
</script>
<template><span class="score-display" :class="{large}" :aria-label="`${label||'评分'} ${value.toFixed(2)}分`"><strong aria-hidden="true">{{ displayed.toFixed(1) }}</strong><span>{{ label||'综合评分' }}</span></span></template>
