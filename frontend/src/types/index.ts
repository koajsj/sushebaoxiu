export interface ApiResult<T> {
  code: number
  message: string
  data: T | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type Role = 'student' | 'worker' | 'admin'

export type UserRole = 'STUDENT' | 'WORKER' | 'ADMIN'
export interface UserInfo {
  id: number
  username: string
  realName: string
  phone: string | null
  role: UserRole
}
export interface LoginCredentials { username: string; password: string }
export interface AuthResult { token: string; expiresAt: string; user: UserInfo; role: UserRole }
