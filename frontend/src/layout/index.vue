<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <div class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <el-icon><Files /></el-icon>
        <span v-show="!isCollapsed">文件管理系统</span>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        :unique-opened="true"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        
        <el-menu-item index="/files">
          <el-icon><Files /></el-icon>
          <span>文件管理</span>
        </el-menu-item>
        
        <el-menu-item index="/folders">
          <el-icon><Folder /></el-icon>
          <span>文件夹管理</span>
        </el-menu-item>
        
        <el-menu-item index="/share">
          <el-icon><Share /></el-icon>
          <span>我的分享</span>
        </el-menu-item>
        
        <el-menu-item index="/recycle">
          <el-icon><DeleteIcon /></el-icon>
          <span>回收站</span>
        </el-menu-item>
        
        <el-sub-menu index="/admin" v-if="authStore.isAdmin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/admin/users">用户管理</el-menu-item>
          <el-menu-item index="/admin/files">文件管理</el-menu-item>
          <el-menu-item index="/admin/logs">日志管理</el-menu-item>
          <el-menu-item index="/admin/system">系统设置</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </div>
    
    <!-- 主内容区 -->
    <div class="main-container">
      <!-- 顶部导航栏 -->
      <div class="navbar">
        <div class="navbar-left">
          <el-button
            type="text"
            @click="toggleSidebar"
            class="toggle-btn"
          >
            <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
          </el-button>
          
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="navbar-right">
          <!-- 存储空间显示 -->
          <div class="storage-info">
            <el-progress
              :percentage="storagePercentage"
              :color="storageColor"
              :show-text="false"
              :stroke-width="4"
              style="width: 100px"
            />
            <span class="storage-text">{{ formatStorage(authStore.user?.quotaUsed || 0) }} / {{ formatStorage(authStore.user?.quotaLimit || 0) }}</span>
          </div>
          
          <!-- 用户头像下拉菜单 -->
          <el-dropdown @command="handleUserCommand">
            <div class="user-avatar">
              <el-avatar
                :size="40"
                :src="authStore.user?.avatarUrl || defaultAvatar"
              />
              <span class="username">{{ authStore.user?.displayName || authStore.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人设置
                </el-dropdown-item>
                <el-dropdown-item command="help">
                  <el-icon><QuestionFilled /></el-icon>
                  帮助中心
                </el-dropdown-item>
                <el-dropdown-item v-if="authStore.isAdmin" command="admin">
                  <el-icon><Setting /></el-icon>
                  管理后台
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      
      <!-- 页面内容 -->
      <div class="main-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessageBox, ElMessage } from 'element-plus'
import { 
  DataLine, 
  Files, 
  Folder, 
  Share, 
  Delete as DeleteIcon, 
  Setting, 
  Fold, 
  Expand, 
  User, 
  QuestionFilled, 
  SwitchButton,
  ArrowDown
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const defaultAvatar = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHZpZXdCb3g9IjAgMCA0MCA0MCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iMjAiIGN5PSIyMCIgcj0iMjAiIGZpbGw9IiNFOEU4RkYiLz4KPGNpcmNsZSBjeD0iMjAiIGN5PSIxNSIgcj0iNiIgZmlsbD0iIzQ1NEE1NSIvPgo8cGF0aCBkPSJNMjAgMjNDMTUuNTg3MiAyMyAxMiAyNi41ODcyIDEyIDMxQzEyIDM1LjQxMjggMTUuNTg3MiAzOSAyMCAzOUMyNC40MTI4IDM5IDI4IDM1LjQxMjggMjggMzFDMjggMjYuNTg3MiAyNC40MTI4IDIzIDIwIDIzWiIgZmlsbD0iIzQ1NEE1NSIvPgo8L3N2Zz4K'

// 当前激活的菜单项
const activeMenu = computed(() => route.path)

// 面包屑导航
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta && item.meta.title)
  return matched.map(item => ({
    path: item.path,
    title: item.meta.title
  }))
})

// 存储空间信息
const storagePercentage = computed(() => {
  const used = authStore.user?.quotaUsed || 0
  const limit = authStore.user?.quotaLimit || 0
  return limit > 0 ? Math.round((used / limit) * 100) : 0
})

const storageColor = computed(() => {
  const percentage = storagePercentage.value
  if (percentage < 60) return '#67c23a'
  if (percentage < 80) return '#e6a23c'
  return '#f56c6c'
})

// 切换侧边栏
const toggleSidebar = () => {
  isCollapsed.value = !isCollapsed.value
}

// 菜单选择
const handleMenuSelect = (key) => {
  router.push(key)
}

// 用户命令处理
const handleUserCommand = async (command) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'help':
      router.push('/help')
      break
    case 'admin':
      router.push('/admin/users')
      break
    case 'logout':
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await handleLogout()
      break
  }
}

// 处理退出登录
const handleLogout = async () => {
  try {
    // 清除所有用户数据
    authStore.logout()
    
    // 清除浏览器中的所有相关数据
    localStorage.removeItem('enterprise_file_manager_token')
    localStorage.removeItem('enterprise_file_manager_user')
    localStorage.removeItem('enterprise_file_manager_roles')
    sessionStorage.clear()
    
    // 清除浏览器缓存
    if ('caches' in window) {
      try {
        const cacheNames = await caches.keys()
        await Promise.all(cacheNames.map(name => caches.delete(name)))
      } catch (error) {
        console.warn('清除浏览器缓存失败:', error)
      }
    }
    
    // 跳转到登录页面
    router.push('/login')
    
    // 显示成功消息
    ElMessage.success('已安全退出登录')
    
    // 刷新页面确保所有状态被清除
    setTimeout(() => {
      window.location.reload()
    }, 500)
    
  } catch (error) {
    console.error('退出登录失败:', error)
    ElMessage.error('退出登录失败，请重试')
  }
}

// 格式化存储空间
const formatStorage = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 监听路由变化
watch(
  () => route.path,
  () => {
    // 可以在这里添加页面切换的逻辑
  }
)
</script>

<style scoped>
.app-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background: #2c3e50;
  transition: width 0.3s;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 64px;
}

.logo {
  display: flex;
  align-items: center;
  padding: 20px;
  color: white;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid #34495e;
}

.logo img {
  width: 30px;
  height: 30px;
  margin-right: 10px;
}

.sidebar-menu {
  border: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item) {
  color: #bdc3c7;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #34495e;
  color: white;
}

.sidebar-menu :deep(.el-sub-menu__title) {
  color: #bdc3c7;
}

.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background: #34495e;
  color: white;
}

/* 子菜单项样式 - 统一背景色 */
.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  background: #34495e !important;
  color: #bdc3c7;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item:hover) {
  background: #3d566e !important;
  color: white !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item.is-active) {
  background: #3d566e !important;
  color: white !important;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 60px;
  background: white;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.toggle-btn {
  padding: 0;
  font-size: 20px;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.storage-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 20px;
}

.storage-text {
  font-size: 12px;
  color: #666;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px;
  border-radius: 20px;
  transition: background 0.3s;
}

.user-avatar:hover {
  background: #f5f5f5;
}

.username {
  font-size: 14px;
  color: #333;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  background: #f5f5f5;
}

:deep(.el-menu--collapse) {
  width: 64px;
}

:deep(.el-menu--collapse .el-sub-menu__title span) {
  display: none;
}

:deep(.el-menu--collapse .el-menu-item span) {
  display: none;
}
</style>