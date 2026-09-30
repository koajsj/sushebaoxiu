export const statusLabels = {
  REJECTED: '审核驳回', REWORK_PENDING: '待安排返工', CREATED: '草稿', WAIT_AUDIT: '待审核', WAIT_ASSIGN: '待派单 / 接单', ASSIGNED: '已分配',
  PROCESSING: '维修中', WAIT_CONFIRM: '待确认', FINISHED: '已完成', COMMENTED: '已评价',
} as const
export type OrderStatus = keyof typeof statusLabels
export function orderStatusLabel(order: { status: OrderStatus; workerId: number|null }) {
  return order.status === 'WAIT_ASSIGN' ? (order.workerId ? '待接单' : '待派单') : statusLabels[order.status]
}
export interface RepairOrder {
  id: number; studentId: number; typeId: number; title: string; description: string
  imageUrl: string | null; buildingId: number; roomNo: string; priority: 'LOW'|'NORMAL'|'HIGH'
  status: OrderStatus; workerId: number|null; createTime: string; updateTime: string
  repairRound:number; dispatchRound:number; assignedTime:string|null; acceptedTime:string|null; startedTime:string|null
  responseDueTime:string|null; repairDueTime:string|null; overdueType:'RESPONSE'|'REPAIR'|null
  appointmentStart:string|null; appointmentEnd:string|null; appointmentStatus:'NONE'|'PROPOSED'|'ACCEPTED'|'REJECTED'; appointmentReason:string|null; appointmentVersion:number
  typeName: string; buildingName: string; workerName: string|null; studentName: string
}
export interface RepairRecord { roundNo:number; id: number; orderId: number; workerId: number; content: string; imageUrl: string|null; startTime: string; finishTime: string|null }
export interface Evaluation { id: number; orderId: number; studentId: number; score: number; content: string; createTime: string }
export interface OrderEvent { roundNo:number; workerId:number|null; content:string|null; id: number; action: string; status: OrderStatus; createTime: string }
export interface OrderDetail { order: RepairOrder; records: RepairRecord[]; evaluation: Evaluation|null; timeline: OrderEvent[]; dispatchHistory:DispatchHistory[] }
export interface Catalog { types: {id:number; name:string; description:string}[]; buildings: {id:number; name:string; type:string}[] }
export interface WorkerOption { id:number; name:string; username:string; skillType:string; score:number; taskCount:number }
export interface CreateOrder { typeId:number; title:string; description:string; imageUrl?:string; buildingId:number; roomNo:string; priority:RepairOrder['priority'] }
export interface OrderFilter { page?:number; size?:number; status?:string; typeId?:number; from?:string; to?:string; overdue?:boolean }
export interface Summary { total:number; pending:number; active:number; completed:number; today:number }

export interface DispatchHistory { id:number; workerId:number; workerName:string|null; roundNo:number; method:string; decision:string; reason:string; rejectReason:string|null; totalScore:number|null; createTime:string; responseTime:string|null }
