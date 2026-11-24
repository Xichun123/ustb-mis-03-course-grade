import request from './request'

export const adminApi = {
  listCodes(params) {
    return request.get('/admin/codes', { params })
  },
  createCode(payload) {
    return request.post('/admin/codes', payload)
  },
  updateCode(id, payload) {
    return request.put(`/admin/codes/${id}`, payload)
  },
  listBackups() {
    return request.get('/admin/backups')
  },
  createBackup(payload) {
    return request.post('/admin/backups', payload)
  },
  restore(id) {
    return request.post(`/admin/backups/${id}/restore`)
  }
}
