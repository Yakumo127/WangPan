<template>
  <div class="file-management">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        
        <el-button @click="showCreateFolderDialog = true">
          <el-icon><FolderAdd /></el-icon>
          新建文件夹
        </el-button>
        
        <el-button @click="refreshFiles">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        
        <el-button 
          v-if="selectedFiles.length > 0" 
          type="danger" 
          @click="batchDelete"
        >
          <el-icon><Delete /></el-icon>
          批量删除
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文件..."
          prefix-icon="Search"
          clearable
          style="width: 200px"
          @input="handleSearch"
        />
        
        <el-select v-model="sortBy" style="width: 120px" @change="handleSort">
          <el-option label="按时间" value="createTime" />
          <el-option label="按名称" value="name" />
          <el-option label="按大小" value="size" />
        </el-select>
        
        <el-select v-model="sortOrder" style="width: 100px" @change="handleSort">
          <el-option label="降序" value="desc" />
          <el-option label="升序" value="asc" />
        </el-select>
      </div>
    </div>
    
    <!-- 面包屑导航 -->
    <div class="breadcrumb">
      <el-breadcrumb>
        <el-breadcrumb-item>
          <el-link @click="navigateToFolder(null)">全部文件</el-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item 
          v-for="folder in breadcrumbFolders" 
          :key="folder.id"
        >
          <el-link @click="navigateToFolder(folder.id)">{{ folder.name }}</el-link>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    
    <!-- 文件列表 -->
    <div class="file-list">
      <div class="file-list-header">
        <el-checkbox 
          v-model="selectAll" 
          @change="handleSelectAll"
        />
        <div class="file-name">文件名</div>
        <div class="file-size">大小</div>
        <div class="file-modify-time">修改时间</div>
        <div class="file-actions">操作</div>
      </div>
      
      <div class="file-list-content">
        <div 
          v-for="file in filteredFiles" 
          :key="file.id"
          class="file-item"
          :class="{ selected: selectedFiles.includes(file.id) }"
          @click="toggleFileSelection(file.id)"
          @contextmenu.prevent="showContextMenu($event, file)"
        >
          <el-checkbox 
            :model-value="selectedFiles.includes(file.id)"
            @change="toggleFileSelection(file.id)"
            @click.stop
          />
          
          <div class="file-info" @dblclick="handleFileDblClick(file)">
            <div class="file-icon">
              <el-icon v-if="file.isFolder"><Folder /></el-icon>
              <el-icon v-else-if="isImage(file)"><Picture /></el-icon>
              <el-icon v-else-if="isDocument(file)"><Document /></el-icon>
              <el-icon v-else-if="isVideo(file)"><VideoPlay /></el-icon>
              <el-icon v-else-if="isAudio(file)"><Headset /></el-icon>
              <el-icon v-else-if="isArchive(file)"><Box /></el-icon>
              <el-icon v-else><Files /></el-icon>
            </div>
            <div class="file-details">
              <div class="file-name">{{ file.originalName }}</div>
              <div class="file-meta" v-if="file.description">{{ file.description }}</div>
            </div>
          </div>
          
          <div class="file-size">{{ formatFileSize(file.fileSize) }}</div>
          <div class="file-modify-time">{{ formatDate(file.updateTime) }}</div>
          
          <div class="file-actions">
            <el-button 
              type="text" 
              size="small"
              @click.stop="downloadFile(file)"
              title="下载"
            >
              <el-icon><Download /></el-icon>
            </el-button>
            
            <el-button 
              type="text" 
              size="small"
              @click.stop="shareFile(file)"
              title="分享"
            >
              <el-icon><Share /></el-icon>
            </el-button>
            
            <el-button 
              type="text" 
              size="small"
              @click.stop="moveFile(file)"
              title="移动"
            >
              <el-icon><Position /></el-icon>
            </el-button>
            
            <el-button 
              type="text" 
              size="small"
              @click.stop="copyFile(file)"
              title="复制"
            >
              <el-icon><CopyDocument /></el-icon>
            </el-button>
            
            <el-button 
              type="text" 
              size="small"
              @click.stop="renameFile(file)"
              title="重命名"
            >
              <el-icon><Edit /></el-icon>
            </el-button>
            
            <el-button 
              type="text" 
              size="small"
              @click.stop="deleteFile(file)"
              title="删除"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 空状态 -->
      <div v-if="filteredFiles.length === 0" class="empty-state">
        <el-empty description="暂无文件">
          <el-button type="primary" @click="showUploadDialog = true">
            上传文件
          </el-button>
        </el-empty>
      </div>
    </div>
    
    <!-- 上传对话框 -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传文件"
      width="600px"
      @close="resetUpload"
    >
      <div class="upload-area" 
        @drop.prevent="handleFileDrop"
        @dragover.prevent="isDragOver = true"
        @dragleave.prevent="isDragOver = false"
        :class="{ 'drag-over': isDragOver }"
      >
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :multiple="true"
          :directory="true"
          :show-file-list="false"
          :on-change="handleFileChange"
          :limit="100"
        >
          <div class="upload-content">
            <el-icon><UploadFilled /></el-icon>
            <p>拖拽文件到这里，或点击选择文件</p>
            <p class="upload-hint">支持上传整个文件夹，单个文件最大1GB</p>
          </div>
        </el-upload>
      </div>
      
      <div v-if="uploadFiles.length > 0" class="upload-list">
        <div v-for="(file, index) in uploadFiles" :key="index" class="upload-item">
          <div class="upload-item-info">
            <el-icon><Document /></el-icon>
            <span>{{ file.name }}</span>
            <span class="upload-size">{{ formatFileSize(file.size) }}</span>
          </div>
          <div class="upload-item-progress">
            <el-progress 
              :percentage="uploadProgress[file.id] || 0"
              :status="uploadStatus[file.id]"
            />
          </div>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button 
          type="primary" 
          :loading="uploading"
          @click="startUpload"
          :disabled="uploadFiles.length === 0"
        >
          开始上传
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="showCreateFolderDialog" title="新建文件夹" width="400px">
      <el-form :model="folderForm" :rules="folderRules" ref="folderFormRef">
        <el-form-item label="文件夹名称" prop="name">
          <el-input v-model="folderForm.name" placeholder="请输入文件夹名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input 
            v-model="folderForm.description" 
            type="textarea" 
            :rows="3"
            placeholder="请输入文件夹描述"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateFolderDialog = false">取消</el-button>
        <el-button type="primary" @click="createFolder">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 右键菜单 -->
    <el-menu
      v-if="contextMenuVisible"
      class="context-menu"
      :style="{ left: contextMenuX + 'px', top: contextMenuY + 'px' }"
      @select="handleContextMenuAction"
    >
      <el-menu-item index="download">
        <el-icon><Download /></el-icon>
        下载
      </el-menu-item>
      <el-menu-item index="share">
        <el-icon><Share /></el-icon>
        分享
      </el-menu-item>
      <el-menu-item index="move">
        <el-icon><Position /></el-icon>
        移动
      </el-menu-item>
      <el-menu-item index="copy">
        <el-icon><CopyDocument /></el-icon>
        复制
      </el-menu-item>
      <el-menu-item index="rename">
        <el-icon><Edit /></el-icon>
        重命名
      </el-menu-item>
      <el-menu-item index="delete" divided>
        <el-icon><Delete /></el-icon>
        删除
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { fileApi } from '@/api/file'
import { folderApi } from '@/api/folder'
import SparkMD5 from 'spark-md5'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 响应式数据
const files = ref([])
const folders = ref([])
const currentFolder = ref(null)
const selectedFiles = ref([])
const searchQuery = ref('')
const sortBy = ref('createTime')
const sortOrder = ref('desc')
const selectAll = ref(false)

