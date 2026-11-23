import request from './request'

export const prepApi = {
  listTeachingClasses(params) {
    return request.get('/prep/teaching-classes', { params })
  },
  getLimits(tcId) {
    return request.get(`/prep/teaching-classes/${tcId}/limits`)
  },
  replaceLimits(tcId, limits) {
    return request.put(`/prep/teaching-classes/${tcId}/limits`, { limits })
  },
  listRounds(params) {
    return request.get('/prep/selection-rounds', { params })
  },
  getRound(roundId) {
    return request.get(`/prep/selection-rounds/${roundId}`)
  },
  createRound(payload) {
    return request.post('/prep/selection-rounds', payload)
  },
  updateRound(roundId, payload) {
    return request.put(`/prep/selection-rounds/${roundId}`, payload)
  },
  submitRound(roundId) {
    return request.post(`/prep/selection-rounds/${roundId}/submit`)
  },
  decideRound(roundId, payload) {
    return request.post(`/prep/selection-rounds/${roundId}/decision`, payload)
  },
  publishRound(roundId) {
    return request.post(`/prep/selection-rounds/${roundId}/publish`)
  }
}
