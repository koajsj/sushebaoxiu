export const TOKEN_KEY = 'campus-repair.token'

export function readToken(): string {
  try { return typeof localStorage === 'undefined' ? '' : localStorage.getItem(TOKEN_KEY) || '' }
  catch { return '' }
}

export function saveToken(token: string) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(expectedToken?: string) {
  try { if (typeof localStorage !== 'undefined' && (expectedToken === undefined || readToken() === expectedToken)) localStorage.removeItem(TOKEN_KEY) }
  catch { /* Memory state is still cleared if browser storage becomes unavailable. */ }
}
