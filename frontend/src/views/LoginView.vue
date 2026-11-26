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
            placeholder="请输入用户名"
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
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLoginRedirect, loginWithPassword } from '../stores/user'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value || submitting.value) return
  submitting.value = true
  try {
    if (!(await formRef.value.validate().catch(() => false))) return
    const user = await loginWithPassword(form.username.trim(), form.password).catch(() => null)
    if (!user) return
    const shortName = (user.displayName || '').replace(/（.*）$/, '').trim()
    ElMessage.success(`欢迎回来，${shortName}`)
    await router.replace(getLoginRedirect(router))
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
  padding: 36px 34px 34px;
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
  width: 64px;
  height: 64px;
  display: block;
  object-fit: contain;
  border-radius: 50%;
  margin: 0 auto 14px;
  filter: drop-shadow(0 4px 10px rgba(0, 91, 148, 0.14));
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

:global(html[data-theme='dark']) .auth-page-bg {
  background: linear-gradient(135deg, #071220 0%, #0d223c 55%, #123359 100%);
}

:global(html[data-theme='dark']) .auth-card {
  background: rgba(22, 31, 41, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20px 56px rgba(0, 0, 0, 0.5);
  color: var(--app-text-primary);
}

:global(html[data-theme='dark']) .auth-brand-logo {
  background: rgba(255, 255, 255, 0.94);
  padding: 3px;
  box-sizing: border-box;
}

:global(html[data-theme='dark']) .auth-title {
  color: var(--app-text-primary);
}

:global(html[data-theme='dark']) .auth-subtitle {
  color: var(--app-text-secondary);
}
</style>
