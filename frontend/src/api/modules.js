import request from './request'

export const systemApi = {
  health() {
    return request.get('/health')
  },
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },
  logout() {
    return request.post('/auth/logout')
  },
  listDemoAccounts() {
    return request.get('/auth/demo-accounts')
  },
  me() {
    return request.get('/auth/me')
  }
}