// 上传相关
const showUploadDialog = ref(false)
const uploadFiles = ref([])
const uploading = ref(false)
const isDragOver = ref(false)
const uploadProgress = ref({})
const uploadStatus = ref({})
const uploadRef = ref()

// 文件夹相关
const showCreateFolderDialog = ref(false)
const folderForm = ref({ name: '', description: '' })
const folderFormRef = ref()
const folderRules = {
  name: [
    { required: true, message: '请输入文件夹名称', trigger: 'blur' },
    { min: 1, max: 50, message: '文件夹名称长度在1-50个字符', trigger: 'blur' }
  ]
}

// 右键菜单
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuFile = ref(null)

// 计算属性
const filteredFiles = computed(() => {
  let result = [...files.value, ...folders.value]
  
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(item => 
      item.name.toLowerCase().includes(query) ||
      (item.description && item.description.toLowerCase().includes(query))
    )
  }
  
  result.sort((a, b) => {
    let aValue = a[sortBy.value]
    let bValue = b[sortBy.value]
    
    if (sortBy.value === 'createTime' || sortBy.value === 'updateTime') {
      aValue = new Date(aValue).getTime()
      bValue = new Date(bValue).getTime()
    }
    
    if (sortOrder.value === 'asc') {
      return aValue > bValue ? 1 : -1
    } else {
      return aValue < bValue ? 1 : -1
    }
  })
  
  return result
})

