export interface Overview { overdueCount:number; reworkCount:number; reworkPendingCount:number; waitingAuditCount:number; todayCount:number; activeCount:number; completionRate:number|null; averageRepairHours:number|null; totalCount:number }
export interface TrendPoint { date:string; created:number; finished:number }
export interface TypeCount { typeId:number; typeName:string; count:number; percentage:number|null }
export interface WorkerCount { workerId:number; workerName:string; completedCount:number; activeCount:number; averageRating:number|null }
export interface OrderMessage { id:number; orderId:number; senderId:number; senderName:string; receiverId:number; content:string; read:boolean; createTime:string }
export interface NotificationItem { id:number; userId:number; title:string; content:string; readStatus:number; createTime:string }
export interface NotificationList { records:NotificationItem[]; total:number; unreadCount:number; page:number; size:number; latestId:number }
