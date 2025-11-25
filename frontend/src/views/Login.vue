<template>
  <div class="login-container">
    <div class="login-wrapper">
      <!-- Left Side: Branding -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="logo-circle">
            <el-icon><Files /></el-icon>
          </div>
          <h1>企业文件管理系统</h1>
          <p class="subtitle">安全 · 高效 · 智能</p>
          <div class="features">
            <div class="feature-item">
              <el-icon><check /></el-icon> <span>企业级加密存储</span>
            </div>
            <div class="feature-item">
              <el-icon><check /></el-icon> <span>多端实时同步</span>
            </div>
            <div class="feature-item">
              <el-icon><check /></el-icon> <span>精细化权限管理</span>
            </div>
          </div>
        </div>
        <!-- Decorative Background Elements -->
        <div class="circle c1"></div>
        <div class="circle c2"></div>
      </div>

      <!-- Right Side: Form -->
      <div class="form-section">
        <div class="form-header">
          <h2>{{ isLogin ? '欢迎回来' : '创建账户' }}</h2>
          <p class="form-subtitle">{{ isLogin ? '请登录您的账户以继续' : '注册一个新的企业账户' }}</p>
        </div>

        <!-- 登录表单 -->
        <el-form
          v-if="isLogin"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="auth-form"
          @submit.prevent="handleLogin"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              prefix-icon="User"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          
          <!-- 验证码 -->
          <div v-if="showCaptcha" class="captcha-container">
            <el-form-item prop="captcha" class="captcha-input">
              <el-input
                v-model="loginForm.captcha"
                placeholder="验证码"
                clearable
              />
            </el-form-item>
            <div class="captcha-box" @click="refreshCaptcha">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <div v-else class="captcha-placeholder">加载中...</div>
            </div>
          </div>
          <div v-else class="captcha-trigger">
            <el-button link type="primary" @click="enableCaptcha">遇到问题？显示验证码</el-button>
          </div>

          <el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
          
          <div class="form-footer">
            还没有账户？ <span class="link" @click="switchMode(false)">立即注册</span>
          </div>
        </el-form>
        
        <!-- 注册表单 -->
        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          class="auth-form"
          @submit.prevent="handleRegister"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              placeholder="用户名"
              prefix-icon="User"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="email">
            <el-input
              v-model="registerForm.email"
              placeholder="电子邮箱"
              prefix-icon="Message"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="设置密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              placeholder="确认密码"
              prefix-icon="Lock"
              show-password
              @keyup.enter="handleRegister"
            />
          </el-form-item>
          
          <el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注册账户
            </el-button>
          </el-form-item>

          <div class="form-footer">
            已有账户？ <span class="link" @click="switchMode(true)">立即登录</span>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { getCaptchaNew } from '@/api/auth'
import { User, Lock, Message, Check, Files } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 表单引用
const loginFormRef = ref()
const registerFormRef = ref()

// 状态管理
const loading = ref(false)
const isLogin = ref(true) // true为登录模式，false为注册模式

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: '',
  captcha: '',
  captchaKey: ''
})

// 注册表单数据
const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

// 登录验证规则
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6-20个字符', trigger: 'blur' }
  ],
  captcha: [
    { required: false, message: '请输入验证码', trigger: 'blur' }
  ]
}

// 注册验证规则
const validatePass = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请输入密码'))
  } else if (value.length < 6) {
    callback(new Error('密码长度不能小于6位'))
  } else {
    if (registerForm.confirmPassword !== '') {
      registerFormRef.value?.validateField('confirmPassword')
    }
    callback()
  }
}

const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { validator: validatePass, trigger: 'blur' }
  ],
  confirmPassword: [
    { validator: validatePass2, trigger: 'blur' }
  ]
}

// 切换登录/注册模式
const switchMode = (loginMode) => {
  isLogin.value = loginMode
  // 清空表单验证状态
  if (loginFormRef.value) {
    loginFormRef.value.clearValidate()
  }
  if (registerFormRef.value) {
    registerFormRef.value.clearValidate()
  }
}

// 验证码逻辑
const showCaptcha = ref(false)
const captchaImage = ref('')
const enableCaptcha = async () => {
  showCaptcha.value = true
  await refreshCaptcha()
}
const refreshCaptcha = async () => {
  try {
    const resp = await getCaptchaNew()
    loginForm.captchaKey = resp.key
    captchaImage.value = resp.imageBase64
    loginForm.captcha = ''
  } catch (e) {
    ElMessage.error('获取验证码失败')
  }
}

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await authStore.handleLogin(loginForm)
        ElMessage.success('登录成功')
        router.push('/')
      } catch (error) {
        const msg = error?.response?.data?.message || error.message || '登录失败'
        ElMessage.error(msg)
        // 若后端提示验证码相关错误，则自动开启并刷新验证码
        if (/验证码/.test(msg)) {
          await enableCaptcha()
        }
      } finally {
        loading.value = false
      }
    }
  })
}

// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return
  
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await authStore.handleRegister({
          username: registerForm.username,
          email: registerForm.email,
          password: registerForm.password,
          confirmPassword: registerForm.confirmPassword
        })
        ElMessage.success('注册成功')
        // 注册成功后切换到登录模式
        isLogin.value = true
        // 清空注册表单
        registerForm.username = ''
        registerForm.email = ''
        registerForm.password = ''
        registerForm.confirmPassword = ''
      } catch (error) {
        ElMessage.error(error.message || '注册失败')
      } finally {
        loading.value = false
      }
    }
  })
}

