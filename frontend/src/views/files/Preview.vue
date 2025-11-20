<template>
  <div class="preview-container">
    <div class="preview-header">
      <el-button type="text" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回文件列表
      </el-button>
      <span class="preview-title">{{ filename }}</span>
    </div>

    <div class="preview-body">
      <template v-if="viewerType === 'video'">
        <video v-if="previewUrl" class="preview-video" controls :src="previewUrl"></video>
      </template>
      <template v-else>
        <iframe v-if="previewUrl" :src="previewUrl" class="preview-iframe" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const fileId = computed(() => route.params.id)
const filename = computed(() => (route.query.name || '').toString())

const getExt = () => {
  const name = filename.value || ''
  const idx = name.lastIndexOf('.')
  if (idx < 0) return ''
  return name.slice(idx + 1).toLowerCase()
}

const viewerType = computed(() => {
  const ext = getExt()
  if (['pdf'].includes(ext)) return 'pdf'
  if (['mp4'].includes(ext)) return 'video'
  if (['txt', 'log', 'md'].includes(ext)) return 'text'
  if (['doc', 'docx', 'ppt', 'pptx'].includes(ext)) return 'office'
  return 'unknown'
})
const previewUrl = computed(() => {
  if (!fileId.value) return ''
  return `/api/files/${fileId.value}/preview`
})

const goBack = () => {
  router.push({ name: 'Files' })
}
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
