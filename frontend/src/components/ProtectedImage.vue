<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { loadImage } from '../api/repair'
const props = defineProps<{ url: string; alt?: string }>()
const source = ref(''), failed = ref(false)
let revision = 0
function release() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
watch(() => props.url, async (url) => {
  const current = ++revision; release(); failed.value = false
  try {
    const blob = await loadImage(url)
    if (current === revision) source.value = URL.createObjectURL(blob)
  } catch { if (current === revision) failed.value = true }
}, { immediate: true })
onBeforeUnmount(() => { revision++; release() })
</script>
<template><figure class="repair-photo"><img v-if="source" :src="source" :alt="alt || '报修现场图片'" /><figcaption v-else>{{ failed ? '图片暂时无法显示' : '正在加载图片…' }}</figcaption></figure></template>