const breadcrumbFolders = computed(() => {
  const breadcrumbs = []
  let current = currentFolder.value
  
  while (current) {
    breadcrumbs.unshift(current)
    current = current.parent
  }
  
  return breadcrumbs
})

// 方法
const loadFiles = async () => {
  try {
    const response = await fileApi.getUserFiles({
      folderId: currentFolder.value?.id,
      search: searchQuery.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value
    })
    
    files.value = response.data.files || []
    folders.value = response.data.folders || []
  } catch (error) {
    ElMessage.error('加载文件失败')
  }
}

const handleFileDrop = (event) => {
  isDragOver.value = false
  const items = event.dataTransfer.items
  
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.kind === 'file') {
      const entry = item.webkitGetAsEntry()
      if (entry) {
        traverseFileTree(entry)
      }
    }
  }
}

const traverseFileTree = (item, path = '') => {
  if (item.isFile) {
    item.file(file => {
      const fileObj = {
        id: Date.now() + Math.random(),
        file: file,
        path: path + file.name,
        size: file.size
      }
      uploadFiles.value.push(fileObj)
    })
  } else if (item.isDirectory) {
    const dirReader = item.createReader()
    dirReader.readEntries(entries => {
      entries.forEach(entry => {
        traverseFileTree(entry, path + item.name + '/')
      })
    })
  }
}

const handleFileChange = (file) => {
  uploadFiles.value.push({
    id: Date.now() + Math.random(),
    file: file.raw,
    path: file.raw.name,
    size: file.raw.size
  })
}

const startUpload = async () => {
  if (uploadFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  
  uploading.value = true
  
  for (const uploadFile of uploadFiles.value) {
    try {
      await uploadFile(uploadFile)
    } catch (error) {
      console.error('上传失败:', error)
      uploadStatus.value[uploadFile.id] = 'exception'
    }
  }
  
  uploading.value = false
  ElMessage.success('上传完成')
  showUploadDialog.value = false
  resetUpload()
  loadFiles()
}

const uploadFile = async (uploadFile) => {
  const { file, path, size } = uploadFile
  
  // 计算文件MD5
  const fileHash = await calculateFileHash(file)
  
  // 检查文件是否已存在
  const existingFile = await fileApi.checkFileExists(fileHash)
  if (existingFile.data.exists) {
    uploadProgress.value[uploadFile.id] = 100
    uploadStatus.value[uploadFile.id] = 'success'
    return
  }
  
  // 分片上传
  if (size > 10 * 1024 * 1024) {
    await uploadInChunks(uploadFile, fileHash)
  } else {
    await uploadDirectly(uploadFile, fileHash)
  }
}

const uploadInChunks = async (uploadFile, fileHash) => {
  const { file } = uploadFile
  const chunkSize = 10 * 1024 * 1024 // 10MB
  const chunks = Math.ceil(file.size / chunkSize)
  
  uploadProgress.value[uploadFile.id] = 0
  
  for (let i = 0; i < chunks; i++) {
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, file.size)
    const chunk = file.slice(start, end)
    
    const formData = new FormData()
    formData.append('file', chunk)
    formData.append('chunkNumber', i + 1)
    formData.append('totalChunks', chunks)
    formData.append('fileHash', fileHash)
    formData.append('fileName', file.name)
    formData.append('fileSize', file.size)
    formData.append('folderId', currentFolder.value?.id)
    
    await fileApi.uploadChunk(formData)
    
    const progress = Math.round(((i + 1) / chunks) * 100)
    uploadProgress.value[uploadFile.id] = progress
  }
  
  uploadStatus.value[uploadFile.id] = 'success'
}

