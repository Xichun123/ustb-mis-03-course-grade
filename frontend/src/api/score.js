import request from './request'

export const scoreApi = {
  myClasses(params) {
    return request.get('/score/my-classes', { params })
  },
  getRoster(lessonNo) {
    return request.get(`/score/classes/${lessonNo}`)
  },
  saveGrades(lessonNo, items) {
    return request.put(`/score/classes/${lessonNo}/grades`, { items })
  },
  submit(lessonNo) {
    return request.post(`/score/classes/${lessonNo}/submit`)
  },
  sheet(lessonNo) {
    return request.get(`/score/classes/${lessonNo}/sheet`)
  },
  auditPending(params) {
    return request.get('/score/audit/pending', { params })
  },
  audit(lessonNo, payload) {
    return request.post(`/score/classes/${lessonNo}/audit`, payload)
  },
  publishPending(params) {
    return request.get('/score/publish/pending', { params })
  },
  publish(lessonNo) {
    return request.post(`/score/classes/${lessonNo}/publish`)
  },
  listExchange(params) {
    return request.get('/score/exchange', { params })
  },
  createExchange(payload) {
    return request.post('/score/exchange', payload)
  }
}
