import request from './request'

export const systemApi = {
  health() {
    return request.get('/health')
  },
  listDemoAccounts() {
    return request.get('/auth/demo-accounts')
  },
  me() {
    return request.get('/auth/me')
  },
  moduleInfo(prefix) {
    return request.get(`/${prefix}/module-info`)
  }
}