const uploadDirectly = async (uploadFile, fileHash) => {
  const { file } = uploadFile
  
  const formData = new FormData()
  formData.append('file', file)
  formData.append('fileHash', fileHash)
  formData.append('folderId', currentFolder.value?.id)
  
  await fileApi.uploadFile(formData)
  
  uploadProgress.value[uploadFile.id] = 100
  uploadStatus.value[uploadFile.id] = 'success'
}

const calculateFileHash = (file) => {
  return new Promise((resolve) => {
    const blobSlice = File.prototype.slice || File.prototype.mozSlice || File.prototype.webkitSlice
    const chunkSize = 2097152 // 2MB
    const chunks = Math.ceil(file.size / chunkSize)
    let currentChunk = 0
    const spark = new SparkMD5.ArrayBuffer()
    const fileReader = new FileReader()
    
    fileReader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++
      
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }
    
    fileReader.onerror = () => {
      console.warn('文件读取错误')
    }
    
    function loadNext() {
      const start = currentChunk * chunkSize
      const end = ((start + chunkSize) >= file.size) ? file.size : start + chunkSize
      
      fileReader.readAsArrayBuffer(blobSlice.call(file, start, end))
    }
    
    loadNext()
  })
}

const resetUpload = () => {
  uploadFiles.value = []
  uploadProgress.value = {}
  uploadStatus.value = {}
  isDragOver.value = false
}

const createFolder = async () => {
  await folderFormRef.value.validate()
  
  try {
    await folderApi.createFolder({
      name: folderForm.value.name,
      description: folderForm.value.description,
      parentId: currentFolder.value?.id
    })
    
    ElMessage.success('文件夹创建成功')
    showCreateFolderDialog.value = false
    folderForm.value = { name: '', description: '' }
    loadFiles()
  } catch (error) {
    ElMessage.error('文件夹创建失败')
  }
}

