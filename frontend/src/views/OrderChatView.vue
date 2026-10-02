<script setup lang="ts">
import { computed,nextTick,onBeforeUnmount,ref,watch } from 'vue'
import { RouterLink,useRoute } from 'vue-router'
import { getMessageContext,getMessages,sendMessage } from '../api/phase5'
import { useAuthStore } from '../store/auth'
import type { OrderMessage } from '../types/phase5'
import type { Role } from '../types'
import '../assets/collaboration-experience.css'
import ChatComposer from '../components/chat/ChatComposer.vue'
import MessageBubble from '../components/MessageBubble.vue'
import { ApiError } from '../utils/request'

const props=defineProps<{role:Role}>(),route=useRoute(),auth=useAuthStore()
const id=computed(()=>Number(route.params.id))
type Context=Awaited<ReturnType<typeof getMessageContext>>
const context=ref<Context|null>(null),messages=ref<OrderMessage[]>([])
const loading=ref(true),olderLoading=ref(false),busy=ref(false),hasOlder=ref(false)
const error=ref(''),feedback=ref(''),draft=ref(''),list=ref<HTMLElement|null>(null),newCount=ref(0)
const canSend=computed(()=>props.role!=='admin'&&!!context.value?.workerId)
let revision=0,poller:ReturnType<typeof setInterval>|undefined,controller:AbortController|undefined,polling=false,receiptCursor=0
let draftWorkerId:number|null|undefined
watch(draft,(value,previous)=>{if(!previous.trim()&&value.trim())draftWorkerId=context.value?.workerId;if(!value.trim())draftWorkerId=undefined},{flush:'sync'})
function nearBottom(){const element=list.value;return !element||element.scrollHeight-element.scrollTop-element.clientHeight<80}
async function follow(){await nextTick();if(list.value)list.value.scrollTop=list.value.scrollHeight;newCount.value=0}
function reset(){revision++;controller?.abort();clearInterval(poller);polling=false;receiptCursor=0;context.value=null;messages.value=[];draft.value='';draftWorkerId=undefined;hasOlder.value=false;newCount.value=0;busy.value=false;olderLoading.value=false;loading.value=true;error.value='';feedback.value=''}
function updateContext(next:Context){
  if(context.value&&context.value.workerId!==next.workerId&&draft.value.trim()){
    draft.value='';draftWorkerId=undefined;error.value='负责人已变更，旧沟通草稿已清除。'
  }
  context.value=next
}
async function load(){const current=++revision;controller?.abort();controller=new AbortController();polling=false;receiptCursor=0;loading.value=true;error.value=''
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
    const unreadRows=messages.value.filter(row=>row.senderId===auth.user?.id&&!row.read)
    const unread=unreadRows.find(row=>row.id>receiptCursor)||unreadRows[0]
    if(unread){
      // Incremental new-message queries cannot return receipt changes on existing rows.
      const receipts=await getMessages(orderId,unread.id>1?{afterId:unread.id-1,size:100}:{beforeId:2,size:1},controller?.signal)
      if(current!==revision||busy.value)return
      // Rotate past old recipients' unread messages instead of pinning every poll to them.
      receiptCursor=receipts.at(-1)?.id||unread.id
      const readIds=new Set(receipts.filter(row=>row.read).map(row=>row.id))
      messages.value=messages.value.map(row=>readIds.has(row.id)&&!row.read?{...row,read:true}:row)
    }
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
  let delivered=false
  busy.value=true;error.value='';feedback.value=''
  try{const next=await getMessageContext(orderId,controller?.signal);if(current!==revision)return;updateContext(next)
    if(expected!==next.workerId){error.value='负责维修人员已变化，请重新输入消息';return}
    const lastId=messages.value.at(-1)?.id||0
    const sent=await sendMessage(orderId,content,expected||undefined,controller?.signal);if(current!==revision)return
    delivered=true
    if(draft.value.trim()===content)draft.value='';feedback.value='消息已发送'
    let cursor=lastId
    // A busy conversation can add over one page while sending. Continue until the sent row is visible.
    while(cursor<sent.id){
      const rows=await getMessages(orderId,{afterId:cursor||undefined,size:100},controller?.signal);if(current!==revision)return
      if(!rows.length)break
      const known=new Set(messages.value.map(row=>row.id));messages.value=[...messages.value,...rows.filter(row=>!known.has(row.id))]
      cursor=rows.at(-1)!.id
    }
    await follow()
  }catch(e){if(current===revision)error.value=delivered?'消息已发送，记录同步失败，请刷新消息。':e instanceof Error?e.message:'发送失败，输入内容已保留'}finally{if(current===revision)busy.value=false}
}
watch([id,()=>auth.user?.id,()=>props.role],()=>{reset();if(auth.user){void load();if(typeof window!=='undefined')poller=setInterval(()=>void refresh(),15000)}},{immediate:true})
onBeforeUnmount(reset)
</script>
<template><section class="business-page chat-page chat-collaboration"><header class="page-heading"><div><p class="eyebrow">工单 #{{ id }} · 沟通记录</p><h1>维修沟通</h1><p>{{ role==='student'?'向当前维修员补充问题、协商上门时间。':role==='worker'?'与报修学生确认上门安排，说明维修进展。':'阅读本单沟通经过，了解双方协商情况；管理员不参与发送。' }}</p></div><RouterLink class="text-button" :to="`/${role}/orders/${id}`">← 返回订单详情</RouterLink></header>
  <div class="chat-shell"><header class="chat-head"><div class="chat-context-symbol" aria-hidden="true">#</div><div><strong>{{ context?.title||'工单沟通' }}</strong><span>{{ !context?(loading?'正在确认工单参与者…':'尚未读取工单参与者'):context.workerName?role==='worker'?'当前由你负责 · 与报修学生沟通':`当前负责人：${context.workerName}`:'当前暂无负责维修人员' }}</span><span class="chat-access-label">{{ !context?(loading?'正在确认沟通权限…':'沟通权限尚未确认'):role==='admin'?'管理员 · 只读查看':canSend?(role==='student'?'报修学生 · 可发送':'当前维修员 · 可发送'):'历史记录 · 暂不可发送' }}</span></div><button class="secondary-button" :disabled="loading||busy" @click="load">{{ loading?'更新中…':'刷新消息' }}</button></header>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="feedback" class="chat-feedback" role="status">{{ feedback }}</p>
    <div ref="list" class="message-list" role="log" aria-label="工单消息" aria-live="polite" :aria-busy="loading"><button v-if="hasOlder" class="text-button chat-history" :disabled="olderLoading" @click="loadOlder">{{ olderLoading?'加载中…':'查看更早消息' }}</button><p v-if="loading&&!messages.length" class="chat-placeholder" role="status">正在读取沟通记录…</p><p v-else-if="!messages.length&&!error" class="chat-placeholder">还没有消息，关于这次维修的沟通会保存在这里。</p><MessageBubble v-for="message in messages" :key="message.id" :message="message" :own="message.senderId===auth.user?.id"/></div>
    <p class="chat-sync-note">可见页面定期同步；阅读历史时，新消息会以提示显示。</p>
    <button v-if="newCount" class="chat-new-hint" @click="follow">{{ newCount }} 条新消息 · 跳到最新</button>
    <ChatComposer v-if="context||loading" v-model="draft" :can-send="canSend" :role="role" :busy="busy" :loading="loading" @send="send" /><p v-else class="chat-placeholder chat-locked">工单信息尚未读取，请先刷新消息，再确认能否发送。</p>
  </div>
</section></template>
