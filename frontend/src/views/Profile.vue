<template>
  <div class="profile-container">
    <el-row :gutter="20">
      <!-- 左侧用户信息 -->
      <el-col :span="8">
        <el-card class="user-info-card">
          <div class="user-avatar">
            <el-avatar :size="100" :src="user?.avatarUrl">
              <el-icon><User /></el-icon>
            </el-avatar>
            <div class="user-name">{{ user?.displayName || user?.username }}</div>
            <div class="user-role">{{ user?.role || 'USER' }}</div>
          </div>
          
          <el-divider />
          
          <div class="user-stats">
            <div class="stat-item">
              <div class="stat-label">注册时间</div>
              <div class="stat-value">{{ formatDateTime(user?.createTime) }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">最后登录</div>
              <div class="stat-value">{{ formatDateTime(user?.lastLoginTime) }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">存储配额</div>
              <div class="stat-value">{{ formatStorage(user?.quotaLimit || 1024 * 1024 * 1024) }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">已用存储</div>
              <div class="stat-value">{{ formatStorage(userStats.usedStorage || 0) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 右侧编辑区域 -->
      <el-col :span="16">
        <el-card class="profile-edit-card">
          <template #header>
            <div class="card-header">
              <span>个人信息</span>
              <el-button type="primary" @click="saveProfile" :loading="saving">
                保存修改
              </el-button>
            </div>
          </template>
          
          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="100px"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="profileForm.username" disabled />
            </el-form-item>
            
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model="profileForm.displayName" placeholder="请输入显示名称" />
            </el-form-item>
            
            <el-form-item label="头像URL" prop="avatarUrl">
              <el-input v-model="profileForm.avatarUrl" placeholder="请输入头像URL" />
            </el-form-item>
            
            <el-divider />
            
            <h3>修改密码</h3>
            <el-form-item label="当前密码" prop="currentPassword">
              <el-input
                v-model="profileForm.currentPassword"
                type="password"
                placeholder="请输入当前密码"
                show-password
              />
            </el-form-item>
            
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="profileForm.newPassword"
                type="password"
                placeholder="请输入新密码"
                show-password
              />
            </el-form-item>
            
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="profileForm.confirmPassword"
                type="password"
                placeholder="请确认新密码"
                show-password
              />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { getFileList } from '@/api/file'
import request from '@/utils/request'

const authStore = useAuthStore()

const saving = ref(false)
const loading = ref(false)
const userStats = ref({
  usedStorage: 0
})

const profileFormRef = ref()
const profileForm = reactive({
  username: '',
  email: '',
  displayName: '',
  avatarUrl: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const user = computed(() => authStore.user)

const profileRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { min: 2, max: 50, message: '显示名称长度在2-50个字符', trigger: 'blur' }
  ],
  currentPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { min: 6, max: 20, message: '密码长度在6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { 
      validator: (rule, value, callback) => {
        if (value && value !== profileForm.newPassword) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 格式化存储大小
const formatStorage = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化日期时间
const formatDateTime = (datetime) => {
  if (!datetime) return '未设置'
  return new Date(datetime).toLocaleString()
}

// 加载用户统计信息
const loadUserStats = async () => {
  try {
    const response = await getFileList()
    const files = response || []
    userStats.value = {
      usedStorage: files.reduce((sum, file) => sum + (file.size || 0), 0)
    }
  } catch (error) {
    console.error('加载用户统计信息失败:', error)
  }
}

// 初始化表单数据
const initProfileForm = () => {
  if (user.value) {
    profileForm.username = user.value.username
    profileForm.email = user.value.email
    profileForm.displayName = user.value.displayName
    profileForm.avatarUrl = user.value.avatarUrl
  }
}

// 保存个人信息
const saveProfile = async () => {
  if (!profileFormRef.value) return
  
  await profileFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 检查是否已登录
        if (!authStore.isAuthenticated) {
          throw new Error('请先登录')
        }
        
        // 更新用户信息
        const profileData = {
          displayName: profileForm.displayName,
          email: profileForm.email,
          avatarUrl: profileForm.avatarUrl
        }
        
        // 使用request工具发送请求，会自动添加token
        const response = await request.put('/auth/profile', profileData)
        
        // 如果修改了密码，调用密码修改API
        if (profileForm.newPassword) {
          await request.post('/auth/change-password', {
            oldPassword: profileForm.currentPassword,
            newPassword: profileForm.newPassword
          })
          
          ElMessage.success('个人信息和密码更新成功，请重新登录')
          authStore.logout()
          window.location.href = '/login'
        } else {
          ElMessage.success('个人信息更新成功')
          // 刷新用户信息
          await authStore.fetchUserInfo()
        }
        
        // 清空密码字段
        profileForm.currentPassword = ''
        profileForm.newPassword = ''
        profileForm.confirmPassword = ''
        
      } catch (error) {
        console.error('保存失败:', error)
        ElMessage.error(error.response?.data?.message || error.message || '保存失败')
      } finally {
        saving.value = false
      }
    }
  })
}

onMounted(async () => {
  // 确保用户信息已加载
  if (!authStore.user && authStore.isAuthenticated) {
    try {
      await authStore.fetchUserInfo()
    } catch (error) {
      console.error('获取用户信息失败:', error)
      ElMessage.error('获取用户信息失败，请重新登录')
      return
    }
  }
  
  initProfileForm()
  loadUserStats()
})
</script>

<style scoped>
.profile-container {
  padding: 20px;
}

.user-info-card {
  text-align: center;
}

.user-avatar {
  padding: 20px 0;
}

.user-name {
  font-size: 20px;
  font-weight: bold;
  margin: 10px 0 5px 0;
  color: #333;
}

.user-role {
  color: #666;
  font-size: 14px;
  padding: 2px 8px;
  background: #f0f0f0;
  border-radius: 12px;
  display: inline-block;
}

.user-stats {
  text-align: left;
  padding: 0 20px;
}

.stat-item {
  margin-bottom: 15px;
}

.stat-label {
  color: #666;
  font-size: 14px;
  margin-bottom: 5px;
}

.stat-value {
  color: #333;
  font-size: 16px;
  font-weight: 500;
}

.profile-edit-card {
  min-height: 500px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.el-form-item__content) {
  max-width: 400px;
}

:deep(.el-divider) {
  margin: 20px 0;
}

h3 {
  margin: 20px 0 15px 0;
  color: #333;
  font-size: 16px;
}
</style>
