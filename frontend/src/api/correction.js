import request from './request'

export const correctionApi = {
  listCandidates(params) {
    return request.get('/correction/candidates', { params })
  },
  submit(payload) {
    return request.post('/correction/score-corrections', payload)
  },
  list(params) {
    return request.get('/correction/score-corrections', { params })
  },
  collegeDecision(applyId, payload) {
    return request.post(`/correction/score-corrections/${applyId}/college-decision`, payload)
  },
  officeDecision(applyId, payload) {
    return request.post(`/correction/score-corrections/${applyId}/office-decision`, payload)
  },
  myResults(params) {
    return request.get('/correction/my-results', { params })
  }
}
