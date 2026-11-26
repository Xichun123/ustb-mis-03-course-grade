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
    icon: 'Setting',
    group: 1,
    bg: 'rgba(11, 92, 173, 0.10)',
    color: '#0b5cad',
    roles: ALL_ROLES,
    roleOverrides: {
      STUDENT: {
        title: '选课通知',
        desc: '查看本学期各轮次选课安排与通知公告',
        icon: 'Bell'
      },
      TEACHER: {
        title: '开课计划',
        desc: '查看本学期教学班与选课轮次安排',
        icon: 'Reading'
      }
    }
  },
  {
    path: '/selection',
    title: '选课管理',
    desc: '教学班名单调整、截止筛选与停开处理',
    icon: 'Calendar',
    group: 1,
    bg: 'rgba(22, 163, 74, 0.10)',
    color: '#16a34a',
    roles: ['STUDENT', 'TEACHER', 'ACADEMIC_OFFICE'],
    roleOverrides: {
      STUDENT: {
        title: '选课与课表',
        desc: '检索开放课程、办理选退课与查看周课表',
        icon: 'Calendar'
      },
      TEACHER: {
        title: '上课名单',
        desc: '查看授课班名单、打印与调整学生',
        icon: 'User'
      }
    }
  },
  {
    path: '/score',
    title: '成绩审核与发布',
    desc: '学院核对、成绩发布与校外交流成绩认定',
    icon: 'EditPen',
    group: 1,
    bg: 'rgba(234, 88, 12, 0.10)',
    color: '#ea580c',
    roles: ['TEACHER', 'COLLEGE_SECRETARY', 'ACADEMIC_OFFICE', 'SYS_ADMIN'],
    roleOverrides: {
      TEACHER: {
        title: '成绩录入',
        desc: '按课堂录入成绩、计算总评并提交审核'
      }
    }
  },
  {
    path: '/stats',
    title: '成绩查询与统计',
    desc: '学业完成情况、学业统计、成绩单打印、统计报表与评教汇总',
    icon: 'DataAnalysis',
    group: 2,
    bg: 'rgba(124, 58, 237, 0.10)',
    color: '#7c3aed',
    roles: ALL_ROLES,
    roleOverrides: {
      STUDENT: {
        title: '评教与成绩',
        desc: '完成课程评价、查分、查看分类学分进度与打印成绩单'
      },
      TEACHER: {
        title: '成绩报表与评教',
        desc: '查看授课班成绩分布与评教汇总'
      }
    }
  },
  {
    path: '/correction',
    title: '成绩更正审批',
    desc: '受理成绩更正申请与学院审批',
    icon: 'DocumentChecked',
    group: 2,
    bg: 'rgba(8, 145, 178, 0.10)',
    color: '#0891b2',
    roles: ['TEACHER', 'COLLEGE_SECRETARY', 'STUDENT', 'SYS_ADMIN'],
    roleOverrides: {
      STUDENT: {
        title: '成绩更正通知',
        desc: '查看已通过审批的成绩更正记录'
      },
      TEACHER: {
        title: '成绩更正',
        desc: '发起已发布成绩的更正申请并跟踪进度'
      }
    }
  },
  {
    path: '/admin',
    title: '系统维护',
    desc: '数据备份登记、日志查看与应急恢复',
    icon: 'Tools',
    group: 3,
    bg: 'rgba(100, 116, 139, 0.12)',
    color: '#475569',
    roles: ['SYS_ADMIN', 'ACADEMIC_OFFICE']
  }
]

function resolveItem(item, role) {
  const override = role && item.roleOverrides?.[role]
  return override ? { ...item, ...override } : item
}

export function getHomeTitle(role) {
  return role === 'STUDENT' || role === 'TEACHER' ? '首页' : '工作台'
}

export function getPageTitle(path, role) {
  if (!path || path === '/' || path === '/dashboard') {
    return getHomeTitle(role)
  }
  const item = MODULE_CONFIG.find((m) => m.path === path)
  return item ? resolveItem(item, role).title : getHomeTitle(role)
}

export function canAccessPath(path, role) {
  if (!path || path === '/dashboard' || path === '/') return true
  if (!role) return true
  const item = MODULE_CONFIG.find((m) => m.path === path)
  if (!item) return true
  return item.roles.includes(role)
}

export function visibleModulesForRole(role) {
  const list = role ? MODULE_CONFIG.filter((m) => m.roles.includes(role)) : MODULE_CONFIG
  return list.map((item) => resolveItem(item, role))
}
