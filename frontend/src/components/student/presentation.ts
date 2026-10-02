import type { RepairOrder } from '../../types/repair'

// Presentation only: stages and available actions remain controlled by the server.
export function studentNextStep(order: RepairOrder) {
  switch (order.phase) {
    case 'REJECTED': return '补充报修信息后，重新提交审核。'
    case 'REWORK_PENDING': return '管理员正在安排下一轮维修，历史记录会保留。'
    case 'WAIT_AUDIT': return '等待管理员审核，处理进展会通过通知告知。'
    case 'WAIT_DISPATCH': return '审核已通过，等待安排维修人员。'
    case 'WAIT_ACCEPT': return '已安排维修人员，等待对方响应。'
    case 'WAIT_START': return order.appointmentStatus === 'PROPOSED' ? '维修人员提出了上门时间，请进入详情确认。' : '已接单，等待维修人员开始维修。'
    case 'PROCESSING': return order.appointmentStatus === 'PROPOSED' ? '维修正在进行，请进入详情确认上门时间。' : '维修正在进行，可在工单内沟通具体情况。'
    case 'WAIT_CONFIRM': return '请查看维修结果：已解决可确认，仍有问题可申请返工。'
    case 'FINISHED': return '维修已验收，欢迎留下你的服务评价。'
    case 'COMMENTED': return '评价已保存，感谢你的反馈。'
    default: return '进入工单详情查看维修进度。'
  }
}

export function studentActionLabel(order: RepairOrder) {
  if (order.phase === 'REJECTED') return '补充信息并重提'
  if (order.phase === 'WAIT_CONFIRM') return '确认维修结果'
  if (order.phase === 'FINISHED') return '评价本次维修'
  if (order.appointmentStatus === 'PROPOSED' && ['ASSIGNED', 'PROCESSING'].includes(order.status)) return '确认上门时间'
  return '查看维修进度'
}
