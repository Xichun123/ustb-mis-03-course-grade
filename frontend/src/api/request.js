import axios from 'axios'
import { ElMessage } from 'element-plus'

export const AUTH_TOKEN_KEY = 'ustb_cg_auth_token'
export const AUTH_EXPIRED_EVENT = 'ustb-auth-expired'

sessionStorage.removeItem(AUTH_TOKEN_KEY)

export function getAuthToken() {
  return localStorage.getItem(AUTH_TOKEN_KEY) || ''
}

export function setAuthToken(token) {
  sessionStorage.removeItem(AUTH_TOKEN_KEY)
  if (token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
  } else {
    clearAuthToken()
  }
}

export function clearAuthToken() {
  sessionStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(AUTH_TOKEN_KEY)
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
    config.headers['X-Demo-User'] = token
  }
  return config
}, undefined, { synchronous: true })

request.interceptors.response.use(
  (res) => {
    if (res.config?.responseType === 'blob' || res.data instanceof Blob) {
      return res.data
    }
    const payload = res.data
    if (payload && payload.code === 'OK') {
      return payload
    }
    const msg = payload?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (err) => {
    const status = err?.response?.status
    const msg = err?.response?.data?.message || err.message || '网络异常，请稍后重试'
    if (status === 401 && !err.config?.url?.includes('/auth/login')) {
      const requestToken = err.config?.headers?.['X-Demo-User']
      const activeToken = getAuthToken()
      if (requestToken && requestToken === activeToken) {
        clearAuthToken()
        window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
      } else if (!requestToken && !activeToken) {
        window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
      }
      return Promise.reject(err)
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default request
