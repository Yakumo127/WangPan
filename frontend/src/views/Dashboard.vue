<template>
  <div class="page-container">
    <!-- Welcome Section -->
    <div class="welcome-section mb-4">
      <div class="welcome-content">
        <h1 class="text-2xl font-bold text-slate-800">早安, {{ userDisplayName }}</h1>
        <p class="text-slate-500 mt-1">这是您今天的概览。</p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" icon="Upload" @click="handleUpload">上传文件</el-button>
        <el-button icon="FolderAdd" @click="handleNewFolder">新建文件夹</el-button>
      </div>
    </div>

    <!-- Stats Grid -->
    <el-row :gutter="24" class="mb-4">
      <el-col :xs="24" :sm="12" :md="6" v-for="(stat, index) in stats" :key="index">
        <el-card shadow="hover" class="stat-card mb-2">
          <div class="stat-icon" :class="stat.colorClass">
            <el-icon><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-trend" v-if="stat.trend">
              <el-icon :class="stat.trend > 0 ? 'text-green-500' : 'text-red-500'">
                <Top v-if="stat.trend > 0" />
                <Bottom v-else />
              </el-icon>
              <span :class="stat.trend > 0 ? 'text-green-500' : 'text-red-500'">{{ Math.abs(stat.trend) }}%</span>
              <span class="text-slate-400 text-xs ml-1">较上周</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="24">
      <!-- Storage Usage -->
      <el-col :xs="24" :lg="8" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="font-bold text-slate-700">存储空间</span>
              <el-tag size="small" type="info">标准版</el-tag>
            </div>
          </template>
          
          <div class="storage-detail py-4">
            <el-progress 
              type="dashboard" 
              :percentage="storagePercentage" 
              :color="storageColor"
              :width="200"
            >
              <template #default="{ percentage }">
                <div class="storage-text">
                  <span class="percentage">{{ percentage }}%</span>
                  <span class="label">已使用</span>
                </div>
              </template>
            </el-progress>
            
            <div class="storage-meta mt-4">
              <div class="meta-item">
                <span class="dot used"></span>
                <span>已用: {{ formatSize(quotaUsed) }}</span>
              </div>
              <div class="meta-item">
                <span class="dot total"></span>
                <span>总计: {{ formatSize(quotaLimit) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Recent Activity -->
      <el-col :xs="24" :lg="16" class="mb-4">
        <el-card shadow="never" class="h-full">
          <template #header>
            <div class="flex justify-between items-center">
              <span class="font-bold text-slate-700">最近动态</span>
              <el-button link type="primary">查看全部</el-button>
            </div>
          </template>
          
          <div class="activity-list">
            <el-timeline>
              <el-timeline-item
                v-for="(activity, index) in activities"
                :key="index"
                :type="activity.type"
                :timestamp="activity.timestamp"
                :hollow="true"
              >
                <div class="activity-content">
                  <span class="font-medium text-slate-700">{{ activity.user }}</span>
                  <span class="text-slate-500 mx-1">{{ activity.action }}</span>
                  <span class="text-blue-600 cursor-pointer">{{ activity.target }}</span>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
import { 
  Document, 
  Folder, 
  Share, 
  Delete, 
  Upload, 
  FolderAdd,
  Top,
  Bottom 
} from '@element-plus/icons-vue'
import { getDashboardSummary } from '@/api/dashboard'

const authStore = useAuthStore()
const router = useRouter()

const loading = ref(false)
const summary = ref(null)
const stats = ref([])
const activities = ref([])

const userDisplayName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || '用户'
})

const quotaUsed = computed(() => {
  const fromSummary = summary.value?.quotaUsed
  if (typeof fromSummary === 'number') return fromSummary
  return authStore.user?.quotaUsed || 0
})

const quotaLimit = computed(() => {
  const fromSummary = summary.value?.quotaLimit
  if (typeof fromSummary === 'number' && fromSummary > 0) return fromSummary
  return authStore.user?.quotaLimit || 10737418240 // 默认 10GB
})

const storagePercentage = computed(() => {
  if (!quotaLimit.value) return 0
  return Math.min(100, Math.round((quotaUsed.value / quotaLimit.value) * 100))
})

const storageColor = [
  { color: '#10b981', percentage: 60 },
  { color: '#f59e0b', percentage: 80 },
  { color: '#ef4444', percentage: 100 },
]

const buildStats = (data) => {
  const fileCount = safeNumber(data?.fileCount)
  const folderCount = safeNumber(data?.folderCount)
  const recycleCount = safeNumber(data?.recycleCount)
  const shareCount = data?.shareCount != null ? data.shareCount : '未接入'
  return [
    { label: '我的文件', value: formatNumber(fileCount), icon: Document, colorClass: 'bg-blue-100 text-blue-600', trend: null },
    { label: '文件夹', value: formatNumber(folderCount), icon: Folder, colorClass: 'bg-yellow-100 text-yellow-600', trend: null },
    { label: '我的分享', value: typeof shareCount === 'number' ? formatNumber(shareCount) : shareCount, icon: Share, colorClass: 'bg-green-100 text-green-600', trend: null },
    { label: '回收站', value: formatNumber(recycleCount), icon: Delete, colorClass: 'bg-red-100 text-red-600', trend: null },
  ]
}

const buildActivities = (data) => {
  const logs = Array.isArray(data?.recentActivities) ? data.recentActivities : []
  if (logs.length > 0) {
    return logs.map(mapLogToTimeline)
  }
  const uploads = Array.isArray(data?.recentUploads) ? data.recentUploads : []
  return uploads.map(file => ({
    user: userDisplayName.value,
    action: '上传了文件',
    target: file?.name || `文件 ${file?.id || ''}`,
    timestamp: formatTimelineTime(file?.createTime),
    type: 'primary'
  }))
}

