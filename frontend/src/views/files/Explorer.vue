<template>
  <div class="files-explorer-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="openNewFolderDialog">
          <el-icon><Folder /></el-icon>
          新建文件夹
        </el-button>
        <el-button @click="openFilePicker">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
      </div>

      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件或文件夹..."
          style="width: 240px"
          clearable
          @keyup.enter="searchEntries"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchEntries">搜索</el-button>
      </div>
    </div>

    <!-- 隐藏文件选择器 -->
    <input
      ref="fileInputRef"
      type="file"
      multiple
      style="display: none;"
      @change="onFileInputChange"
    />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item
          v-for="item in breadcrumbs"
          :key="item.id ?? 'root'"
          @click="onBreadcrumbClick(item)"
          class="breadcrumb-item-clickable"
        >
          {{ item.name }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 列表 + 拖拽上传区域 -->
    <div
      class="entries-list"
      @dragover.prevent="onDragOver"
      @drop.prevent="onDropFiles"
    >
      <el-table
        ref="tableRef"
        :data="entries"
        style="width: 100%; table-layout: fixed;"
        v-loading="loading"
        :fit="false"
        height="100%"
        border
        @selection-change="onSelectionChange"
        @header-dragend="onHeaderDragEnd"
      >
        <el-table-column type="selection" width="50" align="center">
          <template #header>
            <el-checkbox
              v-model="allChecked"
              :indeterminate="isIndeterminate"
              @change="onToggleAll"
            />
          </template>
        </el-table-column>
        <el-table-column label="名称" :width="columnWidths.name" column-key="col-name">
          <template #default="{ row }">
            <div class="entry-info" @dblclick="onEntryDblClick(row)">
              <div class="entry-thumb">
                <img v-if="getThumbnail(row)" :src="getThumbnail(row)" alt="thumb" />
                <el-icon v-else class="entry-icon">
                  <FolderOpened v-if="row.type === 'folder'" />
                  <Document v-else />
                </el-icon>
              </div>
              <span
                class="entry-name"
                :class="{ clickable: row.type === 'folder' || row.type === 'file' }"
                @click.stop="onEntryClick(row)"
              >
                {{ row.name }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="size" label="大小" :width="columnWidths.size" column-key="col-size">
          <template #default="{ row }">
            <span v-if="row.type === 'file'">
              {{ formatFileSize(row.size) }}
            </span>
            <span v-else>--</span>
          </template>
        </el-table-column>

        <el-table-column prop="type" label="类型" :width="columnWidths.type" column-key="col-type">
          <template #default="{ row }">
            <span>{{ formatFileType(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" :width="columnWidths.createTime" column-key="col-create">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" :width="columnWidths.actions" column-key="col-actions" align="center">
          <template #default="{ row }">
            <div class="op-group" v-if="row.type === 'folder'">
              <el-tooltip content="打开" placement="top">
                <el-button size="small" class="op-btn" @click="enterFolder(row.raw)">
                  <el-icon><Folder /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="重命名" placement="top">
                <el-button size="small" class="op-btn" @click="renameFolderEntry(row.raw)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button size="small" class="op-btn" type="danger" @click="deleteFolderEntry(row.raw)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
            <div class="op-group" v-else>
              <el-tooltip content="下载" placement="top">
                <el-button size="small" class="op-btn" @click="downloadFileEntry(row.raw)">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="重命名" placement="top">
                <el-button size="small" class="op-btn" @click="renameFileEntry(row.raw)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button size="small" class="op-btn" type="danger" @click="deleteFileEntry(row.raw)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
              <el-dropdown trigger="click" placement="bottom">
                <el-button size="small" class="op-btn op-btn-plain" plain>
                  <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="previewFileEntry(row.raw)">预览</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="imagePreviewVisible"
      title="图片预览"
      width="70%"
      class="image-preview-dialog"
      @closed="onImagePreviewClosed"
    >
      <div class="image-preview-wrapper" @wheel.prevent="onImageWheel">
        <img
          v-if="imagePreviewUrl"
          :src="imagePreviewUrl"
          class="image-preview-img"
          :style="{ transform: `scale(${imageScale})` }"
        />
      </div>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="newFolderDialogVisible" title="新建文件夹" width="400px">
      <el-form>
        <el-form-item label="名称" label-width="60px">
          <el-input
            v-model="newFolderName"
            placeholder="请输入文件夹名称"
            @keyup.enter="confirmCreateFolder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newFolderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateFolder">确定</el-button>
      </template>
    </el-dialog>

    <!-- 上传队列面板（底部抽屉） -->
    <el-drawer
      v-model="uploadDrawerVisible"
      title="上传任务"
      size="30%"
      direction="btt"
    >
      <div v-if="uploadQueue.length === 0" style="color:#999;">暂无上传任务</div>
      <el-table
        v-else
        :data="uploadQueue"
        style="width:100%;"
        size="small"
      >
        <el-table-column prop="name" label="文件名" min-width="220" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <span v-if="row.status === 'pending'">排队中</span>
            <span v-else-if="row.status === 'hashing'">计算哈希</span>
            <span v-else-if="row.status === 'checking_fast'">秒传检查</span>
            <span v-else-if="row.status === 'uploading'">上传中</span>
            <span v-else-if="row.status === 'completed'">
              <span v-if="row.isFastUploaded">秒传完成</span>
              <span v-else>已完成</span>
            </span>
            <span v-else-if="row.status === 'failed'">失败</span>
            <span v-else>{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :status="row.status === 'failed' ? 'exception' : (row.status === 'completed' ? 'success' : undefined)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="!row.file && row.status === 'paused'"
              type="primary"
              link
              size="small"
              @click="onSelectFileForTask(row.id)"
            >
              选择文件
            </el-button>
            <el-button
              v-if="row.status === 'failed'"
              type="primary"
              link
              size="small"
              @click="retryTask(row.id)"
            >
              重试
            </el-button>
            <el-button
              v-if="row.status === 'uploading'"
              type="primary"
              link
              size="small"
              @click="pauseTask(row.id)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'paused'"
              type="primary"
              link
              size="small"
              @click="resumeTask(row.id)"
            >
              继续
            </el-button>
            <el-button
              v-if="row.status !== 'completed'"
              type="danger"
              link
              size="small"
              @click="cancelTask(row.id)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, FolderOpened, Upload, Search, Document, Download, Delete, Edit, View, ArrowDown } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getFileList, deleteFile as deleteFileApi, renameFile as renameFileApi, getDownloadUrl, previewFile as previewFileApi } from '@/api/file'
import { getFolderList, createFolder, deleteFolder as deleteFolderApi, renameFolder as renameFolderApi, getFolderPath } from '@/api/folder'
import { useUploadQueue } from '@/composables/useUploadQueue'

const router = useRouter()

const loading = ref(false)
const tableRef = ref(null)
const columnWidths = ref({
  name: 345,
  size: 100,
  type: 120,
  createTime: 160,
  actions: 180
})
const currentFolderId = ref(null)
const folderList = ref([])
const fileList = ref([])
const searchKeyword = ref('')
const newFolderDialogVisible = ref(false)
const newFolderName = ref('')
const uploadDrawerVisible = ref(false)
const fileInputRef = ref(null)
const resumeTaskId = ref(null)
const imagePreviewVisible = ref(false)
const imagePreviewUrl = ref('')
const imageScale = ref(1)

const breadcrumbs = ref([
  { id: null, name: '根目录' }
])

const entries = computed(() => {
    const folders = (folderList.value || []).map(f => ({
      id: f.id,
      name: f.name,
      type: 'folder',
      createTime: f.createTime,
      size: null,
      raw: f,
      thumbnail: null
    }))
    const files = (fileList.value || []).map(f => ({
      id: f.id,
      name: f.originalFilename || f.name || f.filename,
      type: 'file',
      createTime: f.createTime,
      size: f.size,
      contentType: f.contentType,
      raw: f,
      thumbnail: f.thumbnailPath || f.thumbnailUrl || null
    }))
    return [...folders, ...files]
  })

const currentFolderLabel = computed(() => {
  const last = breadcrumbs.value[breadcrumbs.value.length - 1]
  return last ? last.name : '根目录'
})

// 上传队列：使用组合函数接入
const {
  uploadQueue,
  hasRunningTasks,
  enqueueFiles,
  pauseTask,
  resumeTask,
  cancelTask,
  retryTask,
  attachFileToTask
} = useUploadQueue({
  getCurrentFolderId: () => currentFolderId.value,
  onTaskCompleted: () => {
    // 单个任务完成后刷新当前目录
    loadEntries()
  }
})

watch(hasRunningTasks, (val) => {
  if (val) {
    uploadDrawerVisible.value = true
  }
})

const hasRestorableTasks = computed(() =>
  uploadQueue.value.some(t => !t.file && t.status === 'paused')
)

const STORAGE_KEY_WIDTHS = 'efm_explorer_column_widths_v1'

const loadColumnWidths = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY_WIDTHS)
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object') {
      columnWidths.value = { ...columnWidths.value, ...parsed }
    }
  } catch (e) {
    // ignore
  }
}

const saveColumnWidths = () => {
  try {
    localStorage.setItem(STORAGE_KEY_WIDTHS, JSON.stringify(columnWidths.value))
  } catch (e) {
    // ignore
  }
}

// 选择列状态
const selectedRows = ref([])
const allChecked = ref(false)
const isIndeterminate = ref(false)

const onSelectionChange = (sel) => {
  selectedRows.value = sel
  const total = entries.value.length
  allChecked.value = total > 0 && sel.length === total
  isIndeterminate.value = sel.length > 0 && sel.length < total
}

const onToggleAll = (val) => {
  const refTable = tableRef.value
  if (!refTable) return
  if (val) {
    entries.value.forEach(row => refTable.toggleRowSelection(row, true))
  } else {
    refTable.clearSelection()
  }
}

const onHeaderDragEnd = (newWidth, oldWidth, column, event) => {
  if (!column || !column.columnKey) return
  switch (column.columnKey) {
    case 'col-name':
      columnWidths.value.name = newWidth
      break
    case 'col-size':
      columnWidths.value.size = newWidth
      break
    case 'col-type':
      columnWidths.value.type = newWidth
      break
    case 'col-create':
      columnWidths.value.createTime = newWidth
      break
    case 'col-actions':
      columnWidths.value.actions = newWidth
      break
    default:
      break
  }
  saveColumnWidths()
}

const formatFileSize = (bytes) => {
  const n = Number(bytes)
  if (!n || n <= 0) return ''
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(n) / Math.log(k))
  return `${parseFloat((n / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

const formatDateTime = (datetime) => {
  if (!datetime) return ''
  const d = new Date(datetime)
  if (isNaN(d.getTime())) return ''
  return d.toLocaleString()
}

const getExtension = (row) => {
  const name = (row.name || '').toString()
  const idx = name.lastIndexOf('.')
  if (idx < 0) return ''
  return name.slice(idx + 1).toLowerCase()
}

const formatFileType = (row) => {
  if (row.type === 'folder') return '文件夹'
  const ext = getExtension(row)
  if (!ext) return '文件'
  return ext.toUpperCase()
}

const getThumbnail = (row) => {
  if (row.type === 'folder') return ''
  return row.thumbnail || ''
}

const previewImage = async (file) => {
  try {
    const resp = await previewFileApi(file.id)
    const blob = resp?.data
    if (!(blob instanceof Blob)) {
      throw new Error('预览数据无效')
    }
    const url = window.URL.createObjectURL(blob)
    imagePreviewUrl.value = url
    imageScale.value = 1
    imagePreviewVisible.value = true
  } catch (e) {
    console.error('图片预览失败:', e)
    ElMessage.error('图片预览失败')
  }
}

const onImageWheel = (event) => {
  const delta = event.deltaY || 0
  let next = imageScale.value
  if (delta > 0) {
    next -= 0.1
  } else if (delta < 0) {
    next += 0.1
  }
  if (next < 0.5) next = 0.5
  if (next > 4) next = 4
  imageScale.value = next
}

const onImagePreviewClosed = () => {
  imageScale.value = 1
  if (imagePreviewUrl.value) {
    try { window.URL.revokeObjectURL(imagePreviewUrl.value) } catch (e) {}
  }
  imagePreviewUrl.value = ''
}

const loadEntries = async () => {
  loading.value = true
  try {
    const [folders, files] = await Promise.all([
      getFolderList({ parentId: currentFolderId.value || null }),
      getFileList({ folderId: currentFolderId.value || null })
    ])
    folderList.value = folders || []
    fileList.value = files || []
  } catch (e) {
    console.error('加载文件/文件夹列表失败:', e)
    ElMessage.error('加载文件/文件夹列表失败')
  } finally {
    loading.value = false
  }
}

const loadBreadcrumb = async () => {
  if (!currentFolderId.value) {
    breadcrumbs.value = [{ id: null, name: '根目录' }]
    return
  }
  try {
    const path = await getFolderPath(currentFolderId.value)
    const list = [{ id: null, name: '根目录' }]
    if (Array.isArray(path)) {
      path.forEach(f => {
        list.push({ id: f.id, name: f.name })
      })
    }
    breadcrumbs.value = list
  } catch (e) {
    console.error('加载目录路径失败:', e)
    breadcrumbs.value = [{ id: null, name: '根目录' }]
  }
}

  const refreshAll = async () => {
    await loadBreadcrumb()
    await loadEntries()
  }

const clearSelection = () => {
  const refTable = tableRef.value
  if (refTable) {
    refTable.clearSelection()
  }
  selectedRows.value = []
  allChecked.value = false
  isIndeterminate.value = false
}

  const onBreadcrumbClick = (item) => {
    if (item.id === currentFolderId.value) return
    currentFolderId.value = item.id
    clearSelection()
  clearSelection()
  refreshAll()
}

  const enterFolder = (folder) => {
    currentFolderId.value = folder.id
    clearSelection()
    refreshAll()
  }

const onEntryClick = (row) => {
  if (row.type === 'folder') {
    enterFolder(row.raw)
  } else if (row.type === 'file') {
    previewFileEntry(row.raw)
  }
}

const onEntryDblClick = (row) => {
  if (row.type === 'folder') {
    enterFolder(row.raw)
  } else if (row.type === 'file') {
    previewFileEntry(row.raw)
  }
}

const openNewFolderDialog = () => {
  newFolderName.value = ''
  newFolderDialogVisible.value = true
}

const confirmCreateFolder = async () => {
  const name = newFolderName.value.trim()
  if (!name) {
    ElMessage.warning('文件夹名称不能为空')
    return
  }
  try {
    await createFolder({
      name,
      parentId: currentFolderId.value || null
    })
    ElMessage.success('文件夹创建成功')
    newFolderDialogVisible.value = false
    await loadEntries()
  } catch (e) {
    console.error('创建文件夹失败:', e)
    ElMessage.error('创建文件夹失败')
  }
}

const renameFolderEntry = async (folder) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的文件夹名称',
      '重命名文件夹',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: folder.name,
        inputPattern: /.+/,
        inputErrorMessage: '名称不能为空'
      }
    )
    await renameFolderApi(folder.id, value)
    ElMessage.success('文件夹重命名成功')
    await loadEntries()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('文件夹重命名失败:', e)
      ElMessage.error('文件夹重命名失败')
    }
  }
}

const deleteFolderEntry = async (folder) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件夹 "${folder.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteFolderApi(folder.id)
    ElMessage.success('文件夹删除成功')
    await loadEntries()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('文件夹删除失败:', e)
      ElMessage.error('文件夹删除失败')
    }
  }
}

const renameFileEntry = async (file) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的文件名称',
      '重命名文件',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: file.originalFilename || file.name || '',
        inputPattern: /.+/,
        inputErrorMessage: '文件名不能为空'
      }
    )
    await renameFileApi(file.id, value)
    ElMessage.success('文件重命名成功')
    await loadEntries()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('文件重命名失败:', e)
      ElMessage.error('文件重命名失败')
    }
  }
}

const deleteFileEntry = async (file) => {
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
    await loadEntries()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('文件删除失败:', e)
      ElMessage.error('文件删除失败')
    }
  }
}

const downloadFileEntry = async (file) => {
  try {
    const res = await getDownloadUrl(file.id)
    const url = res && res.url
    if (!url) throw new Error('下载链接为空')
    const a = document.createElement('a')
    a.href = url
    a.download = file.originalFilename || file.name || 'download'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } catch (e) {
    console.error('文件下载失败:', e)
    ElMessage.error('文件下载失败')
  }
}

const previewFileEntry = (file) => {
  const ext = getExtension({ name: file.originalFilename || file.name || '' })
  if (['jpg', 'jpeg', 'png'].includes(ext)) {
    previewImage(file)
    return
  }
  router.push({
    name: 'FilePreviewNew',
    params: { id: file.id },
    query: {
      name: file.originalFilename || file.name || '',
      size: file.size || '',
      createTime: file.createTime || '',
      updateTime: file.updateTime || '',
      location: file.folderPath || ''
    }
  })
}

const searchEntries = async () => {
  // 第一版：仅提示“暂未实现目录范围搜索”，避免影响现有逻辑
  if (!searchKeyword.value.trim()) {
    await loadEntries()
    return
  }
  ElMessage.info('目录范围内的搜索功能暂未实现，后续迭代中支持')
}

const openUploadDialog = () => {
  uploadDrawerVisible.value = true
}

const openFilePicker = () => {
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

const onFileInputChange = (e) => {
  const files = e?.target?.files
  if (!files || !files.length) return
  if (resumeTaskId.value) {
    const taskId = resumeTaskId.value
    resumeTaskId.value = null
    attachFileToTask(taskId, files[0])
  } else {
    enqueueFiles(files, currentFolderId.value || null)
  }
}

const onDragOver = (e) => {
  e.dataTransfer.dropEffect = 'copy'
}

const onDropFiles = (e) => {
  const files = e?.dataTransfer?.files
  if (!files || !files.length) return
  enqueueFiles(files, currentFolderId.value || null)
}

const onSelectFileForTask = (taskId) => {
  resumeTaskId.value = taskId
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

onMounted(() => {
  loadColumnWidths()
  refreshAll()
  if (hasRestorableTasks.value) {
    uploadDrawerVisible.value = true
    ElMessage.info('检测到上次会话有未完成的上传任务，请为对应任务选择文件继续上传')
  }
})

watch(entries, () => {
  clearSelection()
})
</script>

<style scoped>
.files-explorer-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
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
  margin-bottom: 16px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.breadcrumb-item-clickable {
  cursor: pointer;
}

.entries-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.entries-list :deep(.el-table__row) {
  height: 40px;
}

.entries-list :deep(.el-table__body-wrapper) {
  overflow-y: auto;
  max-height: 100%;
}

.entries-list :deep(.el-table__cell) {
  padding: 6px 8px;
  line-height: 20px;
}

.entry-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.entry-icon {
  font-size: 20px;
  color: #909399;
}

.entry-name {
  font-weight: 500;
  color: #303133;
  cursor: pointer;
  padding-left: 2px;
}

.entry-thumb {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.entry-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

:deep(.el-table__row:hover) .entry-name {
  text-decoration: underline;
}

.op-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.op-btn {
  padding: 6px 8px;
}

.op-btn :deep(.el-icon) {
  font-size: 18px;
}

.op-btn-plain {
  padding: 4px 6px;
  border-radius: 4px;
}

.op-btn-plain :deep(.el-icon) {
  font-size: 14px;
}

.image-preview-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.image-preview-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 80vh;
  overflow: auto;
}

.image-preview-img {
  max-width: 100%;
  max-height: 80vh;
  min-width: 80vh;
  min-height: 70vh;
  display: block;
  margin: 0 auto;
  object-fit: contain;
}
</style>
