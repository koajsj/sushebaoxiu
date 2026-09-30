import axios from 'axios'
import type { ApiResult } from '../types'
import { readToken } from './session'

export class ApiError extends Error {
  constructor(message: string, public readonly code?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

let unauthorizedHandler: (() => void) | undefined
let currentToken: () => string = readToken
export function setUnauthorizedHandler(handler: () => void, tokenProvider: () => string = readToken) {
  unauthorizedHandler = handler
  currentToken = tokenProvider
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10_000,
  withCredentials: false,
})

http.interceptors.request.use((config) => {
  const token = currentToken()
  if (token && config.url !== '/auth/login') config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') return response
    const result = response.data as ApiResult<unknown>
    if (result.code !== 0) throw new ApiError(result.message || '请求失败，请稍后重试', result.code)
    return response
  },
  (error: unknown) => {
    if (axios.isAxiosError<ApiResult<unknown>>(error)) {
      const result = error.response?.data
      const isUnauthorized = error.response?.status === 401
      const sentToken = error.config?.headers?.Authorization
      if (isUnauthorized && error.config?.url !== '/auth/login'
        && sentToken && sentToken === `Bearer ${currentToken()}`) unauthorizedHandler?.()
      const message = result?.message || (error.code === 'ECONNABORTED'
        ? '请求超时，请稍后重试' : '无法连接服务，请稍后重试')
      return Promise.reject(new ApiError(message, result?.code || (isUnauthorized ? 40100 : undefined)))
    }
    return Promise.reject(new ApiError('请求失败，请稍后重试'))
  },
)
