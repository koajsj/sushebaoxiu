import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../api/auth'
import { ApiError } from '../utils/request'
import { readToken, removeToken, saveToken } from '../utils/session'
import type { LoginCredentials, Role, UserInfo } from '../types'

const homePaths: Record<Role, string> = { student: '/student', worker: '/worker', admin: '/admin' }

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readToken())
  const user = ref<UserInfo | null>(null)
  const initialized = ref(false)
  let restorePromise: Promise<void> | null = null
  let revision = 0
  const role = computed<Role | null>(() => {
    const value = user.value?.role
    return value ? value.toLowerCase() as Role : null
  })
  const authenticated = computed(() => Boolean(token.value && user.value))
  const homePath = computed(() => role.value ? homePaths[role.value] : '/login')

  function clearSession() {
    const previousToken = token.value
    revision += 1
    restorePromise = null
    token.value = ''
    user.value = null
    initialized.value = true
    removeToken(previousToken)
  }

  function syncFromStorage() {
    const stored = readToken()
    if (stored === token.value) return
    revision += 1
    restorePromise = null
    token.value = stored
    user.value = null
    initialized.value = false
  }

  async function signIn(credentials: LoginCredentials) {
    const startedAt = revision
    const result = await api.login(credentials)
    if (revision !== startedAt) throw new Error('登录状态已变化，请重试')
    if (!['STUDENT', 'WORKER', 'ADMIN'].includes(result.user.role)) throw new Error('账号角色无效')
    try { saveToken(result.token) }
    catch { throw new Error('浏览器无法保存登录状态，请允许本地存储后重试') }
    revision += 1
    token.value = result.token
    user.value = result.user
    initialized.value = true
  }

  async function restore() {
    if (initialized.value) return
    if (!token.value) { initialized.value = true; return }
    if (restorePromise) return restorePromise
    const startedAt = revision
    const pending = (async () => {
      try {
        const info = await api.currentUser()
        if (revision !== startedAt) return
        if (!['STUDENT', 'WORKER', 'ADMIN'].includes(info.role)) { clearSession(); return }
        user.value = info
        initialized.value = true
      } catch (error) {
        if (revision === startedAt && error instanceof ApiError && (error.code === 40100 || error.code === 40101)) {
          clearSession()
        } else if (revision === startedAt) { throw error }
      } finally { if (revision === startedAt) restorePromise = null }
    })()
    restorePromise = pending
    return pending
  }

  async function signOut() {
    const startedAt = revision
    try { await api.logout() }
    catch (error) {
      if (!(error instanceof ApiError) || (error.code !== 40100 && error.code !== 40101)) throw error
    }
    if (revision === startedAt) clearSession()
  }

  return { token, user, initialized, role, authenticated, homePath, signIn, restore, signOut, clearSession, syncFromStorage }
})
