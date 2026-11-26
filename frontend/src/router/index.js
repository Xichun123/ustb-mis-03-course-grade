import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { getAuthToken } from '../api/request'
import { canAccessPath } from '../stores/permissions'
import { initUserStore, userState } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '系统工作台' } },
      { path: 'prep', name: 'Prep', component: () => import('../views/prep/PrepView.vue'), meta: { title: '选课准备与发布' } },
      { path: 'selection', name: 'Selection', component: () => import('../views/selection/SelectionView.vue'), meta: { title: '学生选课' } },
      { path: 'score', name: 'Score', component: () => import('../views/score/ScoreView.vue'), meta: { title: '成绩录入与审核' } },
      { path: 'stats', name: 'Stats', component: () => import('../views/stats/StatsView.vue'), meta: { title: '成绩查询与统计' } },
      { path: 'correction', name: 'Correction', component: () => import('../views/correction/CorrectionView.vue'), meta: { title: '成绩更正申请' } },
      { path: 'admin', name: 'Admin', component: () => import('../views/admin/AdminView.vue'), meta: { title: '系统维护' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeResolve(async (to) => {
  if (to.meta.public) {
    if (!getAuthToken()) return true
    return (await initUserStore()) ? '/dashboard' : true
  }
  if (!(await initUserStore())) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!canAccessPath(to.path, userState.currentUser?.role)) {
    return '/dashboard'
  }
  return true
})

export default router
