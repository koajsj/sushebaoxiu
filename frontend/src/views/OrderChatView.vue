<script setup lang="ts">
import { computed,nextTick,onBeforeUnmount,ref,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getMessageContext,getMessages,sendMessage } from '../api/phase5'
import { useAuthStore } from '../store/auth'
import type { OrderMessage } from '../types/phase5'
import type { Role } from '../types'
import MessageBubble from '../components/MessageBubble.vue'
import { ApiError } from '../utils/request'

const props=defineProps<{role:Role}>(),route=useRoute(),auth=useAuthStore()
const id=computed(()=>Number(route.params.id))
type Context=Awaited<ReturnType<typeof getMessageContext>>
const context=ref<Context|null>(null),messages=ref<OrderMessage[]>([])
const loading=ref(true),olderLoading=ref(false),busy=ref(false),hasOlder=ref(false)
const error=ref(''),feedback=ref(''),draft=ref(''),list=ref<HTMLElement|null>(null),newCount=ref(0)
const canSend=computed(()=>props.role!=='admin'&&!!context.value?.workerId)
let revision=0,poller:ReturnType<typeof setInterval>|undefined,controller:AbortController|undefined,polling=false
let draftWorkerId:number|null|undefined
watch(draft,(value,previous)=>{if(!previous.trim()&&value.trim())draftWorkerId=context.value?.workerId;if(!value.trim())draftWorkerId=undefined},{flush:'sync'})
function nearBottom(){const element=list.value;return !element||element.scrollHeight-element.scrollTop-element.clientHeight<80}
async function follow(){await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight;newCount.value=0}
function reset(){revision++;controller?.abort();clearInterval(poller);polling=false;context.value=null;messages.value=[];draft.value='';draftWorkerId=undefined;hasOlder.value=false;newCount.value=0;busy.value=false;olderLoading.value=false;loading.value=true;error.value='';feedback.value=''}
function updateContext(next:Context){
  if(context.value&&context.value.workerId!==next.workerId&&draft.value.trim()){
    draft.value='';draftWorkerId=undefined;error.value='负责人已变更，旧沟通草稿已清除。'
  }
  context.value=next
}
async function load(){const current=++revision;controller?.abort();controller=new AbortController();loading.value=true;error.value=''
  try{const [next,rows]=await Promise.all([getMessageContext(id.value,controller.signal),getMessages(id.value,{size:50},controller.signal)])
    if(current!==revision)return;updateContext(next);messages.value=rows;hasOlder.value=rows.length===50;await follow()
  }catch(e){if(current===revision&&!(e instanceof Error&&e.name==='CanceledError'))error.value=e instanceof Error?e.message:'消息加载失败'}
  finally{if(current===revision)loading.value=false}
}
async function refresh(){if(loading.value||busy.value||polling||typeof document==='undefined'||document.hidden||!context.value)return
  polling=true
  const current=revision,orderId=id.value,previousWorker=context.value.workerId,atBottom=nearBottom()
  try{const next=await getMessageContext(orderId,controller?.signal);if(current!==revision||busy.value)return;updateContext(next)
    const lastId=messages.value.at(-1)?.id||0
    const rows=await getMessages(orderId,{afterId:lastId||undefined,size:100},controller?.signal);if(current!==revision||busy.value)return
    const known=new Set(messages.value.map(row=>row.id));const additions=rows.filter(row=>!known.has(row.id))
    if(additions.length){messages.value=[...messages.value,...additions];if(atBottom)await follow();else newCount.value+=additions.length}
    if(previousWorker!==next.workerId&&next.workerId==null)feedback.value='当前没有负责人，历史沟通仍可查看。'
  }catch(e){if(current===revision&&!(e instanceof Error&&e.name==='CanceledError')){
    if(e instanceof ApiError&&[40100,40300,40400].includes(e.code||0)){context.value=null;messages.value=[];draft.value='';newCount.value=0}
    error.value=e instanceof Error?e.message:'刷新失败，请稍后重试'
  }}finally{if(current===revision)polling=false}
}
async function loadOlder(){if(olderLoading.value||!hasOlder.value||!messages.value.length)return
  const current=revision,beforeId=messages.value[0].id,element=list.value,previousHeight=element?.scrollHeight||0
  olderLoading.value=true
  try{const rows=await getMessages(id.value,{beforeId,size:50},controller?.signal);if(current!==revision)return
    const known=new Set(messages.value.map(row=>row.id));messages.value=[...rows.filter(row=>!known.has(row.id)),...messages.value]
    hasOlder.value=rows.length===50;await nextTick();if(element)element.scrollTop+=element.scrollHeight-previousHeight
  }catch(e){if(current===revision)error.value=e instanceof Error?e.message:'更早消息加载失败'}finally{if(current===revision)olderLoading.value=false}
}
async function send(){const content=draft.value.trim();if(!content||busy.value||loading.value||!canSend.value)return
  const current=revision,expected=draftWorkerId,orderId=id.value
  busy.value=true;error.value='';feedback.value=''
  try{const next=await getMessageContext(orderId,controller?.signal);if(current!==revision)return;updateContext(next)
    if(expected!==next.workerId){error.value='负责维修人员已变化，请重新输入消息';return}
    const lastId=messages.value.at(-1)?.id||0
    const sent=await sendMessage(orderId,content,expected||undefined,controller?.signal);if(current!==revision)return
    let cursor=lastId
    // A busy conversation can add over one page while sending. Continue until the sent row is visible.
    while(cursor<sent.id){
      const rows=await getMessages(orderId,{afterId:cursor||undefined,size:100},controller?.signal);if(current!==revision)return
      if(!rows.length)break
      const known=new Set(messages.value.map(row=>row.id));messages.value=[...messages.value,...rows.filter(row=>!known.has(row.id))]
      cursor=rows.at(-1)!.id
    }
    if(draft.value.trim()===content)draft.value='';feedback.value='消息已发送';await follow()
  }catch(e){if(current===revision)error.value=e instanceof Error?e.message:'发送失败，输入内容已保留'}finally{if(current===revision)busy.value=false}
}
watch([id,()=>auth.user?.id,()=>props.role],()=>{reset();if(auth.user){void load();if(typeof window!=='undefined')poller=setInterval(()=>void refresh(),15000)}},{immediate:true})
onBeforeUnmount(reset)
</script>
<template><section class="business-page chat-page"><header class="page-heading"><div><p class="eyebrow">工单 #{{ id }} · 沟通记录</p><h1>维修沟通</h1><p>沟通记录仅属于这张工单，管理员可以查看处理经过。</p></div><RouterLink class="text-button" :to="`/${role}/orders/${id}`">← 返回订单详情</RouterLink></header>
  <div class="chat-shell"><header class="chat-head"><div class="chat-symbol" aria-hidden="true">✦</div><div><strong>{{ context?.title||'工单沟通' }}</strong><span>{{ context?.workerName?`学生与 ${context.workerName} 的沟通`:'当前暂无负责维修人员' }}</span></div><button class="secondary-button" :disabled="loading||busy" @click="load">{{ loading?'更新中…':'刷新消息' }}</button></header>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="feedback" class="chat-feedback" role="status">{{ feedback }}</p>
    <div ref="list" class="message-list" role="log" aria-label="工单消息" aria-live="polite" :aria-busy="loading"><button v-if="hasOlder" class="text-button chat-history" :disabled="olderLoading" @click="loadOlder">{{ olderLoading?'加载中…':'查看更早消息' }}</button><p v-if="loading&&!messages.length" class="chat-placeholder">正在读取沟通记录…</p><p v-else-if="!messages.length&&!error" class="chat-placeholder">还没有消息，关于这次维修的沟通会保存在这里。</p><MessageBubble v-for="message in messages" :key="message.id" :message="message" :own="message.senderId===auth.user?.id"/></div>
    <button v-if="newCount" class="chat-new-hint" @click="follow">{{ newCount }} 条新消息 · 跳到最新</button>
    <form v-if="canSend" class="chat-composer" @submit.prevent="send"><label for="message-draft" class="sr-only">输入工单消息</label><textarea id="message-draft" v-model="draft" maxlength="2000" rows="2" placeholder="输入关于这张工单的问题或处理说明…" :disabled="busy||loading"/><button class="primary-button" type="submit" :disabled="busy||loading||!draft.trim()">{{ busy?'发送中…':'发送消息' }}</button></form><p v-else class="chat-placeholder chat-locked">{{ role==='admin'?'管理员仅可查看沟通记录。':'当前没有负责人，可查看历史消息，暂不能发送。' }}</p>
  </div>
</section></template>
