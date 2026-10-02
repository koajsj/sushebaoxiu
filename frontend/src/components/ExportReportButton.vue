<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { getReport, reportLabels, type ReportType } from '../api/export'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const type = ref<ReportType>('orders'), busy = ref(false), error = ref(''), feedback = ref('')
let request: AbortController | undefined
const objectUrls = new Map<string, ReturnType<typeof setTimeout>>()

function reset() {
  request?.abort(); request = undefined; busy.value = false; error.value = ''; feedback.value = ''
  for (const [url, timer] of objectUrls) { clearTimeout(timer); URL.revokeObjectURL(url) }
  objectUrls.clear()
}
watch(() => auth.token, reset)
onBeforeUnmount(reset)

async function download() {
  if (busy.value) return
  if (auth.user?.role !== 'ADMIN') { error.value = '只有管理员可以导出报表'; return }
  const controller = new AbortController(), token = auth.token, selected = type.value
  request = controller; busy.value = true; error.value = ''; feedback.value = ''
  try {
    const file = await getReport(selected, controller.signal)
    if (request !== controller || controller.signal.aborted || auth.token !== token || auth.user?.role !== 'ADMIN') return
    const url = URL.createObjectURL(file.blob), link = document.createElement('a')
    link.href = url; link.download = file.filename
    // Delay revocation until the browser has started consuming the object URL.
    const timer = setTimeout(() => { URL.revokeObjectURL(url); objectUrls.delete(url) }, 1000)
    objectUrls.set(url, timer)
    document.body.appendChild(link)
    try { link.click() } finally { link.remove() }
    feedback.value = `${reportLabels[selected]}报表已生成，已发起下载。`
  } catch (failure) {
    if (request === controller && !controller.signal.aborted) error.value = failure instanceof Error ? failure.message : '下载失败，请重试'
  } finally {
    if (request === controller) { request = undefined; busy.value = false }
  }
}
</script>

<template>
  <div v-if="auth.user?.role === 'ADMIN'" class="report-export">
    <form class="report-export-controls" @submit.prevent="download" :aria-busy="busy">
      <label><span class="report-export-label">报表类型</span><select v-model="type" :disabled="busy" aria-label="报表类型"><option v-for="(label,value) in reportLabels" :key="value" :value="value">{{ label }}</option></select></label>
      <button type="submit" class="secondary-button" :disabled="busy">{{ busy ? '正在生成报表…' : '下载 Excel 报表' }}</button>
    </form>
    <p v-if="error" class="report-export-feedback error" role="alert">{{ error }}</p>
    <p v-else-if="feedback" class="report-export-feedback" role="status">{{ feedback }}</p>
  </div>
</template>
