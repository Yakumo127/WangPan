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
              <div class="suffix-tools" style="margin-top:8px; display:flex; flex-direction:column; gap:8px;">
                <div class="category-buttons" style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                  <span class="cat-label" style="color:#666;">快速添加分类：</span>
                  <el-button size="small" @click="addCategory('image')">图片</el-button>
                  <el-button size="small" @click="addCategory('document')">文档</el-button>
                  <el-button size="small" @click="addCategory('archive')">压缩</el-button>
                  <el-button size="small" @click="addCategory('audio')">音频</el-button>
                  <el-button size="small" @click="addCategory('video')">视频</el-button>
                </div>
                <div class="policy-ops" style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                  <span class="cat-label" style="color:#666;">策略导入/导出：</span>
                  <el-button size="small" @click="exportPolicy">导出JSON</el-button>
                  <el-button size="small" type="primary" @click="openImportDialog">导入JSON</el-button>
                </div>
                <div class="template-ops" style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                  <span class="cat-label" style="color:#666;">模板：</span>
                  <el-select v-model="selectedTemplate" placeholder="选择模板" style="min-width:220px;">
                    <el-option v-for="opt in templateOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                  <el-button size="small" type="primary" :disabled="!selectedTemplate" @click="applyTemplate">应用模板</el-button>
                  <span v-if="templatePreview" style="color:#666;">{{ templatePreview }}</span>
                  <el-button size="small" @click="openTemplateDialog">模板配置</el-button>
                </div>
                <div class="quick-check" style="display:flex; align-items:center; gap:8px;">
                  <span class="qc-label" style="color:#666;">文件名快速校验：</span>
                  <el-input v-model="testFilename" placeholder="如：report.pdf" style="max-width:280px;" clearable />
                  <el-tag v-if="testAllowed !== null" :type="testAllowed ? 'success' : 'danger'">{{ testHint }}</el-tag>
                  <span v-else class="hint" style="color:#999;">输入文件名以校验是否允许上传</span>
                </div>
              </div>
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
          <!-- 策略导入对话框 -->
          <el-dialog v-model="showImportDialog" title="导入上传策略（JSON）" width="520px">
            <div style="display:flex; flex-direction:column; gap:10px;">
              <el-alert title="仅支持 allowAll 和 allowedSuffixes 字段；导入后将覆盖当前策略。" type="info" show-icon />
              <div>
                <input type="file" accept="application/json,.json" @change="onImportFileChange" />
              </div>
              <el-input
                v-model="importText"
                type="textarea"
                :rows="8"
                placeholder='可粘贴策略JSON，如：{"allowAll":false,"allowedSuffixes":["jpg","pdf"]}'
              />
              <div v-if="importPreview" style="color:#666;">预览：{{ importPreview }}</div>
              <div v-if="importDiff" style="color:#666;">
                <div>差异：</div>
                <ul style="margin:4px 0 0 16px; line-height:1.6;">
                  <li v-if="importDiff.allowAllChanged">allowAll：{{ importDiff.allowAllFrom ? '开' : '关' }} → {{ importDiff.allowAllTo ? '开' : '关' }}</li>
                  <li>
                    新增后缀：{{ (importDiff.added||[]).length }} 个
                    <template v-if="(importDiff.added||[]).length">
                      <a href="javascript:void(0)" @click="showImportDiffDetails = !showImportDiffDetails" style="margin-left:8px;">{{ showImportDiffDetails ? '收起' : '查看详情' }}</a>
                    </template>
                  </li>
                  <li>
                    移除后缀：{{ (importDiff.removed||[]).length }} 个
                  </li>
                </ul>
                <div v-if="showImportDiffDetails" style="margin:6px 0 0 16px; display:flex; gap:24px;">
                  <div>
                    <div style="font-weight:600;">新增后缀：</div>
                    <ul style="margin:4px 0 0 16px; max-height:120px; overflow:auto;">
                      <li v-for="s in importDiff.added" :key="'add-'+s">{{ s }}</li>
                    </ul>
                  </div>
                  <div>
                    <div style="font-weight:600;">移除后缀：</div>
                    <ul style="margin:4px 0 0 16px; max-height:120px; overflow:auto;">
                      <li v-for="s in importDiff.removed" :key="'rm-'+s">{{ s }}</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
            <template #footer>
              <el-button @click="showImportDialog = false">取消</el-button>
              <el-button type="primary" :loading="importing" @click="confirmImport">确定导入</el-button>
            </template>
          </el-dialog>

          <!-- 模板配置对话框 -->
          <el-dialog v-model="showTemplateDialog" title="上传策略模板配置（JSON）" width="720px">
            <div style="display:flex; flex-direction:column; gap:10px;">
              <el-alert title="编辑模板集合（键为模板名，值为 { allowAll, allowedSuffixes }）。保存后可在模板选择中使用。" type="info" show-icon />
              <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap;">
                <el-button size="small" @click="exportTemplates">导出模板JSON</el-button>
                <el-button size="small" type="warning" @click="resetTemplatesToDefault">恢复默认模板</el-button>
              </div>
              <el-input v-model="templatesJson" type="textarea" :rows="16" placeholder='{"imageOnly":{"allowAll":false,"allowedSuffixes":["jpg","png"]}}' />
              <div v-if="templatesPreview" style="color:#666;">预览：{{ templatesPreview }}</div>
            </div>
            <template #footer>
              <el-button @click="showTemplateDialog = false">取消</el-button>
              <el-button type="primary" :loading="savingTemplates" @click="saveTemplatesFromJson">保存模板</el-button>
            </template>
          </el-dialog>
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
import { ref, onMounted, nextTick, computed } from "vue"
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
    const showImportDialog = ref(false)
    const importText = ref("")
    const importing = ref(false)
    const selectedTemplate = ref("")
    const showImportDiffDetails = ref(false)
    const showTemplateDialog = ref(false)
    const templatesJson = ref("")
    const savingTemplates = ref(false)
    
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
    const categories = {
      image: ['jpg','jpeg','png','gif','webp','bmp','svg'],
      document: ['pdf','doc','docx','xls','xlsx','ppt','pptx','txt','md'],
      archive: ['zip','rar','7z','tar','gz','bz2'],
      audio: ['mp3','wav','flac','aac','ogg'],
      video: ['mp4','avi','mkv','mov','webm']
    }
    const addCategory = (key) => {
      const list = categories[key] || []
      const before = new Set(uploadPolicy.value.allowedSuffixes || [])
      uploadPolicy.value.allowedSuffixes = normalizeSuffixes([...(uploadPolicy.value.allowedSuffixes||[]), ...list])
      const after = new Set(uploadPolicy.value.allowedSuffixes)
      let added = 0
      for (const s of after) if (!before.has(s)) added++
      if (added > 0) ElMessage.success(`已添加 ${added} 个后缀`)
    }
    const testFilename = ref("")
    const testInfo = computed(() => {
      const name = (testFilename.value || '').trim()
      if (!name) return { allowed: null, hint: '' }
      if (uploadPolicy.value.allowAll) return { allowed: true, hint: '允许（未限制类型）' }
      const idx = name.lastIndexOf('.')
      if (idx < 0) return { allowed: false, hint: '不允许：无后缀' }
      const ext = name.slice(idx + 1).toLowerCase()
      if (!/^[a-z0-9]+$/.test(ext)) return { allowed: false, hint: `不允许：非法后缀“${ext}”` }
      const ok = (uploadPolicy.value.allowedSuffixes || []).includes(ext)
      return { allowed: ok, hint: ok ? '允许' : `不允许：未在白名单中（${ext}）` }
    })
    const testAllowed = computed(() => testInfo.value.allowed)
    const testHint = computed(() => testInfo.value.hint)
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

    // 策略导出（JSON 文件）
    const exportPolicy = async () => {
      try {
        // 以当前状态导出，也可先刷新一次 getUploadPolicy()
        const payload = {
          allowAll: !!uploadPolicy.value.allowAll,
          allowedSuffixes: normalizeSuffixes(uploadPolicy.value.allowedSuffixes),
          version: 1,
          exportedAt: new Date().toISOString()
        }
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `upload-policy-${new Date().toISOString().replace(/[:.]/g,'-')}.json`
        a.click()
        URL.revokeObjectURL(url)
      } catch (e) {
        ElMessage.error('导出失败')
      }
    }

    const openImportDialog = () => {
      importText.value = ''
      showImportDialog.value = true
    }
    const onImportFileChange = (e) => {
      try {
        const file = e.target?.files?.[0]
        if (!file) return
        const reader = new FileReader()
        reader.onload = () => {
          try { importText.value = String(reader.result || '') } catch {}
        }
        reader.readAsText(file)
        // 清空 input 以便可重复选择同一文件
        e.target.value = ''
      } catch {}
    }

    const importPreview = computed(() => {
      const txt = (importText.value || '').trim()
      if (!txt) return ''
      try {
        const obj = JSON.parse(txt)
        const allowAll = !!obj.allowAll
        const suffixes = normalizeSuffixes(obj.allowedSuffixes || [])
        return allowAll ? '策略：允许全部文件类型' : `策略：白名单后缀（${suffixes.length}）：${suffixes.join(', ')}`
      } catch {
        return 'JSON 格式不正确'
      }
    })

    const confirmImport = async () => {
      try {
        importing.value = true
        const obj = JSON.parse(importText.value || '{}')
        const allowAll = !!obj.allowAll
        const suffixes = normalizeSuffixes(obj.allowedSuffixes || [])
        if (!allowAll && suffixes.length === 0) {
          ElMessage.error('导入的策略为空白白名单，请添加后缀或开启允许全部')
          return
        }
        await updateUploadPolicy({ allowAll, allowedSuffixes: suffixes })
        uploadPolicy.value.allowAll = allowAll
        uploadPolicy.value.allowedSuffixes = suffixes
        invalidSuffixes.value = []
        showImportDialog.value = false
        ElMessage.success('策略导入成功')
      } catch (e) {
        ElMessage.error('导入失败，请检查JSON内容')
      } finally {
        importing.value = false
      }
    }

    // 策略差异计算（导入/模板预览复用）
    const getPolicyDiff = (curr, next) => {
      const c = { allowAll: !!curr.allowAll, allowedSuffixes: normalizeSuffixes(curr.allowedSuffixes || []) }
      const n = { allowAll: !!next.allowAll, allowedSuffixes: normalizeSuffixes(next.allowedSuffixes || []) }
      const cSet = new Set(c.allowedSuffixes)
      const nSet = new Set(n.allowedSuffixes)
      const added = Array.from(nSet).filter(x => !cSet.has(x))
      const removed = Array.from(cSet).filter(x => !nSet.has(x))
      const allowAllChanged = c.allowAll !== n.allowAll
      return { allowAllChanged, allowAllFrom: c.allowAll, allowAllTo: n.allowAll, added, removed }
    }

    const importDiff = computed(() => {
      const txt = (importText.value || '').trim()
      if (!txt) return null
      try {
        const obj = JSON.parse(txt)
        const next = { allowAll: !!obj.allowAll, allowedSuffixes: normalizeSuffixes(obj.allowedSuffixes || []) }
        return getPolicyDiff(uploadPolicy.value, next)
      } catch { return null }
    })

    const defaultTemplates = {
      allowAll: { allowAll: true, allowedSuffixes: [] },
      imageOnly: { allowAll: false, allowedSuffixes: ['jpg','jpeg','png','gif','webp','bmp','svg'] },
      office: { allowAll: false, allowedSuffixes: ['pdf','doc','docx','xls','xlsx','ppt','pptx','txt','md'] },
      common: { allowAll: false, allowedSuffixes: ['jpg','jpeg','png','gif','webp','pdf','doc','docx','xls','xlsx','ppt','pptx','txt','zip','rar','7z'] },
      media: { allowAll: false, allowedSuffixes: ['jpg','jpeg','png','gif','webp','mp4','webm','mp3','wav'] }
    }
    const TEMPLATES_KEY = 'upload_policy_templates'
    const templatesMap = ref({ ...defaultTemplates })
    const templateOptions = computed(() => Object.keys(templatesMap.value).map(k => ({ value: k, label: k })))
    const templatePreview = computed(() => {
      const key = selectedTemplate.value
      if (!key || !templatesMap.value[key]) return ''
      const diff = getPolicyDiff(uploadPolicy.value, templatesMap.value[key])
      const parts = []
      if (diff.allowAllChanged) parts.push(`allowAll: ${diff.allowAllFrom ? '开' : '关'} → ${diff.allowAllTo ? '开' : '关'}`)
      if (diff.added.length) parts.push(`将新增 ${diff.added.length}`)
      if (diff.removed.length) parts.push(`将移除 ${diff.removed.length}`)
      return parts.length ? `变更预览：${parts.join('，')}` : '与当前一致'
    })
    const applyTemplate = async () => {
      const key = selectedTemplate.value
      const t = templatesMap.value[key]
      if (!t) return
      uploadPolicy.value.allowAll = !!t.allowAll
      uploadPolicy.value.allowedSuffixes = normalizeSuffixes(t.allowedSuffixes || [])
      await saveUploadPolicy()
      ElMessage.success('模板已应用并保存')
    }

    // 模板配置：打开、导出、恢复默认、保存
    const openTemplateDialog = () => {
      try {
        templatesJson.value = JSON.stringify(templatesMap.value, null, 2)
      } catch { templatesJson.value = '' }
      showTemplateDialog.value = true
    }
    const exportTemplates = () => {
      try {
        const blob = new Blob([JSON.stringify(templatesMap.value, null, 2)], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `upload-policy-templates-${new Date().toISOString().replace(/[:.]/g,'-')}.json`
        a.click()
        URL.revokeObjectURL(url)
      } catch { ElMessage.error('导出模板失败') }
    }
    const resetTemplatesToDefault = () => {
      templatesMap.value = { ...defaultTemplates }
      try { localStorage.setItem(TEMPLATES_KEY, JSON.stringify(templatesMap.value)) } catch {}
      templatesJson.value = JSON.stringify(templatesMap.value, null, 2)
      ElMessage.success('已恢复默认模板')
    }
    const saveTemplatesFromJson = () => {
      try {
        savingTemplates.value = true
        const obj = JSON.parse(templatesJson.value || '{}')
        // 基本校验
        const next = {}
        for (const [k, v] of Object.entries(obj)) {
          if (!k || typeof v !== 'object' || v == null) continue
          const allowAll = !!v.allowAll
          const suffixes = normalizeSuffixes(v.allowedSuffixes || [])
          if (!allowAll && suffixes.length === 0) continue
          next[k] = { allowAll, allowedSuffixes: suffixes }
        }
        if (Object.keys(next).length === 0) {
          ElMessage.error('模板内容无效或为空')
          return
        }
        templatesMap.value = next
        try { localStorage.setItem(TEMPLATES_KEY, JSON.stringify(templatesMap.value)) } catch {}
        showTemplateDialog.value = false
        ElMessage.success('模板已保存')
      } catch {
        ElMessage.error('模板JSON格式不正确')
      } finally {
        savingTemplates.value = false
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
        // 加载自定义模板
        try {
          const saved = localStorage.getItem(TEMPLATES_KEY)
          if (saved) {
            const obj = JSON.parse(saved)
            if (obj && typeof obj === 'object') templatesMap.value = obj
          }
        } catch {}
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
      exportPolicy,
      openImportDialog,
      showImportDialog,
      importText,
      importPreview,
      importDiff,
      onImportFileChange,
      importing,
      confirmImport,
      showImportDiffDetails,
      selectedTemplate,
      templateOptions,
      templatePreview,
      applyTemplate,
      openTemplateDialog,
      showTemplateDialog,
      templatesJson,
      templatesPreview,
      savingTemplates,
      exportTemplates,
      resetTemplatesToDefault,
      saveTemplatesFromJson,
      addCommonSuffixes,
      clearSuffixes,
      saveUploadPolicy,
      addCategory,
      testFilename,
      testAllowed,
      testHint,
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
