import { http } from '../utils/request'
import type { ApiResult, AuthResult, LoginCredentials, UserInfo } from '../types'

export async function login(credentials: LoginCredentials): Promise<AuthResult> {
  const { data } = await http.post<ApiResult<AuthResult>>('/auth/login', credentials)
  if (!data.data) throw new Error('登录响应不完整，请重试')
  return data.data
}

export async function currentUser(): Promise<UserInfo> {
  const { data } = await http.get<ApiResult<UserInfo>>('/users/me')
  if (!data.data) throw new Error('用户信息不可用，请重新登录')
  return data.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
