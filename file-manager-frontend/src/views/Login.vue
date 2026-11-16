<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>用户登录</h2>
        </div>
      </template>
      
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="0"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe">
            记住我
          </el-checkbox>
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        
        <div class="login-footer">
          <span>还没有账号?</span>
          <el-link type="primary" :underline="false" @click="goToRegister">
            立即注册
          </el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

/**
 * 「记住我」保存的凭据。
 * 注意: 密码以明文存于 localStorage, 同源脚本(XSS)可读取。
 * 仅在用户主动勾选时保存, 是浏览器端「记住密码」的通行做法;
 * 公共/多用户环境下建议改用后端签发长期令牌。
 */
const REMEMBER_KEY = 'file-manager-remembered-login'

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

function loadRemembered() {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || !parsed.username) return null
    return parsed
  } catch (e) {
    return null
  }
}

function persistRemembered(username, password, remember) {
  try {
    if (remember) {
      localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username, password }))
    } else {
      // 取消勾选后要把之前记下的凭据清掉, 否则下次仍会自动回填
      localStorage.removeItem(REMEMBER_KEY)
    }
  } catch (e) {
    // 隐私模式下 localStorage 可能不可写, 忽略即可
  }
}

onMounted(() => {
  const saved = loadRemembered()
  if (saved) {
    loginForm.username = saved.username
    loginForm.password = saved.password || ''
    loginForm.rememberMe = true
  }
})

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await userStore.login(loginForm)
        if (response.code === 200) {
          // 只在登录成功后记录, 避免把错误的密码存下来
          persistRemembered(loginForm.username, loginForm.password, loginForm.rememberMe)
          ElMessage.success('登录成功')
          router.push('/')
        } else {
          ElMessage.error(response.message || '登录失败')
        }
      } catch (error) {
        ElMessage.error(error.message || '登录失败，请重试')
      } finally {
        loading.value = false
      }
    }
  })
}

const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  background: var(--canvas-bg);
}

.login-card {
  width: 400px;
  max-width: 90%;
  background: var(--theme-surface);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 40px;
}

.card-header {
  text-align: center;
  margin-bottom: 24px;
}

.card-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
}

.login-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
}
</style>