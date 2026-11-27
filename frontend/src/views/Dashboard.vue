<template>
  <div class="dashboard-page">
    <!-- 顶部欢迎区（极简克制，对齐 USTB Manager） -->
    <div class="welcome-bar">
      <div>
        <h1 class="welcome-title">
          {{ greeting }}{{ shortName ? `，${shortName}` : '' }}
        </h1>
        <p class="welcome-sub">
          {{ todayText }} · {{ currentTerm }} 学期
        </p>
      </div>
    </div>

    <!-- 第一排：个人名片 + 学期概览双卡 -->
    <el-row :gutter="20" class="section-row">
      <el-col :xs="24" :lg="11" class="col-gap">
        <el-card class="app-card-hover full-height-card profile-card">
          <div class="profile-layout">
            <div class="profile-avatar">
              <span>{{ avatarText }}</span>
            </div>
            <div class="profile-main">
              <div class="profile-name-row">
                <span class="profile-name">{{ shortName || '加载中' }}</span>
                <span class="role-badge">{{ roleLabel(userState.currentUser?.role) }}</span>
              </div>
              <div class="profile-meta-grid">
                <div class="meta-item">
                  <span class="meta-label">{{ userState.currentUser?.studentId ? '学号' : '工号' }}</span>
                  <span class="meta-val">{{ userState.currentUser?.studentId || userState.currentUser?.empId || '—' }}</span>
                </div>
                <div v-if="userState.currentUser?.studentId" class="meta-item">
                  <span class="meta-label">班级</span>
                  <span class="meta-val">{{ studentClass }}</span>
                </div>
                <div v-else class="meta-item">
                  <span class="meta-label">所属单位</span>
                  <span class="meta-val">{{ collegeLabel(userState.currentUser?.collegeId, userState.currentUser?.orgId) }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">当前学期</span>
                  <span class="meta-val">{{ currentTerm }}</span>
                </div>
                <div v-if="userState.currentUser?.studentId" class="meta-item">
                  <span class="meta-label">所属单位</span>
                  <span class="meta-val">{{ collegeLabel(userState.currentUser?.collegeId, userState.currentUser?.orgId) }}</span>
                </div>
                <div v-else class="meta-item">
                  <span class="meta-label">当前周次</span>
                  <span class="meta-val">{{ currentWeekText }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="13" class="col-gap">
        <el-card class="app-card-hover full-height-card overview-card">
          <div class="overview-top">
            <span class="block-label">本学期选课与教学概览</span>
            <el-button text circle size="small" :loading="loading" @click="loadOverview" title="刷新">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
          <div class="metrics-strip">
            <div class="metric-cell">
              <div class="metric-value metric-primary">{{ activeRoundCount }}</div>
              <div class="metric-label">进行中轮次</div>
            </div>
            <div class="metric-sep" />
            <div class="metric-cell">
              <div class="metric-value">{{ publishedRounds.length }}</div>
              <div class="metric-label">本学期已发布</div>
            </div>
            <div class="metric-sep" />
            <div class="metric-cell">
              <div class="metric-value">{{ quickEntries.length }}</div>
              <div class="metric-label">可用业务板块</div>
            </div>
          </div>
          <div class="round-footnote">
            <el-icon class="footnote-icon"><Clock /></el-icon>
            <span v-if="currentActiveRound">
              <strong>{{ currentActiveRound.round_name }}</strong>
              <span class="footnote-sep">·</span>
              <span>截止 {{ formatTime(currentActiveRound.end_time) }}</span>
            </span>
            <span v-else>当前暂无开放办理的选课轮次</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二排：独立悬浮服务入口（去套娃卡片） -->
    <div class="section-head">
      <h2 class="section-title">常用服务</h2>
    </div>
    <div class="entry-grid">
      <div
        v-for="item in quickEntries"
        :key="item.path"
        class="entry-card app-card-hover"
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

    <!-- 第二排半：全班跨组数据联动一览 -->
    <div v-if="crossOverview" class="section-head" style="margin-top: 26px">
      <div class="cross-head-left">
        <h2 class="section-title">全班跨组数据联动一览</h2>
        <span class="cross-head-badge">
          已联动 {{ crossOverview.summary?.connected_business_groups || 8 }} 个业务组 ＋ 公共主数据 · 共 {{ crossOverview.summary?.active_upstream_tables || 0 }} 张共享表 · {{ crossOverview.summary?.total_upstream_records || 0 }} 条实时数据
        </span>
      </div>
      <el-button type="primary" link :loading="loadingCross" @click="loadCrossOverview">刷新联动状态</el-button>
    </div>
    <div v-if="crossOverview" class="cross-grid">
      <div
        v-for="g in crossOverview.groups || []"
        :key="g.group_no"
        class="cross-card app-card-hover"
      >
        <div class="cross-card-top">
          <div class="cross-card-title">
            <span class="cross-group-no">{{ g.group_label }}</span>
            <span class="cross-system-name">{{ g.system_name }}</span>
          </div>
          <el-tag
            size="small"
            :type="g.direction === '双向协同' ? 'success' : g.direction === '只读接入' ? 'primary' : 'info'"
            effect="light"
          >
            {{ g.direction }}
          </el-tag>
        </div>
        <p class="cross-card-desc">{{ g.scenarios }}</p>
        <div class="cross-card-foot">
          <span>共享表 <b>{{ g.active_table_count }}</b> / {{ g.table_count }} 张<template v-if="g.view_count > 0"> · 视图 {{ g.view_count }} 个</template></span>
          <span>实时记录 <b>{{ g.total_records }}</b> 条</span>
        </div>
      </div>
    </div>

    <!-- 第三排：教务通知公告 -->
    <div class="section-head" style="margin-top: 26px">
      <h2 class="section-title">选课通知公告</h2>
      <el-button type="primary" link @click="router.push('/prep')">全部轮次</el-button>
    </div>
    <el-card class="notice-card">
      <div v-if="publishedRounds.length" class="notice-list">
        <div v-for="r in publishedRounds" :key="r.round_id" class="notice-item">
          <div class="notice-bar" :class="r.round_status === 3 ? 'bar-active' : 'bar-muted'" />
          <div class="notice-main">
            <div class="notice-top">
              <div class="notice-title-wrap">
                <span class="notice-name">{{ r.round_name }}</span>
                <el-tag
                  size="small"
                  :type="r.round_status === 3 ? 'success' : r.round_status === 2 ? 'primary' : 'info'"
                  effect="light"
                >
                  {{ r.round_status === 3 ? '进行中' : r.round_status === 2 ? '已发布' : '已结束' }}
                </el-tag>
              </div>
              <span class="notice-time">{{ formatTime(r.start_time) }} — {{ formatTime(r.end_time) }}</span>
            </div>
            <p class="notice-content">{{ r.notice_content }}</p>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无选课通知" :image-size="60" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { prepApi } from '../api/prep'
import { systemApi } from '../api/modules'
import { userState } from '../stores/user'
import { visibleModulesForRole } from '../stores/permissions'
import { DEFAULT_TERM, loadDynamicTermOptions } from '../constants/terms'

const router = useRouter()
const rounds = ref([])
const loading = ref(false)
const currentTerm = ref(DEFAULT_TERM)
const crossOverview = ref(null)
const loadingCross = ref(false)

const shortName = computed(() => {
  const full = userState.currentUser?.displayName || ''
  return full.replace(/（.*）$/, '').trim() || full
})

const studentClass = computed(() => {
  if (userState.currentUser?.className) {
    return userState.currentUser.className
  }
  const full = userState.currentUser?.displayName || ''
  const match = full.match(/（([^）]+)）$/)
  if (!match) return '—'
  const inside = match[1]
  return inside.includes('·') ? inside.split('·').pop().trim() : inside.trim()
})

const avatarText = computed(() => {
  const n = shortName.value
  if (!n) return 'U'
  return /^[A-Za-z]/.test(n) ? n.slice(0, 2).toUpperCase() : n.slice(0, 1)
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

const todayText = computed(() => {
  const d = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weekdays[d.getDay()]}`
})

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

const currentWeekText = computed(() => {
  const start = new Date(2026, 8, 7) // 2026-09-07
  const diffDays = Math.floor((Date.now() - start.getTime()) / 86400000)
  const week = Math.min(20, Math.max(1, Math.floor(diffDays / 7) + 1))
  return `第 ${week} 周`
})

const collegeLabel = (collegeId, orgId) => {
  const map = { '0001': '经济管理学院', '0002': '计算机与通信工程学院' }
  if (collegeId && map[collegeId]) return map[collegeId]
  const orgMap = { ORG900: '教务处', ORG901: '研究生院', ORG999: '信息中心' }
  return orgMap[orgId] || '校级单位'
}

const quickEntries = computed(() => visibleModulesForRole(userState.currentUser?.role))

function formatTime(t) {
  return String(t || '').replace('T', ' ').slice(0, 16)
}

async function loadCrossOverview() {
  loadingCross.value = true
  try {
    const res = await systemApi.crossGroupOverview()
    crossOverview.value = res?.data || null
  } catch {
    // ignore
  } finally {
    loadingCross.value = false
  }
}

async function loadOverview() {
  loading.value = true
  try {
    const termData = await loadDynamicTermOptions(systemApi)
    currentTerm.value = termData.currentTerm || DEFAULT_TERM
    const [roundsRes] = await Promise.all([
      prepApi.listRounds({ term: currentTerm.value }),
      loadCrossOverview()
    ])
    rounds.value = roundsRes?.data || []
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.welcome-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 22px;
}
.welcome-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--app-text-primary);
  line-height: 1.25;
}
.welcome-sub {
  margin: 6px 0 0;
  font-size: 13.5px;
  color: var(--app-text-secondary);
}

.section-row {
  margin-bottom: 8px;
}
.col-gap {
  margin-bottom: 18px;
}
.full-height-card {
  height: 100%;
}
.profile-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  padding: 18px 24px !important;
}
.overview-card :deep(.el-card__body) {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 18px 22px !important;
}

/* 个人名片 */
.profile-layout {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 22px;
}
.profile-avatar {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, #003366 0%, #0b5cad 100%);
  color: #ffffff;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8px 20px rgba(0, 51, 102, 0.22);
}
.profile-main {
  flex: 1;
  min-width: 0;
}
.profile-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.profile-name {
  font-size: 21px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--app-text-primary);
}
.role-badge {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--brand-primary);
  background: var(--app-menu-active-bg);
  padding: 3px 11px;
  border-radius: 999px;
}
.profile-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
}
.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.meta-label {
  font-size: 12.5px;
  color: var(--app-text-tertiary);
}
.meta-val {
  font-size: 15.5px;
  font-weight: 600;
  color: var(--app-text-primary);
}

/* 概览卡 */
.overview-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.block-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
}
.metrics-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 12px;
}
.metric-cell {
  flex: 1;
  text-align: center;
}
.metric-value {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--app-text-primary);
  line-height: 1.2;
}
.metric-primary {
  color: var(--brand-primary);
}
.metric-label {
  margin-top: 5px;
  font-size: 12px;
  color: var(--app-text-secondary);
}
.metric-sep {
  width: 1px;
  height: 34px;
  background: var(--app-border);
}
.round-footnote {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--app-card-muted-bg);
  font-size: 12.5px;
  color: var(--app-text-secondary);
}
.footnote-icon {
  color: var(--brand-primary);
  flex-shrink: 0;
}
.footnote-sep {
  margin: 0 6px;
  color: var(--app-text-tertiary);
}

/* 分区标题 */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 6px 2px 12px;
}
.section-title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 650;
  color: var(--app-text-primary);
}

/* 快捷业务独立悬浮卡 */
.entry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.entry-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 15px;
  border: 1px solid var(--app-border);
  background: var(--app-card-bg);
  box-shadow: var(--app-shadow-sm);
  cursor: pointer;
}
.entry-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
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
  margin-bottom: 4px;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 通知列表 */
.notice-list {
  display: flex;
  flex-direction: column;
}
.notice-item {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  border-bottom: 1px solid var(--app-border);
}
.notice-item:first-child {
  padding-top: 2px;
}
.notice-item:last-child {
  padding-bottom: 2px;
  border-bottom: none;
}
.notice-bar {
  width: 3px;
  border-radius: 3px;
  flex-shrink: 0;
}
.bar-active {
  background: var(--brand-primary);
}
.bar-muted {
  background: var(--app-border-strong);
}
.notice-main {
  flex: 1;
  min-width: 0;
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
  margin: 0;
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

/* 跨组联动概览 */
.cross-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.cross-head-badge {
  font-size: 12px;
  color: var(--brand-primary);
  background: var(--app-menu-active-bg);
  padding: 3px 10px;
  border-radius: 999px;
}
.cross-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.cross-card {
  padding: 15px 16px;
  border-radius: 14px;
  border: 1px solid var(--app-border);
  background: var(--app-card-bg);
  box-shadow: var(--app-shadow-sm);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
}
.cross-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cross-card-title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.cross-group-no {
  font-size: 12px;
  font-weight: 700;
  color: var(--brand-primary);
  white-space: nowrap;
}
.cross-system-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--app-text-primary);
}
.cross-card-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--app-text-secondary);
}
.cross-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px dashed var(--app-border);
  font-size: 12px;
  color: var(--app-text-tertiary);
}
.cross-card-foot b {
  color: var(--app-text-primary);
  font-weight: 650;
}

@media (max-width: 1080px) {
  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .cross-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }
  .cross-grid {
    grid-template-columns: 1fr;
  }
  .profile-layout {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
