import { ApiError, http } from '../utils/request'
import type { ApiResult, HealthStatus } from '../types'

// Infrastructure endpoint only; no login or business API is connected.
export async function getHealth(): Promise<HealthStatus> {
  const response = await http.get<ApiResult<HealthStatus>>('/health')
  if (!response.data.data) throw new ApiError('服务返回内容不完整')
  return response.data.data
}
