import request from './request'

export const selectionApi = {
  context: () => request.get('/selection/context'),
  courses: params => request.get('/selection/courses', { params }),
  results: term => request.get('/selection/results', { params: { term } }),
  timetable: (term, week) => request.get('/selection/timetable', { params: { term, week } }),
  check: payload => request.post('/selection/check', payload),
  enroll: payload => request.post('/selection/applications', payload),
  withdraw: (id, payload) => request.post(`/selection/records/${encodeURIComponent(id)}/withdraw`, payload),
  teachingClasses: term => request.get('/selection/teaching-classes', { params: { term } }),
  roster: (id, keyword) => request.get(`/selection/teaching-classes/${encodeURIComponent(id)}/roster`, { params: { keyword } }),
  adjust: (id, payload) => request.post(`/selection/teaching-classes/${encodeURIComponent(id)}/adjustments`, payload),
  screen: id => request.post(`/selection/rounds/${id}/screen`, null, { timeout: 60000 }),
  stopPreview: term => request.get('/selection/stop-preview', { params: { term } })
}
