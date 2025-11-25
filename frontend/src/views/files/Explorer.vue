<template>
  <div class="files-explorer-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left" v-if="!hasSelection">
        <el-button @click="openUploadDialog">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        <el-button type="primary" @click="openNewFolderDialog">
          <el-icon><Folder /></el-icon>
          新建文件夹
        </el-button>
      </div>
      <div class="toolbar-left" v-else>
        <el-button type="primary" @click="openMoveCopyDialogForSelection('move')">移动</el-button>
        <el-button @click="openMoveCopyDialogForSelection('copy')">复制</el-button>
        <el-button type="danger" plain @click="deleteSelected">删除</el-button>
        <el-button @click="downloadSelected">下载</el-button>
        <el-dropdown>
          <el-button>
            更多
            <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :disabled="selectedRows.length !== 1" @click="renameSelected">
                重命名
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
    <input
      ref="uploadDialogFileInputRef"
      type="file"
      multiple
      webkitdirectory
      style="display: none;"
      @change="onUploadDialogFileChange"
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
                <el-icon v-else class="entry-icon" :size="24" :color="getFileIconConfig(row.name, row.type === 'folder').color">
                  <component :is="getFileIconConfig(row.name, row.type === 'folder').name" />
                </el-icon>
              </div>
              <div class="entry-name-wrapper">
                <span
                  class="entry-name"
                  :class="{ clickable: row.type === 'folder' || row.type === 'file' }"
                  @click.stop="onEntryClick(row)"
                >
                  {{ row.name }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="size" label="大小" :width="columnWidths.size" column-key="col-size" align="center">
          <template #default="{ row }">
            <span v-if="row.type === 'file'">
              {{ formatFileSize(row.size) }}
            </span>
            <span v-else>--</span>
          </template>
        </el-table-column>

        <el-table-column prop="type" label="类型" :width="columnWidths.type" column-key="col-type" align="center">
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
                <el-button size="small" class="op-btn" @click="deleteFolderEntry(row.raw)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
              <el-dropdown trigger="click" placement="bottom">
                <el-button size="small" class="op-btn op-btn-plain" plain>
                  <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openMoveCopyDialog('move', row.raw, 'folder')">移动</el-dropdown-item>
                    <el-dropdown-item @click="openMoveCopyDialog('copy', row.raw, 'folder')">复制</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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
                <el-button size="small" class="op-btn" @click="deleteFileEntry(row.raw)">
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
                    <el-dropdown-item @click="goHistoryPage(row.raw)">历史</el-dropdown-item>
                    <el-dropdown-item @click="openMoveCopyDialog('move', row.raw, 'file')">移动</el-dropdown-item>
                    <el-dropdown-item @click="openMoveCopyDialog('copy', row.raw, 'file')">复制</el-dropdown-item>
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
      width="60%"
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

    <!-- 上传弹窗：拖拽/选择文件或文件夹，任务在底部队列展示 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="600px" @closed="onUploadDialogClosed">
      <div class="upload-dialog-body">
        <div
          class="upload-dropzone"
          :class="{ 'is-dragover': uploadDialogDragover }"
          @dragover.prevent="onUploadDialogDragOver"
          @dragleave.prevent="onUploadDialogDragLeave"
          @drop.prevent="onUploadDialogDrop"
        >
          <el-icon class="drop-icon"><Upload /></el-icon>
          <p class="drop-title">拖拽文件或文件夹到此处</p>
          <p class="drop-sub">支持多文件/文件夹递归，拖入后自动加入上传队列</p>
          <el-button type="primary" plain @click="openUploadDialogPicker">
            选择文件/文件夹
          </el-button>
        </div>
        <div class="upload-dialog-hint">
          已加入的任务会出现在下方的上传队列抽屉中，可随时查看进度
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="moveCopyDialogVisible"
      :title="moveCopyTitle"
      width="520px"
      class="move-copy-dialog"
      @close="closeMoveCopyDialog"
    >
      <div class="move-copy-body">
        <el-tree
          v-loading="folderTreeLoading"
          :data="folderTreeData"
          node-key="id"
          highlight-current
          :expand-on-click-node="false"
          lazy
          :load="loadFolderChildren"
          :default-expanded-keys="expandedKeys"
          @node-click="onFolderTreeNodeClick"
        >
          <template #default="{ data }">
            <span :class="['folder-node', { active: moveCopyTargetFolderId === data.id }]">{{ data.label }}</span>
          </template>
        </el-tree>
        <el-form label-width="100px" class="move-copy-form">
          <el-form-item v-if="moveCopyMode === 'single'" label="新名称">
            <el-input v-model="moveCopyTargetName" placeholder="不填则沿用原名" />
          </el-form-item>
        </el-form>
        <div v-if="moveCopyError" class="move-copy-error">{{ moveCopyError }}</div>
      </div>
      <template #footer>
        <el-button @click="closeMoveCopyDialog">取消</el-button>
        <el-button type="primary" :loading="moveCopyLoading" @click="handleMoveCopySubmit">提交</el-button>
      </template>
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
import { Folder, FolderOpened, Upload, Search, Document, Download, Delete, Edit, View, ArrowDown, Close, Cpu, Monitor, Setting, Warning } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getFileList, deleteFile as deleteFileApi, renameFile as renameFileApi, getDownloadUrl, previewFile as previewFileApi, moveFile as moveFileApi, copyFile as copyFileApi } from '@/api/file'
import { getFolderList, createFolder, deleteFolder as deleteFolderApi, renameFolder as renameFolderApi, getFolderPath, moveFolder as moveFolderApi, copyFolder as copyFolderApi } from '@/api/folder'
import { useUploadQueue } from '@/composables/useUploadQueue'
import { getFileIconConfig } from '@/utils/file-icons'

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
const filesCache = ref(new Map())
const searchKeyword = ref('')
const newFolderDialogVisible = ref(false)
const newFolderName = ref('')
const uploadDrawerVisible = ref(false)
const fileInputRef = ref(null)
const uploadDialogFileInputRef = ref(null)
const resumeTaskId = ref(null)
const imagePreviewVisible = ref(false)
const imagePreviewUrl = ref('')
const imageScale = ref(1)
const moveCopyDialogVisible = ref(false)
const moveCopyTargetFolderId = ref(null)
const moveCopyTargetName = ref('')
const moveCopyLoading = ref(false)
const moveCopyError = ref('')
const moveCopyType = ref('copy') // 'copy' | 'move'
const moveCopyItems = ref([]) // [{ type: 'file' | 'folder', raw }]
const moveCopyMode = ref('single') // 'single' | 'batch'
const folderTreeData = ref([])
const folderTreeLoading = ref(false)
const expandedKeys = ref([])
const folderCache = ref(new Map())
const folderLoading = ref(new Map()) // parentId -> Promise
const uploadDialogVisible = ref(false)
const uploadDialogDragover = ref(false)

