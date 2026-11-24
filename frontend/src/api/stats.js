import request from './request'

/**
 * 【业务④ 成绩查询与统计（4.1–4.6）· 黎家俊】前端接口封装
 */
export const statsApi = {
  // 4.1 学生评教
  getPendingEvaluations(params) {
    return request.get('/stats/my/evaluations/pending', { params })
  },
  submitEvaluation(payload) {
    return request.post('/stats/my/evaluations', payload)
  },

  // 4.2 本人成绩查询
  getMyGrades(params) {
    return request.get('/stats/my/grades', { params })
  },

  // 4.3 学业统计台账与重算
  listAcademicStats(params) {
    return request.get('/stats/academic-stats', { params })
  },
  recomputeAcademicStats(payload = {}) {
    return request.post('/stats/academic-stats/recompute', payload)
  },

  // 4.4 成绩单预览与打印
  listTranscriptStudents() {
    return request.get('/stats/transcript/students')
  },
  previewTranscript(payload = {}) {
    return request.post('/stats/my/transcript-preview', payload)
  },

  // 4.5 成绩统计报表
  getReportOptions() {
    return request.get('/stats/grade-reports/options')
  },
  getGradeReport(params) {
    return request.get('/stats/grade-reports', { params })
  },

  // 4.6 评教结果汇总
  getEvaluationSummaries(params) {
    return request.get('/stats/teaching-evaluation-summaries', { params })
  },
  recomputeEvaluationSummaries(payload = {}) {
    return request.post('/stats/teaching-evaluation-summaries/recompute', payload)
  },
  getEvaluationDetails(params) {
    return request.get('/stats/teaching-evaluation-details', { params })
  }
}
