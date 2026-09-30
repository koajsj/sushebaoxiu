import { http, ApiError } from '../utils/request'
import type { ApiResult, PageResult, Role } from '../types'
import type { Catalog, CreateOrder, OrderDetail, OrderFilter, RepairOrder, Summary, WorkerOption } from '../types/repair'

function unwrap<T>(result: ApiResult<T>): T {
  if (result.data === null) throw new ApiError('服务返回内容不完整')
  return result.data
}
export async function getCatalog() { return unwrap((await http.get<ApiResult<Catalog>>('/catalog')).data) }
export async function getOrders(role: Role, params: OrderFilter = {}) {
  return unwrap((await http.get<ApiResult<PageResult<RepairOrder>>>(`/${role}/orders`, { params })).data)
}
export async function createOrder(input: CreateOrder) { return unwrap((await http.post<ApiResult<RepairOrder>>('/student/orders', input)).data) }
export async function getOrder(id: number) { return unwrap((await http.get<ApiResult<OrderDetail>>(`/orders/${id}`)).data) }
export async function getSummary(role: 'student'|'worker') { return unwrap((await http.get<ApiResult<Summary>>(`/${role}/summary`)).data) }
export async function getWorkers() { return unwrap((await http.get<ApiResult<WorkerOption[]>>('/admin/workers')).data) }
export async function actOnOrder(role: Role, id: number, action: 'audit'|'assign'|'accept'|'start'|'finish'|'confirm', workerId?: number) {
  await http.put(`/${role}/orders/${id}/${action}`, workerId ? {workerId} : undefined)
}
export async function addRepairRecord(orderId: number, content: string, imageUrl?: string) { await http.post('/worker/repair-record', {orderId, content, imageUrl}) }
export async function evaluateOrder(orderId: number, score: number, content: string) { await http.post('/student/evaluation', {orderId, score, content}) }
export async function uploadImage(file: File) {
  const data = new FormData(); data.append('file', file)
  return unwrap((await http.post<ApiResult<{url:string}>>('/images', data)).data).url
}
export async function loadImage(url: string): Promise<Blob> {
  if (!/^\/api\/images\/[0-9a-f-]{36}$/.test(url)) throw new ApiError('图片地址不可用')
  return (await http.get<Blob>(url.slice(4), { responseType: 'blob' })).data
}
