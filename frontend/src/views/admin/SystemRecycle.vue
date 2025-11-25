<template>
  <div class="system-recycle-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>系统回收站</span>
          <el-button v-if="systemConfig.manualPurgeEnabled" type="warning" @click="manualPurgeExpired" :loading="emptying">
            <el-icon><Delete /></el-icon>
            手动清理到期文件
          </el-button>
        </div>
      </template>

      <!-- 统计信息 -->
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
                <div class="stat-number">{{ recycleStats.oldestDeleteTime || '无' }}</div>
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

      <!-- 搜索与过滤 -->
      <div class="filter-row">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-input v-model="searchKeyword" placeholder="搜索文件名或用户名..." clearable @keyup.enter="searchRecycleBin">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-col>
          <el-col :span="8">
            <el-date-picker v-model="execRange" type="datetimerange" range-separator="至" start-placeholder="到期开始时间" end-placeholder="到期结束时间" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-col>
          <el-col :span="8">
            <el-input v-model="reasonKeyword" placeholder="搜索删除理由..." clearable @keyup.enter="searchRecycleBin">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-col>
          <el-col :span="4" class="align-center">
            <el-checkbox v-model="onlyScheduled" @change="searchRecycleBin">仅显示保留期中</el-checkbox>
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

      <!-- 列表 -->
      <div class="recycle-list">
        <el-table :data="recycleItems" style="width: 100%" v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" />
          <el-table-column label="文件信息" min-width="300" sortable>
            <template #default="{ row }">
              <div class="item-info">
                <div class="entry-thumb">
                  <el-icon :size="24" :color="getFileIconConfig(row.originalFilename, false).color">
                    <component :is="getFileIconConfig(row.originalFilename, false).name" />
                  </el-icon>
                </div>
                <div class="item-details">
                  <div class="item-name">{{ row.originalFilename }}</div>
                  <div class="item-meta">
                    <span class="item-size">{{ formatFileSize(row.size) }}</span>
                    <span class="separator">|</span>
                    <span class="item-user">用户: {{ row.ownerUsername || row.username || '未知' }}</span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="deleteTime" label="删除时间" width="180" sortable>
            <template #default="{ row }">{{ formatDateTime(row.deleteTime) }}</template>
          </el-table-column>
          <el-table-column prop="ownerUsername" label="所属用户" width="120" sortable />
          <el-table-column prop="adminDeleteExecuteTime" label="到期时间" width="180" sortable>
            <template #default="{ row }">{{ row.adminDeleteExecuteTime ? formatDateTime(row.adminDeleteExecuteTime) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="adminDeleteReason" label="删除理由" min-width="200" show-overflow-tooltip sortable />
          <el-table-column label="剩余时间" width="140">
            <template #default="{ row }">{{ formatRemaining(row.adminDeleteExecuteTime) }}</template>
          </el-table-column>
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
  </div>
  
</template>

<script>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search, Document, User, Calendar, DataLine, RefreshLeft, Picture, VideoPlay, Headset, Box, Reading, Link, Coin, Cpu, Monitor, Setting, Warning } from '@element-plus/icons-vue'
import { getAllRecycleBinFiles, adminRestoreFile, adminScheduleDeleteFile } from '@/api/file'
import { getRecycleSettings } from '@/api/system'
import { getFileIconConfig } from '@/utils/file-icons'

