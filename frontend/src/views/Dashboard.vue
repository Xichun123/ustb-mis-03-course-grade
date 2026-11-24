<template>
  <div>
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">后端服务与云库状态</div>
          <div class="stat-value">
            <el-tag :type="health?.db_connected ? 'success' : 'warning'" size="large">
              {{ health?.db_connected ? `已连接 · ${health.database} (${health.own_v9_tables}/14 表)` : '待配置云库连接' }}
            </el-tag>
          </div>
          <div class="stat-desc">{{ health?.schema_version || '请先启动 Spring Boot 后端（8080 端口）' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">当前演示身份</div>
          <div class="stat-value">{{ userState.currentUser?.displayName || '未加载' }}</div>
          <div class="stat-desc">
            角色：{{ userState.currentUser?.role || '-' }}
            <span v-if="userState.currentUser?.empId"> · 工号 {{ userState.currentUser.empId }}</span>
            <span v-if="userState.currentUser?.studentId"> · 学号 {{ userState.currentUser.studentId }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">全组 29 模块实施进度</div>
          <div class="stat-value">已完成业务 ① ④ ⑤</div>
          <div class="stat-desc">业务①④⑤已实库联调通过；业务②③⑥可按独立包并行合入</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="03 组六大业务模块与成员分工看板">
      <el-table :data="moduleRows" border stripe>
        <el-table-column prop="biz" label="业务子系统" width="180" />
        <el-table-column prop="owner" label="负责人" width="100" />
        <el-table-column prop="tester" label="交叉测试人" width="110" />
        <el-table-column prop="scope" label="涵盖叶子模块（共 29 个）" />
        <el-table-column prop="status" label="当前状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.ready ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入口" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(row.path)">进入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { systemApi } from '../api/modules'
import { userState } from '../stores/user'

const router = useRouter()
const health = ref(null)

const moduleRows = [
  { biz: '① 选课准备与发布', owner: '朱鳞太', tester: '张文浩', scope: '1.1 开课计划导入、1.2 限选条件设置、1.3 选课轮次设置、1.4 选课方案审批、1.5 选课通知发布', status: '样板已完成', ready: true, path: '/prep' },
  { biz: '② 学生选课', owner: '张文浩', tester: '黎家俊', scope: '2.1 课程查询、2.2 选退课、2.3 选课校验（含重修免听）、2.4 超额筛选、2.5 停开、2.6 课表与上课名单、2.7 上课名单调整', status: '骨架就绪待开发', ready: false, path: '/selection' },
  { biz: '③ 成绩录入与审核', owner: '蒋作', tester: '朱鳞太', scope: '3.1 按课堂号录分、3.2 总评提交与随机码、3.3 学院核对、3.4 发布、3.5 校外交流成绩换算认定', status: '骨架就绪待开发', ready: false, path: '/score' },
  { biz: '④ 成绩查询与统计', owner: '黎家俊', tester: '蒋作', scope: '4.1 学生评教、4.2 先评教后查分、4.3 学业统计（排除辅修）、4.4 成绩单打印、4.5 统计报表、4.6 评教汇总', status: '已完成（4.1–4.6）', ready: true, path: '/stats' },
  { biz: '⑤ 成绩更正申请', owner: '朱鳞太', tester: '张文浩', scope: '5.1 更正申请（原值并发核验）、5.2 学院审核、5.3 教务处审批、5.4 改分重算与学生通知', status: '样板已完成', ready: true, path: '/correction' },
  { biz: '⑥ 系统维护', owner: '蒋作', tester: '朱鳞太', scope: '6.1 编码与系统参数维护、6.2 数据备份与恢复', status: '骨架就绪待开发', ready: false, path: '/admin' }
]

onMounted(async () => {
  try {
    const res = await systemApi.health()
    health.value = res.data
  } catch {
    health.value = null
  }
})
</script>

<style scoped>
.stat-label { font-size: 13px; color: #64748b; margin-bottom: 8px; }
.stat-value { font-size: 18px; font-weight: 600; color: #0f172a; margin-bottom: 6px; }
.stat-desc { font-size: 12px; color: #94a3b8; }
</style>
