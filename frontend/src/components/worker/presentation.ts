import { isAwaitingAcceptance, type RepairOrder } from '../../types/repair'

// Presentation only: action availability remains owned by the existing workflow.
export function workerNextStep(order: RepairOrder) {
  if (isAwaitingAcceptance(order)) return '查看问题和地点，确认可以处理后接单；无法处理请说明原因。'
  switch (order.phase) {
    case 'WAIT_START': return '任务已接受，准备好后开始维修。上门时间可在详情中与学生协商。'
    case 'PROCESSING': return '记录处理过程与结果，保存本轮维修记录后提交学生验收。'
    case 'WAIT_CONFIRM': return '维修结果已提交，等待学生验收；需要补充说明可通过工单沟通。'
    case 'REWORK_PENDING': return '学生反馈问题仍未解决，等待管理员安排下一轮维修。'
    case 'FINISHED': return '学生已确认维修完成，处理过程已归档。'
    case 'COMMENTED': return '维修已完成，学生评价可在详情中查看。'
    default: return '查看工单详情，了解当前处理进展。'
  }
}
export function workerActionLabel(order: RepairOrder) {
  if (isAwaitingAcceptance(order)) return '确认任务与接单'
  if (order.status === 'ASSIGNED') return '进入任务开始维修'
  if (order.status === 'PROCESSING') return '填写维修记录'
  if (order.status === 'WAIT_CONFIRM') return '查看验收进展'
  return '查看任务详情'
}
export function workerDeadline(order: RepairOrder) {
  if (isAwaitingAcceptance(order) && order.responseDueTime) return { label: '接单时限', value: order.responseDueTime }
  if (order.status === 'ASSIGNED' && order.acceptedTime && order.startDueTime) return { label: '开工时限', value: order.startDueTime }
  if (order.status === 'PROCESSING' && order.repairDueTime) return { label: '维修时限', value: order.repairDueTime }
  return null
}
export function hasActiveAppointment(order: RepairOrder) {
  return ['PROPOSED', 'ACCEPTED'].includes(order.appointmentStatus) && !!order.appointmentStart && !!order.appointmentEnd
}
