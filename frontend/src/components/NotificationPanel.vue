<script setup lang="ts">
import { onMounted,onBeforeUnmount,ref,useId,watch } from 'vue'
import { getNotifications,markNotificationRead,markAllNotificationsRead } from '../api/phase5'
import type { NotificationList } from '../types/phase5'
import { formatTime } from '../utils/format'
import { useAuthStore } from '../store/auth'
const auth=useAuthStore()
const open=ref(false),loading=ref(false),busy=ref(0),error=ref(''),data=ref<NotificationList>({records:[],total:0,unreadCount:0,page:1,size:20,latestId:0})
const page=ref(1),unreadOnly=ref(false)
const host=ref<HTMLElement|null>(null),toggleButton=ref<HTMLButtonElement|null>(null),panelId=useId()
let revision=0
let mounted=false
async function load(){const current=++revision;loading.value=true;error.value=''
  try{const result=await getNotifications(page.value,unreadOnly.value);if(current===revision)data.value=result}
  catch(e){if(current===revision)error.value=e instanceof Error?e.message:'通知加载失败'}
  finally{if(current===revision)loading.value=false}
}
function toggle(){open.value=!open.value;if(open.value){page.value=1;void load()}}
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
<template><div ref="host" class="notification-host"><button ref="toggleButton" class="notification-toggle" type="button" :aria-expanded="open" :aria-controls="panelId" :aria-label="`系统通知，${data.unreadCount}条未读`" @click="toggle">通知 <span v-if="data.unreadCount" class="notification-badge">{{ data.unreadCount>99?'99+':data.unreadCount }}</span></button>
  <div v-if="open" :id="panelId" class="notification-panel" role="region" aria-label="系统通知" :aria-busy="loading"><header><strong>消息通知</strong><div><button type="button" :disabled="loading||busy!==0" @click="load">{{ loading?'刷新中…':'刷新' }}</button><button type="button" aria-label="关闭通知面板" @click="open=false;toggleButton?.focus()">关闭</button></div></header>
    <div class="notification-tools"><button type="button" :aria-pressed="!unreadOnly" @click="changeFilter(false)">全部</button><button type="button" :aria-pressed="unreadOnly" @click="changeFilter(true)">未读 {{ data.unreadCount }}</button><button type="button" :disabled="busy!==0||!data.unreadCount||loading" @click="readAll">全部已读</button></div>
    <p v-if="error" class="notification-error" role="alert">{{ error }}</p><p v-if="loading&&!data.records.length" class="notification-empty">正在读取通知…</p>
    <p v-else-if="!data.records.length&&!error" class="notification-empty">暂无通知，新的处理进展会出现在这里。</p>
    <ul v-else><li v-for="item in data.records" :key="item.id" :class="{unread:item.readStatus===0}"><div><strong>{{ item.title }}</strong><small>{{ formatTime(item.createTime) }}</small></div><p>{{ item.content }}</p><button v-if="item.readStatus===0" type="button" :disabled="busy!==0" @click="read(item.id)">标记已读</button></li></ul>
    <div v-if="data.total>data.size" class="notification-pages"><button type="button" :disabled="loading||page<=1" @click="changePage(page-1)">上一页</button><span>{{ page }} / {{ Math.ceil(data.total/data.size) }}</span><button type="button" :disabled="loading||page*data.size>=data.total" @click="changePage(page+1)">下一页</button></div>
  </div>
</div></template>
