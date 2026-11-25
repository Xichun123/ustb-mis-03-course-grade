export const MAJOR_NAME_MAP = {
  '120102': '信息管理与信息系统',
  '080901': '计算机科学与技术',
  '120100': '管理科学与工程'
}

export const COLLEGE_NAME_MAP = {
  '0001': '经济管理学院',
  '0002': '计算机与通信工程学院'
}

export const MAJOR_OPTIONS = Object.entries(MAJOR_NAME_MAP).map(([value, label]) => ({ value, label }))

export function getMajorName(code) {
  if (!code) return '—'
  const key = String(code).trim()
  return MAJOR_NAME_MAP[key] || key
}

export function getCollegeName(code) {
  if (!code) return '—'
  const key = String(code).trim()
  return COLLEGE_NAME_MAP[key] || key
}

export function formatReasonText(text) {
  if (!text) return ''
  return String(text).replace(/【([0-9A-Za-z]+)】/g, (full, code) => {
    const name = MAJOR_NAME_MAP[code] || COLLEGE_NAME_MAP[code]
    return name ? `【${name}】` : full
  })
}
