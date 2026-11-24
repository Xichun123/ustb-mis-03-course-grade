<template>
  <div class="app-shell">
    <!-- 移动端抽屉侧边栏 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerOpen"
      direction="ltr"
      :size="240"
      :with-header="false"
      body-class="mobile-drawer-body"
    >
      <div class="sider-inner">
        <div class="app-sider-brand">
          <img class="app-sider-brand-logo" src="/app-icon.png" alt="USTB" />
          <span class="app-sider-brand-name">USTB 选课成绩</span>
        </div>
        <el-menu
          :default-active="route.path"
          router
          class="app-side-menu"
          @select="drawerOpen = false"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>系统工作台</template>
          </el-menu-item>
          <div class="app-menu-divider" />
          <el-menu-item index="/prep">
            <el-icon><Setting /></el-icon>
            <template #title>选课准备与发布</template>
          </el-menu-item>
          <el-menu-item index="/selection">
            <el-icon><Calendar /></el-icon>
            <template #title>学生选课</template>
          </el-menu-item>
          <el-menu-item index="/score">
            <el-icon><EditPen /></el-icon>
            <template #title>成绩录入与审核</template>
          </el-menu-item>
          <div class="app-menu-divider" />
          <el-menu-item index="/stats">
            <el-icon><DataAnalysis /></el-icon>
            <template #title>成绩查询与统计</template>
          </el-menu-item>
          <el-menu-item index="/correction">
            <el-icon><DocumentChecked /></el-icon>
            <template #title>成绩更正申请</template>
          </el-menu-item>
          <div class="app-menu-divider" />
          <el-menu-item index="/admin">
            <el-icon><Tools /></el-icon>
            <template #title>系统维护</template>
          </el-menu-item>
        </el-menu>
      </div>
    </el-drawer>

    <!-- 桌面端固定毛玻璃白色侧边栏 -->
    <aside
      v-else
      class="app-sider desktop-sider"
      :style="{ width: collapsed ? '72px' : '220px' }"
    >
      <div class="app-sider-brand" :class="{ collapsed }">
        <img class="app-sider-brand-logo" src="/app-icon.png" alt="USTB" />
        <span class="app-sider-brand-name">USTB 选课成绩</span>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="app-side-menu sider-scroll"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>系统工作台</template>
        </el-menu-item>
        <div class="app-menu-divider" />
        <el-menu-item index="/prep">
          <el-icon><Setting /></el-icon>
          <template #title>① 选课准备与发布</template>
        </el-menu-item>
        <el-menu-item index="/selection">
          <el-icon><Calendar /></el-icon>
          <template #title>② 学生选课</template>
        </el-menu-item>
        <el-menu-item index="/score">
          <el-icon><EditPen /></el-icon>
          <template #title>③ 成绩录入与审核</template>
        </el-menu-item>
        <div class="app-menu-divider" />
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>④ 成绩查询与统计</template>
        </el-menu-item>
        <el-menu-item index="/correction">
          <el-icon><DocumentChecked /></el-icon>
          <template #title>⑤ 成绩更正申请</template>
        </el-menu-item>
        <div class="app-menu-divider" />
        <el-menu-item index="/admin">
          <el-icon><Tools /></el-icon>
          <template #title>⑥ 系统维护</template>
        </el-menu-item>
      </el-menu>

      <button
        class="sider-collapse-trigger"
        type="button"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="toggleCollapse"
      >
        <el-icon>
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
      </button>
    </aside>

    <!-- 右侧主内容区 -->
    <div
      class="app-main-wrap"
      :style="{ marginLeft: isMobile ? '0px' : (collapsed ? '72px' : '220px') }"
    >
      <header class="app-header">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            text
            circle
            @click="drawerOpen = true"
          >
            <el-icon :size="18"><Menu /></el-icon>
          </el-button>
          <span class="app-header-title">{{ route.meta.title || '系统工作台' }}</span>
          <el-tag size="small" effect="plain" class="header-pill">2026-2027-1</el-tag>
          <el-tag size="small" type="success" effect="plain" class="header-pill hide-xs">云库 v9</el-tag>
        </div>

        <div class="header-right">
          <div class="identity-box">
            <span class="switch-label hide-xs">身份</span>
            <el-select
              v-model="userState.currentKey"
              size="default"
              class="identity-select"
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

          <el-dropdown trigger="click" @command="setThemeMode">
            <el-tooltip content="主题设置" placement="bottom">
              <el-button text circle class="header-icon-btn" aria-label="主题设置">
                <el-icon :size="17">
                  <Moon v-if="themeState.mode === 'dark'" />
                  <Sunny v-else-if="themeState.mode === 'light'" />
                  <Monitor v-else />
                </el-icon>
              </el-button>
            </el-tooltip>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="light">
                  <span class="theme-option">
                    <span><el-icon><Sunny /></el-icon> 浅色</span>
                    <el-icon v-if="themeState.mode === 'light'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
                <el-dropdown-item command="dark">
                  <span class="theme-option">
                    <span><el-icon><Moon /></el-icon> 深色</span>
                    <el-icon v-if="themeState.mode === 'dark'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
                <el-dropdown-item command="system">
                  <span class="theme-option">
                    <span><el-icon><Monitor /></el-icon> 跟随系统</span>
                    <el-icon v-if="themeState.mode === 'system'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="app-content">
        <router-view :key="userState.currentKey" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { initUserStore, switchDemoUser, userState } from '../stores/user'
import { setThemeMode, themeState } from '../stores/theme'

const COLLAPSED_KEY = 'ustb_cg_sidebar_collapsed'
const route = useRoute()
const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === 'true')
const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth < 992 : false)
const drawerOpen = ref(false)

function handleResize() {
  isMobile.value = window.innerWidth < 992
  if (!isMobile.value) drawerOpen.value = false
}

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSED_KEY, String(collapsed.value))
}

onMounted(() => {
  initUserStore()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

async function handleSwitch(val) {
  await switchDemoUser(val)
  ElMessage.success(`已切换身份：${userState.currentUser?.displayName || val}`)
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--app-content-bg);
}

.desktop-sider {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  height: 100vh;
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.sider-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--app-sidebar-bg);
}

.sider-scroll {
  flex: 1;
  overflow-y: auto;
}

.sider-collapse-trigger {
  height: 46px;
  border: none;
  border-top: 1px solid var(--app-border);
  background: var(--app-sidebar-bg);
  color: var(--app-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.sider-collapse-trigger:hover {
  color: var(--brand-primary);
  background: var(--app-menu-hover-bg);
}

.app-main-wrap {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  transition: margin-left 0.2s ease;
  background: var(--app-content-bg);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-pill {
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.identity-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-label {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.identity-select {
  width: 230px;
}

.header-icon-btn {
  color: var(--app-text-primary);
}

.theme-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-width: 112px;
}

.theme-option > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.theme-check {
  color: var(--brand-primary);
}

.app-content {
  padding: 24px;
  flex: 1;
  min-width: 0;
}

@media (max-width: 768px) {
  .hide-xs {
    display: none;
  }
  .identity-select {
    width: 175px;
  }
  .app-content {
    padding: 16px;
  }
}
</style>

<style>
.mobile-drawer-body {
  padding: 0 !important;
}
</style>
