import axios from 'axios'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'cgm_demo_user'

export function getDemoUserKey() {
  return localStorage.getItem(STORAGE_KEY) || 'office_admin'
}

export function setDemoUserKey(key) {
  localStorage.setItem(STORAGE_KEY, key)
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  config.headers['X-Demo-User'] = getDemoUserKey()
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
    const msg = err?.response?.data?.message || err.message || '网络异常，请检查后端服务是否已启动'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default request
