import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '系统工作台' } },
      { path: 'prep', name: 'Prep', component: () => import('../views/prep/PrepView.vue'), meta: { title: '① 选课准备与发布' } },
      { path: 'selection', name: 'Selection', component: () => import('../views/selection/SelectionView.vue'), meta: { title: '② 学生选课' } },
      { path: 'score', name: 'Score', component: () => import('../views/score/ScoreView.vue'), meta: { title: '③ 成绩录入与审核' } },
      { path: 'stats', name: 'Stats', component: () => import('../views/stats/StatsView.vue'), meta: { title: '④ 成绩查询与统计' } },
      { path: 'correction', name: 'Correction', component: () => import('../views/correction/CorrectionView.vue'), meta: { title: '⑤ 成绩更正申请' } },
      { path: 'admin', name: 'Admin', component: () => import('../views/admin/AdminView.vue'), meta: { title: '⑥ 系统维护' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
