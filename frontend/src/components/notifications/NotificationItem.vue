<script setup lang="ts">
import type { NotificationItem } from '../../types/phase5'
import { formatTime } from '../../utils/format'
import { notificationCategory } from './presentation'
defineProps<{ item: NotificationItem; busy: number }>()
defineEmits<{ read: [id:number] }>()
</script>

<template>
  <li class="message-center-item" :class="{unread:item.readStatus===0}"><div class="notification-item-meta"><span>{{ notificationCategory(item.title) }}</span><span :class="{'notification-unread-label':item.readStatus===0}">{{ item.readStatus===0?'未读':'已读' }}</span></div><h3>{{ item.title }}</h3><p>{{ item.content }}</p><footer><time :datetime="item.createTime">{{ formatTime(item.createTime) }}</time><button v-if="item.readStatus===0" type="button" :disabled="busy!==0" @click="$emit('read',item.id)">{{ busy===item.id?'标记中…':'标记已读' }}</button><span v-else class="notification-read-mark">已读 ✓</span></footer></li>
</template>
