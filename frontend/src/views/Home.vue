<template>
  <div class="home-container">
    <!-- 欢迎区域 -->
    <div class="welcome-section">
      <h1>欢迎回来，{{ user?.displayName || user?.username || '用户' }}</h1>
      <p>今天是 {{ currentDate }}，祝您工作愉快！</p>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card" :loading="loading">
          <div class="stat-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ userStats.totalFiles || 0 }}</div>
            <div class="stat-label">我的文件</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card" :loading="loading">
          <div class="stat-icon">
            <el-icon><FolderOpened /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ userStats.totalFolders || 0 }}</div>
            <div class="stat-label">我的文件夹</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card" :loading="loading">
          <div class="stat-icon">
            <el-icon><DataLine /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ formatStorage(userStats.usedStorage || 0) }}</div>
            <div class="stat-label">已用存储</div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="stat-card" :loading="loading">
          <div class="stat-icon">
            <el-icon><PieChart /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ storagePercentage }}%</div>
            <div class="stat-label">存储使用率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快速操作和最近活动 -->
    <el-row :gutter="20" class="content-row">
      <el-col :span="12">
        <el-card class="quick-actions-card">
          <template #header>
            <div class="card-header">
              <span>快速操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="goToUpload" size="large">
              <el-icon><Upload /></el-icon>
              上传文件
            </el-button>
            <el-button type="success" @click="goToFiles" size="large">
              <el-icon><Document /></el-icon>
              管理文件
            </el-button>
            <el-button @click="goToProfile" size="large">
              <el-icon><User /></el-icon>
              个人中心
            </el-button>
          </div>
          
          <!-- 存储使用情况 -->
          <div class="storage-info">
            <div class="storage-header">
              <span>存储使用情况</span>
              <span>{{ formatStorage(userStats.usedStorage || 0) }} / {{ formatStorage(userStats.storageLimit || 1024 * 1024 * 1024) }}</span>
            </div>
            <el-progress 
              :percentage="storagePercentage" 
              :color="storagePercentage > 80 ? '#f56c6c' : '#409eff'"
              :stroke-width="8"
            />
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card class="recent-activity-card">
          <template #header>
            <div class="card-header">
              <span>最近活动</span>
              <el-button @click="refreshActivities" :loading="loading" text>
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
          </template>
          <div class="recent-activity">
            <div v-if="recentActivities.length === 0" class="empty-activity">
              <el-icon><Document /></el-icon>
              <p>暂无活动记录</p>
            </div>
            <div v-else v-for="activity in recentActivities" :key="activity.id" class="activity-item">
              <div class="activity-icon">
                <el-icon>
                  <Document v-if="activity.type === 'upload'" />
                  <Download v-else-if="activity.type === 'download'" />
                  <Delete v-else-if="activity.type === 'delete'" />
                  <Folder v-else />
                </el-icon>
              </div>
              <div class="activity-content">
                <div class="activity-title">{{ activity.title }}</div>
                <div class="activity-time">{{ formatTime(activity.time) }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近文件 -->
    <el-row :gutter="20" class="files-row">
      <el-col :span="24">
        <el-card class="recent-files-card">
          <template #header>
            <div class="card-header">
              <span>最近文件</span>
              <el-button @click="goToFiles" type="primary" text>
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>
          <div class="recent-files">
            <div v-if="recentFiles.length === 0" class="empty-files">
              <el-icon><Document /></el-icon>
              <p>暂无文件</p>
              <el-button type="primary" @click="goToUpload">
                <el-icon><Upload /></el-icon>
                上传第一个文件
              </el-button>
            </div>
            <div v-else class="file-grid">
              <div v-for="file in recentFiles" :key="file.id" class="file-item" @click="downloadFile(file)">
                <div class="file-icon">
                  <el-icon>
                    <Document v-if="!file.isDirectory" />
                    <Folder v-else />
                  </el-icon>
                </div>
                <div class="file-name">{{ file.originalFilename || file.name }}</div>
                <div class="file-size">{{ formatFileSize(file.size) }}</div>
                <div class="file-time">{{ formatTime(file.createTime) }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  Document, FolderOpened, User, DataLine, Upload, Refresh, 
  Download, Delete, Folder, ArrowRight, PieChart
} from '@element-plus/icons-vue'
import { getFileList } from '@/api/file'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const userStats = ref({
  totalFiles: 0,
  totalFolders: 0,
  usedStorage: 0,
  storageLimit: 1024 * 1024 * 1024 // 1GB
})
const recentActivities = ref([])
const recentFiles = ref([])

const user = computed(() => authStore.user)
const currentDate = computed(() => new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}))

const storagePercentage = computed(() => {
  if (!userStats.value.storageLimit) return 0
  return Math.round((userStats.value.usedStorage / userStats.value.storageLimit) * 100)
})

// 格式化存储大小
const formatStorage = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  
  return date.toLocaleDateString()
}

// 加载用户统计信息
const loadUserStats = async () => {
  loading.value = true
  try {
    const response = await getFileList()
    const files = response || []
    
    userStats.value = {
      totalFiles: files.length,
      totalFolders: files.filter(f => f.isDirectory).length,
      usedStorage: files.reduce((sum, file) => sum + (file.size || 0), 0),
      storageLimit: user.value?.quotaLimit || 1024 * 1024 * 1024
    }
    
    // 更新最近文件
    recentFiles.value = files
      .filter(f => !f.isDirectory)
      .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
      .slice(0, 8)
    
    // 生成最近活动
    recentActivities.value = files
      .sort((a, b) => new Date(b.createTime) - new Date(a.createTime))
      .slice(0, 5)
      .map(file => ({
        id: file.id,
        type: 'upload',
        title: `上传了文件 "${file.originalFilename || file.name}"`,
        time: file.createTime
      }))
    
  } catch (error) {
    ElMessage.error('加载统计信息失败')
  } finally {
    loading.value = false
  }
}

// 刷新活动记录
const refreshActivities = () => {
  loadUserStats()
}

// 页面跳转
const goToUpload = () => {
  router.push('/files')
}

const goToFiles = () => {
  router.push('/files')
}

const goToProfile = () => {
  router.push('/profile')
}

// 下载文件
const downloadFile = async (file) => {
  try {
    const { downloadFile: downloadFileApi } = await import('@/api/file')
    const response = await downloadFileApi(file.id)
    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.originalFilename || file.name
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  } catch (error) {
    ElMessage.error('文件下载失败')
  }
}

onMounted(() => {
  loadUserStats()
})
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
}

.stat-icon {
  font-size: 48px;
  color: #409EFF;
  margin-bottom: 10px;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.mt-20 {
  margin-top: 20px;
}

.quick-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.activity-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 5px;
}

.activity-time {
  font-size: 12px;
  color: #999;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>