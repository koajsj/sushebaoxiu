<script setup lang="ts">
import type { Role } from '../../types'
defineProps<{ modelValue: string; canSend: boolean; busy: boolean; loading: boolean; role: Role }>()
defineEmits<{ 'update:modelValue': [value:string]; send: [] }>()
</script>

<template>
  <p v-if="loading&&!canSend" class="chat-placeholder" role="status">正在确认工单沟通权限…</p>
  <form v-else-if="canSend" class="chat-composer" @submit.prevent="$emit('send')"><div class="chat-compose-field"><label for="message-draft" class="sr-only">输入工单消息</label><textarea id="message-draft" :value="modelValue" @input="$emit('update:modelValue',($event.target as HTMLTextAreaElement).value)" maxlength="2000" rows="2" :placeholder="role==='student'?'补充故障情况，或与维修员协商上门时间…':'说明上门安排、维修进展或需要学生配合的事项…'" :disabled="busy||loading" /><div class="chat-compose-hint"><span>仅用于这张工单的文字沟通</span><small>{{ modelValue.length }} / 2000</small></div></div><button class="primary-button" type="submit" :disabled="busy||loading||!modelValue.trim()">{{ busy?'发送中…':'发送消息' }}</button></form><p v-else class="chat-placeholder chat-locked">{{ role==='admin'?'管理员可阅读本单沟通记录，不能发送消息。':'当前没有负责人，可查看历史消息，暂不能发送。' }}</p>
</template>
