// Display labels for existing server titles; unknown notifications stay unclassified.
const categories: Record<string,string> = {
  '报修提交成功':'维修进度', '报修审核完成':'维修进度', '维修人员已安排':'维修进度',
  '维修已完成':'维修进度', '报修审核驳回':'维修进度', '报修重新提交':'维修进度',
  '维修时间待确认':'维修进度', '学生已接受预约':'维修进度', '学生拒绝预约':'维修进度',
  '学生验收未通过':'维修进度', '原维修人员返工':'维修进度', '返工等待重新派单':'维修进度',
  '收到新维修任务':'任务通知', '新报修待审核':'任务通知', '维修人员拒单':'任务通知',
  '任务已由管理员收回':'任务通知', '工单接单超时':'系统提醒', '工单待开工超时':'系统提醒', '工单维修超时':'系统提醒',
}
export function notificationCategory(title: string) { return Object.prototype.hasOwnProperty.call(categories,title) ? categories[title]! : '服务消息' }
