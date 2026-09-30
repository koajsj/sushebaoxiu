export const statusLabels = {
  CREATED: '草稿', WAIT_AUDIT: '待审核', WAIT_ASSIGN: '待接单', ASSIGNED: '已接单',
  PROCESSING: '维修中', WAIT_CONFIRM: '待确认', FINISHED: '已完成', COMMENTED: '已评价',
} as const
export type OrderStatus = keyof typeof statusLabels
export interface RepairOrder {
  id: number; studentId: number; typeId: number; title: string; description: string
  imageUrl: string | null; buildingId: number; roomNo: string; priority: 'LOW'|'NORMAL'|'HIGH'
  status: OrderStatus; workerId: number|null; createTime: string; updateTime: string
  typeName: string; buildingName: string; workerName: string|null; studentName: string
}
export interface RepairRecord { id: number; orderId: number; workerId: number; content: string; imageUrl: string|null; startTime: string; finishTime: string|null }
export interface Evaluation { id: number; orderId: number; studentId: number; score: number; content: string; createTime: string }
export interface OrderEvent { id: number; action: string; status: OrderStatus; createTime: string }
export interface OrderDetail { order: RepairOrder; records: RepairRecord[]; evaluation: Evaluation|null; timeline: OrderEvent[] }
export interface Catalog { types: {id:number; name:string; description:string}[]; buildings: {id:number; name:string; type:string}[] }
export interface WorkerOption { id:number; name:string; username:string; skillType:string; score:number; taskCount:number }
export interface CreateOrder { typeId:number; title:string; description:string; imageUrl?:string; buildingId:number; roomNo:string; priority:RepairOrder['priority'] }
export interface OrderFilter { page?:number; size?:number; status?:string; typeId?:number; from?:string; to?:string }
export interface Summary { total:number; pending:number; active:number; completed:number; today:number }
