<template>
  <div class="dashboard-page">
    <!-- 欢迎区（复刻 USTB Manager 首页风格） -->
    <div class="welcome-header">
      <div class="welcome-title">
        {{ greeting }}{{ userState.currentUser?.displayName ? `，${userState.currentUser.displayName}` : '' }}
      </div>
      <div class="welcome-sub">
        {{ todayText }} · 2026-2027-1 学期 · 选课及成绩管理一体化平台（03组）
      </div>
    </div>

    <!-- 双核心状态卡片 -->
    <el-row :gutter="24" style="margin-bottom: 24px">
      <!-- 当前身份信息卡 -->
      <el-col :xs="24" :lg="12" style="margin-bottom: 16px">
        <el-card class="app-card-hover full-height-card">
          <template #header>
            <div class="card-head">
              <span class="card-head-title">
                <el-icon><User /></el-icon>
                当前身份信息
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
              <el-descriptions-item label="姓名 / 账号">
                <strong class="profile-name">{{ userState.currentUser?.displayName || '加载中...' }}</strong>
              </el-descriptions-item>
              <el-descriptions-item label="系统角色">
                {{ roleLabel(userState.currentUser?.role) }}（{{ userState.currentUser?.role || '-' }}）
              </el-descriptions-item>
              <el-descriptions-item v-if="userState.currentUser?.studentId" label="学号">
                {{ userState.currentUser.studentId }}
              </el-descriptions-item>
              <el-descriptions-item v-if="userState.currentUser?.empId" label="教职工号">
                {{ userState.currentUser.empId }}
                <span v-if="userState.currentUser?.teacherCode8" class="muted-inline">
                  （教学班工号 {{ userState.currentUser.teacherCode8 }}）
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="所属学院 / 单位">
                {{ collegeLabel(userState.currentUser?.collegeId, userState.currentUser?.orgId) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <!-- 系统与云库状态卡 -->
      <el-col :xs="24" :lg="12" style="margin-bottom: 16px">
        <el-card class="app-card-hover full-height-card">
          <template #header>
            <div class="card-head">
              <span class="card-head-title">
                <el-icon><Connection /></el-icon>
                云数据库与实施总览
              </span>
              <el-button text circle :loading="loadingHealth" @click="fetchHealth" title="刷新连接状态">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>

          <el-row :gutter="16" class="kpi-grid">
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">云库数据表</div>
                <div class="stat-num" :class="health?.db_connected ? 'num-success' : 'num-warn'">
                  {{ health?.db_connected ? `${health.own_v9_tables}/14` : '离线' }}
                </div>
                <div class="stat-foot">{{ health?.database || '等待连接' }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">业务子系统</div>
                <div class="stat-num num-primary">6 / 6</div>
                <div class="stat-foot">全模块已合入</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-box">
                <div class="stat-title">叶子功能模块</div>
                <div class="stat-num">29</div>
                <div class="stat-foot">v9 全量对齐</div>
              </div>
            </el-col>
          </el-row>

          <div class="health-footer">
            <el-tag :type="health?.db_connected ? 'success' : 'warning'" size="small" effect="light">
              {{ health?.db_connected ? '云 MySQL 8.0 在线' : '后端未连接' }}
            </el-tag>
            <span class="health-meta">
              {{ health?.schema_version || '请确认 Spring Boot 8080 端口已启动' }}
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 六大业务板块快捷看板 -->
    <el-card class="app-card-hover">
      <template #header>
        <div class="card-head">
          <span class="card-head-title">
            <el-icon><Grid /></el-icon>
            03 组六大业务子系统与分工总览
          </span>
          <el-tag type="success" size="small" effect="light">29 个叶子模块全部上线</el-tag>
        </div>
      </template>

      <el-table :data="moduleRows" stripe>
        <el-table-column prop="biz" label="业务子系统" width="185">
          <template #default="{ row }">
            <span class="biz-name">{{ row.biz }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="负责人" width="95" />
        <el-table-column prop="tester" label="交叉测试" width="95" />
        <el-table-column prop="scope" label="涵盖叶子模块（共 29 个）" min-width="320" />
        <el-table-column prop="status" label="状态" width="145">
          <template #default="{ row }">
            <el-tag type="success" effect="light" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="快捷进入" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(row.path)">
              进入 →
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { systemApi } from '../api/modules'
import { userState } from '../stores/user'

const router = useRouter()
const health = ref(null)
const loadingHealth = ref(false)

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

const roleLabel = (role) => ({
  ACADEMIC_OFFICE: '教务处经办',
  ACADEMIC_DIRECTOR: '教务处处长',
  COLLEGE_SECRETARY: '学院教学秘书',
  TEACHER: '任课教师',
  STUDENT: '在校学生',
  GRADUATE_SCHOOL: '研究生院审核',
  SYS_ADMIN: '系统管理员'
}[role] || '演示用户')

const collegeLabel = (collegeId, orgId) => {
  const map = { '0001': '经济管理学院', '0002': '计算机与通信工程学院' }
  if (collegeId && map[collegeId]) return `${map[collegeId]}（${collegeId}）`
  const orgMap = { ORG900: '教务处', ORG901: '研究生院', ORG999: '信息中心 / 系统运维' }
  return orgMap[orgId] || collegeId || orgId || '校级公共'
}

const moduleRows = [
  { biz: '① 选课准备与发布', owner: '朱鳞太', tester: '张文浩', scope: '1.1 开课计划导入、1.2 限选条件设置、1.3 选课轮次设置、1.4 选课方案审批、1.5 选课通知发布', status: '已完成（1.1–1.5）', path: '/prep' },
  { biz: '② 学生选课', owner: '张文浩', tester: '黎家俊', scope: '2.1 课程查询、2.2 选退课、2.3 选课校验（含重修免听）、2.4 超额筛选、2.5 停开预览、2.6 课表与上课名单、2.7 上课名单调整', status: '已完成（2.1–2.7）', path: '/selection' },
  { biz: '③ 成绩录入与审核', owner: '蒋作', tester: '朱鳞太', scope: '3.1 按课堂号录分、3.2 总评提交与随机码、3.3 学院核对、3.4 发布、3.5 校外交流成绩换算认定', status: '已完成（3.1–3.5）', path: '/score' },
  { biz: '④ 成绩查询与统计', owner: '黎家俊', tester: '蒋作', scope: '4.1 学生评教、4.2 先评教后查分、4.3 学业统计（排除辅修）、4.4 成绩单打印、4.5 统计报表、4.6 评教汇总', status: '已完成（4.1–4.6）', path: '/stats' },
  { biz: '⑤ 成绩更正申请', owner: '朱鳞太', tester: '张文浩', scope: '5.1 更正申请（原值并发核验）、5.2 学院审核、5.3 教务处审批、5.4 改分重算与学生通知', status: '已完成（5.1–5.4）', path: '/correction' },
  { biz: '⑥ 系统维护', owner: '蒋作', tester: '朱鳞太', scope: '6.1 编码与系统参数维护、6.2 数据备份与恢复', status: '已完成（6.1–6.2）', path: '/admin' }
]

async function fetchHealth() {
  loadingHealth.value = true
  try {
    const res = await systemApi.health()
    health.value = res.data
  } catch {
    health.value = null
  } finally {
    loadingHealth.value = false
  }
}

onMounted(fetchHealth)
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
.muted-inline {
  color: var(--app-text-secondary);
  font-size: 12px;
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
  font-size: 24px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1.2;
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
  padding-top: 8px;
  border-top: 1px dashed var(--app-border);
}
.health-meta {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.biz-name {
  font-weight: 600;
  color: var(--app-text-primary);
}
</style>
