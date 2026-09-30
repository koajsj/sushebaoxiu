<script setup lang="ts">
import { onBeforeUnmount,onMounted,ref,watch } from 'vue'
const props=defineProps<{label:string;value:number;suffix?:string;hint:string;index:number}>()
const shown=ref(props.value)
let frame=0
function animate(){
  cancelAnimationFrame(frame)
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){shown.value=props.value;return}
  const start=performance.now(),target=props.value
  function step(now:number){const p=Math.min(1,(now-start)/650),ease=1-(1-p)**3
    shown.value=Number((target*ease).toFixed(target%1?1:0))
    if(p<1)frame=requestAnimationFrame(step)
  }
  shown.value=0;frame=requestAnimationFrame(step)
}
onMounted(animate)
watch(()=>props.value,()=>{if(typeof window!=='undefined')animate()})
onBeforeUnmount(()=>cancelAnimationFrame(frame))
</script>
<template><article class="dashboard-card" :style="{'--entry-delay':`${Math.min(index,3)*75}ms`}"><span>{{ label }}</span><strong>{{ shown }}<small>{{ suffix }}</small></strong><p>{{ hint }}</p></article></template>
