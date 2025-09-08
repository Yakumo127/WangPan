<template>
  <div class="system-container">
    <el-tabs v-model="activeTab" type="card">
      <!-- 基础设置 -->
      <el-tab-pane label="基础设置" name="basic">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>系统基础设置</span>
            </div>
          </template>
          
          <el-form :model="systemConfig" label-width="120px">
            <el-form-item label="系统名称">
              <el-input v-model="systemConfig.systemName" />
            </el-form-item>
            
            <el-form-item label="文件存储路径">
              <el-input v-model="systemConfig.storagePath" />
            </el-form-item>
            
            <el-form-item label="最大文件大小">
              <el-input-number v-model="systemConfig.maxFileSize" :min="1" :max="1000" />
              <span class="unit">MB</span>
            </el-form-item>
            
            <el-form-item label="允许的文件类型">
              <el-select v-model="systemConfig.allowedTypes" multiple placeholder="选择允许的文件类型">
                <el-option label="图片" value="image" />
                <el-option label="文档" value="document" />
                <el-option label="视频" value="video" />
                <el-option label="音频" value="audio" />
                <el-option label="压缩包" value="archive" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="会话超时">
              <el-input-number v-model="systemConfig.sessionTimeout" :min="1" :max="1440" />
              <span class="unit">分钟</span>
            </el-form-item>
            
            <el-form-item label="启用日志">
              <el-switch v-model="systemConfig.enableLogging" />
            </el-form-item>
            
            <el-form-item label="日志级别">
              <el-select v-model="systemConfig.logLevel" placeholder="选择日志级别">
                <el-option label="DEBUG" value="DEBUG" />
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="ERROR" value="ERROR" />
              </el-select>
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="saveConfig">保存设置</el-button>
              <el-button @click="resetConfig">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
      
      <!-- 回收站管理 -->
      <el-tab-pane label="回收站管理" name="recycle">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>回收站管理</span>
              <el-button type="danger" @click="emptyAllRecycleBin" :loading="emptying">
                <el-icon><Delete /></el-icon>
                清空所有用户回收站
              </el-button>
            </div>
          </template>
          
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
                    <div class="stat-label">总文件数</div>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card class="stat-card">
                  <div class="stat-icon">
                    <el-icon><User /></el-icon>
                  </div>
                  <div class="stat-info">
                    <div class="stat-number">{{ recycleStats.userCount || 0 }}</div>
                    <div class="stat-label">涉及用户</div>
                  </div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card class="stat-card">
                  <div class="stat-icon">
                    <el-icon><Calendar /></el-icon>
                  </div>
                  <div class="stat-info">
                    <div class="stat-number">{{ recycleStats.oldestDeleteTime || "无" }}</div>
                    <div class="stat-label">最早删除</div>
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
          
          <!-- 搜索和过滤 -->
          <div class="filter-row">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-input
                  v-model="searchKeyword"
                  placeholder="搜索文件名或用户名..."
                  clearable
                  @keyup.enter="searchRecycleBin"
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                </el-input>
              </el-col>
              <el-col :span="4">
                <el-button @click="searchRecycleBin">搜索</el-button>
              </el-col>
              <el-col :span="4">
                <el-button @click="refreshRecycleBin" :loading="loading">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </el-col>
            </el-row>
          </div>
          
          <!-- 回收站文件列表 -->
          <div class="recycle-list">
            <el-table
              :data="recycleItems"
              style="width: 100%"
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="55" />
              <el-table-column label="文件信息" min-width="300">
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
                        <span class="item-user">用户: {{ row.username || "未知" }}</span>
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
              <el-table-column prop="username" label="所属用户" width="120" />
              <el-table-column label="操作" width="200">
                <template #default="{ row }">
                  <el-button-group>
                    <el-button size="small" type="success" @click="restoreItem(row)">
                      <el-icon><RefreshLeft /></el-icon>
                      恢复
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
          
          <!-- 批量操作 -->
          <div class="batch-actions" v-if="selectedItems.length > 0">
            <el-button type="success" @click="batchRestore" :loading="batchRestoring">
              <el-icon><RefreshLeft /></el-icon>
              恢复选中 ({{ selectedItems.length }})
            </el-button>
            <el-button type="danger" @click="batchDelete" :loading="batchDeleting">
              <el-icon><Delete /></el-icon>
              彻底删除选中 ({{ selectedItems.length }})
            </el-button>
          </div>
        </el-card>
      </el-tab-pane>
      
      <!-- 系统信息 -->
      <el-tab-pane label="系统信息" name="info">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          
          <el-descriptions :column="2" border>
            <el-descriptions-item label="系统版本">{{ systemInfo.version }}</el-descriptions-item>
            <el-descriptions-item label="构建时间">{{ systemInfo.buildTime }}</el-descriptions-item>
            <el-descriptions-item label="Java版本">{{ systemInfo.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="数据库版本">{{ systemInfo.databaseVersion }}</el-descriptions-item>
            <el-descriptions-item label="运行时间">{{ systemInfo.uptime }}</el-descriptions-item>
            <el-descriptions-item label="内存使用">{{ systemInfo.memoryUsage }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { ref, onMounted, nextTick } from "vue"
import { ElMessage, ElMessageBox } from "element-plus"
import { Delete, Refresh, Search, Document, User, Calendar, DataLine, RefreshLeft } from "@element-plus/icons-vue"
import { getAllRecycleBinFiles, adminRestoreFile, adminPermanentDeleteFile, adminEmptyAllRecycleBin } from "@/api/file"

export default {
  name: "System",
  components: {
    Delete, Refresh, Search, Document, User, Calendar, DataLine, RefreshLeft
  },
  setup() {
    const activeTab = ref("basic")
    const loading = ref(false)
    const emptying = ref(false)
    const batchRestoring = ref(false)
    const batchDeleting = ref(false)
    const recycleItems = ref([])
    const searchKeyword = ref("")
    const selectedItems = ref([])
    
    const systemConfig = ref({
      systemName: "企业文件管理系统",
      storagePath: "/app/storage",
      maxFileSize: 100,
      allowedTypes: ["image", "document", "video", "audio", "archive"],
      sessionTimeout: 30,
      enableLogging: true,
      logLevel: "INFO"
    })
    
    const systemInfo = ref({
      version: "2.0.0",
      buildTime: "2024-01-01 12:00:00",
      javaVersion: "17.0.2",
      databaseVersion: "8.0.35",
      uptime: "2天 3小时 45分钟",
      memoryUsage: "256MB / 1024MB"
    })
    
    const recycleStats = ref({
      totalItems: 0,
      userCount: 0,
      oldestDeleteTime: "无",
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
    
    // 加载回收站数据
    const loadRecycleBinData = async () => {
      loading.value = true
      try {
        // 使用管理员API获取所有用户的回收站文件
        const response = await getAllRecycleBinFiles()
        recycleItems.value = response || []
        
        // 使用nextTick确保DOM更新后再更新统计信息
        await nextTick()
        
        // 更新统计信息
        const uniqueUsers = new Set(recycleItems.value.map(item => item.username))
        recycleStats.value = {
          totalItems: recycleItems.value.length,
          userCount: uniqueUsers.size,
          oldestDeleteTime: recycleItems.value.length > 0 ? 
            formatDateTime(recycleItems.value[recycleItems.value.length - 1].deleteTime) : "无",
          totalSize: recycleItems.value.reduce((sum, item) => sum + (item.size || 0), 0)
        }
      } catch (error) {
        ElMessage.error("加载回收站数据失败")
        console.error("加载回收站数据失败:", error)
      } finally {
        loading.value = false
      }
    }
    
    // 刷新回收站
    const refreshRecycleBin = () => {
      loadRecycleBinData()
    }
    
    // 搜索回收站
    const searchRecycleBin = () => {
      if (!searchKeyword.value.trim()) {
        loadRecycleBinData()
        return
      }
      
      const keyword = searchKeyword.value.toLowerCase()
      recycleItems.value = recycleItems.value.filter(item => 
        (item.originalFilename && item.originalFilename.toLowerCase().includes(keyword)) ||
        (item.username && item.username.toLowerCase().includes(keyword))
      )
      
      ElMessage.success(`找到 ${recycleItems.value.length} 个匹配项目`)
    }
    
    // 恢复单个文件
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
        
        await adminRestoreFile(item.id)
        ElMessage.success("文件恢复成功")
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error("恢复文件失败")
          console.error("恢复文件失败:", error)
        }
      }
    }
    
    // 彻底删除单个文件
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
        
        await adminPermanentDeleteFile(item.id)
        ElMessage.success("文件已彻底删除")
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error("彻底删除失败")
          console.error("彻底删除失败:", error)
        }
      }
    }
    
    // 批量恢复
    const batchRestore = async () => {
      if (selectedItems.value.length === 0) {
        ElMessage.warning("请选择要恢复的文件")
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
        
        batchRestoring.value = true
        
        // 批量恢复
        for (const item of selectedItems.value) {
          await adminRestoreFile(item.id)
        }
        
        ElMessage.success(`成功恢复 ${selectedItems.value.length} 个文件`)
        selectedItems.value = []
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error("批量恢复失败")
          console.error("批量恢复失败:", error)
        }
      } finally {
        batchRestoring.value = false
      }
    }
    
    // 批量删除
    const batchDelete = async () => {
      if (selectedItems.value.length === 0) {
        ElMessage.warning("请选择要彻底删除的文件")
        return
      }
      
      try {
        await ElMessageBox.confirm(
          `确定要彻底删除选中的 ${selectedItems.value.length} 个文件吗？此操作不可恢复！`,
          "批量彻底删除",
          {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "error",
            inputPattern: /^批量删除$/,
            inputPlaceholder: "请输入 \"批量删除\" 确认操作",
            inputValidator: (value) => {
              if (value !== "批量删除") {
                return "请输入 \"批量删除\" 确认操作"
              }
              return true
            },
            showInput: true
          }
        )
        
        batchDeleting.value = true
        
        // 批量删除
        for (const item of selectedItems.value) {
          await adminPermanentDeleteFile(item.id)
        }
        
        ElMessage.success(`成功彻底删除 ${selectedItems.value.length} 个文件`)
        selectedItems.value = []
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error("批量彻底删除失败")
          console.error("批量彻底删除失败:", error)
        }
      } finally {
        batchDeleting.value = false
      }
    }
    
    // 清空所有回收站
    const emptyAllRecycleBin = async () => {
      try {
        await ElMessageBox.confirm(
          `确定要清空所有用户的回收站吗？此操作将彻底删除所有文件，不可恢复！`,
          "清空所有回收站",
          {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "error",
            inputPattern: /^清空所有回收站$/,
            inputPlaceholder: "请输入 \"清空所有回收站\" 确认操作",
            inputValidator: (value) => {
              if (value !== "清空所有回收站") {
                return "请输入 \"清空所有回收站\" 确认操作"
              }
              return true
            },
            showInput: true
          }
        )
        
        emptying.value = true
        
        await adminEmptyAllRecycleBin()
        ElMessage.success("所有回收站已清空")
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error("清空回收站失败")
          console.error("清空回收站失败:", error)
        }
      } finally {
        emptying.value = false
      }
    }
    
    // 保存配置
    const saveConfig = () => {
      ElMessage.success("设置保存成功")
    }
    
    // 重置配置
    const resetConfig = () => {
      systemConfig.value = {
        systemName: "企业文件管理系统",
        storagePath: "/app/storage",
        maxFileSize: 100,
        allowedTypes: ["image", "document", "video", "audio", "archive"],
        sessionTimeout: 30,
        enableLogging: true,
        logLevel: "INFO"
      }
      ElMessage.info("设置已重置")
    }
    
    onMounted(() => {
      // 使用nextTick确保组件完全挂载后再加载数据
      nextTick(() => {
        loadRecycleBinData()
      })
    })
    
    return {
      activeTab,
      loading,
      emptying,
      batchRestoring,
      batchDeleting,
      recycleItems,
      searchKeyword,
      selectedItems,
      systemConfig,
      systemInfo,
      recycleStats,
      formatFileSize,
      formatStorage,
      formatDateTime,
      handleSelectionChange,
      refreshRecycleBin,
      searchRecycleBin,
      restoreItem,
      deletePermanently,
      batchRestore,
      batchDelete,
      emptyAllRecycleBin,
      saveConfig,
      resetConfig
    }
  }
}
</script>

<style scoped>
.system-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.unit {
  margin-left: 10px;
  color: #666;
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

.filter-row {
  margin-bottom: 20px;
}

.recycle-list {
  margin-bottom: 20px;
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

.item-user {
  font-size: 12px;
  color: #999;
}

.batch-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  padding: 15px;
  background: #f5f5f5;
  border-radius: 8px;
}
</style>
