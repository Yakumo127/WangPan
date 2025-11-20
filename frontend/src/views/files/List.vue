<template>
  <div class="files-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        <el-button @click="refreshFiles" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件..."
          style="width: 200px"
          clearable
          @keyup.enter="searchFiles"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchFiles">搜索</el-button>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="file-list">
      <el-table
        :data="files"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column label="文件名" min-width="300">
          <template #default="{ row }">
            <div class="file-info">
              <el-icon class="file-icon">
                <Document />
              </el-icon>
              <span class="file-name">{{ row.originalFilename || row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="150" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" @click="downloadFile(row)">
                <el-icon><Download /></el-icon>
                下载
              </el-button>
              <el-button size="small" @click="renameFile(row)">
                <el-icon><Edit /></el-icon>
                重命名
              </el-button>
              <el-button size="small" type="danger" @click="deleteFile(row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="600px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="选择文件夹">
          <el-select v-model="uploadForm.folderId" placeholder="选择目标文件夹（可选）" clearable>
            <el-option label="根目录" :value="null" />
            <el-option 
              v-for="folder in availableFolders" 
              :key="folder.id" 
              :label="folder.name" 
              :value="folder.id" 
            />
          </el-select>
        </el-form-item>
      </el-form>
      
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :auto-upload="false"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :limit="10"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
      </el-upload>
      
      <!-- 上传进度 -->
      <div v-if="uploadProgress.length > 0" class="upload-progress">
        <div v-for="(progress, index) in uploadProgress" :key="index" class="progress-item">
          <div class="progress-info">
            <span class="file-name">{{ progress.name }}</span>
            <span class="progress-speed" v-if="progress.speed">{{ progress.speed }}</span>
            <span class="progress-percent">{{ progress.percent }}%</span>
          </div>
          <el-progress :percentage="progress.percent" :status="progress.status" />
        </div>
      </div>
      
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="uploadFilesFunc" :loading="uploading">
          开始上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, Search, Document, Download, Delete, Edit } from '@element-plus/icons-vue'
import { getFileList, uploadFile, deleteFile as deleteFileApi, searchFiles as searchFilesApi, renameFile as renameFileApi, checkFileExists, uploadChunk as uploadChunkApi, mergeChunks, getChunkStatus } from '@/api/file'
import { getFolderList } from '@/api/folder'
import { getToken } from '@/utils/auth'

const loading = ref(false)
const uploading = ref(false)
const files = ref([])
const searchKeyword = ref('')
const showUploadDialog = ref(false)
const uploadFiles = ref([])
const availableFolders = ref([])
const uploadProgress = ref([])

const uploadForm = reactive({
  folderId: null
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
  // 速度格式化（B/s -> 人类可读）
  const formatSpeed = (bps) => {
    if (!bps || bps < 1) return '0 KB/s'
    const kb = bps / 1024
    const mb = kb / 1024
    if (mb >= 1) return mb.toFixed(2) + ' MB/s'
    return kb.toFixed(2) + ' KB/s'
  }

// 加载文件夹列表
const loadFolders = async () => {
  try {
    const response = await getFolderList()
    availableFolders.value = response || []
  } catch (error) {
    console.error('加载文件夹列表失败:', error)
  }
}

// 加载文件列表
const loadFiles = async () => {
  loading.value = true
  try {
    const response = await getFileList()
    files.value = response || []
  } catch (error) {
    ElMessage.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

// 刷新文件列表
const refreshFiles = () => {
  loadFiles()
}

// 搜索文件
const searchFiles = async () => {
  if (!searchKeyword.value.trim()) {
    await loadFiles()
    return
  }
  
  loading.value = true
  try {
    const response = await searchFilesApi(searchKeyword.value)
    files.value = response || []
    ElMessage.success(`找到 ${files.value.length} 个文件`)
  } catch (error) {
    ElMessage.error('搜索失败')
  } finally {
    loading.value = false
  }
}

// 上传相关方法
const handleFileChange = (file) => {
  uploadFiles.value.push(file)
}

const handleFileRemove = (file) => {
  const index = uploadFiles.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    uploadFiles.value.splice(index, 1)
  }
}

// 计算 SHA-256（注意：会读取整个文件到内存，100MB 内可接受；如需更大文件，应改为增量哈希库）
const sha256Hex = async (file) => {
  const buf = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buf)
  const bytes = new Uint8Array(hash)
  return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
}

const uploadFilesFunc = async () => {
  if (uploadFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  uploadProgress.value = uploadFiles.value.map(file => ({
    name: file.name,
    percent: 0,
    status: '',
    speed: '0 KB/s',
    lastTime: Date.now(),
    lastLoadedBytes: 0
  }))

  try {
    const MAX_MB = 100
    const MAX_BYTES = MAX_MB * 1024 * 1024
    for (let i = 0; i < uploadFiles.value.length; i++) {
      const elFile = uploadFiles.value[i]
      const raw = elFile.raw
      // 前置大小校验，避免后端 413
      if (raw.size > MAX_BYTES) {
        uploadProgress.value[i].status = 'exception'
        uploadProgress.value[i].percent = 0
        ElMessage.error(`文件 "${elFile.name}" 超过 ${MAX_MB}MB 限制`)
        continue
      }

      uploadProgress.value[i].status = 'uploading'

      // 1) 计算哈希并秒传判断（同一用户同哈希文件已存在则跳过）
      let fileHash = ''
      try {
        fileHash = await sha256Hex(raw)
      } catch (e) {
        console.warn('计算哈希失败，退回直传：', e)
      }

      if (fileHash) {
        try {
          const existsResp = await checkFileExists(fileHash)
          if (existsResp && existsResp.exists) {
            uploadProgress.value[i].status = 'success'
            uploadProgress.value[i].percent = 100
            continue
          }
        } catch (e) {
          // 秒传检查失败不影响上传，继续走上传逻辑
        }
      }

      // 2) 大小阈值：>10MB 走分片上传，否则直传
      const CHUNK_SIZE = 10 * 1024 * 1024
      if (raw.size > CHUNK_SIZE && fileHash) {
        await uploadInChunksWithRetryAndResume(elFile, i, fileHash, CHUNK_SIZE)
      } else {
        const formData = new FormData()
        formData.append('file', raw)
        if (uploadForm.folderId) {
          formData.append('folderId', uploadForm.folderId)
        }
        await uploadFile(formData, (progressEvent) => {
          const loaded = progressEvent.loaded || 0
          const total = progressEvent.total || raw.size || 1
          const percent = Math.round((loaded * 100) / total)
          const pr = uploadProgress.value[i]
          const now = Date.now()
          const dt = Math.max((now - (pr.lastTime || now)) / 1000, 0.001)
          const delta = Math.max(loaded - (pr.lastLoadedBytes || 0), 0)
          const bps = delta / dt
          pr.speed = formatSpeed(bps)
          pr.lastTime = now
          pr.lastLoadedBytes = loaded
          pr.percent = percent
        })
        uploadProgress.value[i].status = 'success'
        uploadProgress.value[i].percent = 100
      }
    }
    
    ElMessage.success('文件上传成功')
    showUploadDialog.value = false
    uploadFiles.value = []
    uploadProgress.value = []
    uploadForm.folderId = null
    await loadFiles()
  } catch (error) {
    // 统一由拦截器弹错误提示，这里不重复提示
    uploadProgress.value.forEach(progress => {
      if (progress.status !== 'success') {
        progress.status = 'exception'
      }
    })
  } finally {
    uploading.value = false
  }
}

// 分片上传（含并发、重试、断点续传-基于本地存储，跨会话可恢复，但服务器端不校验已有分片列表）
const uploadInChunksWithRetryAndResume = async (elFile, progressIndex, fileHash, CHUNK_SIZE) => {
  const raw = elFile.raw
  const totalChunks = Math.ceil(raw.size / CHUNK_SIZE)
  const CONCURRENCY = 3
  const MAX_RETRIES = 3
  const resumeKey = `upload_resume_${fileHash}`
  let resume = { done: [], totalChunks }
  try {
    const saved = localStorage.getItem(resumeKey)
    if (saved) resume = Object.assign(resume, JSON.parse(saved))
  } catch {}

  const doneSet = new Set(resume.done || [])
  // 从服务端获取已收分片，优先使用服务端记录增强跨端断点能力
  try {
    const status = await getChunkStatus(fileHash, totalChunks)
    if (status && Array.isArray(status.uploaded)) {
      status.uploaded.forEach(n => doneSet.add(n))
    }
  } catch (e) {
    // 取不到服务端状态时，继续使用本地记录
  }
  let completed = doneSet.size

  const tasks = []
  for (let n = 1; n <= totalChunks; n++) {
    if (!doneSet.has(n)) tasks.push(n)
  }

  let pointer = 0
  const updateProgress = () => {
    const percent = Math.round((completed / totalChunks) * 100)
    uploadProgress.value[progressIndex].percent = percent
  }
  const formatSpeed = (bps) => {
    if (!bps || bps < 1) return '0 KB/s'
    const kb = bps / 1024
    const mb = kb / 1024
    if (mb >= 1) return mb.toFixed(2) + ' MB/s'
    return kb.toFixed(2) + ' KB/s'
  }
  const getChunkSize = (n) => (n < totalChunks ? CHUNK_SIZE : (raw.size - CHUNK_SIZE * (totalChunks - 1)))
  const calcLoadedNow = () => {
    const pr = uploadProgress.value[progressIndex]
    let doneBytes = 0
    for (const n of doneSet) {
      doneBytes += getChunkSize(n)
    }
    let partialBytes = 0
    const map = pr.chunkLoaded || {}
    for (const k in map) if (Object.prototype.hasOwnProperty.call(map, k)) partialBytes += map[k] || 0
    const total = Math.min(doneBytes + partialBytes, raw.size)
    return total
  }
  const updateSpeedAgg = () => {
    const pr = uploadProgress.value[progressIndex]
    if (!pr.lastTime) { pr.lastTime = Date.now(); pr.lastLoadedBytes = 0; pr.speed = '0 KB/s' }
    const now = Date.now()
    const loadedNow = calcLoadedNow()
    const dt = Math.max((now - pr.lastTime) / 1000, 0.001)
    const delta = Math.max(loadedNow - (pr.lastLoadedBytes || 0), 0)
    const bps = delta / dt
    pr.speed = formatSpeed(bps)
    pr.lastLoadedBytes = loadedNow
    pr.lastTime = now
  }
  updateProgress()

  const uploadOne = async (chunkNumber) => {
    const start = (chunkNumber - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, raw.size)
    const blob = raw.slice(start, end)
    const formData = new FormData()
    formData.append('file', blob)
    formData.append('fileHash', fileHash)
    formData.append('chunkNumber', String(chunkNumber))
    formData.append('totalChunks', String(totalChunks))

    let attempt = 0
    while (attempt < MAX_RETRIES) {
      try {
        // 初始化聚合追踪
        const pr = uploadProgress.value[progressIndex]
        if (!pr.chunkLoaded) pr.chunkLoaded = {}
        await uploadChunkApi(formData, (e) => {
          pr.chunkLoaded[chunkNumber] = e.loaded || 0
          updateSpeedAgg()
        })
        // 标记完成并持久化
        doneSet.add(chunkNumber)
        // 清除该分片的局部计数，避免重复统计
        if (uploadProgress.value[progressIndex].chunkLoaded) delete uploadProgress.value[progressIndex].chunkLoaded[chunkNumber]
        completed = doneSet.size
        localStorage.setItem(resumeKey, JSON.stringify({ done: Array.from(doneSet), totalChunks }))
        updateProgress()
        return
      } catch (e) {
        attempt++
        if (attempt >= MAX_RETRIES) throw e
        await new Promise(r => setTimeout(r, 500 * attempt))
      }
    }
  }

  const worker = async () => {
    while (true) {
      let idx
      if (pointer >= tasks.length) return
      idx = pointer
      pointer++
      const n = tasks[idx]
      await uploadOne(n)
    }
  }

  const workers = []
  const pc = Math.min(CONCURRENCY, tasks.length)
  for (let w = 0; w < pc; w++) workers.push(worker())
  await Promise.all(workers)

  // 上传完成后再次向服务端核对缺失分片
  try {
    const status = await getChunkStatus(fileHash, totalChunks)
    const missing = Array.isArray(status?.missing) ? status.missing : []
    if (missing.length > 0) {
      ElMessage.warning(`检测到 ${missing.length} 个分片未上传，正在补传...`)
      // 依次补传缺失分片，可复用重试逻辑
      for (const n of missing) {
        await uploadOne(n)
      }
    }
  } catch (e) {
    // 忽略状态检查失败，不阻断后续合并（合并内部也会校验）
  }

  // 合并前友好提示
  ElMessage.info('分片已上传完成，开始服务端合并...')
  try {
    await mergeChunks({ fileHash, filename: raw.name, totalChunks, folderId: uploadForm.folderId || null })
    uploadProgress.value[progressIndex].status = 'success'
    uploadProgress.value[progressIndex].percent = 100
    // 清理断点续传记录
    try { localStorage.removeItem(resumeKey) } catch {}
  } catch (e) {
    // 合并失败引导：显示简要原因与操作建议
    uploadProgress.value[progressIndex].status = 'exception'
    const msg = (e && e.message) ? e.message : '合并失败'
    ElMessage.error(`${msg}。建议稍后重试，已上传分片将在72小时内保留用于断点续传。`)
    throw e
  }
}

// 下载文件（使用 fetch + a 链接，避免 Axios 超时限制）
const downloadFile = async (file) => {
  try {
    const token = getToken()
    const headers = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
    const res = await fetch(`/api/files/download/${file.id}`, {
      method: 'GET',
      headers
    })
    if (!res.ok) {
      throw new Error(`下载失败，状态码：${res.status}`)
    }
    const blob = await res.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.originalFilename || file.name || 'download'
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  } catch (error) {
    console.error('文件下载失败:', error)
    ElMessage.error('文件下载失败')
  }
}

// 重命名文件
const renameFile = async (file) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入文件的新名称`,
      '重命名文件',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: file.originalFilename || file.name,
        inputPattern: /.+/,
        inputErrorMessage: '文件名不能为空'
      }
    )
    
    await renameFileApi(file.id, value)
    ElMessage.success('文件重命名成功')
    await loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('文件重命名失败')
    }
  }
}

// 删除文件
const deleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${file.originalFilename || file.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteFileApi(file.id)
    ElMessage.success('文件删除成功')
    await loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('文件删除失败')
    }
  }
}

onMounted(() => {
  loadFiles()
  loadFolders()
})
</script>

<style scoped>
.files-container {
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

.breadcrumb {
  margin-bottom: 20px;
  padding: 10px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.file-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-icon {
  font-size: 18px;
  color: #409EFF;
}

.file-name {
  font-weight: 500;
}

.upload-area {
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.3s;
}

.upload-area:hover {
  border-color: #409EFF;
}

.upload-area .el-icon--upload {
  font-size: 48px;
  color: #409EFF;
  margin-bottom: 16px;
}

.el-upload__text {
  color: #666;
  font-size: 14px;
}

.el-upload__text em {
  color: #409EFF;
  font-style: normal;
}

:deep(.el-table) {
  height: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

.upload-progress {
  margin-top: 20px;
}

.progress-item {
  margin-bottom: 15px;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.progress-item .file-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.progress-item .progress-percent {
  font-size: 12px;
  color: #666;
}
.progress-item .progress-speed {
  margin: 0 8px;
  color: #909399;
  font-size: 12px;
}
</style>