const getFolderCacheKey = (parentId) => (parentId === null || parentId === undefined ? 'root' : parentId)
const getFilesCacheKey = (folderId) => (folderId === null || folderId === undefined ? 'root' : String(folderId))

const findExistingFileId = (filename, folderId) => {
  const key = getFilesCacheKey(folderId)
  const list = filesCache.value.get(key) || (key === getFilesCacheKey(currentFolderId.value) ? fileList.value : [])
  if (!list || !list.length) return null
  const matched = list.find((f) => (f?.originalFilename || f?.name || f?.filename) === filename)
  return matched ? matched.id : null
}

const fetchFolderList = async (parentId, options = {}) => {
  const { force = false, cacheResult = true } = options
  const key = getFolderCacheKey(parentId)
  if (!force && folderCache.value.has(key)) {
    return folderCache.value.get(key)
  }
  if (folderLoading.value.has(key)) {
    return folderLoading.value.get(key)
  }
  const promise = getFolderList({ parentId: parentId ?? null })
    .then((res) => {
      const list = Array.isArray(res) ? res : []
      if (cacheResult) {
        folderCache.value.set(key, list)
      }
      folderLoading.value.delete(key)
      return list
    })
    .catch((err) => {
      folderLoading.value.delete(key)
      throw err
    })
  folderLoading.value.set(key, promise)
  return promise
}

