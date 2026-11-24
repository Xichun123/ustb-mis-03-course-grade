export const ALL_ROLES = [
  'ACADEMIC_OFFICE',
  'ACADEMIC_DIRECTOR',
  'COLLEGE_SECRETARY',
  'TEACHER',
  'STUDENT',
  'GRADUATE_SCHOOL',
  'SYS_ADMIN'
]

export const MODULE_CONFIG = [
  {
    path: '/prep',
    title: '选课准备与发布',
    desc: '开课计划、限选条件与轮次审批发布',
    studentDesc: '查看本学期选课轮次安排与通知公告',
    icon: 'Setting',
    group: 1,
    bg: 'rgba(11, 92, 173, 0.10)',
    color: '#0b5cad',
    roles: ALL_ROLES
  },
  {
    path: '/selection',
    title: '学生选课',
    desc: '选退课办理、周课表与上课名单调整',
    studentDesc: '检索开放课程、办理选退课与查看按周课表',
    icon: 'Calendar',
    group: 1,
    bg: 'rgba(22, 163, 74, 0.10)',
    color: '#16a34a',
    roles: ['STUDENT', 'TEACHER', 'ACADEMIC_OFFICE']
  },
  {
    path: '/score',
    title: '成绩录入与审核',
    desc: '课堂录分、学院核对发布与交流认定',
    icon: 'EditPen',
    group: 1,
    bg: 'rgba(234, 88, 12, 0.10)',
    color: '#ea580c',
    roles: ['TEACHER', 'COLLEGE_SECRETARY', 'ACADEMIC_OFFICE', 'SYS_ADMIN']
  },
  {
    path: '/stats',
    title: '成绩查询与统计',
    desc: '教学评价、查分、成绩单与统计报表',
    studentDesc: '完成课程教学评价、查询成绩与打印成绩单',
    icon: 'DataAnalysis',
    group: 2,
    bg: 'rgba(124, 58, 237, 0.10)',
    color: '#7c3aed',
    roles: ALL_ROLES
  },
  {
    path: '/correction',
    title: '成绩更正申请',
    desc: '成绩更正发起、两级审批与结果通知',
    studentDesc: '查看本人已通过审批的成绩更正记录',
    icon: 'DocumentChecked',
    group: 2,
    bg: 'rgba(8, 145, 178, 0.10)',
    color: '#0891b2',
    roles: ALL_ROLES
  },
  {
    path: '/admin',
    title: '系统维护',
    desc: '编码参数配置、数据备份与应急快照',
    icon: 'Tools',
    group: 3,
    bg: 'rgba(100, 116, 139, 0.12)',
    color: '#475569',
    roles: ['SYS_ADMIN', 'ACADEMIC_OFFICE']
  }
]

export function canAccessPath(path, role) {
  if (!path || path === '/dashboard' || path === '/') return true
  if (!role) return true
  const item = MODULE_CONFIG.find((m) => m.path === path)
  if (!item) return true
  return item.roles.includes(role)
}

export function visibleModulesForRole(role) {
  if (!role) return MODULE_CONFIG
  return MODULE_CONFIG.filter((m) => m.roles.includes(role))
}
