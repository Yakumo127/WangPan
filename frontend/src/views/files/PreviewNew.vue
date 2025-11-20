<template>
  <div class="file-preview-page">
    <!-- 内部顶部栏：系统名称 + 文件名 + 操作按钮 -->
    <div class="file-preview-header">
      <div class="header-left">
        <span class="file-name-label">文件名：</span>
        <span class="file-name">{{ filename }}</span>
      </div>
      <div class="header-actions">
        <el-button size="small" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回文件列表
        </el-button>
        <el-button size="small" @click="toggleDetails">
          详情
        </el-button>
        <el-button size="small" @click="handleHistory">
          历史
        </el-button>
        <el-button size="small" @click="handleDownload">
          <el-icon><Download /></el-icon>
          下载
        </el-button>
      </div>
    </div>

    <!-- 主体区域：左侧信息栏 + 右侧预览区 -->
    <div class="file-preview-body" v-loading="loading">
      <template v-if="error">
        <div class="preview-error">
          <el-icon><WarningFilled /></el-icon>
          <p>{{ error }}</p>
        </div>
      </template>
      <template v-else>
        <div class="file-preview-layout">
          <aside v-if="showSidebar" class="file-preview-sidebar">
            <h3 class="sidebar-title">文件信息</h3>
            <div class="sidebar-section">
              <div class="sidebar-label">名称</div>
              <div class="sidebar-value">{{ filename }}</div>
            </div>
            <div class="sidebar-section">
              <div class="sidebar-label">类型</div>
              <div class="sidebar-value">{{ viewerTypeLabel }}</div>
            </div>
            <div class="sidebar-section">
              <div class="sidebar-label">大小</div>
              <div class="sidebar-value">{{ sizeLabel }}</div>
            </div>
            <div class="sidebar-section">
              <div class="sidebar-label">上传时间</div>
              <div class="sidebar-value">{{ createTimeLabel }}</div>
            </div>
            <div class="sidebar-section">
              <div class="sidebar-label">修改时间</div>
              <div class="sidebar-value">{{ updateTimeLabel }}</div>
            </div>
            <div class="sidebar-section">
              <div class="sidebar-label">位置</div>
              <div class="sidebar-value">{{ locationLabel }}</div>
            </div>
          </aside>
          <main class="file-preview-content">
            <template v-if="viewerType === 'video'">
              <video
                v-if="blobUrl"
                class="preview-video"
                controls
                :src="blobUrl"
              ></video>
            </template>
            <template v-else-if="viewerType === 'text'">
              <pre class="preview-text">{{ textContent }}</pre>
            </template>
            <template v-else>
              <iframe
                v-if="blobUrl"
                :src="blobUrl"
                class="preview-iframe"
              />
            </template>
          </main>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, WarningFilled, Download } from '@element-plus/icons-vue'
import { previewFile as previewFileApi, getDownloadUrl } from '@/api/file'

const route = useRoute()
const router = useRouter()

const fileId = computed(() => route.params.id)
const filename = computed(() => (route.query.name || '').toString())
const rawSize = computed(() => route.query.size)
const rawCreateTime = computed(() => route.query.createTime)
const rawUpdateTime = computed(() => route.query.updateTime)
const rawLocation = computed(() => route.query.location)

const loading = ref(false)
const error = ref('')
const blobUrl = ref('')
const textContent = ref('')
const viewerType = ref('unknown')
const showSidebar = ref(false)

const viewerTypeLabel = computed(() => {
  switch (viewerType.value) {
    case 'pdf':
      return 'PDF 文件'
    case 'video':
      return '视频文件'
    case 'text':
      return '文本文件'
    case 'office':
      return 'Office 文档'
    default:
      return '其他类型'
  }
})

