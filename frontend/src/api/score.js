import request from './request'

export const scoreApi = {
  myClasses(params) {
    return request.get('/score/my-classes', { params })
  },
  getRoster(lessonNo) {
    return request.get(`/score/classes/${lessonNo}`)
  },
  saveGrades(lessonNo, items, usualRatio) {
    const body = { items }
    if (usualRatio !== undefined && usualRatio !== null) body.usual_ratio = usualRatio
    return request.put(`/score/classes/${lessonNo}/grades`, body)
  },
  setClassRatio(lessonNo, usualRatio) {
    return request.put(`/score/classes/${lessonNo}/ratio`, { usual_ratio: usualRatio })
  },
  importGrades(lessonNo, file, usualRatio, dryRun = false) {
    const form = new FormData()
    form.append('file', file)
    if (usualRatio !== undefined && usualRatio !== null) form.append('usual_ratio', usualRatio)
    form.append('dry_run', dryRun ? 'true' : 'false')
    return request.post(`/score/classes/${lessonNo}/grades/import`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000
    })
  },
  downloadTemplate(lessonNo) {
    return request.get(`/score/classes/${lessonNo}/template`, {
      responseType: 'blob',
      timeout: 30000
    })
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
  },
  collegeAuditExchange(convertId, payload) {
    return request.post(`/score/exchange/${convertId}/college-audit`, payload)
  },
  schoolApproveExchange(convertId, payload) {
    return request.post(`/score/exchange/${convertId}/school-approve`, payload)
  },
  recognizeExchange(convertId, payload) {
    return request.post(`/score/exchange/${convertId}/recognize`, payload || {})
  },
  exchangeCandidates(params) {
    return request.get('/score/exchange/candidates', { params })
  }
}