const mapLogToTimeline = (log) => ({
  user: userDisplayName.value,
  action: renderActionText(log?.actionType, log?.resourceType),
  target: log?.resourceName || renderResourceName(log?.resourceType),
  timestamp: formatTimelineTime(log?.time),
  type: log?.status === 'FAILED' ? 'danger' : 'primary'
})

const renderActionText = (actionType, resourceType) => {
  const a = (actionType || '').toUpperCase()
  switch (a) {
    case 'UPLOAD':
    case 'UPLOAD_QUICK':
    case 'UPLOAD_CHUNK':
    case 'UPLOAD_MERGE':
      return '上传了文件'
    case 'DOWNLOAD':
    case 'DOWNLOAD_PROBE':
      return '下载了文件'
    case 'DELETE':
      return '删除了文件'
    case 'RESTORE':
      return '恢复了文件'
    case 'MOVE':
      return '移动了文件'
    case 'COPY':
      return '复制了文件'
    case 'RENAME':
      return '重命名了文件'
    case 'CREATE_FOLDER':
      return '创建了文件夹'
    case 'DELETE_FOLDER':
      return '删除了文件夹'
    case 'PREVIEW':
      return '预览了文件'
    default:
      return resourceType === 'FOLDER' ? '操作了文件夹' : '执行了操作'
  }
}

const renderResourceName = (resourceType) => {
  const r = (resourceType || '').toUpperCase()
  if (r === 'FOLDER') return '文件夹'
  if (r === 'FILE') return '文件'
  return '资源'
}

const formatTimelineTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const safeNumber = (n) => (typeof n === 'number' && !Number.isNaN(n) ? n : 0)
const formatNumber = (n) => (typeof n === 'number' ? n.toLocaleString('zh-CN') : (n ?? 0))

stats.value = buildStats(null)

const loadDashboard = async () => {
  loading.value = true
  try {
    const res = await getDashboardSummary({ activityLimit: 5, uploadLimit: 5 })
    summary.value = res || {}
    stats.value = buildStats(summary.value)
    activities.value = buildActivities(summary.value)
  } catch (error) {
    ElMessage.error('加载仪表盘数据失败')
    stats.value = buildStats(null)
    activities.value = []
  } finally {
    loading.value = false
  }
}

const handleUpload = () => {
  router.push('/files')
}

const handleNewFolder = () => {
  router.push('/folders')
}

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  @media (max-width: $breakpoint-sm) {
    flex-direction: column;
    align-items: flex-start;
    gap: $spacing-base;
    
    .welcome-actions {
      width: 100%;
      display: flex;
      gap: $spacing-sm;
      
      .el-button {
        flex: 1;
      }
    }
  }
}

.stat-card {
  display: flex;
  align-items: center;
  border: none;
  
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    padding: 24px;
    width: 100%;
  }
  
  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    margin-right: 16px;
  }
  
  .stat-info {
    flex: 1;
    
    .stat-label {
      font-size: 14px;
      color: $text-secondary;
      margin-bottom: 4px;
    }
    
    .stat-value {
      font-size: 24px;
      font-weight: bold;
      color: $text-primary;
      line-height: 1.2;
    }
    
    .stat-trend {
      display: flex;
      align-items: center;
      font-size: 12px;
      margin-top: 4px;
      
      .el-icon {
        margin-right: 2px;
      }
    }
  }
}

.storage-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  
  .storage-text {
    display: flex;
    flex-direction: column;
    align-items: center;
    
    .percentage {
      font-size: 28px;
      font-weight: bold;
      color: $text-primary;
    }
    
    .label {
      font-size: 12px;
      color: $text-secondary;
    }
  }
  
  .storage-meta {
    width: 100%;
    display: flex;
    justify-content: center;
    gap: 24px;
    
    .meta-item {
      display: flex;
      align-items: center;
      font-size: 13px;
      color: $text-regular;
      
      .dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        margin-right: 8px;
        
        &.used { background-color: $primary-color; }
        &.total { background-color: $border-color-base; }
      }
    }
  }
}

// Utility classes for colors (since we don't have Tailwind fully setup)
.bg-blue-100 { background-color: #dbeafe; }
.text-blue-600 { color: #2563eb; }

.bg-yellow-100 { background-color: #fef3c7; }
.text-yellow-600 { color: #d97706; }

.bg-green-100 { background-color: #d1fae5; }
.text-green-600 { color: #059669; }

.bg-red-100 { background-color: #fee2e2; }
.text-red-600 { color: #dc2626; }

.text-slate-800 { color: #1e293b; }
.text-slate-700 { color: #334155; }
.text-slate-500 { color: #64748b; }
.text-slate-400 { color: #94a3b8; }

.text-green-500 { color: #10b981; }
.text-red-500 { color: #ef4444; }

.font-bold { font-weight: 700; }
.font-medium { font-weight: 500; }
.text-2xl { font-size: 24px; }
.text-xs { font-size: 12px; }
.ml-1 { margin-left: 4px; }
.mt-1 { margin-top: 4px; }
.mt-4 { margin-top: 16px; }
.mx-1 { margin-left: 4px; margin-right: 4px; }
.mb-2 { margin-bottom: 8px; }
.mb-4 { margin-bottom: 16px; }
.py-4 { padding-top: 16px; padding-bottom: 16px; }
.h-full { height: 100%; }
.flex { display: flex; }
.justify-between { justify-content: space-between; }
.items-center { align-items: center; }
.cursor-pointer { cursor: pointer; }
</style>
