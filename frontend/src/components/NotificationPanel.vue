<script setup lang="ts">
import { computed,onMounted,onBeforeUnmount,ref,useId,watch } from 'vue'
import { getNotifications,markNotificationRead,markAllNotificationsRead } from '../api/phase5'
import type { NotificationList } from '../types/phase5'
import '../assets/collaboration-experience.css'
import NotificationItem from './notifications/NotificationItem.vue'
import { useAuthStore } from '../store/auth'
const auth=useAuthStore()
const open=ref(false),loading=ref(false),busy=ref(0),error=ref(''),data=ref<NotificationList>({records:[],total:0,unreadCount:0,page:1,size:20,latestId:0})
const page=ref(1),unreadOnly=ref(false)
const host=ref<HTMLElement|null>(null),toggleButton=ref<HTMLButtonElement|null>(null),panelId=useId()
const copy=computed(()=>({student:{subtitle:'审核、上门与验收进展',empty:'还没有维修通知。提交报修后，审核与维修进展会显示在这里。'},worker:{subtitle:'新任务与维修进展提醒',empty:'还没有任务通知。新的维修安排会显示在这里。'},admin:{subtitle:'新报修与运营提醒',empty:'还没有运营通知。新的报修与任务提醒会显示在这里。'}}[auth.role || 'student']))
let revision=0
let mounted=false
async function load(){const current=++revision;loading.value=true;error.value=''
  try{let result=await getNotifications(page.value,unreadOnly.value);if(current!==revision)return
    const lastPage=Math.max(1,Math.ceil(result.total/result.size))
    if(page.value>lastPage){page.value=lastPage;result=await getNotifications(page.value,unreadOnly.value)}
    if(current===revision)data.value=result}
  catch(e){if(current===revision)error.value=e instanceof Error?e.message:'通知加载失败'}
  finally{if(current===revision)loading.value=false}
}
function toggle(){open.value=!open.value;if(open.value){page.value=1;void load()}}
function show(){if(!open.value){open.value=true;page.value=1;void load()}toggleButton.value?.focus()}
defineExpose({show,opened:open,panelId})
function changeFilter(value:boolean){unreadOnly.value=value;page.value=1;void load()}
function changePage(value:number){page.value=value;void load()}
function outside(event:PointerEvent){if(event.target instanceof Node&&!host.value?.contains(event.target))open.value=false}
function escape(event:KeyboardEvent){if(event.key==='Escape'&&open.value){open.value=false;toggleButton.value?.focus()}}
async function read(id:number){if(busy.value)return;const accountId=auth.user?.id;busy.value=id;error.value=''
  try{await markNotificationRead(id);if(accountId===auth.user?.id)await load()}
  catch(e){if(accountId===auth.user?.id)error.value=e instanceof Error?e.message:'标记已读失败'}
  finally{if(accountId===auth.user?.id)busy.value=0}
}
async function readAll(){if(busy.value||!data.value.latestId||!data.value.unreadCount)return
  const accountId=auth.user?.id;busy.value=-1;error.value=''
  try{await markAllNotificationsRead(data.value.latestId);if(accountId===auth.user?.id){page.value=1;await load()}}
  catch(e){if(accountId===auth.user?.id)error.value=e instanceof Error?e.message:'全部已读失败'}finally{if(accountId===auth.user?.id)busy.value=0}
}
watch(()=>auth.user?.id,()=>{revision++;open.value=false;page.value=1;unreadOnly.value=false;busy.value=0;error.value='';data.value={records:[],total:0,unreadCount:0,page:1,size:20,latestId:0};if(mounted&&auth.user)void load()})
onMounted(()=>{mounted=true;void load();document.addEventListener('pointerdown',outside);document.addEventListener('keydown',escape)})
onBeforeUnmount(()=>{mounted=false;revision++;document.removeEventListener('pointerdown',outside);document.removeEventListener('keydown',escape)})
</script>
<template><div ref="host" class="notification-host message-center"><button ref="toggleButton" class="notification-toggle" type="button" :aria-expanded="open" :aria-controls="panelId" :aria-label="`系统通知，${data.unreadCount}条未读`" @click="toggle">通知 <span v-if="data.unreadCount" class="notification-badge">{{ data.unreadCount>99?'99+':data.unreadCount }}</span></button>
  <div v-if="open" :id="panelId" class="notification-panel" role="region" aria-label="系统通知" :aria-busy="loading"><header><div class="notification-heading"><strong>消息中心</strong><small>{{ copy.subtitle }}</small></div><div><button type="button" :disabled="loading||busy!==0" @click="load">{{ loading?'刷新中…':'刷新' }}</button><button type="button" aria-label="关闭通知面板" @click="open=false;toggleButton?.focus()">关闭</button></div></header>
    <div class="notification-tools"><button type="button" :aria-pressed="!unreadOnly" @click="changeFilter(false)">全部</button><button type="button" :aria-pressed="unreadOnly" @click="changeFilter(true)">未读 {{ data.unreadCount }}</button><button type="button" :disabled="busy!==0||!data.unreadCount||loading" @click="readAll">全部已读</button></div>
    <p class="notification-count" aria-live="polite">{{ data.unreadCount }} 条未读 · {{ data.total }} 条{{ unreadOnly?'未读消息':'消息' }}<span v-if="busy" role="status"> · {{ busy===-1?'正在标记全部已读…':'正在标记已读…' }}</span></p><p v-if="error" class="notification-error" role="alert">{{ error }} <button type="button" :disabled="loading||busy!==0" @click="load">重新读取通知</button></p><p v-if="loading&&!data.records.length" class="notification-empty">正在读取通知…</p>
    <p v-else-if="!data.records.length&&!error" class="notification-empty">{{ unreadOnly?'未读消息已处理，历史通知可在「全部」中查看。':copy.empty }}</p>
    <ul v-else><NotificationItem v-for="item in data.records" :key="item.id" :item="item" :busy="busy" @read="read" /></ul>
    <div v-if="data.total>data.size" class="notification-pages"><button type="button" :disabled="loading||page<=1" @click="changePage(page-1)">上一页</button><span>{{ page }} / {{ Math.ceil(data.total/data.size) }}</span><button type="button" :disabled="loading||page*data.size>=data.total" @click="changePage(page+1)">下一页</button></div>
  </div>
</div></template>
