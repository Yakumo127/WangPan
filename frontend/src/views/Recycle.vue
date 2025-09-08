<template>
  <div class="recycle-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="danger" @click="emptyRecycleBinAction" :loading="emptying">
          <el-icon><Delete /></el-icon>
          清空回收站
        </el-button>
        <el-button type="success" @click="restoreSelected" :disabled="!selectedItems.length" :loading="restoring">
          <el-icon><RefreshLeft /></el-icon>
          恢复选中
        </el-button>
        <el-button @click="refreshItems" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索回收站项目..."
          style="width: 200px"
          clearable
          @keyup.enter="searchItems"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchItems">搜索</el-button>
      </div>
    </div>

    <!-- 回收站统计 -->
    <div class="stats-row">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Delete /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ recycleStats.totalItems || 0 }}</div>
              <div class="stat-label">总项目数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ recycleStats.fileCount || 0 }}</div>
              <div class="stat-label">文件数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Folder /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ recycleStats.folderCount || 0 }}</div>
              <div class="stat-label">文件夹数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ formatStorage(recycleStats.totalSize || 0) }}</div>
              <div class="stat-label">总大小</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 回收站项目列表 -->
    <div class="recycle-list">
      <el-table
        :data="recycleItems"
        style="width: 100%"
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column label="项目信息" min-width="300">
          <template #default="{ row }">
            <div class="item-info">
              <el-icon class="item-icon">
                <Document />
              </el-icon>
              <div class="item-details">
                <div class="item-name">{{ row.originalFilename }}</div>
                <div class="item-meta">
                  <el-tag size="small" type="primary">
                    文件
                  </el-tag>
                  <span class="item-size">{{ formatFileSize(row.size) }}</span>
                  <span class="original-path">路径: {{ row.filePath }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="deleteTime" label="删除时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.deleteTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="文件类型" width="120" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" type="success" @click="restoreItem(row)">
                <el-icon><RefreshLeft /></el-icon>
                恢复
              </el-button>
              <el-button size="small" @click="viewItemDetails(row)">
                <el-icon><View /></el-icon>
                详情
              </el-button>
              <el-button size="small" type="danger" @click="deletePermanently(row)">
                <el-icon><Delete /></el-icon>
                彻底删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 项目详情对话框 -->
    <el-dialog v-model="showDetailsDialog" title="项目详情" width="600px">
      <div v-if="selectedItem" class="item-details-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件名称">{{ selectedItem.originalFilename }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">
            <el-tag type="primary">
              文件
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatFileSize(selectedItem.size) }}</el-descriptions-item>
          <el-descriptions-item label="删除时间">{{ formatDateTime(selectedItem.deleteTime) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(selectedItem.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="文件路径" :span="2">{{ selectedItem.filePath }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ selectedItem.contentType }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ selectedItem.downloadCount || 0 }}</el-descriptions-item>
        </el-descriptions>
      </div>
      
      <template #footer>
        <el-button @click="showDetailsDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { Delete, Refresh, Search, Document, Folder, RefreshLeft, View, DataLine } from "@element-plus/icons-vue"
import { getRecycleBinFiles, restoreFile, permanentDeleteFile, emptyRecycleBin } from "@/api/file"

const loading = ref(false)
const emptying = ref(false)
const restoring = ref(false)
const recycleItems = ref([])
const searchKeyword = ref("")
const selectedItems = ref([])
const showDetailsDialog = ref(false)
const selectedItem = ref(null)

const recycleStats = ref({
  totalItems: 0,
  fileCount: 0,
  folderCount: 0,
  totalSize: 0
})

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return "0 B"
  const k = 1024
  const sizes = ["B", "KB", "MB", "GB", "TB"]
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i]
}

// 格式化存储大小
const formatStorage = (bytes) => {
  return formatFileSize(bytes)
}

// 格式化日期时间
const formatDateTime = (datetime) => {
  if (!datetime) return ""
  return new Date(datetime).toLocaleString()
}

// 处理选择变化
const handleSelectionChange = (selection) => {
  selectedItems.value = selection
}

