import { reactive } from 'vue'
import { systemApi } from '../api/modules'
import { AUTH_EXPIRED_EVENT, AUTH_TOKEN_KEY, clearAuthToken, getAuthToken, setAuthToken } from '../api/request'

export const userState = reactive({
  currentKey: '',
  currentUser: null,
  loaded: false
})

/** @type {{ token: string, promise: Promise<any> } | null} */
let pendingUserLoad = null
let authRevision = 0
let authSyncRegistered = false
/** @type {(() => Promise<void>) | null} */
let syncSessionHandler = null

function updateUserState(token, user, loaded = true) {
  userState.currentKey = token
  userState.currentUser = user
  userState.loaded = loaded
}

export async function initUserStore() {
  const token = getAuthToken()
  if (!token) {
    pendingUserLoad = null
    updateUserState('', null)
    return false
  }
  if (userState.currentKey === token && userState.currentUser) return true

  if (pendingUserLoad?.token !== token) {
    updateUserState('', null, false)
    const promise = systemApi.me().then((res) => res.data || null).catch(() => null)
    pendingUserLoad = { token, promise }
  }
  const load = pendingUserLoad
  const user = await load.promise
  // Another tab may have changed the token while the profile request was in flight.
  if (load !== pendingUserLoad || token !== getAuthToken()) return initUserStore()

  pendingUserLoad = null
  if (!user) clearAuthToken()
  updateUserState(user ? token : '', user)
  return !!user
}

export async function loginWithPassword(username, password) {
  if (getAuthToken()) {
    const synced = await initUserStore()
    if (synced) {
      if (syncSessionHandler) await syncSessionHandler()
      return null
    }
  }
  const revision = authRevision

  const res = await systemApi.login(username, password)
  if (revision !== authRevision || getAuthToken()) {
    if (getAuthToken() && syncSessionHandler) await syncSessionHandler()
    return null
  }

  const { token, user } = res.data
  authRevision += 1
  pendingUserLoad = null
  setAuthToken(token)
  updateUserState(token, user)
  return user
}

export function logoutSession() {
  const token = getAuthToken()
  if (!token || token !== userState.currentKey) return false

  authRevision += 1
  systemApi.logout().catch(() => {})
  clearAuthToken()
  pendingUserLoad = null
  updateUserState('', null)
  return true
}

export function getLoginRedirect(router) {
  const redirect = router.currentRoute.value.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/')) {
    const target = router.resolve(redirect)
    if (target.name && !target.meta.public) return target.fullPath
  }
  return '/dashboard'
}

export function initAuthSync(router) {
  if (authSyncRegistered) return
  authSyncRegistered = true

  async function syncUserSession() {
    const revision = ++authRevision
    await initUserStore()
    await router.isReady()
    if (revision !== authRevision) return

    const token = getAuthToken()
    const onLoginPage = router.currentRoute.value.name === 'Login'
    if (!token && !onLoginPage) {
      await router.replace('/login')
    } else if (token && token === userState.currentKey && onLoginPage) {
      await router.replace(getLoginRedirect(router))
    }
  }

  syncSessionHandler = syncUserSession
  window.addEventListener('storage', (event) => {
    if (event.storageArea === window.localStorage && (event.key === AUTH_TOKEN_KEY || event.key === null)) {
      void syncUserSession()
    }
  })
  window.addEventListener(AUTH_EXPIRED_EVENT, syncUserSession)
}
