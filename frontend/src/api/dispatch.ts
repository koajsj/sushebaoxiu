import { http, unwrap } from '../utils/request'
import type { ApiResult, PageResult } from '../types'
import type { CampusBuilding, MapOrder, MapWorker, WorkerRecommendation } from '../types/dispatch'

export async function getRecommendations(orderId:number) {
  return unwrap((await http.get<ApiResult<WorkerRecommendation[]>>(`/admin/dispatch/recommend/${orderId}`)).data)
}
export async function generateRecommendations(orderId:number,refresh=false) {
  return unwrap((await http.post<ApiResult<WorkerRecommendation[]>>(`/admin/dispatch/recommend/${orderId}`,undefined,{params:{refresh}})).data)
}
export async function confirmDispatch(orderId:number,worker:WorkerRecommendation) {
  return unwrap((await http.post<ApiResult<WorkerRecommendation>>('/admin/dispatch',{
    orderId,workerId:worker.workerId,recommendationId:worker.recommendationId,
  })).data)
}
export async function getMapOrders(status?:string) {
  return unwrap((await http.get<ApiResult<PageResult<MapOrder>>>('/map/orders',{params:{status:status||undefined}})).data)
}
export async function getMapWorkers() { return unwrap((await http.get<ApiResult<MapWorker[]>>('/map/workers')).data) }
export async function getMapBuildings() { return unwrap((await http.get<ApiResult<CampusBuilding[]>>('/map/buildings')).data) }
