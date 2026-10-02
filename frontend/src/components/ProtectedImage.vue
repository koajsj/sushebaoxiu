<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { loadImage } from '../api/repair'
import { useAuthStore } from '../store/auth'
const props = defineProps<{ url: string; alt?: string }>()
const auth = useAuthStore()
const source = ref(''), failed = ref(false)
let revision = 0
function release() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
function imageFailed(event: Event) {
  if ((event.target as HTMLImageElement).src !== source.value) return
  failed.value = true
  release()
}
watch([() => props.url, () => auth.user?.id, () => auth.token], async ([url]) => {
  const current = ++revision; release(); failed.value = false
  if (!auth.authenticated) return
  try {
    const blob = await loadImage(url)
    if (current === revision) source.value = URL.createObjectURL(blob)
  } catch { if (current === revision) failed.value = true }
}, { immediate: true })
onBeforeUnmount(() => { revision++; release() })
</script>
<template><figure class="repair-photo"><img v-if="source" :src="source" :alt="alt || '报修现场图片'" decoding="async" loading="lazy" @error="imageFailed" /><figcaption v-else :role="failed ? 'alert' : 'status'">{{ failed ? '图片暂时无法显示' : '正在加载图片…' }}</figcaption></figure></template>
