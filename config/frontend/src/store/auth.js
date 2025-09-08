import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login, register, getUserInfo } from '@/api/auth'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(null)
  const roles = ref([])

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => roles.value.includes('ADMIN'))

  // 登录
  async function handleLogin(loginData) {
    try {
      const response = await login(loginData)
      token.value = response.token
      setToken(response.token)
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
      const response = await getUserInfo()
      user.value = response
      roles.value = response.role ? [response.role] : []
      return response
    } catch (error) {
      throw error
    }
  }

  // 登出
  function logout() {
    token.value = null
    user.value = null
    roles.value = []
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