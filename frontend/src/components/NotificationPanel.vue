<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { getNotifications,markNotificationRead } from '../api/phase5'
import type { NotificationList } from '../types/phase5'
import { formatTime } from '../utils/format'
const open=ref(false),loading=ref(false),busy=ref(0),error=ref(''),data=ref<NotificationList>({records:[],total:0,unreadCount:0})
let revision=0
async function load(){const current=++revision;loading.value=true;error.value=''
  try{const result=await getNotifications();if(current===revision)data.value=result}
  catch(e){if(current===revision)error.value=e instanceof Error?e.message:'通知加载失败'}
  finally{if(current===revision)loading.value=false}
}
function toggle(){open.value=!open.value;if(open.value)void load()}
async function read(id:number){if(busy.value)return;busy.value=id;error.value=''
  try{await markNotificationRead(id);await load()}
  catch(e){error.value=e instanceof Error?e.message:'标记已读失败'}
  finally{busy.value=0}
}
onMounted(load)
</script>
<template><div class="notification-host"><button class="notification-toggle" type="button" :aria-expanded="open" aria-label="查看系统通知" @click="toggle">通知 <span v-if="data.unreadCount" class="notification-badge">{{ data.unreadCount>99?'99+':data.unreadCount }}</span></button>
  <div v-if="open" class="notification-panel" role="region" aria-label="系统通知"><header><strong>消息通知</strong><button type="button" :disabled="loading" @click="load">{{ loading?'刷新中…':'刷新' }}</button></header>
    <p v-if="error" class="notification-error" role="alert">{{ error }}</p><p v-if="loading&&!data.records.length" class="notification-empty">正在读取通知…</p>
    <p v-else-if="!data.records.length" class="notification-empty">暂无通知，新的处理进展会出现在这里。</p>
    <ul v-else><li v-for="item in data.records" :key="item.id" :class="{unread:item.readStatus===0}"><div><strong>{{ item.title }}</strong><small>{{ formatTime(item.createTime) }}</small></div><p>{{ item.content }}</p><button v-if="item.readStatus===0" type="button" :disabled="busy!==0" @click="read(item.id)">标记已读</button></li></ul>
    <p v-if="data.total>data.records.length" class="notification-empty">显示最新 {{ data.records.length }} / {{ data.total }} 条</p>
  </div>
</div></template>
