import request from './request'

export const prepApi = {
  listTeachingClasses(params) {
    return request.get('/prep/teaching-classes', { params })
  },
  getScheduleSyncSummary(term) {
    return request.get('/prep/schedule-sync-summary', { params: { term } })
  },
  listRoundClassOptions(term) {
    return request.get('/prep/round-class-options', { params: { term } })
  },
  getLimits(tcId) {
    return request.get(`/prep/teaching-classes/${tcId}/limits`)
  },
  replaceLimits(tcId, limits, specialRequirement) {
    const body = { limits }
    if (specialRequirement !== undefined) body.special_requirement = specialRequirement
    return request.put(`/prep/teaching-classes/${tcId}/limits`, body)
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
