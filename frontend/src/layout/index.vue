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
        
        <el-menu-item index="/files-explorer">
          <el-icon><Files /></el-icon>
          <span>文件管理（新版）</span>
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
          <el-menu-item index="/admin/system-recycle">系统回收站</el-menu-item>
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
            <el-breadcrumb-item v-if="currentBreadcrumbTitle">
              {{ currentBreadcrumbTitle }}
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

// 当前页面标题（面包屑）
const currentBreadcrumbTitle = computed(() => {
  const matched = route.matched.filter(item => item.meta && item.meta.title)
  const last = matched[matched.length - 1]
  return last?.meta?.title || ''
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
  try { ElMessage.closeAll && ElMessage.closeAll() } catch (e) {}
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

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.app-container {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  background-color: $background-color-base;
}

/* Sidebar Styles */
.sidebar {
  width: $sidebar-width;
  background: #0f172a; // Deep Slate/Navy
  transition: width $transition-base;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  z-index: 10;
  
  &.collapsed {
    width: $sidebar-collapsed-width;
    
    .logo span {
      opacity: 0;
      width: 0;
      display: none;
    }
    
    .logo {
      justify-content: center;
      padding: 0;
      height: $header-height;
    }
  }
}

.logo {
  height: $header-height;
  display: flex;
  align-items: center;
  padding: 0 $spacing-lg;
  color: white;
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  background: rgba(255, 255, 255, 0.05);
  white-space: nowrap;
  overflow: hidden;
  transition: all $transition-base;
  
  .el-icon {
    font-size: 24px;
    color: $primary-light;
    margin-right: 12px;
    flex-shrink: 0;
  }
}

.sidebar-menu {
  border-right: none;
  background: transparent;
  flex: 1;
  overflow-y: auto;
  padding-top: $spacing-sm;

  // Remove default scrollbar
  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(255,255,255,0.1);
  }

  :deep(.el-menu-item), :deep(.el-sub-menu__title) {
    color: #94a3b8; // Slate 400
    height: 50px;
    line-height: 50px;
    margin: 4px 8px;
    border-radius: $border-radius-base;
    
    &:hover {
      color: white;
      background-color: rgba(255, 255, 255, 0.08);
    }
    
    .el-icon {
      color: inherit;
      font-size: 18px;
    }
  }
  
  :deep(.el-menu-item.is-active) {
    color: white;
    background-color: $primary-color;
    font-weight: $font-weight-medium;
    box-shadow: 0 4px 12px rgba($primary-color, 0.3);
    
    .el-icon {
      color: white;
    }
  }
  
  // Submenu adjustments
  :deep(.el-sub-menu) {
    .el-menu {
      background-color: rgba(0, 0, 0, 0.2);
      padding: 4px 0;
    }
    
    &.is-active > .el-sub-menu__title {
      color: white;
    }
  }
}

/* Main Layout Styles */
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0; // Prevent flex overflow
}

.navbar {
  height: $header-height;
  background: $background-color-overlay;
  border-bottom: 1px solid $border-color-light;
  padding: 0 $spacing-lg;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: $box-shadow-sm;
  z-index: 9;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: $spacing-base;
  
  .toggle-btn {
    padding: 4px;
    height: 32px;
    width: 32px;
    border-radius: $border-radius-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    color: $text-regular;
    
    &:hover {
      background-color: $background-color-base;
      color: $primary-color;
    }
    
    .el-icon {
      font-size: 20px;
    }
  }
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

/* Storage Indicator */
.storage-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 6px 12px;
  background: $background-color-base;
  border-radius: 20px;
  border: 1px solid $border-color-light;
  
  .storage-text {
    font-size: $font-size-xs;
    color: $text-secondary;
    white-space: nowrap;
    font-family: monospace; // For better number alignment
  }
}

/* User Profile */
.user-avatar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 24px;
  transition: background-color $transition-fast;
  border: 1px solid transparent;
  
  &:hover {
    background-color: $background-color-base;
    border-color: $border-color-light;
  }
  
  .username {
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    color: $text-primary;
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  .el-icon {
    font-size: 12px;
    color: $text-secondary;
  }
}

/* Main Content Area */
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-base;
  background-color: $background-color-base;
  position: relative;
  
  // Add a subtle fade-in for route transitions if desired
}

/* Responsive adjustments */
@media (max-width: $breakpoint-sm) {
  .sidebar {
    position: absolute;
    height: 100%;
    box-shadow: $box-shadow-xl;
    
    &.collapsed {
      transform: translateX(-100%);
      width: $sidebar-width; // Keep width when showing
    }
  }
  
  .navbar {
    padding: 0 $spacing-base;
  }
  
  .storage-info {
    display: none; // Hide storage on mobile
  }
}
</style>