const invalidateFolderCache = (parentId) => {
  const key = getFolderCacheKey(parentId)
  folderCache.value.delete(key)
}

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
  },
  resolveParentId: (name, folderId) => findExistingFileId(name, folderId)
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

const moveCopyTitle = computed(() => {
  const actionText = moveCopyType.value === 'copy' ? '复制' : '移动'
  if (!moveCopyItems.value.length) {
    return `${actionText}到`
  }
  if (moveCopyMode.value === 'batch' && moveCopyItems.value.length > 1) {
    return `${actionText} ${moveCopyItems.value.length} 项到`
  }
  const current = moveCopyItems.value[0]
  const name =
    current?.raw?.originalFilename ||
    current?.raw?.name ||
    current?.raw?.filename ||
    ''
  return `${actionText} ${name} 到`
})

// 选择列状态
const selectedRows = ref([])
const allChecked = ref(false)
const isIndeterminate = ref(false)
const hasSelection = computed(() => selectedRows.value.length > 0)
const singleSelection = computed(() => (selectedRows.value.length === 1 ? selectedRows.value[0] : null))

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
  const parentId = currentFolderId.value || null
  try {
    const [folders, files] = await Promise.all([
      fetchFolderList(parentId, { force: true }),
      getFileList({ folderId: parentId })
    ])
    folderList.value = dedupeFolders(folders || [])
    fileList.value = files || []
    filesCache.value.set(getFilesCacheKey(parentId), files || [])
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
    invalidateFolderCache(currentFolderId.value || null)
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
    invalidateFolderCache(currentFolderId.value || null)
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
    invalidateFolderCache(currentFolderId.value || null)
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
  uploadDialogVisible.value = true
}

const closeUploadDialog = () => {
  uploadDialogVisible.value = false
  uploadDialogDragover.value = false
}

const openUploadDialogPicker = () => {
  if (uploadDialogFileInputRef.value) {
    uploadDialogFileInputRef.value.value = ''
    uploadDialogFileInputRef.value.click()
  }
}

const onUploadDialogClosed = () => {
  uploadDialogDragover.value = false
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
  handleDataTransfer(e?.dataTransfer, true)
}

const onSelectFileForTask = (taskId) => {
  resumeTaskId.value = taskId
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
    fileInputRef.value.click()
  }
}

const goHistoryPage = (file) => {
  if (!file?.id) return
  router.push({
    name: 'FileHistory',
    params: { id: file.id },
    query: { name: file.originalFilename || file.name || '' }
  })
}

const handleDataTransfer = async (dataTransfer, closeDialogAfter = false) => {
  try {
    const files = await extractFilesFromDataTransfer(dataTransfer)
    if (!files.length) {
      ElMessage.warning('未检测到可上传的文件')
      return
    }
    enqueueFiles(files, currentFolderId.value || null)
    uploadDrawerVisible.value = true
    if (closeDialogAfter) {
      closeUploadDialog()
    }
  } catch (err) {
    console.error('处理拖拽/选择文件失败:', err)
    ElMessage.error('处理文件失败，请重试')
  }
}

const onUploadDialogDrop = async (e) => {
  uploadDialogDragover.value = false
  await handleDataTransfer(e?.dataTransfer, true)
}

const onUploadDialogDragOver = () => {
  uploadDialogDragover.value = true
}

const onUploadDialogDragLeave = () => {
  uploadDialogDragover.value = false
}

const onUploadDialogFileChange = async (e) => {
  const files = e?.target?.files
  if (!files || !files.length) return
  enqueueFiles(files, currentFolderId.value || null)
  uploadDrawerVisible.value = true
  closeUploadDialog()
}

const extractFilesFromDataTransfer = async (dataTransfer) => {
  const result = []
  if (!dataTransfer) return result
  const items = dataTransfer.items
  if (items && items.length) {
    const tasks = []
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      const entry = item.webkitGetAsEntry ? item.webkitGetAsEntry() : null
      if (entry) {
        tasks.push(readEntryRecursive(entry, result))
      } else {
        const f = item.getAsFile ? item.getAsFile() : null
        if (f) result.push(f)
      }
    }
    await Promise.all(tasks)
  } else if (dataTransfer.files && dataTransfer.files.length) {
    for (let i = 0; i < dataTransfer.files.length; i++) {
      result.push(dataTransfer.files[i])
    }
  }
  return result
}

