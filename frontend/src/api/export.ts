import { http, ApiError } from '../utils/request'
import type { ApiResult } from '../types'

export const reportLabels = { orders: '工单统计', workers: '维修效率', types: '故障分析' } as const
export type ReportType = keyof typeof reportLabels
const excelType = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

export async function getReport(type: ReportType, signal?: AbortSignal) {
  if (!Object.hasOwn(reportLabels, type)) throw new ApiError('请选择有效的报表类型')
  const response = await http.get<Blob>(`/admin/export/${type}`, { responseType: 'blob', timeout: 60_000, signal })
  if (String(response.headers['content-type']).includes('application/json')) {
    let result: ApiResult<unknown> | undefined
    try { result = JSON.parse(await response.data.text()) as ApiResult<unknown> } catch { /* Use the file error below. */ }
    throw new ApiError(result?.message || '报表生成失败，请重试', result?.code)
  }
  if (!String(response.headers['content-type']).includes(excelType) || !response.data.size) {
    throw new ApiError('服务未返回有效的Excel文件，请重试')
  }
  const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
  let filename = `${reportLabels[type]}_${date}.xlsx`
  const encoded = String(response.headers['content-disposition'] || '').match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) {
    try {
      const value = decodeURIComponent(encoded).replace(/[\\/\r\n]/g, '_')
      if (value.endsWith('.xlsx')) filename = value
    } catch { /* Missing or malformed attachment headers use the dated fallback. */ }
  }
  return { blob: response.data, filename }
}
