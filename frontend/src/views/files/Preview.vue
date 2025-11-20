<template>
  <div class="preview-container">
    <div class="preview-header">
      <el-button type="text" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回文件列表
      </el-button>
      <span class="preview-title">{{ filename }}</span>
    </div>

    <div class="preview-body" v-loading="loading">
      <template v-if="error">
        <div class="preview-error">
          <el-icon><WarningFilled /></el-icon>
          <p>{{ error }}</p>
        </div>
      </template>
      <template v-else>
        <template v-if="viewerType === 'pdf' || viewerType === 'office'">
          <iframe v-if="blobUrl" :src="blobUrl" class="preview-iframe" />
        </template>
        <template v-else-if="viewerType === 'video'">
          <video v-if="blobUrl" class="preview-video" controls :src="blobUrl"></video>
        </template>
        <template v-else-if="viewerType === 'text'">
          <pre class="preview-text">{{ textContent }}</pre>
        </template>
        <template v-else>
          <div class="preview-unsupported">
            <el-icon><WarningFilled /></el-icon>
            <p>当前类型暂不支持在线预览。</p>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, WarningFilled } from '@element-plus/icons-vue'
import { previewFile as previewFileApi } from '@/api/file'

const route = useRoute()
const router = useRouter()

const fileId = computed(() => route.params.id)
const filename = computed(() => (route.query.name || '').toString())

const loading = ref(false)
const error = ref('')
const blobUrl = ref('')
const textContent = ref('')

const getExt = () => {
  const name = filename.value || ''
  const idx = name.lastIndexOf('.')
  if (idx < 0) return ''
  return name.slice(idx + 1).toLowerCase()
}

const viewerType = computed(() => {
  const ext = getExt()
  if (['pdf'].includes(ext)) return 'pdf'
  if (['txt', 'log', 'md'].includes(ext)) return 'text'
  if (['mp4'].includes(ext)) return 'video'
  if (['doc', 'docx', 'ppt', 'pptx'].includes(ext)) return 'office'
  return 'unknown'
})

const loadPreview = async () => {
  if (!fileId.value) {
    error.value = '缺少文件ID'
    return
  }
  loading.value = true
  error.value = ''
  let url = ''
  try {
    const res = await previewFileApi(fileId.value)
    const blob = res?.data
    if (!(blob instanceof Blob)) {
      throw new Error('预览数据无效')
    }
    if (viewerType.value === 'text') {
      textContent.value = await blob.text()
    } else {
      url = window.URL.createObjectURL(blob)
      blobUrl.value = url
    }
  } catch (e) {
    console.error('加载预览失败:', e)
    error.value = e?.message || '加载预览失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push({ name: 'Files' })
}

onMounted(() => {
  loadPreview()
})

onBeforeUnmount(() => {
  if (blobUrl.value) {
    try { window.URL.revokeObjectURL(blobUrl.value) } catch (e) {}
  }
})
</script>

<style scoped>
.preview-container {
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.preview-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.preview-body {
  flex: 1;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
  position: relative;
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

.preview-error,
.preview-unsupported {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #f56c6c;
}
</style>