const readEntryRecursive = (entry, collector) => {
  return new Promise((resolve, reject) => {
    if (entry.isFile) {
      entry.file(
        (file) => {
          collector.push(file)
          resolve()
        },
        (err) => reject(err)
      )
    } else if (entry.isDirectory) {
      const reader = entry.createReader()
      const readEntries = () => {
        reader.readEntries(async (entries) => {
          if (!entries.length) return resolve()
          try {
            await Promise.all(entries.map((ent) => readEntryRecursive(ent, collector)))
            readEntries()
          } catch (e) {
            reject(e)
          }
        })
      }
      readEntries()
    } else {
      resolve()
    }
  })
}

const buildMoveCopyItem = (item, itemType) => {
  if (!item) return null
  return {
    type: itemType || item.type || '',
    raw: item.raw || item
  }
}

  const loadFolderTreeRoot = async () => {
  folderTreeLoading.value = true
  folderTreeData.value = []
  expandedKeys.value = []
  try {
    const roots = await fetchFolderList(null)
    const mapped = dedupeFolders(roots || []).map(f => ({
      id: f.id,
      label: f.name,
      children: [],
      hasChildren: true
    }))
    folderTreeData.value = mapped
  } catch (e) {
    console.error('加载根目录失败', e)
    ElMessage.error('加载目录失败')
  } finally {
    folderTreeLoading.value = false
  }
}

const loadFolderChildren = async (node, resolve) => {
  const data = node?.data
  if (!data || !data.id) {
    resolve([])
    return
  }
  try {
    const children = await fetchFolderList(data.id)
    const mapped = dedupeFolders(children || []).map(f => ({
      id: f.id,
      label: f.name,
      children: [],
      hasChildren: true
    }))
    resolve(mapped)
  } catch (e) {
    console.error('加载子目录失败', e)
    resolve([])
  }
}

const openMoveCopyDialog = (type, item, itemType) => {
  const normalized = buildMoveCopyItem(item, itemType)
  if (!normalized) return
  moveCopyType.value = type
  moveCopyMode.value = 'single'
  moveCopyItems.value = [normalized]
  moveCopyTargetFolderId.value = currentFolderId.value
  moveCopyTargetName.value = normalized.raw?.originalFilename || normalized.raw?.name || normalized.raw?.filename || ''
  moveCopyError.value = ''
  moveCopyDialogVisible.value = true
  loadFolderTreeRoot()
}

const openMoveCopyDialogForSelection = (type) => {
  if (!hasSelection.value) {
    ElMessage.warning('请先选择文件或文件夹')
    return
  }
  moveCopyType.value = type
  moveCopyMode.value = selectedRows.value.length > 1 ? 'batch' : 'single'
  moveCopyItems.value = selectedRows.value.map(item => ({
    type: item.type,
    raw: item.raw
  }))
  moveCopyTargetFolderId.value = currentFolderId.value
  moveCopyTargetName.value = moveCopyMode.value === 'single'
    ? (moveCopyItems.value[0]?.raw?.originalFilename || moveCopyItems.value[0]?.raw?.name || moveCopyItems.value[0]?.raw?.filename || '')
    : ''
  moveCopyError.value = ''
  moveCopyDialogVisible.value = true
  loadFolderTreeRoot()
}

const closeMoveCopyDialog = () => {
  moveCopyDialogVisible.value = false
  moveCopyItems.value = []
  moveCopyMode.value = 'single'
  moveCopyError.value = ''
}

