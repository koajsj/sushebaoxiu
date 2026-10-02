<script setup lang="ts">
import { ref } from 'vue'
import { uploadImage } from '../api/repair'
import ProtectedImage from './ProtectedImage.vue'
const props = defineProps<{ modelValue: string; disabled?:boolean }>()
const emit = defineEmits<{ 'update:modelValue': [url:string]; pending: [value:boolean] }>()
const uploading = ref(false), error = ref('')
async function choose(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || uploading.value || props.disabled) return
  error.value = ''
  if (!['image/png','image/jpeg'].includes(file.type) || file.size > 5*1024*1024) { error.value='请选择5MB以内的PNG或JPEG图片'; input.value=''; return }
  uploading.value=true; emit('pending',true)
  try { emit('update:modelValue', await uploadImage(file)) }
  catch (e) { error.value=e instanceof Error ? e.message : '上传失败，请重试' }
  finally { uploading.value=false; emit('pending',false); input.value='' }
}
</script>
<template><div class="image-upload" :aria-busy="uploading">
  <div class="upload-layout">
    <div v-if="props.modelValue" class="upload-preview">
      <ProtectedImage :url="props.modelValue" alt="已上传的现场图片" />
      <footer><span>已添加 1 张现场图片</span><button class="text-button danger-action" type="button" :disabled="uploading||props.disabled" @click="emit('update:modelValue','')">移除图片</button></footer>
    </div>
    <label class="upload-drop" :class="{ 'is-loading':uploading }">
      <span class="upload-symbol" aria-hidden="true">{{ props.modelValue ? '↻' : '+' }}</span>
      <strong role="status">{{ uploading ? '正在上传现场图片…' : props.modelValue ? '更换现场图片' : '添加现场图片' }}</strong>
      <small>点击选择，或拖入图片<br />PNG / JPEG · 最大 5MB · 可选</small>
      <progress v-if="uploading" aria-label="正在上传现场图片" />
      <input type="file" accept="image/png,image/jpeg" :disabled="uploading||props.disabled" aria-label="上传现场图片" @change="choose" />
    </label>
  </div>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
</div></template>
