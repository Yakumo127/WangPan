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
import { ref, computed } from 'vue'
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

const authStore = useAuthStore()
const router = useRouter()

const userDisplayName = computed(() => {
  return authStore.user?.displayName || authStore.user?.username || '用户'
})

const quotaUsed = computed(() => authStore.user?.quotaUsed || 0)
const quotaLimit = computed(() => authStore.user?.quotaLimit || 10737418240) // Default 10GB

const storagePercentage = computed(() => {
  if (!quotaLimit.value) return 0
  return Math.round((quotaUsed.value / quotaLimit.value) * 100)
})

const storageColor = [
  { color: '#10b981', percentage: 60 },
  { color: '#f59e0b', percentage: 80 },
  { color: '#ef4444', percentage: 100 },
]

// Mock Data for Display
const stats = [
  { label: '我的文件', value: '1,284', icon: 'Document', colorClass: 'bg-blue-100 text-blue-600', trend: 12 },
  { label: '文件夹', value: '42', icon: 'Folder', colorClass: 'bg-yellow-100 text-yellow-600', trend: 5 },
  { label: '我的分享', value: '18', icon: 'Share', colorClass: 'bg-green-100 text-green-600', trend: -2 },
  { label: '回收站', value: '3', icon: 'Delete', colorClass: 'bg-red-100 text-red-600', trend: 0 },
]

const activities = [
  { user: '您', action: '上传了文件', target: '2024年度项目计划.pdf', timestamp: '刚刚', type: 'primary' },
  { user: '您', action: '创建了文件夹', target: '财务报表', timestamp: '2小时前', type: 'success' },
  { user: '您', action: '分享了文件', target: '会议记录.docx', timestamp: '昨天 14:30', type: 'warning' },
  { user: '系统', action: '自动备份', target: '数据库备份', timestamp: '昨天 02:00', type: 'info' },
]

const handleUpload = () => {
  router.push('/files')
}

const handleNewFolder = () => {
  router.push('/folders')
}

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
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