<script setup lang="ts">
import type { OrderMessage } from '../types/phase5'
import { formatTime } from '../utils/format'
defineProps<{message:OrderMessage;own:boolean}>()
</script>
<template><article class="message-row" :class="{own}"><div class="message-bubble"><span>{{ message.senderName }}</span><p>{{ message.content }}</p><small><time :datetime="message.createTime">{{ formatTime(message.createTime) }}</time><span v-if="own"> · {{ message.read?'已读':'已发送' }}</span></small></div></article></template>
