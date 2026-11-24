<template>
  <div class="dashboard-page">
    <!-- 顶部欢迎区（对标 USTB Manager 首页风格） -->
    <div class="welcome-header">
      <div class="welcome-title">
        {{ greeting }}{{ shortName ? `，${shortName}` : '' }}
      </div>
      <div class="welcome-sub">
        {{ todayText }} · 2026-2027 学年第 1 学期 · 北京科技大学本科生与研究生选课及成绩管理平台
      </div>
    </div>

    <!-- 第一排：个人信息 + 本学期教务概览 -->
    <el-row :gutter="24" style="margin-bottom: 24px">
      <el-col :xs="24" :lg="12" style="margin-bottom: 16px">
        <el-card class="app-card-hover full-height-card">
          <template #header>
            <div class="card-head">
              <span class="card-head-title">
                <el-icon><User /></el-icon>
                个人信息
              </span>
              <el-tag size="small" type="primary" effect="light">
                {{ roleLabel(userState.currentUser?.role) }}
              </el-tag>
            </div>
          </template>
          <div class="profile-body">
            <el-avatar :size="76" class="ustb-avatar">
              <el-icon :size="36"><UserFilled /></el-icon>
            </el-avatar>
            <el-descriptions :column="1" size="small" class="profile-desc">
              <el-descriptions-item label="姓名">
                <strong class="profile-name">{{ shortName || '加载中...' }}</strong>
              </el-descriptions-item>
              <el-descriptions-item label="当前角色">
                {{ roleLabel(userState.currentUser?.role) }}
              </el-descriptions-item>
              <el-descriptions-item v-if="userState.currentUser?.studentId" label="学号">
                {{ userState.currentUser.studentId }}
              </el-descriptions-item>
              <el-descriptions-item v-if="userState.currentUser?.empId" label="教职工号">
                {{ userState.currentUser.empId }}
              </el-descriptions-item>
              <el-descriptions-item label="所属单位">
                {{ collegeLabel(userState.currentUser?.collegeId, userState.currentUser?.orgId) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12" style="margin-bottom: 16px">
        <el-card class="app-card-hover full-height-card">
          <template #header>
            <div class="card-head">
              <span class="card-head-title">
                <el-icon><Calendar /></el-icon>
                本学期教务概览
              </span>
              <el-button text circle :loading="loading" @click="loadOverview" title="刷新">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>

          <el-row :gutter="14" class="kpi-grid">
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">当前学期</div>
                <div class="stat-num num-primary" style="font-size: 18px">2026-2027-1</div>
                <div class="stat-foot">秋季学期进行中</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">开放选课轮次</div>
                <div class="stat-num num-success">{{ activeRoundCount }}</div>
                <div class="stat-foot">已发布 {{ publishedRounds.length }} 轮</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">系统服务状态</div>
                <div class="stat-num" :class="serviceOnline ? 'num-success' : 'num-warn'" style="font-size: 18px">
                  {{ serviceOnline ? '运行正常' : '连接中' }}
                </div>
                <div class="stat-foot">教务数据实时同步</div>
              </div>
            </el-col>
          </el-row>

          <div class="health-footer">
            <el-tag type="primary" size="small" effect="light">当前轮次</el-tag>
            <span class="health-meta">
              {{ currentActiveRound ? `${currentActiveRound.round_name}（截止 ${formatTime(currentActiveRound.end_time)}）` : '当前暂无进行中的选课轮次' }}
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二排：快捷功能入口网格 -->
    <el-card class="app-card-hover" style="margin-bottom: 24px">
      <template #header>
        <div class="card-head">
          <span class="card-head-title">
            <el-icon><Grid /></el-icon>
            业务快捷入口
          </span>
          <span class="header-hint">点击卡片进入对应业务办理</span>
        </div>
      </template>

      <div class="entry-grid">
        <div
          v-for="item in quickEntries"
          :key="item.path"
          class="entry-card"
          @click="router.push(item.path)"
        >
          <div class="entry-icon" :style="{ background: item.bg, color: item.color }">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </div>
          <div class="entry-body">
            <div class="entry-title">
              <span>{{ item.title }}</span>
              <el-icon class="entry-arrow"><ArrowRight /></el-icon>
            </div>
            <div class="entry-desc">{{ item.desc }}</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 第三排：最新选课与教务通知公告 -->
    <el-card class="app-card-hover">
      <template #header>
        <div class="card-head">
          <span class="card-head-title">
            <el-icon><Bell /></el-icon>
            选课与教务通知公告
          </span>
          <el-button type="primary" link @click="router.push('/prep')">查看全部轮次</el-button>
        </div>
      </template>

      <div v-if="publishedRounds.length" class="notice-list">
        <div v-for="r in publishedRounds" :key="r.round_id" class="notice-item">
          <div class="notice-top">
            <div class="notice-title-wrap">
              <el-tag
                size="small"
                :type="r.round_status === 3 ? 'success' : r.round_status === 2 ? 'primary' : 'info'"
                effect="light"
              >
                {{ r.round_status === 3 ? '进行中' : r.round_status === 2 ? '已发布' : '已结束' }}
              </el-tag>
              <span class="notice-name">{{ r.round_name }}</span>
            </div>
            <span class="notice-time">{{ formatTime(r.start_time) }} 至 {{ formatTime(r.end_time) }}</span>
          </div>
          <div class="notice-content">{{ r.notice_content }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无已发布的选课通知" :image-size="64" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { systemApi } from '../api/modules'
import { prepApi } from '../api/prep'
import { userState } from '../stores/user'

const router = useRouter()
const serviceOnline = ref(false)
const rounds = ref([])
const loading = ref(false)

const shortName = computed(() => {
  const full = userState.currentUser?.displayName || ''
  return full.replace(/（.*）$/, '').trim() || full
})

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' })
)

const publishedRounds = computed(() =>
  rounds.value.filter((r) => [2, 3, 4].includes(r.round_status))
)

const activeRoundCount = computed(() =>
  rounds.value.filter((r) => r.round_status === 3 || r.round_status === 2).length
)

const currentActiveRound = computed(() =>
  rounds.value.find((r) => r.round_status === 3) || rounds.value.find((r) => r.round_status === 2) || null
)

const roleLabel = (role) => ({
  ACADEMIC_OFFICE: '教务处经办',
  ACADEMIC_DIRECTOR: '教务处处长',
  COLLEGE_SECRETARY: '学院教学秘书',
  TEACHER: '任课教师',
  STUDENT: '在校学生',
  GRADUATE_SCHOOL: '研究生院审核',
  SYS_ADMIN: '系统管理员'
}[role] || '教务用户')

const collegeLabel = (collegeId, orgId) => {
  const map = { '0001': '经济管理学院', '0002': '计算机与通信工程学院' }
  if (collegeId && map[collegeId]) return map[collegeId]
  const orgMap = { ORG900: '教务处', ORG901: '研究生院', ORG999: '信息中心' }
  return orgMap[orgId] || '校级单位'
}

const quickEntries = [
  {
    title: '选课准备与发布',
    desc: '开课计划维护、限选条件配置、选课轮次审批与通知发布',
    path: '/prep',
    icon: 'Setting',
    bg: 'rgba(11, 92, 173, 0.10)',
    color: '#0b5cad'
  },
  {
    title: '学生选课',
    desc: '全校课程检索、选退课办理、按周课表与教学班上课名单管理',
    path: '/selection',
    icon: 'Calendar',
    bg: 'rgba(22, 163, 74, 0.10)',
    color: '#16a34a'
  },
  {
    title: '成绩录入与审核',
    desc: '按课堂录分提交、学院核对码核验、成绩发布与校外交流成绩认定',
    path: '/score',
    icon: 'EditPen',
    bg: 'rgba(234, 88, 12, 0.10)',
    color: '#ea580c'
  },
  {
    title: '成绩查询与统计',
    desc: '教学评价、本人查分、学业统计、中英文成绩单打印与统计报表',
    path: '/stats',
    icon: 'DataAnalysis',
    bg: 'rgba(124, 58, 237, 0.10)',
    color: '#7c3aed'
  },
  {
    title: '成绩更正申请',
    desc: '已发布成绩更正发起、学院与教务处两级审批及结果通知',
    path: '/correction',
    icon: 'DocumentChecked',
    bg: 'rgba(8, 145, 178, 0.10)',
    color: '#0891b2'
  },
  {
    title: '系统维护',
    desc: '教学编码参数配置、数据备份登记与恢复前应急快照管理',
    path: '/admin',
    icon: 'Tools',
    bg: 'rgba(100, 116, 139, 0.12)',
    color: '#475569'
  }
]

function formatTime(t) {
  return String(t || '').replace('T', ' ').slice(0, 16)
}

async function loadOverview() {
  loading.value = true
  try {
    const [healthRes, roundsRes] = await Promise.allSettled([
      systemApi.health(),
      prepApi.listRounds({ term: '2026-2027-1' })
    ])
    serviceOnline.value = healthRes.status === 'fulfilled' && !!healthRes.value?.data?.db_connected
    if (roundsRes.status === 'fulfilled') {
      rounds.value = roundsRes.value?.data || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.welcome-header {
  margin-bottom: 22px;
}
.welcome-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary);
  letter-spacing: 0.2px;
}
.welcome-sub {
  color: var(--app-text-secondary);
  margin-top: 6px;
  font-size: 13px;
}

.full-height-card {
  height: 100%;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.card-head-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}
.header-hint {
  font-size: 12px;
  color: var(--app-text-tertiary);
  font-weight: 400;
}

.profile-body {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 22px;
}
.ustb-avatar {
  background: linear-gradient(135deg, #003366 0%, #0b5cad 100%);
  color: #ffffff;
  flex-shrink: 0;
  box-shadow: 0 6px 18px rgba(0, 51, 102, 0.22);
}
.profile-desc {
  flex: 1 1 240px;
  min-width: 0;
}
.profile-name {
  font-size: 15px;
  color: var(--app-text-primary);
}

.kpi-grid {
  margin-bottom: 18px;
}
.stat-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
}
.stat-title {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-bottom: 6px;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1.3;
}
.num-primary { color: var(--brand-primary); }
.num-success { color: #389e0d; }
.num-warn { color: #d48806; }
.stat-foot {
  margin-top: 4px;
  font-size: 11px;
  color: var(--app-text-tertiary);
}

.health-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 10px;
  border-top: 1px dashed var(--app-border);
}
.health-meta {
  font-size: 12.5px;
  color: var(--app-text-secondary);
}

/* 快捷业务卡片网格 */
.entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.entry-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  background: var(--app-card-muted-bg);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}
.entry-card:hover {
  transform: translateY(-2px);
  border-color: rgba(11, 92, 173, 0.28);
  background: var(--app-card-bg);
  box-shadow: var(--app-shadow-sm);
}
.entry-icon {
  width: 42px;
  height: 42px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.entry-body {
  flex: 1;
  min-width: 0;
}
.entry-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14.5px;
  font-weight: 600;
  color: var(--app-text-primary);
  margin-bottom: 5px;
}
.entry-arrow {
  color: var(--app-text-tertiary);
  transition: transform 0.2s ease, color 0.2s ease;
}
.entry-card:hover .entry-arrow {
  transform: translateX(3px);
  color: var(--brand-primary);
}
.entry-desc {
  font-size: 12.5px;
  color: var(--app-text-secondary);
  line-height: 1.55;
}

/* 通知列表 */
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.notice-item {
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
}
.notice-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.notice-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notice-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}
.notice-time {
  font-size: 12px;
  color: var(--app-text-tertiary);
}
.notice-content {
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 680px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }
}
</style>
