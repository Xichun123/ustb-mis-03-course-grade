import { reactive } from 'vue'
import { systemApi } from '../api/modules'
import { clearAuthToken, getAuthToken, setAuthToken } from '../api/request'

export const userState = reactive({
  currentKey: getAuthToken(),
  currentUser: null,
  loaded: false
})

export async function initUserStore() {
  const token = getAuthToken()
  userState.currentKey = token
  if (!token) {
    userState.currentUser = null
    userState.loaded = true
    return false
  }
  try {
    const meRes = await systemApi.me()
    userState.currentUser = meRes.data || null
    userState.loaded = true
    return !!userState.currentUser
  } catch {
    clearAuthToken()
    userState.currentKey = ''
    userState.currentUser = null
    userState.loaded = true
    return false
  }
}

export async function loginWithPassword(username, password) {
  const res = await systemApi.login(username, password)
  const { token, user } = res.data
  setAuthToken(token)
  userState.currentKey = token
  userState.currentUser = user
  userState.loaded = true
  return user
}

export async function logoutSession() {
  try {
    await systemApi.logout()
  } catch {
    // ignore
  }
  clearAuthToken()
  userState.currentKey = ''
  userState.currentUser = null
  userState.loaded = true
}
