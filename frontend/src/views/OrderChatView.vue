<script setup lang="ts">
import { computed,nextTick,onMounted,ref } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getOrder } from '../api/repair'
import { getMessages,sendMessage } from '../api/phase5'
import { useAuthStore } from '../store/auth'
import type { OrderMessage } from '../types/phase5'
import type { OrderDetail } from '../types/repair'
import type { Role } from '../types'
import MessageBubble from '../components/MessageBubble.vue'
const props=defineProps<{role:Role}>(),route=useRoute(),auth=useAuthStore()
const id=Number(route.params.id),detail=ref<OrderDetail|null>(null),messages=ref<OrderMessage[]>([])
const loading=ref(true),busy=ref(false),error=ref(''),feedback=ref(''),draft=ref(''),list=ref<HTMLElement|null>(null)
const canSend=computed(()=>props.role!=='admin'&&!!detail.value?.order.workerId)
async function load(){loading.value=true;error.value=''
  try{const [order,rows]=await Promise.all([getOrder(id),getMessages(id)]);detail.value=order;messages.value=rows;await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight}
  catch(e){error.value=e instanceof Error?e.message:'消息加载失败'}finally{loading.value=false}
}
async function send(){const content=draft.value.trim();if(!content||busy.value||!canSend.value)return
  busy.value=true;error.value='';feedback.value=''
  try{const message=await sendMessage(id,content);messages.value.push(message);draft.value='';feedback.value='消息已发送';await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight}
  catch(e){error.value=e instanceof Error?e.message:'发送失败'}finally{busy.value=false}
}
onMounted(load)
</script>
<template><section class="business-page chat-page"><header class="page-heading"><div><p class="eyebrow">ORDER CONVERSATION · #{{ id }}</p><h1>维修沟通</h1><p>沟通记录仅属于这张工单，管理员可以查看处理经过。</p></div><RouterLink class="text-button" :to="`/${role}/orders/${id}`">← 返回订单详情</RouterLink></header>
  <div class="chat-shell"><header class="chat-head"><div class="chat-symbol">✦</div><div><strong>{{ detail?.order.title||'工单沟通' }}</strong><span>{{ detail?.order.workerName?`学生与 ${detail.order.workerName} 的沟通`:'等待分配维修人员' }}</span></div><button class="secondary-button" :disabled="loading" @click="load">刷新消息</button></header>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="feedback" class="chat-feedback" role="status">{{ feedback }}</p>
    <div ref="list" class="message-list" aria-live="polite"><p v-if="loading&&!messages.length" class="chat-placeholder">正在读取沟通记录…</p><p v-else-if="!messages.length&&!error" class="chat-placeholder">还没有消息，关于这次维修的沟通会保存在这里。</p><MessageBubble v-for="message in messages" :key="message.id" :message="message" :own="message.senderId===auth.user?.id"/></div>
    <form v-if="canSend" class="chat-composer" @submit.prevent="send"><label for="message-draft" class="sr-only">输入工单消息</label><textarea id="message-draft" v-model="draft" maxlength="2000" rows="2" placeholder="输入关于这张工单的问题或处理说明…" :disabled="busy||loading"/><button class="primary-button" type="submit" :disabled="busy||loading||!draft.trim()">{{ busy?'发送中…':'发送消息' }}</button></form><p v-else class="chat-placeholder chat-locked">{{ role==='admin'?'管理员仅可查看沟通记录。':'派单后即可与维修人员沟通。' }}</p>
  </div>
</section></template>
