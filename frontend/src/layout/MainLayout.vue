<template>
  <el-container class="layout-root">
    <el-aside width="236px" class="layout-aside">
      <div class="brand">
        <div class="brand-title">选课及成绩管理系统</div>
        <div class="brand-sub">03 组 · 教学教务一体化平台</div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1b2a4a"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>系统工作台</span>
        </el-menu-item>
        <el-menu-item index="/prep">
          <el-icon><Setting /></el-icon>
          <span>① 选课准备与发布</span>
        </el-menu-item>
        <el-menu-item index="/selection">
          <el-icon><Select /></el-icon>
          <span>② 学生选课</span>
        </el-menu-item>
        <el-menu-item index="/score">
          <el-icon><EditPen /></el-icon>
          <span>③ 成绩录入与审核</span>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <span>④ 成绩查询与统计</span>
        </el-menu-item>
        <el-menu-item index="/correction">
          <el-icon><DocumentChecked /></el-icon>
          <span>⑤ 成绩更正申请</span>
        </el-menu-item>
        <el-menu-item index="/admin">
          <el-icon><Tools /></el-icon>
          <span>⑥ 系统维护</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <span class="page-title">{{ route.meta.title || '系统工作台' }}</span>
          <el-tag size="small" type="info" effect="plain">2026-2027-1</el-tag>
          <el-tag size="small" type="success" effect="plain">DB v9 (14表)</el-tag>
        </div>
        <div class="header-right">
          <span class="switch-label">演示身份：</span>
          <el-select
            v-model="userState.currentKey"
            size="small"
            style="width: 240px"
            @change="handleSwitch"
          >
            <el-option
              v-for="acc in userState.accounts"
              :key="acc.userId"
              :label="acc.displayName"
              :value="acc.userId"
            />
          </el-select>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view :key="userState.currentKey" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { initUserStore, switchDemoUser, userState } from '../stores/user'

const route = useRoute()

onMounted(() => {
  initUserStore()
})

async function handleSwitch(val) {
  await switchDemoUser(val)
  ElMessage.success(`已切换身份：${userState.currentUser?.displayName || val}`)
}
</script>

<style scoped>
.layout-root { height: 100vh; }
.layout-aside { background-color: #1b2a4a; color: #fff; display: flex; flex-direction: column; }
.brand { padding: 18px 16px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); }
.brand-title { font-size: 16px; font-weight: 700; letter-spacing: 0.5px; }
.brand-sub { font-size: 12px; color: #94a3b8; margin-top: 4px; }
.side-menu { border-right: none; flex: 1; }
.layout-header {
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.header-left { display: flex; align-items: center; gap: 10px; }
.page-title { font-size: 16px; font-weight: 600; color: #1e293b; }
.header-right { display: flex; align-items: center; gap: 8px; }
.switch-label { font-size: 13px; color: #64748b; }
.layout-main { padding: 20px; overflow-y: auto; }
</style>
