import { http, ApiError } from '../utils/request'
import type { ApiResult } from '../types'
import type { NotificationList, OrderMessage, Overview, TrendPoint, TypeCount, WorkerCount } from '../types/phase5'

function unwrap<T>(result:ApiResult<T>):T { if(result.data===null) throw new ApiError('服务返回内容不完整'); return result.data }
export async function getOverview(){return unwrap((await http.get<ApiResult<Overview>>('/admin/statistics/overview')).data)}
export async function getTrend(){return unwrap((await http.get<ApiResult<TrendPoint[]>>('/admin/statistics/trend')).data)}
export async function getTypeStats(){return unwrap((await http.get<ApiResult<TypeCount[]>>('/admin/statistics/types')).data)}
export async function getWorkerStats(){return unwrap((await http.get<ApiResult<WorkerCount[]>>('/admin/statistics/workers')).data)}
export async function getMessages(orderId:number){return unwrap((await http.get<ApiResult<OrderMessage[]>>(`/orders/${orderId}/messages`)).data)}
export async function sendMessage(orderId:number,content:string,expectedWorkerId?:number){return unwrap((await http.post<ApiResult<OrderMessage>>(`/orders/${orderId}/messages`,{content,expectedWorkerId})).data)}
export async function getNotifications(){return unwrap((await http.get<ApiResult<NotificationList>>('/notifications')).data)}
export async function markNotificationRead(id:number){await http.put(`/notifications/${id}/read`)}
