import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'ustb_cg_auth_token'

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setAuthToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY)
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
})

request.interceptors.response.use(
  (res) => {
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
      clearAuthToken()
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
      return Promise.reject(err)
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default request
