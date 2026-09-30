export function formatTime(value: string | null | undefined) {
  if (!value) return '等待处理'
  return value.replace('T', ' ').slice(0, 16)
}
