<template>
  <div class="share-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showShareDialog = true">
          <el-icon><Share /></el-icon>
          创建分享
        </el-button>
        <el-button @click="refreshShares" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索分享..."
          style="width: 200px"
          clearable
          @keyup.enter="searchShares"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchShares">搜索</el-button>
      </div>
    </div>

    <!-- 分享统计 -->
    <div class="stats-row">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Share /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalShares || 0 }}</div>
              <div class="stat-label">总分享数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Link /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.activeShares || 0 }}</div>
              <div class="stat-label">有效分享</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Download /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalDownloads || 0 }}</div>
              <div class="stat-label">总下载次数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><View /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalViews || 0 }}</div>
              <div class="stat-label">总浏览次数</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分享列表 -->
    <div class="share-list">
      <el-table
        :data="shares"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column label="文件信息" min-width="300">
          <template #default="{ row }">
            <div class="share-info">
              <el-icon class="share-icon">
                <Document v-if="row.type === 'file'" />
                <Folder v-else />
              </el-icon>
              <div class="share-details">
                <div class="share-name">{{ row.name }}</div>
                <div class="share-meta">
                  <el-tag size="small" :type="row.type === 'file' ? 'primary' : 'success'">
                    {{ row.type === 'file' ? '文件' : '文件夹' }}
                  </el-tag>
                  <span class="share-size">{{ formatFileSize(row.size) }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分享链接" min-width="200">
          <template #default="{ row }">
            <div class="share-link">
              <el-input
                v-model="row.shareUrl"
                readonly
                size="small"
                :style="{ width: '100%' }"
              >
                <template #append>
                  <el-button @click="copyShareLink(row)" size="small">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                </template>
              </el-input>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="expireTime" label="过期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expireTime" :class="{ 'expired': isExpired(row.expireTime) }">
              {{ formatDateTime(row.expireTime) }}
            </span>
            <span v-else class="no-expire">永久有效</span>
          </template>
        </el-table-column>
        <el-table-column label="统计" width="120">
          <template #default="{ row }">
            <div class="share-stats">
              <div class="stat-item">
                <el-icon><View /></el-icon>
                {{ row.viewCount || 0 }}
              </div>
              <div class="stat-item">
                <el-icon><Download /></el-icon>
                {{ row.downloadCount || 0 }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getShareStatusType(row)">
              {{ getShareStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" @click="viewShare(row)">
                <el-icon><View /></el-icon>
                查看
              </el-button>
              <el-button size="small" @click="editShare(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="deleteShare(row)">
                <el-icon><Delete /></el-icon>
                取消
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建分享对话框 -->
    <el-dialog v-model="showShareDialog" title="创建分享" width="600px">
      <el-form :model="shareForm" label-width="100px">
        <el-form-item label="选择类型">
          <el-radio-group v-model="shareForm.type">
            <el-radio label="file">文件</el-radio>
            <el-radio label="folder">文件夹</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item :label="shareForm.type === 'file' ? '选择文件' : '选择文件夹'">
          <el-select
            v-model="shareForm.itemId"
            :placeholder="`请选择${shareForm.type === 'file' ? '文件' : '文件夹'}`"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="item in availableItems"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
              <div class="option-item">
                <el-icon>
                  <Document v-if="shareForm.type === 'file'" />
                  <Folder v-else />
                </el-icon>
                <span>{{ item.name }}</span>
                <span class="item-size">{{ formatFileSize(item.size) }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="过期时间">
          <el-radio-group v-model="shareForm.expireType">
            <el-radio label="never">永久有效</el-radio>
            <el-radio label="custom">自定义时间</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item v-if="shareForm.expireType === 'custom'" label="选择时间">
          <el-date-picker
            v-model="shareForm.expireTime"
            type="datetime"
            placeholder="选择过期时间"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="提取码">
          <el-switch
            v-model="shareForm.requireCode"
            active-text="需要提取码"
            inactive-text="不需要提取码"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="createShareFunc" :loading="creating">
          创建分享
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Share, Refresh, Search, Document, Folder, CopyDocument, Edit, Delete, View, Link, Download } from '@element-plus/icons-vue'

const loading = ref(false)
const creating = ref(false)
const shares = ref([])
const searchKeyword = ref('')
const showShareDialog = ref(false)
const availableItems = ref([])

const shareForm = reactive({
  type: 'file',
  itemId: '',
  expireType: 'never',
  expireTime: null,
  requireCode: false
})

const shareStats = ref({
  totalShares: 0,
  activeShares: 0,
  totalDownloads: 0,
  totalViews: 0
})

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化日期时间
const formatDateTime = (datetime) => {
  if (!datetime) return ''
  return new Date(datetime).toLocaleString()
}

// 检查是否过期
const isExpired = (expireTime) => {
  return new Date(expireTime) < new Date()
}

// 获取分享状态
const getShareStatus = (share) => {
  if (share.expireTime && isExpired(share.expireTime)) {
    return '已过期'
  }
  return share.active ? '有效' : '已取消'
}

// 获取分享状态类型
const getShareStatusType = (share) => {
  if (share.expireTime && isExpired(share.expireTime)) {
    return 'danger'
  }
  return share.active ? 'success' : 'info'
}

// 禁用过去的日期
const disabledDate = (time) => {
  return time.getTime() < Date.now()
}

// 加载分享列表
const loadShares = async () => {
  loading.value = true
  try {
    // 模拟数据，实际应该从API获取
    shares.value = [
      {
        id: 1,
        name: '项目文档.pdf',
        type: 'file',
        size: 2048576,
        shareUrl: 'https://example.com/share/abc123',
        createTime: '2024-01-15T10:30:00',
        expireTime: '2024-02-15T10:30:00',
        viewCount: 25,
        downloadCount: 8,
        active: true
      },
      {
        id: 2,
        name: '设计素材',
        type: 'folder',
        size: 10485760,
        shareUrl: 'https://example.com/share/def456',
        createTime: '2024-01-10T14:20:00',
        expireTime: null,
        viewCount: 15,
        downloadCount: 3,
        active: true
      }
    ]
    
    // 更新统计信息
    shareStats.value = {
      totalShares: shares.value.length,
      activeShares: shares.value.filter(s => s.active).length,
      totalDownloads: shares.value.reduce((sum, s) => sum + (s.downloadCount || 0), 0),
      totalViews: shares.value.reduce((sum, s) => sum + (s.viewCount || 0), 0)
    }
  } catch (error) {
    ElMessage.error('加载分享列表失败')
  } finally {
    loading.value = false
  }
}

// 加载可选项目
const loadAvailableItems = async () => {
  try {
    // 模拟数据，实际应该从API获取
    availableItems.value = [
      { id: 1, name: '项目文档.pdf', type: 'file', size: 2048576 },
      { id: 2, name: '设计素材', type: 'folder', size: 10485760 },
      { id: 3, name: '会议记录.docx', type: 'file', size: 512000 },
      { id: 4, name: '图片资源', type: 'folder', size: 5242880 }
    ]
  } catch (error) {
    console.error('加载可选项目失败:', error)
  }
}

// 刷新分享列表
const refreshShares = () => {
  loadShares()
}

// 搜索分享
const searchShares = () => {
  ElMessage.info('分享搜索功能开发中...')
}

// 复制分享链接
const copyShareLink = async (share) => {
  try {
    await navigator.clipboard.writeText(share.shareUrl)
    ElMessage.success('分享链接已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制分享链接失败')
  }
}

// 查看分享
const viewShare = (share) => {
  window.open(share.shareUrl, '_blank')
}

// 编辑分享
const editShare = (share) => {
  ElMessage.info('编辑分享功能开发中...')
}

// 删除分享
const deleteShare = async (share) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消分享 "${share.name}" 吗？`,
      '取消分享',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 模拟删除操作
    share.active = false
    ElMessage.success('分享已取消')
    await loadShares()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消分享失败')
    }
  }
}

// 创建分享
const createShareFunc = async () => {
  if (!shareForm.itemId) {
    ElMessage.warning('请选择要分享的文件或文件夹')
    return
  }

  creating.value = true
  try {
    // 模拟创建分享
    const newItem = availableItems.value.find(item => item.id === shareForm.itemId)
    const newShare = {
      id: shares.value.length + 1,
      name: newItem.name,
      type: shareForm.type,
      size: newItem.size,
      shareUrl: `https://example.com/share/${Math.random().toString(36).substr(2, 9)}`,
      createTime: new Date().toISOString(),
      expireTime: shareForm.expireType === 'custom' ? shareForm.expireTime.toISOString() : null,
      viewCount: 0,
      downloadCount: 0,
      active: true
    }
    
    shares.value.unshift(newShare)
    
    ElMessage.success('分享创建成功')
    showShareDialog.value = false
    
    // 重置表单
    shareForm.itemId = ''
    shareForm.expireType = 'never'
    shareForm.expireTime = null
    shareForm.requireCode = false
    
    await loadShares()
  } catch (error) {
    ElMessage.error('创建分享失败')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadShares()
  loadAvailableItems()
})
</script>

<style scoped>
.share-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.toolbar-left {
  display: flex;
  gap: 10px;
}

.toolbar-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 20px;
}

.stat-icon {
  font-size: 32px;
  color: #409EFF;
  margin-bottom: 10px;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.share-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
}

.share-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.share-icon {
  font-size: 18px;
  color: #409EFF;
}

.share-details {
  flex: 1;
}

.share-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.share-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-size {
  font-size: 12px;
  color: #666;
}

.share-stats {
  display: flex;
  gap: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: #666;
}

.expired {
  color: #f56c6c;
  font-weight: bold;
}

.no-expire {
  color: #67c23a;
  font-weight: bold;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-size {
  margin-left: auto;
  font-size: 12px;
  color: #666;
}

:deep(.el-table) {
  height: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}
</style>