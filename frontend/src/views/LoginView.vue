<template>
  <div class="auth-page-bg">
    <div class="auth-card">
      <div class="auth-head">
        <img class="auth-brand-logo" src="/app-icon.png" alt="USTB" />
        <h1 class="auth-title">USTB 选课与成绩管理</h1>
        <p class="auth-subtitle">北京科技大学教务统一身份认证</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        class="auth-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入学号 / 教职工号"
            clearable
            autocomplete="username"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="auth-submit"
          :loading="submitting"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="auth-helper">
        <button type="button" class="helper-toggle" @click="showQuick = !showQuick">
          <span>{{ showQuick ? '收起快速填充' : '常用学号 / 工号参考' }}</span>
          <el-icon :class="{ rotated: showQuick }"><ArrowDown /></el-icon>
        </button>

        <div v-if="showQuick" class="quick-panel">
          <div class="quick-tip">点击自动填充账号（默认密码 123456）</div>
          <div v-for="group in quickGroups" :key="group.label" class="quick-group">
            <div class="quick-group-label">{{ group.label }}</div>
            <div class="quick-chips">
              <button
                v-for="item in group.items"
                :key="item.code"
                type="button"
                class="quick-chip"
                @click="fillAccount(item.code)"
              >
                <span class="chip-name">{{ item.name }}</span>
                <span class="chip-code">{{ item.code }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginWithPassword } from '../stores/user'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const submitting = ref(false)
const showQuick = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入学号或教职工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

const quickGroups = [
  {
    label: '学生',
    items: [
      { name: '张晨曦（本科）', code: 'U202440001' },
      { name: '林思远（本科）', code: 'U202440002' },
      { name: '刘浩然（重修）', code: 'U202340004' },
      { name: '许嘉豪（研究生）', code: 'M202610001' },
      { name: 'Alex（交换生）', code: 'EX20260001' }
    ]
  },
  {
    label: '教师与学院',
    items: [
      { name: '王明远（教师）', code: 'T000010001' },
      { name: '李晓静（教师）', code: 'T000010002' },
      { name: '周敏（经管秘）', code: 'E000010001' },
      { name: '赵刚（计通秘）', code: 'E000020001' }
    ]
  },
  {
    label: '校级管理',
    items: [
      { name: '林建华（教务处）', code: 'A000000001' },
      { name: '吴海峰（处长）', code: 'A000000002' },
      { name: '孙梅（研究生院）', code: 'G000000001' },
      { name: '系统管理员', code: 'S000000001' }
    ]
  }
]

function fillAccount(code) {
  form.username = code
  form.password = '123456'
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const user = await loginWithPassword(form.username.trim(), form.password)
    const shortName = (user.displayName || '').replace(/（.*）$/, '').trim()
    ElMessage.success(`欢迎回来，${shortName}`)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect !== '/login'
      ? route.query.redirect
      : '/dashboard'
    router.replace(redirect)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page-bg {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #003366 0%, #0b5cad 55%, #1976d2 100%);
  position: relative;
  overflow: hidden;
}

.auth-page-bg::before {
  content: '';
  position: absolute;
  width: 620px;
  height: 620px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  top: -210px;
  right: -160px;
  pointer-events: none;
}

.auth-page-bg::after {
  content: '';
  position: absolute;
  width: 420px;
  height: 420px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  bottom: -150px;
  left: -100px;
  pointer-events: none;
}

.auth-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 416px;
  padding: 36px 34px 28px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(16px);
  box-shadow: 0 20px 56px rgba(0, 24, 58, 0.28);
  color: #0f172a;
}

.auth-head {
  text-align: center;
  margin-bottom: 28px;
}

.auth-brand-logo {
  width: 56px;
  height: 56px;
  display: block;
  object-fit: cover;
  border-radius: 16px;
  margin: 0 auto 14px;
  box-shadow: 0 8px 22px rgba(0, 51, 102, 0.22);
}

.auth-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #0f172a;
}

.auth-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  margin-top: 6px;
}

.auth-helper {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  text-align: center;
}

.helper-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12.5px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: color 0.18s ease, background-color 0.18s ease;
}

.helper-toggle:hover {
  color: #0b5cad;
  background: rgba(11, 92, 173, 0.06);
}

.helper-toggle .el-icon {
  transition: transform 0.2s ease;
}
.helper-toggle .el-icon.rotated {
  transform: rotate(180deg);
}

.quick-panel {
  margin-top: 12px;
  text-align: left;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.quick-tip {
  font-size: 11.5px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.quick-group {
  margin-bottom: 10px;
}
.quick-group:last-child {
  margin-bottom: 0;
}

.quick-group-label {
  font-size: 11.5px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 6px;
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 7px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #ffffff;
  font-size: 11.5px;
  color: #334155;
  cursor: pointer;
  transition: all 0.15s ease;
}

.quick-chip:hover {
  border-color: #0b5cad;
  color: #0b5cad;
  background: rgba(11, 92, 173, 0.05);
}

.chip-code {
  color: #94a3b8;
  font-size: 10.5px;
}
</style>