const downloadFile = async (file) => {
  try {
    const response = await fileApi.downloadFile(file.id)
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = file.originalName
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

const moveFile = async (file) => {
  // 实现移动文件逻辑
}

const copyFile = async (file) => {
  // 实现复制文件逻辑
}

const renameFile = async (file) => {
  const newName = await ElMessageBox.prompt('请输入新文件名', '重命名', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: file.originalName
  })
  
  try {
    await fileApi.renameFile(file.id, newName.value)
    ElMessage.success('重命名成功')
    loadFiles()
  } catch (error) {
    ElMessage.error('重命名失败')
  }
}

const deleteFile = async (file) => {
  try {
    await ElMessageBox.confirm('确定要删除这个文件吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await fileApi.deleteFile(file.id)
    ElMessage.success('删除成功')
    loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const batchDelete = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请选择要删除的文件')
    return
  }
  
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedFiles.value.length} 个文件吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await Promise.all(selectedFiles.value.map(id => fileApi.deleteFile(id)))
    ElMessage.success('批量删除成功')
    selectedFiles.value = []
    loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const toggleFileSelection = (fileId) => {
  const index = selectedFiles.value.indexOf(fileId)
  if (index > -1) {
    selectedFiles.value.splice(index, 1)
  } else {
    selectedFiles.value.push(fileId)
  }
}

const handleSelectAll = () => {
  if (selectAll.value) {
    selectedFiles.value = filteredFiles.value.map(f => f.id)
  } else {
    selectedFiles.value = []
  }
}

const handleSearch = () => {
  loadFiles()
}

const handleSort = () => {
  loadFiles()
}

const refreshFiles = () => {
  loadFiles()
}

const navigateToFolder = (folderId) => {
  currentFolder.value = folderId ? folders.value.find(f => f.id === folderId) : null
  loadFiles()
}

const handleFileDblClick = (file) => {
  if (file.isFolder) {
    navigateToFolder(file.id)
  } else {
    downloadFile(file)
  }
}

const showContextMenu = (event, file) => {
  contextMenuVisible.value = true
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuFile.value = file
}

const handleContextMenuAction = (action) => {
  contextMenuVisible.value = false
  
  switch (action) {
    case 'download':
      downloadFile(contextMenuFile.value)
      break
    case 'move':
      moveFile(contextMenuFile.value)
      break
    case 'copy':
      copyFile(contextMenuFile.value)
      break
    case 'rename':
      renameFile(contextMenuFile.value)
      break
    case 'delete':
      deleteFile(contextMenuFile.value)
      break
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDate = (dateString) => {
  const date = new Date(dateString)
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString()
}

const isImage = (file) => {
  return file.contentType?.startsWith('image/')
}

const isDocument = (file) => {
  return file.contentType?.includes('document') || file.contentType?.includes('pdf') || file.contentType?.includes('text')
}

const isVideo = (file) => {
  return file.contentType?.startsWith('video/')
}

const isAudio = (file) => {
  return file.contentType?.startsWith('audio/')
}

const isArchive = (file) => {
  return file.contentType?.includes('zip') || file.contentType?.includes('rar') || file.contentType?.includes('7z')
}

// 生命周期
onMounted(() => {
  loadFiles()
  
  // 点击其他地方关闭右键菜单
  document.addEventListener('click', () => {
    contextMenuVisible.value = false
  })
})

onUnmounted(() => {
  document.removeEventListener('click', () => {
    contextMenuVisible.value = false
  })
})
</script>

<style scoped>
.file-management {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: white;
  border-bottom: 1px solid #e6e6e6;
}

.toolbar-left {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.breadcrumb {
  padding: 12px 16px;
  background: white;
  border-bottom: 1px solid #e6e6e6;
}

.file-list {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.file-list-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #e6e6e6;
  font-weight: 600;
  color: #666;
  font-size: 14px;
}

.file-list-header > div {
  flex: 1;
  display: flex;
  align-items: center;
}

.file-list-header .file-name {
  flex: 2;
}

.file-list-header .file-actions {
  flex: 1.5;
  justify-content: flex-end;
}

.file-list-content {
  flex: 1;
  overflow-y: auto;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}

.file-item:hover {
  background: #f8f9fa;
}

.file-item.selected {
  background: #e6f7ff;
}

.file-item > div {
  flex: 1;
  display: flex;
  align-items: center;
}

.file-item .file-name {
  flex: 2;
}

.file-item .file-actions {
  flex: 1.5;
  justify-content: flex-end;
  gap: 4px;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 2;
}

.file-icon {
  font-size: 24px;
  color: #666;
}

.file-details {
  flex: 1;
}

.file-name {
  font-weight: 500;
  color: #333;
}

.file-meta {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.file-size, .file-modify-time {
  color: #666;
  font-size: 14px;
}

.file-actions .el-button {
  padding: 4px 8px;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-area {
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 40px;
  text-align: center;
  transition: border-color 0.3s;
}

.upload-area.drag-over {
  border-color: #409eff;
  background: #f0f9ff;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.upload-content .el-icon {
  font-size: 48px;
  color: #409eff;
}

.upload-hint {
  font-size: 12px;
  color: #999;
}

.upload-list {
  margin-top: 20px;
  max-height: 300px;
  overflow-y: auto;
}

.upload-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  margin-bottom: 8px;
}

.upload-item-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-size {
  font-size: 12px;
  color: #999;
}

.context-menu {
  position: fixed;
  z-index: 1000;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.context-menu .el-menu-item {
  height: 40px;
  line-height: 40px;
  padding: 0 16px;
  font-size: 14px;
}

.context-menu .el-menu-item .el-icon {
  margin-right: 8px;
}
</style>