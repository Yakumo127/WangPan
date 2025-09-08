<template>
  <div class="folders-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><FolderOpened /></el-icon>
          新建文件夹
        </el-button>
        <el-button @click="refreshFolders" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件夹..."
          style="width: 200px"
          clearable
          @keyup.enter="searchFolders"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchFolders">搜索</el-button>
      </div>
    </div>

    <!-- 面包屑导航 -->
    <div class="breadcrumb" v-if="breadcrumb.length > 0">
      <el-breadcrumb>
        <el-breadcrumb-item>
          <a @click="navigateToFolder(null)">根目录</a>
        </el-breadcrumb-item>
        <el-breadcrumb-item v-for="folder in breadcrumb" :key="folder.id">
          <a @click="navigateToFolder(folder.id)">{{ folder.name }}</a>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 文件夹列表 -->
    <div class="folder-list">
      <el-table
        :data="folders"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column label="文件夹名称" min-width="300">
          <template #default="{ row }">
            <div class="folder-info">
              <el-icon class="folder-icon">
                <Folder />
              </el-icon>
              <span class="folder-name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" @click="openFolder(row)">
                <el-icon><FolderOpened /></el-icon>
                打开
              </el-button>
              <el-button size="small" @click="renameFolder(row)">
                <el-icon><Edit /></el-icon>
                重命名
              </el-button>
              <el-button size="small" type="danger" @click="deleteFolder(row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建文件夹" width="400px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="文件夹名称">
          <el-input 
            v-model="createForm.name" 
            placeholder="请输入文件夹名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createFolderFunc" :loading="creating">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Refresh, Search, Folder, Edit, Delete } from '@element-plus/icons-vue'
import { getFolderList, createFolder, deleteFolder as deleteFolderApi, renameFolder as renameFolderApi, getFolderPath } from '@/api/folder'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const folders = ref([])
const searchKeyword = ref('')
const showCreateDialog = ref(false)
const currentFolderId = ref(null)
const breadcrumb = ref([])

const createForm = reactive({
  name: ''
})

// 格式化日期时间
const formatDateTime = (datetime) => {
  if (!datetime) return ''
  return new Date(datetime).toLocaleString()
}

// 加载文件夹列表
const loadFolders = async () => {
  loading.value = true
  try {
    const response = await getFolderList({ parentId: currentFolderId.value })
    folders.value = response || []
    
    // 更新面包屑
    if (currentFolderId.value) {
      const pathResponse = await getFolderPath(currentFolderId.value)
      breadcrumb.value = pathResponse || []
    } else {
      breadcrumb.value = []
    }
  } catch (error) {
    ElMessage.error('加载文件夹列表失败')
  } finally {
    loading.value = false
  }
}

// 刷新文件夹列表
const refreshFolders = () => {
  loadFolders()
}

// 搜索文件夹
const searchFolders = () => {
  ElMessage.info('文件夹搜索功能开发中...')
}

// 创建文件夹
const createFolderFunc = async () => {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }

  creating.value = true
  try {
    await createFolder({
      name: createForm.name,
      parentId: currentFolderId.value
    })
    
    ElMessage.success('文件夹创建成功')
    showCreateDialog.value = false
    createForm.name = ''
    await loadFolders()
  } catch (error) {
    ElMessage.error('文件夹创建失败')
  } finally {
    creating.value = false
  }
}

// 打开文件夹
const openFolder = (folder) => {
  currentFolderId.value = folder.id
  loadFolders()
}

// 导航到文件夹
const navigateToFolder = (folderId) => {
  currentFolderId.value = folderId
  loadFolders()
}

// 重命名文件夹
const renameFolder = async (folder) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入文件夹的新名称`,
      '重命名文件夹',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: folder.name,
        inputPattern: /.+/,
        inputErrorMessage: '文件夹名称不能为空'
      }
    )
    
    await renameFolderApi(folder.id, value)
    ElMessage.success('文件夹重命名成功')
    await loadFolders()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('文件夹重命名失败')
    }
  }
}

// 删除文件夹
const deleteFolder = async (folder) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件夹 "${folder.name}" 吗？删除后无法恢复！`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteFolderApi(folder.id)
    ElMessage.success('文件夹删除成功')
    await loadFolders()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('文件夹删除失败')
    }
  }
}

onMounted(() => {
  loadFolders()
})
</script>

<style scoped>
.folders-container {
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

.folder-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
}

.folder-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.folder-icon {
  font-size: 18px;
  color: #E6A23C;
}

.folder-name {
  font-weight: 500;
}

:deep(.el-table) {
  height: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}
</style>
