import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getUserInfo } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)
  const roles = ref([])

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  // 登录
  async function handleLogin(loginData) {
    try {
      const response = await login(loginData)
      token.value = response.token
      setToken(response.token)
      
      // 确保token已经设置到localStorage
      await new Promise(resolve => setTimeout(resolve, 100))
      
      await fetchUserInfo()
      return response
    } catch (error) {
      throw error
    }
  }

  // 注册
  async function handleRegister(registerData) {
    try {
      const response = await register(registerData)
      return response
    } catch (error) {
      throw error
    }
  }

  // 获取用户信息
  async function fetchUserInfo() {
    try {
      console.log('正在获取用户信息...')
      const response = await getUserInfo()
      console.log('获取用户信息成功:', response)
      user.value = response
      roles.value = response.role ? [response.role] : []
      return response
    } catch (error) {
      console.error('获取用户信息失败:', error)
      if (error.response) {
        console.error('错误状态:', error.response.status)
        console.error('错误数据:', error.response.data)
      }
      throw error
    }
  }

  // 登出
  function logout() {
    // 清除store中的数据
    token.value = null
    user.value = null
    roles.value = []
    
    // 清除localStorage中的数据
    localStorage.removeItem('enterprise_file_manager_token')
    localStorage.removeItem('enterprise_file_manager_user')
    localStorage.removeItem('enterprise_file_manager_roles')
    
    // 清除sessionStorage中的数据
    sessionStorage.clear()
    
    // 清除token
    removeToken()
  }

  return {
    token,
    user,
    roles,
    isAuthenticated,
    isAdmin,
    handleLogin,
    handleRegister,
    fetchUserInfo,
    logout
  }
})