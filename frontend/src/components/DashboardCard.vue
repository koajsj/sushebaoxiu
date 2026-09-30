<script setup lang="ts">
import { onBeforeUnmount,onMounted,ref,watch } from 'vue'
const props=defineProps<{label:string;value:number;suffix?:string;hint:string;index:number}>()
const shown=ref(props.value)
let frame=0,mounted=false
function animate(){
  cancelAnimationFrame(frame)
  if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){shown.value=props.value;return}
  const start=performance.now(),target=props.value,from=shown.value
  function step(now:number){const p=Math.min(1,(now-start)/260),ease=1-(1-p)**3
    shown.value=Number((from+(target-from)*ease).toFixed(target%1?1:0))
    if(p<1)frame=requestAnimationFrame(step)
  }
  frame=requestAnimationFrame(step)
}
onMounted(()=>{mounted=true;shown.value=0;animate()})
watch(()=>props.value,()=>{if(mounted)animate()})
onBeforeUnmount(()=>{mounted=false;cancelAnimationFrame(frame)})
</script>
<template><article class="dashboard-card" :style="{'--entry-delay':`${Math.min(index,3)*35}ms`}"><span>{{ label }}</span><strong :aria-label="`${value}${suffix||''}`"><span aria-hidden="true">{{ shown }}<small>{{ suffix }}</small></span></strong><p>{{ hint }}</p></article></template>