const formatFileSize = (bytes) => {
  const n = Number(bytes)
  if (!n || n <= 0) return ''
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(n) / Math.log(k))
  return `${parseFloat((n / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

const formatDateTime = (val) => {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString()
}

const sizeLabel = computed(() => formatFileSize(rawSize.value))
const createTimeLabel = computed(() => formatDateTime(rawCreateTime.value))
const updateTimeLabel = computed(() => formatDateTime(rawUpdateTime.value))
const locationLabel = computed(() => {
  const loc = (rawLocation.value || '').toString()
  if (!loc) return '根目录'
  return loc
})

const getExt = () => {
  const name = filename.value || ''
  const idx = name.lastIndexOf('.')
  if (idx < 0) return ''
  return name.slice(idx + 1).toLowerCase()
}

const loadPreview = async () => {
  if (!fileId.value) {
    error.value = '缺少文件ID'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await previewFileApi(fileId.value)
    const blob = res?.data
    if (!(blob instanceof Blob)) {
      throw new Error('预览数据无效')
    }
    const ext = getExt()
    const contentType =
      (res &&
        res.headers &&
        (res.headers['content-type'] || res.headers['Content-Type'])) ||
      ''

    let type = 'unknown'
    if (contentType && contentType.includes('application/pdf')) {
      type = 'pdf'
    } else if (contentType.startsWith('video/') || ext === 'mp4') {
      type = 'video'
    } else if (
      contentType.startsWith('text/') ||
      ['txt', 'log', 'md'].includes(ext)
    ) {
      type = 'text'
    } else if (['doc', 'docx', 'ppt', 'pptx'].includes(ext)) {
      type = 'office'
    }
    viewerType.value = type

    if (viewerType.value === 'text') {
      textContent.value = await blob.text()
    } else {
      let finalBlob = blob
      if (
        viewerType.value === 'pdf' &&
        blob.type !== 'application/pdf'
      ) {
        finalBlob = new Blob([blob], { type: 'application/pdf' })
      }
      const url = window.URL.createObjectURL(finalBlob)
      blobUrl.value = url
    }
  } catch (e) {
    console.error('加载预览失败(新预览页):', e)
    error.value = e?.message || '加载预览失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push({ name: 'Files' })
}

const handleDownload = async () => {
  try {
    const res = await getDownloadUrl(fileId.value)
    const url = res && res.url
    if (!url) {
      throw new Error('下载链接为空')
    }
    const a = document.createElement('a')
    a.href = url
    a.download = filename.value || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } catch (e) {
    console.error('下载失败(新预览页):', e)
    ElMessage.error('下载失败')
  }
}

const toggleDetails = () => {
  showSidebar.value = !showSidebar.value
}

const handleHistory = () => {
  ElMessage.info('历史功能暂未实现')
}

onMounted(() => {
  loadPreview()
})

onBeforeUnmount(() => {
  if (blobUrl.value) {
    try {
      window.URL.revokeObjectURL(blobUrl.value)
    } catch (e) {}
  }
})
</script>

<style scoped>
.file-preview-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px 16px;
  box-sizing: border-box;
}

.file-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.file-name-label {
  font-weight: 500;
  color: #666;
}

.file-name {
  font-weight: 600;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.file-preview-body {
  flex: 1;
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.file-preview-layout {
  display: flex;
  height: 100%;
}

.file-preview-sidebar {
  width: 260px;
  border-right: 1px solid #ebeef5;
  padding: 16px;
  box-sizing: border-box;
  background: #fafafa;
}

.sidebar-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.sidebar-section {
  margin-bottom: 12px;
}

.sidebar-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.sidebar-value {
  font-size: 13px;
  color: #333;
  word-break: break-all;
}

.file-preview-content {
  flex: 1;
  position: relative;
  box-sizing: border-box;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-video {
  width: 100%;
  height: 100%;
  background: #000;
}

.preview-text {
  margin: 0;
  padding: 16px;
  height: 100%;
  overflow: auto;
  font-family: monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-error {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #f56c6c;
}
</style>