export default {
  name: 'SystemRecycle',
  components: { Delete, Refresh, Search, Document, User, Calendar, DataLine, RefreshLeft },
  setup() {
    const loading = ref(false)
    const emptying = ref(false)
    const batchRestoring = ref(false)
    const batchDeleting = ref(false)
    const recycleItems = ref([])
    const recycleRawItems = ref([])
    const searchKeyword = ref('')
    const reasonKeyword = ref('')
    const onlyScheduled = ref(false)
    const execRange = ref([])
    const selectedItems = ref([])

    const systemConfig = ref({ retentionDays: 15, manualPurgeEnabled: false })

    const recycleStats = ref({ totalItems: 0, userCount: 0, oldestDeleteTime: '无', totalSize: 0 })

    const formatFileSize = (bytes) => {
      if (!bytes) return '0 B'
      const k = 1024
      const sizes = ['B','KB','MB','GB','TB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }
    const formatStorage = (bytes) => formatFileSize(bytes)
    const formatDateTime = (dt) => dt ? new Date(dt).toLocaleString() : ''
    const formatRemaining = (execTime) => {
      if (!execTime) return '-'
      const end = new Date(execTime).getTime()
      const now = Date.now()
      const diff = end - now
      if (diff <= 0) return '已到期'
      const d = Math.floor(diff / (24*3600*1000))
      const h = Math.floor((diff % (24*3600*1000)) / (3600*1000))
      return d > 0 ? `${d}天${h}小时` : `${h}小时`
    }

    const handleSelectionChange = (selection) => { selectedItems.value = selection }

    const loadRecycleBinData = async () => {
      loading.value = true
      try {
        const params = {}
        if (Array.isArray(execRange.value) && execRange.value.length === 2 && execRange.value[0] && execRange.value[1]) {
          params.fromExec = execRange.value[0]
          params.toExec = execRange.value[1]
        }
        if (onlyScheduled.value) params.scheduledOnly = true
        if (searchKeyword.value && searchKeyword.value.trim()) params.keyword = searchKeyword.value.trim()
        if (reasonKeyword.value && reasonKeyword.value.trim()) params.reason = reasonKeyword.value.trim()
        const response = await getAllRecycleBinFiles(params)
        recycleRawItems.value = response || []
        recycleItems.value = [...recycleRawItems.value]

        await nextTick()
        const uniqueUsers = new Set(recycleItems.value.map(i => i.ownerUsername || i.username))
        recycleStats.value = {
          totalItems: recycleItems.value.length,
          userCount: uniqueUsers.size,
          oldestDeleteTime: recycleItems.value.length ? formatDateTime(recycleItems.value[recycleItems.value.length - 1].deleteTime) : '无',
          totalSize: recycleItems.value.reduce((s, i) => s + (i.size || 0), 0)
        }
      } catch (e) {
        ElMessage.error('加载回收站数据失败')
        console.error('加载回收站数据失败:', e)
      } finally {
        loading.value = false
      }
    }

    const refreshRecycleBin = () => loadRecycleBinData()
    const searchRecycleBin = () => {
      const k1 = (searchKeyword.value || '').toLowerCase().trim()
      const k2 = (reasonKeyword.value || '').toLowerCase().trim()
      const scheduledOnly = !!onlyScheduled.value
      const hasExecRange = Array.isArray(execRange.value) && execRange.value.length === 2 && execRange.value[0] && execRange.value[1]
      const startTs = hasExecRange ? new Date(execRange.value[0]).getTime() : null
      const endTs = hasExecRange ? new Date(execRange.value[1]).getTime() : null
      recycleItems.value = (recycleRawItems.value || []).filter(item => {
        const nameMatch = !k1 || (item.originalFilename && item.originalFilename.toLowerCase().includes(k1)) || (item.ownerUsername && item.ownerUsername.toLowerCase().includes(k1))
        const reasonMatch = !k2 || (item.adminDeleteReason && item.adminDeleteReason.toLowerCase().includes(k2))
        const scheduledMatch = !scheduledOnly || item.adminDeleteScheduled
        const execMatch = !hasExecRange || (item.adminDeleteExecuteTime && (() => { const t = new Date(item.adminDeleteExecuteTime).getTime(); return t >= startTs && t <= endTs })())
        return nameMatch && reasonMatch && scheduledMatch && execMatch
      })
    }

    const restoreItem = async (item) => {
      try {
        await ElMessageBox.confirm(`确定要恢复 "${item.originalFilename}" 吗？`, '恢复确认', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'success' })
        await adminRestoreFile(item.id)
        ElMessage.success('文件恢复成功')
        await loadRecycleBinData()
      } catch (e) {
        if (e !== 'cancel') {
          ElMessage.error('恢复文件失败')
        }
      }
    }

    const deletePermanently = async (item) => {
      try {
        await ElMessageBox.confirm('此操作将发起彻底删除并进入保留期，期间可恢复，到期自动删除且不可恢复。', '警告', { confirmButtonText: '继续', cancelButtonText: '取消', type: 'warning' })
        const { value: reason } = await ElMessageBox.prompt(`删除后文件进入保留期${systemConfig.value.retentionDays || 15}天，可在此期间恢复；到期自动删除且不可恢复。`, '确认并填写理由', { confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '请输入删除理由（必填）', inputValidator: (val) => !!val && val.trim().length, type: 'warning' })
        await adminScheduleDeleteFile(item.id, reason)
        ElMessage.success('已发起彻底删除并进入保留期')
        await loadRecycleBinData()
      } catch (e) {
        if (e !== 'cancel') {
          ElMessage.error('发起彻底删除失败')
        }
      }
    }

    const batchRestore = async () => {
      if (!selectedItems.value.length) { ElMessage.warning('请选择要恢复的文件'); return }
      try {
        await ElMessageBox.confirm(`确定要恢复选中的 ${selectedItems.value.length} 个文件吗？`, '批量恢复', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'success' })
        batchRestoring.value = true
        for (const item of selectedItems.value) { await adminRestoreFile(item.id) }
        ElMessage.success(`已恢复 ${selectedItems.value.length} 个文件`)
        selectedItems.value = []
        await loadRecycleBinData()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('批量恢复失败')
      } finally { batchRestoring.value = false }
    }

    const batchDelete = async () => {
      if (!selectedItems.value.length) { ElMessage.warning('请选择要彻底删除的文件'); return }
      try {
        await ElMessageBox.confirm(`确定要彻底删除选中的 ${selectedItems.value.length} 个文件吗？此操作不可恢复！`, '批量彻底删除', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'error', inputPattern: /^批量删除$/, inputPlaceholder: '请输入 "批量删除" 确认操作', inputValidator: (v) => v === '批量删除' ? true : '请输入 "批量删除" 确认操作', showInput: true })
        batchDeleting.value = true
        const { value: reason } = await ElMessageBox.prompt(`删除后文件进入保留期${systemConfig.value.retentionDays || 15}天，可在此期间恢复；到期自动删除且不可恢复。`, '确认并填写理由', { confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '请输入删除理由（必填）', inputValidator: (val) => !!val && val.trim().length > 0, type: 'warning' })
        for (const item of selectedItems.value) { await adminScheduleDeleteFile(item.id, reason) }
        ElMessage.success(`已发起彻底删除（${selectedItems.value.length} 项）`)
        selectedItems.value = []
        await loadRecycleBinData()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('批量彻底删除失败')
      } finally { batchDeleting.value = false }
    }

    const manualPurgeExpired = async () => {
      try {
        await ElMessageBox.confirm('将触发到期文件的立即清理，是否继续？', '手动清理到期', { confirmButtonText: '继续', cancelButtonText: '取消', type: 'warning' })
        emptying.value = true
        const token = localStorage.getItem('enterprise_file_manager_token')
        const resp = await fetch('/api/files/admin/recycle/bin/purge-expired', { method: 'POST', headers: { 'Authorization': token ? `Bearer ${token}` : '' } })
        const data = await resp.json()
        if (!resp.ok) throw new Error(data?.message || '请求失败')
        ElMessage.success(data.message || '已清理到期文件')
        await loadRecycleBinData()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error(e?.message || '手动清理失败或未启用')
      } finally {
        emptying.value = false
      }
    }

    onMounted(async () => {
      try {
        const cfg = await getRecycleSettings()
        systemConfig.value.manualPurgeEnabled = !!cfg.manualPurgeEnabled
        if (cfg.retentionDays) systemConfig.value.retentionDays = cfg.retentionDays
      } catch (e) {}
      nextTick(() => { loadRecycleBinData() })
    })

    return {
      loading, emptying, batchRestoring, batchDeleting,
      recycleItems, searchKeyword, reasonKeyword, onlyScheduled, execRange, selectedItems,
      systemConfig, recycleStats,
      formatFileSize, formatStorage, formatDateTime, formatRemaining,
      handleSelectionChange, refreshRecycleBin, searchRecycleBin,
      restoreItem, deletePermanently, batchRestore, batchDelete,
      manualPurgeExpired, getFileIconConfig
    }
  }
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.system-recycle-container {
  padding: $spacing-base;
  height: 100%;
  box-sizing: border-box;
  background-color: $background-color-base;
}