// 组件挂载时检查URL参数
onMounted(() => {
  // 如果URL中有mode=register参数，默认显示注册表单
  if (route.query.mode === 'register') {
    isLogin.value = false
  }
})

</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-color: #0f172a; /* Slate-900 */
  background-image: 
    radial-gradient(at 0% 0%, hsla(253,16%,7%,1) 0, transparent 50%), 
    radial-gradient(at 50% 100%, hsla(225,39%,30%,1) 0, transparent 50%), 
    radial-gradient(at 100% 0%, hsla(339,49%,30%,1) 0, transparent 50%);
  position: relative;
  overflow: hidden;
  
  &::before {
    content: '';
    position: absolute;
    top: -10%;
    left: -10%;
    width: 50vw;
    height: 50vw;
    background: radial-gradient(circle, rgba(59,130,246,0.15) 0%, rgba(0,0,0,0) 70%);
    filter: blur(60px);
    animation: float 25s infinite ease-in-out alternate;
    z-index: 0;
  }

  &::after {
    content: '';
    position: absolute;
    bottom: -10%;
    right: -10%;
    width: 40vw;
    height: 40vw;
    background: radial-gradient(circle, rgba(236,72,153,0.15) 0%, rgba(0,0,0,0) 70%);
    filter: blur(60px);
    animation: float 20s infinite ease-in-out alternate-reverse;
    z-index: 0;
  }
}

.login-wrapper {
  position: relative;
  z-index: 1; /* Ensure it stays above the background blobs */
  display: flex;
  width: 100%;
  max-width: 1000px;
  height: 600px;
  background: rgba(255, 255, 255, 0.95); /* Slight transparency */
  backdrop-filter: blur(10px);
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  overflow: hidden;
  
  @media (max-width: $breakpoint-md) {
    flex-direction: column;
    height: auto;
    max-width: 500px;
  }
}

.brand-section {
  flex: 1;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  color: white;
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
  
  @media (max-width: $breakpoint-md) {
    padding: 40px 30px;
    text-align: center;
  }
  
  .brand-content {
    position: relative;
    z-index: 2;
  }
  
  .logo-circle {
    width: 64px;
    height: 64px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24px;
    
    .el-icon {
      font-size: 32px;
      color: $primary-light;
    }
    
    @media (max-width: $breakpoint-md) {
      margin: 0 auto 20px;
    }
  }
  
  h1 {
    font-size: 32px;
    margin-bottom: 12px;
    color: white;
  }
  
  .subtitle {
    font-size: 18px;
    color: #94a3b8;
    margin-bottom: 40px;
  }
  
  .features {
    display: flex;
    flex-direction: column;
    gap: 16px;
    
    @media (max-width: $breakpoint-md) {
      align-items: center;
    }
    
    .feature-item {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #cbd5e1;
      
      .el-icon {
        color: $success-color;
      }
    }
  }
  
  /* Decorative Circles */
  .circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.1;
    background: white;
  }
  
  .c1 {
    width: 300px;
    height: 300px;
    top: -50px;
    right: -50px;
  }
  
  .c2 {
    width: 200px;
    height: 200px;
    bottom: -50px;
    left: -50px;
  }
}

.form-section {
  flex: 1;
  padding: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: white;
  
  @media (max-width: $breakpoint-md) {
    padding: 40px 30px;
  }
}

.form-header {
  margin-bottom: 32px;
  text-align: center;
  
  h2 {
    font-size: 24px;
    color: $text-primary;
    margin-bottom: 8px;
  }
  
  .form-subtitle {
    color: $text-secondary;
    font-size: 14px;
  }
}

.auth-form {
  .submit-btn {
    width: 100%;
    padding: 12px;
    font-weight: $font-weight-medium;
    height: 44px;
    margin-top: 8px;
  }
}

.form-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: $text-secondary;
  
  .link {
    color: $primary-color;
    cursor: pointer;
    font-weight: $font-weight-medium;
    
    &:hover {
      text-decoration: underline;
    }
  }
}

/* Captcha Styles */
.captcha-container {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
  
  .captcha-input {
    flex: 1;
    margin-bottom: 0;
  }
  
  .captcha-box {
    width: 100px;
    height: 40px;
    background: #f8fafc;
    border-radius: 4px;
    cursor: pointer;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid #e2e8f0;
    
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    
    .captcha-placeholder {
      font-size: 12px;
      color: #94a3b8;
    }
  }
}

.captcha-trigger {
  text-align: right;
  margin-bottom: 16px;
}

:deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  padding: 8px 12px;
  
  &:hover {
    box-shadow: 0 0 0 1px #cbd5e1 inset;
  }
  
  &.is-focus {
    box-shadow: 0 0 0 1px $primary-color inset !important;
  }
}

@keyframes float {
  0% { transform: translate(0, 0) rotate(0deg); }
  50% { transform: translate(30px, 20px) rotate(5deg); }
  100% { transform: translate(-20px, 40px) rotate(-5deg); }
}
</style>