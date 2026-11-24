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
            <el-icon><House /></el-icon>
            <template #title>{{ homeTitle }}</template>
          </el-menu-item>
          <template v-for="(grp, gIdx) in menuGroups" :key="gIdx">
            <div class="app-menu-divider" />
            <el-menu-item v-for="item in grp" :key="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
          </template>
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
          <el-icon><House /></el-icon>
          <template #title>{{ homeTitle }}</template>
        </el-menu-item>
        <template v-for="(grp, gIdx) in menuGroups" :key="gIdx">
          <div class="app-menu-divider" />
          <el-menu-item v-for="item in grp" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
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
          <span class="app-header-title">{{ pageTitle }}</span>
          <span class="term-badge hide-xs">2026-2027-1</span>
        </div>

        <div class="header-right">
          <div class="user-pill">
            <span class="identity-dot">{{ currentInitial }}</span>
            <span class="user-name">{{ currentShortName }}</span>
            <span class="user-role hide-xs">{{ currentRoleLabel }}</span>
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

          <el-tooltip content="退出登录" placement="bottom">
            <el-button text circle class="header-icon-btn" aria-label="退出登录" @click="handleLogout">
              <el-icon :size="17"><SwitchButton /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </header>

      <main class="app-content">
        <router-view :key="userState.currentKey" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { initUserStore, logoutSession, userState } from '../stores/user'
import { setThemeMode, themeState } from '../stores/theme'
import { canAccessPath, getHomeTitle, getPageTitle, visibleModulesForRole } from '../stores/permissions'

const COLLAPSED_KEY = 'ustb_cg_sidebar_collapsed'
const route = useRoute()
const router = useRouter()
const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === 'true')
const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth < 992 : false)
const drawerOpen = ref(false)

const currentRole = computed(() => userState.currentUser?.role)
const homeTitle = computed(() => getHomeTitle(currentRole.value))
const pageTitle = computed(() => getPageTitle(route.path, currentRole.value))

const menuGroups = computed(() => {
  const list = visibleModulesForRole(currentRole.value)
  const grouped = []
  for (const g of [1, 2, 3]) {
    const items = list.filter((m) => m.group === g)
    if (items.length) grouped.push(items)
  }
  return grouped
})

watch([currentRole, () => route.path], ([role, path]) => {
  if (role && !canAccessPath(path, role)) {
    router.replace('/dashboard')
  }
}, { immediate: true })

const currentShortName = computed(() => {
  const full = userState.currentUser?.displayName || ''
  return full.replace(/（.*）$/, '').trim() || full
})

const currentRoleLabel = computed(() => ({
  ACADEMIC_OFFICE: '教务处',
  ACADEMIC_DIRECTOR: '教务处处长',
  COLLEGE_SECRETARY: '教学秘书',
  TEACHER: '任课教师',
  STUDENT: '学生',
  GRADUATE_SCHOOL: '研究生院',
  SYS_ADMIN: '系统管理员'
}[currentRole.value] || ''))

const currentInitial = computed(() => {
  const name = currentShortName.value
  return name ? name[0].toUpperCase() : 'U'
})

function handleResize() {
  isMobile.value = window.innerWidth < 992
  if (!isMobile.value) drawerOpen.value = false
}

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSED_KEY, String(collapsed.value))
}

onMounted(async () => {
  const ok = await initUserStore()
  if (!ok) {
    router.replace('/login')
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

async function handleLogout() {
  await logoutSession()
  ElMessage.success('已退出登录')
  router.replace('/login')
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

.term-badge {
  font-size: 12px;
  font-weight: 500;
  color: var(--brand-primary);
  background: var(--app-menu-active-bg);
  padding: 3px 9px;
  border-radius: 999px;
  line-height: 1.4;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.user-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 5px;
  border-radius: 999px;
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
}

.user-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.user-role {
  font-size: 11.5px;
  color: var(--app-text-secondary);
  padding-left: 6px;
  border-left: 1px solid var(--app-border-strong);
}

.identity-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, #003366 0%, #0b5cad 100%);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-icon-btn {
  color: var(--app-text-secondary);
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
  padding: 26px 28px;
  flex: 1;
  min-width: 0;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  box-sizing: border-box;
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
