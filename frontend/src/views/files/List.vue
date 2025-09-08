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
import { getFileList, uploadFile, downloadFile as downloadFileApi, deleteFile as deleteFileApi, searchFiles as searchFilesApi, renameFile as renameFileApi } from '@/api/file'
import { getFolderList } from '@/api/folder'

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

const uploadFilesFunc = async () => {
  if (uploadFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  uploadProgress.value = uploadFiles.value.map(file => ({
    name: file.name,
    percent: 0,
    status: ''
  }))

  try {
    for (let i = 0; i < uploadFiles.value.length; i++) {
      const file = uploadFiles.value[i]
      const formData = new FormData()
      formData.append('file', file.raw)
      
      if (uploadForm.folderId) {
        formData.append('folderId', uploadForm.folderId)
      }

      uploadProgress.value[i].status = 'uploading'
      
      await uploadFile(formData, (progressEvent) => {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        uploadProgress.value[i].percent = percent
      })
      
      uploadProgress.value[i].status = 'success'
      uploadProgress.value[i].percent = 100
    }
    
    ElMessage.success('文件上传成功')
    showUploadDialog.value = false
    uploadFiles.value = []
    uploadProgress.value = []
    uploadForm.folderId = null
    await loadFiles()
  } catch (error) {
    ElMessage.error('文件上传失败')
    uploadProgress.value.forEach(progress => {
      if (progress.status !== 'success') {
        progress.status = 'exception'
      }
    })
  } finally {
    uploading.value = false
  }
}

// 下载文件
const downloadFile = async (file) => {
  try {
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
</style>
