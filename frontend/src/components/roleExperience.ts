import type { Role } from '../types'

// Presentation only. Routes and authorization remain defined by the router.
export const roleExperience: Record<Role, {
  title: string
  purpose: string
  note: string
  menu: { path: string; label: string; compact: string }[]
}> = {
  student: {
    title: '校园维修服务', purpose: '报修、进度与结果，一处掌握。', note: '遇到设施问题，提交报修；维修完成后，确认结果。',
    menu: [
      { path: '/student', label: '服务首页', compact: '首页' },
      { path: '/student/orders/new', label: '我要报修', compact: '报修' },
      { path: '/student/orders', label: '我的工单', compact: '工单' },
    ],
  },
  worker: {
    title: '维修任务工作台', purpose: '接单、开工与记录，有序完成。', note: '先确认任务，再开始维修；保存记录后，提交验收。',
    menu: [
      { path: '/worker', label: '任务工作台', compact: '工作台' },
      { path: '/worker/orders', label: '我的任务', compact: '任务' },
      { path: '/worker/orders?phase=FINISHED', label: '已完成任务', compact: '完成' },
    ],
  },
  admin: {
    title: '校园维修运营中心', purpose: '审核、调度与运营，全局把握。', note: '优先审核新报修，安排维修人员，跟进超时与返工。',
    menu: [
      { path: '/admin', label: '运营中心', compact: '运营' },
      { path: '/admin/orders', label: '工单管理', compact: '工单' },
      { path: '/admin/dispatch', label: '智能派单', compact: '派单' },
      { path: '/admin/dashboard', label: '数据与报表', compact: '报表' },
      { path: '/admin/map', label: '校园地图', compact: '地图' },
      { path: '/admin/manage', label: '基础资料', compact: '资料' },
    ],
  },
}