// 加载回收站项目
const loadRecycleItems = async () => {
  loading.value = true
  try {
    const response = await getRecycleBinFiles()
    recycleItems.value = response || []
    
    // 更新统计信息
    recycleStats.value = {
      totalItems: recycleItems.value.length,
      fileCount: recycleItems.value.length,
      folderCount: 0,
      totalSize: recycleItems.value.reduce((sum, item) => sum + (item.size || 0), 0)
    }
  } catch (error) {
    ElMessage.error("加载回收站项目失败")
    console.error("加载回收站项目失败:", error)
  } finally {
    loading.value = false
  }
}

// 刷新项目列表
const refreshItems = () => {
  loadRecycleItems()
}

// 搜索项目
const searchItems = () => {
  if (!searchKeyword.value.trim()) {
    loadRecycleItems()
    return
  }
  
  const keyword = searchKeyword.value.toLowerCase()
  recycleItems.value = recycleItems.value.filter(item => 
    item.originalFilename.toLowerCase().includes(keyword) ||
    item.filePath.toLowerCase().includes(keyword)
  )
  
  ElMessage.success(`找到 ${recycleItems.value.length} 个匹配项目`)
}

// 恢复单个项目
const restoreItem = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定要恢复 "${item.originalFilename}" 吗？`,
      "恢复确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "success"
      }
    )
    
    await restoreFile(item.id)
    ElMessage.success("文件恢复成功")
    await loadRecycleItems()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("恢复文件失败")
      console.error("恢复文件失败:", error)
    }
  }
}

// 恢复选中项目
const restoreSelected = async () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning("请选择要恢复的项目")
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要恢复选中的 ${selectedItems.value.length} 个文件吗？`,
      "批量恢复",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "success"
      }
    )
    
    restoring.value = true
    
    // 批量恢复
    for (const item of selectedItems.value) {
      await restoreFile(item.id)
    }
    
    ElMessage.success(`成功恢复 ${selectedItems.value.length} 个文件`)
    selectedItems.value = []
    await loadRecycleItems()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("批量恢复失败")
      console.error("批量恢复失败:", error)
    }
  } finally {
    restoring.value = false
  }
}

// 彻底删除项目
const deletePermanently = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除 "${item.originalFilename}" 吗？此操作不可恢复！`,
      "彻底删除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "error",
        inputPattern: new RegExp(`^${item.originalFilename}$`),
        inputPlaceholder: `请输入 "${item.originalFilename}" 确认删除`,
        inputValidator: (value) => {
          if (value !== item.originalFilename) {
            return "输入的文件名称不正确"
          }
          return true
        },
        showInput: true
      }
    )
    
    await permanentDeleteFile(item.id)
    ElMessage.success("文件已彻底删除")
    await loadRecycleItems()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("彻底删除失败")
      console.error("彻底删除失败:", error)
    }
  }
}

// 清空回收站
const emptyRecycleBinAction = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要清空回收站吗？此操作将彻底删除所有文件，不可恢复！`,
      "清空回收站",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "error",
        inputPattern: /^清空回收站$/,
        inputPlaceholder: "请输入 \"清空回收站\" 确认操作",
        inputValidator: (value) => {
          if (value !== "清空回收站") {
            return "请输入 \"清空回收站\" 确认操作"
          }
          return true
        },
        showInput: true
      }
    )
    
    emptying.value = true
    
    await emptyRecycleBin()
    ElMessage.success("回收站已清空")
    await loadRecycleItems()
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("清空回收站失败")
      console.error("清空回收站失败:", error)
    }
  } finally {
    emptying.value = false
  }
}

// 查看项目详情
const viewItemDetails = (item) => {
  selectedItem.value = item
  showDetailsDialog.value = true
}

onMounted(() => {
  loadRecycleItems()
})
</script>

<style scoped>
.recycle-container {
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
  color: #f56c6c;
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

.recycle-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.item-icon {
  font-size: 18px;
  color: #409EFF;
}

.item-details {
  flex: 1;
}

.item-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.item-size {
  font-size: 12px;
  color: #666;
}

.original-path {
  font-size: 12px;
  color: #999;
}

.warning {
  color: #e6a23c;
  font-weight: bold;
}

.item-details-content {
  padding: 10px 0;
}

.file-preview {
  margin-top: 20px;
}

.preview-content {
  margin-top: 10px;
  padding: 20px;
  background: #f5f5f5;
  border-radius: 8px;
  text-align: center;
}

:deep(.el-table) {
  height: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}
</style>