.box-card {
  border-radius: $border-radius-base;
  border: 1px solid $border-color-light;
  box-shadow: $box-shadow-sm;
  
  :deep(.el-card__header) {
    padding: $spacing-base 20px;
    border-bottom: 1px solid $border-color-light;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

/* Stats */
.stats-row {
  margin-bottom: $spacing-base;
}

.stat-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  overflow: hidden;
  position: relative;
  
  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.025);
  }

  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    width: 80px;
    height: 80px;
    background: linear-gradient(135deg, transparent 50%, rgba(var(--el-color-primary-rgb), 0.05) 50%);
    border-radius: 0 0 0 80px;
    pointer-events: none;
  }
  
  :deep(.el-card__body) {
    padding: 24px;
    display: flex;
    align-items: center;
    gap: 20px;
  }
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background-color: rgba($primary-color, 0.08);
  color: $primary-color;
  transition: transform 0.3s;
  
  .el-icon {
    font-size: 28px;
  }
}

.stat-card:hover .stat-icon {
  transform: scale(1.1) rotate(5deg);
  background-color: rgba($primary-color, 0.15);
}

.stat-info {
  flex: 1;
  text-align: left;
  z-index: 1;
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.stat-label {
  font-size: 14px;
  color: $text-secondary;
  margin-top: 4px;
  font-weight: 500;
}

/* Filter */
.filter-row {
  margin-bottom: $spacing-base;
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid $border-color-light;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  
  .el-col {
    margin-bottom: 8px;
    
    &:last-child {
      margin-bottom: 0;
    }
  }
}

.align-center {
  display: flex;
  align-items: center;
  height: 100%;
}

/* List */
.recycle-list {
  margin-bottom: $spacing-base;
  border-radius: 12px;
  border: 1px solid $border-color-light;
  overflow: hidden;
  box-shadow: $box-shadow-sm;
}

.recycle-list :deep(.el-table) {
  --el-table-header-bg-color: #f1f5f9;
  --el-table-header-text-color: #475569;
  --el-table-row-hover-bg-color: #f8fafc;
  
  th.el-table__cell {
    font-weight: 600;
    height: 52px;
    background-color: #f1f5f9 !important;
    color: #334155;
  }
  
  .el-table__row {
    height: 64px;
    transition: background-color 0.2s;
  }

  .el-table__row:hover > td {
    background-color: #f0f9ff !important;
  }
}

.item-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.entry-thumb {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.item-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.item-name {
  font-weight: 500;
  color: $text-primary;
  font-size: 14px;
  margin-bottom: 4px;
}

.item-meta {
  font-size: 12px;
  color: $text-secondary;
  display: flex;
  align-items: center;
  gap: 8px;
  
  .separator {
    color: $border-color-light;
  }
  
  .item-user {
    background: #f1f5f9;
    padding: 2px 6px;
    border-radius: 4px;
  }
}

.batch-actions {
  display: flex;
  gap: 12px;
  padding: 12px 20px;
  background: #fff;
  border: 1px solid $border-color-light;
  border-radius: $border-radius-base;
  align-items: center;
  box-shadow: $box-shadow-sm;
}
</style>