const deleteSelected = async () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择要删除的条目')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRows.value.length} 个条目吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    for (const item of selectedRows.value) {
      if (!item || !item.raw) continue
      if (item.type === 'file') {
        await deleteFileApi(item.raw.id)
      } else {
        await deleteFolderApi(item.raw.id)
      }
    }
    const affectedParents = new Set()
    affectedParents.add(currentFolderId.value || null)
    selectedRows.value.forEach(item => {
      if (!item || !item.raw) return
      if (item.type === 'folder') {
        affectedParents.add(item.raw.parentId ?? null)
      } else {
        affectedParents.add(item.raw.folderId ?? null)
      }
    })
    affectedParents.forEach(pid => invalidateFolderCache(pid ?? null))
    ElMessage.success('删除成功')
    clearSelection()
    refreshAll()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('批量删除失败:', e)
      ElMessage.error('删除失败')
    }
  }
}

const downloadSelected = async () => {
  const files = selectedRows.value.filter(item => item.type === 'file')
  if (!files.length) {
    ElMessage.warning('请选择需要下载的文件')
    return
  }
  if (files.length !== selectedRows.value.length) {
    ElMessage.info('已忽略选中的文件夹，当前仅支持批量下载文件')
  }
  for (const item of files) {
    await downloadFileEntry(item.raw)
  }
}

const renameSelected = async () => {
  if (!singleSelection.value) {
    ElMessage.warning('请选择单个条目进行重命名')
    return
  }
  const item = singleSelection.value
  if (item.type === 'file') {
    await renameFileEntry(item.raw)
  } else {
    await renameFolderEntry(item.raw)
  }
  clearSelection()
}

const onFolderTreeNodeClick = (node) => {
  moveCopyTargetFolderId.value = node.id
}

