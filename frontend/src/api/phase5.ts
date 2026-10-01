import { http, ApiError } from '../utils/request'
import type { ApiResult } from '../types'
import type { NotificationList, OrderMessage, Overview, TrendPoint, TypeCount, WorkerCount } from '../types/phase5'

function unwrap<T>(result:ApiResult<T>):T { if(result.data===null) throw new ApiError('服务返回内容不完整'); return result.data }
export async function getOverview(){return unwrap((await http.get<ApiResult<Overview>>('/admin/statistics/overview')).data)}
export async function getTrend(){return unwrap((await http.get<ApiResult<TrendPoint[]>>('/admin/statistics/trend')).data)}
export async function getTypeStats(){return unwrap((await http.get<ApiResult<TypeCount[]>>('/admin/statistics/types')).data)}
export async function getWorkerStats(){return unwrap((await http.get<ApiResult<WorkerCount[]>>('/admin/statistics/workers')).data)}
export async function getMessages(orderId:number,params:{beforeId?:number;afterId?:number;size?:number}={},signal?:AbortSignal){return unwrap((await http.get<ApiResult<OrderMessage[]>>(`/orders/${orderId}/messages`,{params,signal})).data)}
export async function getMessageContext(orderId:number,signal?:AbortSignal){return unwrap((await http.get<ApiResult<{orderId:number;title:string;workerId:number|null;workerName:string|null}>>(`/orders/${orderId}/messages/context`,{signal})).data)}
export async function sendMessage(orderId:number,content:string,expectedWorkerId?:number,signal?:AbortSignal){return unwrap((await http.post<ApiResult<OrderMessage>>(`/orders/${orderId}/messages`,{content,expectedWorkerId},{signal})).data)}
export async function getNotifications(page=1,unread=false){return unwrap((await http.get<ApiResult<NotificationList>>('/notifications',{params:{page,size:20,unread}})).data)}
export async function markNotificationRead(id:number){await http.put(`/notifications/${id}/read`)}
export async function markAllNotificationsRead(throughId:number){await http.put('/notifications/read-all',undefined,{params:{throughId}})}
