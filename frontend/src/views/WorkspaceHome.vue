<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { getOrders, getSummary } from '../api/repair'
import { useAuthStore } from '../store/auth'
import type { RepairOrder, Summary } from '../types/repair'
import WorkerHome from '../components/worker/WorkerHome.vue'
import StudentHome from '../components/student/StudentHome.vue'
const props = defineProps<{role:'student'|'worker'}>()
const studentView=computed(()=>props.role==='student')
const auth=useAuthStore()
const summary=ref<Summary|null>(null), orders=ref<RepairOrder[]>([]), loading=ref(true), error=ref('')
let revision=0
async function load() {
  const current=++revision;loading.value=true; error.value=''
  try { const [counts, page]=await Promise.all([getSummary(props.role),getOrders(props.role,{size:4})]);if(current!==revision)return;summary.value=counts;orders.value=page.records }
  catch(e){if(current===revision){summary.value=null;orders.value=[];error.value=e instanceof Error?e.message:props.role==='student'?'维修进度暂时无法读取，请重试。':'任务暂时无法读取，请重试。'}}
  finally {if(current===revision)loading.value=false}
}
onMounted(load)
onBeforeUnmount(()=>{revision++})
</script>
<template><StudentHome v-if="studentView" :name="auth.user?.realName||'同学'" :summary="summary" :orders="orders" :loading="loading" :error="error" @retry="load" /><WorkerHome v-else :name="auth.user?.realName||'维修人员'" :summary="summary" :orders="orders" :loading="loading" :error="error" @retry="load" /></template>