const handleMoveCopySubmit = async () => {
  if (!moveCopyItems.value.length) return
  moveCopyLoading.value = true
  moveCopyError.value = ''
  const targetName = moveCopyMode.value === 'single'
    ? (moveCopyTargetName.value ? moveCopyTargetName.value.trim() : '')
    : ''
  try {
    for (const item of moveCopyItems.value) {
      if (!item || !item.raw) continue
      const isFile = item.type === 'file'
      if (moveCopyType.value === 'copy') {
        if (isFile) {
          await copyFileApi(item.raw.id, moveCopyTargetFolderId.value || null, targetName)
        } else {
          await copyFolderApi(item.raw.id, moveCopyTargetFolderId.value || null, targetName)
        }
      } else {
        if (isFile) {
          await moveFileApi(item.raw.id, moveCopyTargetFolderId.value || null, targetName)
        } else {
          await moveFolderApi(item.raw.id, moveCopyTargetFolderId.value || null, targetName)
        }
      }
    }
    const affectedParents = new Set()
    affectedParents.add(currentFolderId.value || null)
    affectedParents.add(moveCopyTargetFolderId.value || null)
    moveCopyItems.value.forEach(item => {
      if (!item || !item.raw) return
      if (item.type === 'folder') {
        affectedParents.add(item.raw.parentId ?? null)
      } else {
        affectedParents.add(item.raw.folderId ?? null)
      }
    })
    affectedParents.forEach(pid => invalidateFolderCache(pid ?? null))
    ElMessage.success(moveCopyType.value === 'copy' ? '复制成功' : '移动成功')
    closeMoveCopyDialog()
    refreshAll()
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '操作失败'
    moveCopyError.value = msg
    if (moveCopyMode.value === 'single' && msg.includes('同名')) {
      ElMessageBox.prompt(
        '目标目录已存在同名，是否输入新名称？',
        '重命名后重试',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputValue: moveCopyTargetName.value || '',
          inputPattern: /.+/,
          inputErrorMessage: '名称不能为空'
        }
      ).then(({ value }) => {
        moveCopyTargetName.value = value
        handleMoveCopySubmit()
      }).catch(() => {})
    } else {
      ElMessage.error(msg)
    }
  } finally {
    moveCopyLoading.value = false
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

watch(moveCopyDialogVisible, (val) => {
  if (!val) {
    moveCopyError.value = ''
    moveCopyTargetName.value = ''
    moveCopyTargetFolderId.value = currentFolderId.value
    moveCopyItems.value = []
    moveCopyMode.value = 'single'
  }
})

const dedupeFolders = (arr) => {
  const map = new Map()
  ;(arr || []).forEach(f => {
    if (!f || !f.id) return
    if (!map.has(f.id)) {
      map.set(f.id, f)
    }
  })
  return Array.from(map.values())
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.files-explorer-container {
  padding: $spacing-base;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  background-color: $background-color-base;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-base;
  padding: 12px 24px;
  background: white;
  border-radius: $border-radius-base;
  box-shadow: $box-shadow-sm;
  border: 1px solid $border-color-light;
}

.toolbar-left {
  display: flex;
  gap: $spacing-sm;
}

.toolbar-right {
  display: flex;
  gap: $spacing-sm;
  align-items: center;
}

.breadcrumb {
  margin-bottom: $spacing-base;
  padding: 12px 24px;
  background: white;
  border-radius: $border-radius-base;
  box-shadow: $box-shadow-sm;
  border: 1px solid $border-color-light;
  display: flex;
  align-items: center;
}

.breadcrumb-item-clickable {
  cursor: pointer;
  transition: color $transition-fast;
  
  &:hover {
    color: $primary-color;
  }
}

.entries-list {
  flex: 1;
  background: white;
  border-radius: $border-radius-base;
  box-shadow: $box-shadow-sm;
  border: 1px solid $border-color-light;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* Table Styles Refined */
.entries-list :deep(.el-table) {
  --el-table-header-bg-color: #f1f5f9;
  --el-table-header-text-color: #475569;
  --el-table-row-hover-bg-color: #f8fafc;
  --el-table-border-color: #e2e8f0;
  border-radius: 0 0 8px 8px;

  th.el-table__cell {
    font-weight: 600;
    height: 50px;
    background-color: #f1f5f9 !important;
    color: #334155;
    border-bottom: 1px solid #e2e8f0;
  }
  
  .el-table__row {
    height: 60px;
    transition: all 0.2s ease;
  }
  
  .el-table__cell {
    padding: 8px 0;
  }

  .el-table__row:hover > td {
    background-color: #f0f9ff !important; /* Light Blue-50 */
  }
}

.entry-info {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-left: 8px;
}

.entry-thumb {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s;
  
  &:hover {
    transform: scale(1.05);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  
  .entry-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.entry-name-wrapper {
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  flex: 1;
}

.entry-name {
  font-weight: 500;
  color: #1e293b;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  transition: color 0.2s;
  
  &.clickable:hover {
    color: $primary-color;
    text-decoration: none;
  }
}

.op-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.op-btn {
  padding: 0;
  height: 36px;
  width: 36px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 6px;
  transition: all 0.2s;
  font-size: 18px; /* Larger icon size */
  display: flex;
  align-items: center;
  justify-content: center;
  
  &:hover {
    background: #e0f2fe; /* Sky-100 */
    color: $primary-color;
    border-color: #bae6fd;
  }
  
  &.el-button--danger:hover {
    color: #ef4444;
    background: #fee2e2;
    border-color: #fecaca;
  }
  
  &.op-btn-plain:hover {
    background: #f1f5f9;
    color: #475569;
    border-color: #cbd5e1;
  }
}

/* Dialogs */
.image-preview-dialog :deep(.el-dialog__body) {
  padding: 0;
  background: #000;
}

.image-preview-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 70vh;
  overflow: hidden;
}

.image-preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  transition: transform 0.1s;
}

.move-copy-body {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid $border-color-light;
  border-radius: $border-radius-sm;
  padding: $spacing-sm;
}

.upload-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.upload-dropzone {
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 28px;
  text-align: center;
  background: #f8fafc;
  transition: all 0.2s ease;
}

.upload-dropzone.is-dragover {
  border-color: #409eff;
  background: #e8f4ff;
}

.upload-dropzone .drop-icon {
  font-size: 40px;
  color: #409eff;
  margin-bottom: 8px;
}

.upload-dropzone .drop-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: #1e293b;
}

.upload-dropzone .drop-sub {
  font-size: 13px;
  color: #64748b;
  margin: 6px 0 14px 0;
}

.upload-dialog-hint {
  font-size: 12px;
  color: #94a3b8;
}
</style>
