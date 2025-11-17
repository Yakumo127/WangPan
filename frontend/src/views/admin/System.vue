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
          
          <el-form :model="systemConfig" label-width="180px">
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
            
            <el-form-item label="不限制上传类型">
              <el-switch v-model="uploadPolicy.allowAll" @change="onAllowAllChange" />
              <span class="tip" style="margin-left:8px;">开启后，所有文件均可上传</span>
            </el-form-item>
            <el-form-item label="允许的文件后缀" v-if="!uploadPolicy.allowAll">
              <el-select v-model="uploadPolicy.allowedSuffixes" multiple filterable allow-create default-first-option placeholder="输入后按回车添加，如：jpg、pdf、zip" @change="onSuffixesChange">
                <el-option v-for="s in uploadPolicy.allowedSuffixes" :key="s" :label="s" :value="s" />
              </el-select>
              <el-button style="margin-left:8px;" @click="addCommonSuffixes">常用后缀</el-button>
              <el-button style="margin-left:8px;" @click="clearSuffixes">清空</el-button>
              <el-button type="primary" style="margin-left:8px;" :disabled="!canSavePolicy" @click="saveUploadPolicy">保存上传策略</el-button>
              <div class="form-hint" :class="{ error: !canSavePolicy }" style="margin-top:6px;">
                <template v-if="!canSavePolicy">
                  <span v-if="invalidSuffixes.length > 0">存在非法后缀：{{ invalidSuffixes.join(', ') }}（仅允许小写字母和数字，不含点）</span>
                  <span v-else>请至少配置一个允许的后缀</span>
                </template>
                <template v-else>
                  已配置 {{ uploadPolicy.allowedSuffixes.length }} 个后缀
                </template>
              </div>
            </el-form-item>
            
            <el-form-item label="会话超时">
              <el-input-number v-model="systemConfig.sessionTimeout" :min="1" :max="1440" />
              <span class="unit">分钟</span>
            </el-form-item>
            
            <el-form-item label="启用日志">
              <el-switch v-model="systemConfig.enableLogging" />
            </el-form-item>
            
            <el-form-item label="系统回收站保留期(天)">
              <el-input-number v-model="systemConfig.retentionDays" :min="1" :max="365" @change="val => updateRecycleSettings({ retentionDays: val })" />
              <span class="tip">（仅对新发起的彻底删除生效）</span>
            </el-form-item>
            
            <el-form-item label="允许手动清理到期文件">
              <el-switch v-model="systemConfig.manualPurgeEnabled" @change="saveManualPurgeSetting" />
            </el-form-item>
            
            <el-form-item label="日志级别（待完善）">
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
      <el-tab-pane label="回收站管理" name="recycle" v-if="false">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>回收站管理</span>
              <el-button v-if="systemConfig.manualPurgeEnabled" type="warning" @click="manualPurgeExpired" :loading="emptying">
                <el-icon><Delete /></el-icon>
                手动清理到期文件
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
              <el-col :span="8">
                <el-date-picker
                  v-model="execRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="到期开始时间"
                  end-placeholder="到期结束时间"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                />
              </el-col>
              <el-col :span="8">
                <el-input
                  v-model="reasonKeyword"
                  placeholder="搜索删除理由..."
                  clearable
                  @keyup.enter="searchRecycleBin"
                >
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
          
          <!-- 回收站文件列表 -->
          <div class="recycle-list">
            <el-table
              :data="recycleItems"
              style="width: 100%"
              v-loading="loading"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="55" />
              <el-table-column label="文件信息" min-width="300" sortable>
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
                <span class="item-user">用户: {{ row.ownerUsername || row.username || "未知" }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>
              <el-table-column prop="deleteTime" label="删除时间" width="180" sortable>
                <template #default="{ row }">
                  {{ formatDateTime(row.deleteTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="ownerUsername" label="所属用户" width="120" sortable />
              <el-table-column prop="adminDeleteExecuteTime" label="到期时间" width="180" sortable>
                <template #default="{ row }">
                  {{ row.adminDeleteExecuteTime ? formatDateTime(row.adminDeleteExecuteTime) : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="adminDeleteReason" label="删除理由" min-width="200" show-overflow-tooltip sortable />
              <el-table-column label="剩余时间" width="140">
                <template #default="{ row }">
                  {{ formatRemaining(row.adminDeleteExecuteTime) }}
                </template>
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
import { getAllRecycleBinFiles, adminRestoreFile, adminScheduleDeleteFile } from "@/api/file"
import { getRecycleSettings, updateRecycleSettings, getUploadPolicy, updateUploadPolicy } from "@/api/system"

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
    const recycleRawItems = ref([])
    const searchKeyword = ref("")
    const reasonKeyword = ref("")
    const onlyScheduled = ref(false)
    const execRange = ref([])
    const selectedItems = ref([])
    
    const systemConfig = ref({
      systemName: "企业文件管理系统",
      storagePath: "/app/storage",
      maxFileSize: 100,
      allowedTypes: ["image", "document", "video", "audio", "archive"],
      sessionTimeout: 30,
      enableLogging: true,
      manualPurgeEnabled: false,
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
    const uploadPolicy = ref({ allowAll: true, allowedSuffixes: [] })
    const invalidSuffixes = ref([])
    
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
    
    // 格式化剩余时间
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
    
    // 处理选择变化
    const handleSelectionChange = (selection) => {
      selectedItems.value = selection
    }
    
    // 加载回收站数据
    const loadRecycleBinData = async () => {
      loading.value = true
      try {
        // 使用管理员API获取所有用户的回收站文件
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
        
        // 使用nextTick确保DOM更新后再更新统计信息
        await nextTick()
        
        // 更新统计信息
        const uniqueUsers = new Set(recycleItems.value.map(item => item.ownerUsername || item.username))
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
        // 第一次确认：警告
        await ElMessageBox.confirm(
          `此操作将发起彻底删除并进入保留期，期间可恢复，到期自动删除且不可恢复。`,
          "警告",
          { confirmButtonText: "继续", cancelButtonText: "取消", type: "warning" }
        )
        const { value: reason } = await ElMessageBox.prompt(
          `删除后文件进入保留期${systemConfig.value.retentionDays || 15}天，可在此期间恢复；到期自动删除且不可恢复。`,
          "确认并填写理由",
          {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            inputPlaceholder: "请输入删除理由（必填）",
            inputValidator: (val) => !!val && val.trim().length > 0,
            type: "warning"
          }
        )
        await adminScheduleDeleteFile(item.id, reason)
        ElMessage.success(`已发起彻底删除（进入保留期${systemConfig.value.retentionDays || 15}天）`)
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
        
        const { value: reason } = await ElMessageBox.prompt(
          `删除后文件进入保留期${systemConfig.value.retentionDays || 15}天，可在此期间恢复；到期自动删除且不可恢复。`,
          "确认并填写理由",
          {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            inputPlaceholder: "请输入删除理由（必填）",
            inputValidator: (val) => !!val && val.trim().length > 0,
            type: "warning"
          }
        )
        // 批量发起
        for (const item of selectedItems.value) {
          await adminScheduleDeleteFile(item.id, reason)
        }
        
        ElMessage.success(`已发起彻底删除（${selectedItems.value.length} 项，保留期${systemConfig.value.retentionDays || 15}天）`)
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
    const manualPurgeExpired = async () => {
      try {
        await ElMessageBox.confirm(
          `将触发到期文件的立即清理，是否继续？`,
          "手动清理到期",
          {
            confirmButtonText: "继续",
            cancelButtonText: "取消",
            type: "warning"
          }
        )
        emptying.value = true
        const token = localStorage.getItem('enterprise_file_manager_token')
        const resp = await fetch('/api/files/admin/recycle/bin/purge-expired', { method: 'POST', headers: { 'Authorization': token ? `Bearer ${token}` : '' } })
        const data = await resp.json()
        if (!resp.ok) throw new Error(data?.message || '请求失败')
        ElMessage.success(data.message || '已清理到期文件')
        await loadRecycleBinData()
      } catch (error) {
        if (error !== "cancel") {
          ElMessage.error(error?.message || "手动清理失败或未启用")
        }
      } finally {
        emptying.value = false
      }
    }
    
    // 保存配置
    const saveConfig = () => {
      ElMessage.success("设置保存成功")
    }
    
    const saveManualPurgeSetting = async () => {
      try {
        await updateRecycleSettings({ manualPurgeEnabled: systemConfig.value.manualPurgeEnabled })
        ElMessage.success('已更新手动清理到期开关')
      } catch (e) {
        ElMessage.error('更新失败')
      }
    }

    const normalizeSuffixes = (arr) => {
      const set = new Set()
      for (const v of (arr || [])) {
        if (!v) continue
        let s = String(v).trim().toLowerCase()
        if (s.startsWith('.')) s = s.slice(1)
        if (s) set.add(s)
      }
      return Array.from(set)
    }
    const addCommonSuffixes = () => {
      const commons = ['jpg','jpeg','png','gif','webp','pdf','doc','docx','xls','xlsx','ppt','pptx','txt','zip','rar','7z','mp4','mp3']
      uploadPolicy.value.allowedSuffixes = normalizeSuffixes([...(uploadPolicy.value.allowedSuffixes||[]), ...commons])
    }
    const clearSuffixes = () => {
      uploadPolicy.value.allowedSuffixes = []
      invalidSuffixes.value = []
    }
    const onSuffixesChange = (vals) => {
      const normalized = normalizeSuffixes(vals)
      const bad = []
      for (const s of vals || []) {
        const v = String(s || '').trim().toLowerCase().replace(/^\./, '')
        if (!v || !/^[a-z0-9]+$/.test(v)) bad.push(s)
      }
      uploadPolicy.value.allowedSuffixes = normalized
      invalidSuffixes.value = bad
      if (bad.length > 0) {
        ElMessage.warning('已自动忽略非法后缀（仅允许小写字母和数字，不含点）')
      }
    }
    const canSavePolicy = computed(() => {
      if (uploadPolicy.value.allowAll) return true
      return (uploadPolicy.value.allowedSuffixes && uploadPolicy.value.allowedSuffixes.length > 0) && (invalidSuffixes.value.length === 0)
    })
    const onAllowAllChange = async (val) => {
      if (val) {
        // 开启 allowAll 直接保存
        try { await saveUploadPolicy(); } catch {}
        return
      }
      // 关闭 allowAll 时，如为空则提示先配置
      if (!uploadPolicy.value.allowedSuffixes || uploadPolicy.value.allowedSuffixes.length === 0) {
        ElMessage.info('已关闭“不限制上传类型”。请先添加允许的后缀再保存。')
      } else {
        try { await saveUploadPolicy(); } catch {}
      }
    }
    const saveUploadPolicy = async () => {
      try {
        const payload = {
          allowAll: !!uploadPolicy.value.allowAll,
          allowedSuffixes: normalizeSuffixes(uploadPolicy.value.allowedSuffixes)
        }
        if (!payload.allowAll && (!payload.allowedSuffixes || payload.allowedSuffixes.length === 0)) {
          ElMessage.error('请至少配置一个允许的后缀，或开启“不限制上传类型”')
          return
        }
        await updateUploadPolicy(payload)
        ElMessage.success('上传策略已更新')
      } catch (e) {
        ElMessage.error('保存失败')
      }
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
        manualPurgeEnabled: false,
        logLevel: "INFO"
      }
      ElMessage.info("设置已重置")
    }
    
    onMounted(async () => {
      try {
        const cfg = await getRecycleSettings()
        systemConfig.value.manualPurgeEnabled = !!cfg.manualPurgeEnabled
        if (cfg.retentionDays) systemConfig.value.retentionDays = cfg.retentionDays
        const pol = await getUploadPolicy()
        uploadPolicy.value.allowAll = !!pol.allowAll
        uploadPolicy.value.allowedSuffixes = Array.isArray(pol.allowedSuffixes) ? normalizeSuffixes(pol.allowedSuffixes) : []
      } catch (e) {}
      nextTick(() => { loadRecycleBinData() })
    })
    
    return {
      activeTab,
      loading,
      emptying,
      batchRestoring,
      batchDeleting,
      recycleItems,
      searchKeyword,
      reasonKeyword,
      onlyScheduled,
      execRange,
      selectedItems,
      systemConfig,
      systemInfo,
      uploadPolicy,
      invalidSuffixes,
      canSavePolicy,
      onAllowAllChange,
      onSuffixesChange,
      recycleStats,
      formatFileSize,
      formatStorage,
      formatDateTime,
      formatRemaining,
      handleSelectionChange,
      refreshRecycleBin,
      searchRecycleBin,
      restoreItem,
      deletePermanently,
      batchRestore,
      batchDelete,
      manualPurgeExpired,
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
