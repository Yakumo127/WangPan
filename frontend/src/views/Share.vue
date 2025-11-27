<template>
  <div class="share-container">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="showShareDialog = true">
          <el-icon><Share /></el-icon>
          创建分享
        </el-button>
        <el-button @click="refreshShares" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      
      <div class="toolbar-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索分享..."
          style="width: 200px"
          clearable
          @keyup.enter="searchShares"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="searchShares">搜索</el-button>
      </div>
    </div>

    <!-- 分享统计 -->
    <div class="stats-row">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Share /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalShares || 0 }}</div>
              <div class="stat-label">总分享数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Link /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.activeShares || 0 }}</div>
              <div class="stat-label">有效分享</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><Download /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalDownloads || 0 }}</div>
              <div class="stat-label">总下载次数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon">
              <el-icon><View /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-number">{{ shareStats.totalViews || 0 }}</div>
              <div class="stat-label">总浏览次数</div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分享列表 -->
    <div class="share-list">
      <el-table
        :data="filteredShares"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column label="文件信息" min-width="300">
          <template #default="{ row }">
            <div class="share-info">
              <el-icon class="share-icon">
                <Document v-if="row.type === 'file'" />
                <Folder v-else />
              </el-icon>
              <div class="share-details">
                <div class="share-name">{{ row.name || '未命名' }}</div>
                <div class="share-meta">
                  <el-tag size="small" :type="row.type === 'file' ? 'primary' : 'success'">
                    {{ row.type === 'file' ? '文件' : '文件夹' }}
                  </el-tag>
                  <span class="share-size">{{ formatFileSize(row.size) }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="权限" width="180">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.allowPreview">预览</el-tag>
            <el-tag size="small" v-if="row.allowDownload" type="success">下载</el-tag>
            <el-tag size="small" v-if="row.allowUpload" type="info">上传</el-tag>
            <el-tag size="small" v-if="row.allowReshare" type="warning">再分享</el-tag>
            <el-tag size="small" v-if="row.allowDeleteMove" type="danger">删/移</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分享链接" min-width="200">
          <template #default="{ row }">
            <div class="share-link">
              <el-input
                v-model="row.shareUrl"
                readonly
                size="small"
                :style="{ width: '100%' }"
              >
                <template #append>
                  <el-button @click="copyShareLink(row)" size="small">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                </template>
              </el-input>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="expireTime" label="过期时间" width="180">
          <template #default="{ row }">
            <span v-if="row.expireTime" :class="{ 'expired': isExpired(row.expireTime) }">
              {{ formatDateTime(row.expireTime) }}
            </span>
            <span v-else class="no-expire">永久有效</span>
          </template>
        </el-table-column>
        <el-table-column label="统计" width="120">
          <template #default="{ row }">
            <div class="share-stats">
              <div class="stat-item">
                <el-icon><View /></el-icon>
                {{ row.viewCount || 0 }}
              </div>
              <div class="stat-item">
                <el-icon><Download /></el-icon>
                {{ row.downloadCount || 0 }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getShareStatusType(row)">
              {{ getShareStatus(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" @click="viewShare(row)">
                <el-icon><View /></el-icon>
                查看
              </el-button>
              <el-button size="small" @click="editShare(row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="deleteShare(row)">
                <el-icon><Delete /></el-icon>
                取消
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager" v-if="pagination.total > pagination.pageSize">
        <el-pagination
          layout="prev, pager, next"
          :page-size="pagination.pageSize"
          :current-page="pagination.page"
          :total="pagination.total"
          background
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 编辑分享对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑分享" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="过期时间">
          <el-radio-group v-model="editForm.expireType">
            <el-radio label="never">永久有效</el-radio>
            <el-radio label="custom">自定义时间</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.expireType === 'custom'" label="选择时间">
          <el-date-picker v-model="editForm.expireTime" type="datetime" placeholder="选择过期时间" :disabled-date="disabledDate" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分享模式">
          <el-radio-group v-model="editForm.shareMode">
            <el-radio label="PUBLIC">公开</el-radio>
            <el-radio label="CONTROLLED">受控</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="提取码">
          <el-switch v-model="editForm.requireCode" active-text="需要提取码" inactive-text="不需要提取码" />
        </el-form-item>
        <el-form-item v-if="editForm.requireCode" label="提取码内容">
          <el-input v-model="editForm.code" maxlength="8" show-word-limit placeholder="请输入提取码（4-8位）" />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="editPermissionSelections">
            <el-checkbox label="preview">预览</el-checkbox>
            <el-checkbox label="download">下载</el-checkbox>
            <el-checkbox label="upload" :disabled="editForm.type === 'file'">上传</el-checkbox>
            <el-checkbox label="reshare" :disabled="true">再分享(禁用)</el-checkbox>
            <el-checkbox label="deleteMove" :disabled="true">删除/移动(禁用)</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="ACL">
          <el-button size="small" @click="openAclDialog(editForm.id)">管理受邀人权限</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="updateShareFunc">保存</el-button>
      </template>
    </el-dialog>

    <!-- ACL 对话框 -->
    <el-dialog v-model="showAclDialog" title="受邀人权限" width="600px">
      <el-table :data="aclList" size="small" style="width: 100%; margin-bottom: 12px;">
        <el-table-column prop="principalType" label="类型" width="100" />
        <el-table-column prop="principalValue" label="标识" />
        <el-table-column label="权限" width="200">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.allowPreview">预览</el-tag>
            <el-tag size="small" v-if="row.allowDownload" type="success">下载</el-tag>
            <el-tag size="small" v-if="row.allowUpload" type="info">上传</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ $index }">
            <el-button size="small" type="danger" @click="removeAcl($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="acl-form">
        <el-select v-model="newAcl.principalType" placeholder="类型" style="width: 120px">
          <el-option label="用户" value="USER" />
          <el-option label="邮箱" value="EMAIL" />
          <el-option label="组" value="GROUP" />
        </el-select>
        <el-input v-model="newAcl.principalValue" placeholder="用户ID/邮箱/组" style="width: 200px; margin-left: 8px" />
        <el-checkbox-group v-model="newAcl.perms" style="margin-left: 8px">
          <el-checkbox label="preview">预览</el-checkbox>
          <el-checkbox label="download">下载</el-checkbox>
          <el-checkbox label="upload">上传</el-checkbox>
        </el-checkbox-group>
        <el-button size="small" type="primary" style="margin-left: 8px" @click="addAcl">添加</el-button>
      </div>
      <template #footer>
        <el-button @click="showAclDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAcl">保存</el-button>
      </template>
    </el-dialog>

    <!-- 创建分享对话框 -->
    <el-dialog v-model="showShareDialog" title="创建分享" width="600px">
      <el-form :model="shareForm" label-width="100px">
        <el-form-item label="选择类型">
          <el-radio-group v-model="shareForm.type">
            <el-radio label="file">文件</el-radio>
            <el-radio label="folder">文件夹</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分享模式">
          <el-radio-group v-model="shareForm.shareMode">
            <el-radio label="PUBLIC">公开</el-radio>
            <el-radio label="CONTROLLED">受控</el-radio>
          </el-radio-group>
          <div class="hint-small">公开模式自动禁用上传/再分享/删除移动</div>
        </el-form-item>
        <el-form-item label="分享模式">
          <el-radio-group v-model="shareForm.shareMode">
            <el-radio label="PUBLIC">公开</el-radio>
            <el-radio label="CONTROLLED">受控</el-radio>
          </el-radio-group>
          <div class="hint-small">公开模式自动禁用上传/再分享/删除移动</div>
        </el-form-item>
        
        <el-form-item :label="shareForm.type === 'file' ? '选择文件' : '选择文件夹'">
          <el-select
            v-model="shareForm.itemId"
            :placeholder="`请选择${shareForm.type === 'file' ? '文件' : '文件夹'}`"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="item in availableItems.filter(i => i.type === shareForm.type)"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
              <div class="option-item">
                <el-icon>
                  <Document v-if="item.type === 'file'" />
                  <Folder v-else />
                </el-icon>
                <span>{{ item.name }}</span>
                <span class="item-size">{{ formatFileSize(item.size) }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="过期时间">
          <el-radio-group v-model="shareForm.expireType">
            <el-radio label="never">永久有效</el-radio>
            <el-radio label="custom">自定义时间</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item v-if="shareForm.expireType === 'custom'" label="选择时间">
          <el-date-picker
            v-model="shareForm.expireTime"
            type="datetime"
            placeholder="选择过期时间"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="提取码">
          <el-switch
            v-model="shareForm.requireCode"
            active-text="需要提取码"
            inactive-text="不需要提取码"
          />
        </el-form-item>
        <el-form-item v-if="shareForm.requireCode" label="提取码内容">
          <el-input v-model="shareForm.code" maxlength="8" show-word-limit placeholder="请输入提取码（4-8位）" />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="permissionSelections">
            <el-checkbox label="preview">预览</el-checkbox>
            <el-checkbox label="download">下载</el-checkbox>
            <el-checkbox label="upload" :disabled="shareForm.type === 'file' || shareForm.shareMode === 'PUBLIC'">上传</el-checkbox>
            <el-checkbox label="reshare" :disabled="true">再分享(禁用)</el-checkbox>
            <el-checkbox label="deleteMove" :disabled="true">删除/移动(禁用)</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="createShareFunc" :loading="creating">
          创建分享
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Share, Refresh, Search, Document, Folder, CopyDocument, Edit, Delete, View, Link, Download } from '@element-plus/icons-vue'
import { listShares, createShare, revokeShare, updateShare, getShareAcl, replaceShareAcl } from '@/api/share'
import { getFileList } from '@/api/file'
import { getFolderList } from '@/api/folder'

const route = useRoute()

const loading = ref(false)
const creating = ref(false)
const updating = ref(false)
const shares = ref([])
const searchKeyword = ref('')
const showShareDialog = ref(false)
const showEditDialog = ref(false)
const showAclDialog = ref(false)
const availableItems = ref([])
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const shareForm = reactive({
  type: 'file',
  itemId: '',
  expireType: 'never',
  expireTime: null,
  requireCode: false,
  code: '',
  allowPreview: true,
  allowDownload: true,
  allowUpload: false,
  allowReshare: false,
  allowDeleteMove: false,
  shareMode: 'PUBLIC'
})

const shareStats = ref({
  totalShares: 0,
  activeShares: 0,
  totalDownloads: 0,
  totalViews: 0
})
const editForm = reactive({
  id: null,
  type: 'file',
  expireType: 'never',
  expireTime: null,
  requireCode: false,
  code: '',
  shareMode: 'PUBLIC'
})
const editPermissionSelections = ref(['preview', 'download'])
const aclDialogShareId = ref(null)
const aclList = ref([])
const newAcl = reactive({
  principalType: 'USER',
  principalValue: '',
  perms: ['preview', 'download']
})
const permissionSelections = ref(['preview', 'download'])

const filteredShares = computed(() => {
  if (!searchKeyword.value) return shares.value
  return shares.value.filter(s => (s.name || '').toLowerCase().includes(searchKeyword.value.toLowerCase()))
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

// 检查是否过期
const isExpired = (expireTime) => {
  return new Date(expireTime) < new Date()
}

// 获取分享状态
const getShareStatus = (share) => {
  if (share.expireTime && isExpired(share.expireTime)) {
    return '已过期'
  }
  return share.active ? '有效' : '已取消'
}

// 获取分享状态类型
const getShareStatusType = (share) => {
  if (share.expireTime && isExpired(share.expireTime)) {
    return 'danger'
  }
  return share.active ? 'success' : 'info'
}

// 禁用过去的日期
const disabledDate = (time) => {
  return time.getTime() < Date.now()
}

// 加载分享列表
const loadShares = async () => {
  loading.value = true
  try {
    const res = await listShares({ page: pagination.page - 1, size: pagination.pageSize })
    const rawItems = Array.isArray(res) ? res : (res?.items || [])
    const total = Array.isArray(res) ? rawItems.length : (res?.total ?? rawItems.length)
    shares.value = (rawItems || []).map(item => ({
      id: item.id,
      name: item.name || item.originalFilename || `资源#${item.resourceId}`,
      type: (item.resourceType || 'FILE').toLowerCase() === 'folder' ? 'folder' : 'file',
      size: item.size || 0,
      shareUrl: `${window.location.origin}/s/${item.id}`,
      createTime: item.createdAt || item.createTime,
      expireTime: item.expireTime,
      viewCount: item.viewCount,
      downloadCount: item.downloadCount,
      active: item.status === 'ACTIVE',
      allowPreview: item.allowPreview,
      allowDownload: item.allowDownload,
      allowUpload: item.allowUpload,
      allowReshare: item.allowReshare,
      allowDeleteMove: item.allowDeleteMove,
      shareMode: item.shareMode || 'PUBLIC'
    }))
    pagination.total = total
    const statsPayload = !Array.isArray(res) ? res?.stats : null
    const computedStats = {
      totalShares: shares.value.length,
      activeShares: shares.value.filter(s => s.active).length,
      totalDownloads: shares.value.reduce((sum, s) => sum + (s.downloadCount || 0), 0),
      totalViews: shares.value.reduce((sum, s) => sum + (s.viewCount || 0), 0)
    }
    shareStats.value = {
      totalShares: statsPayload?.totalShares ?? computedStats.totalShares,
      activeShares: statsPayload?.activeShares ?? computedStats.activeShares,
      totalDownloads: statsPayload?.totalDownloads ?? computedStats.totalDownloads,
      totalViews: statsPayload?.totalViews ?? computedStats.totalViews
    }
  } catch (error) {
    ElMessage.error('加载分享列表失败')
  } finally {
    loading.value = false
  }
}

// 加载可选项目
const loadAvailableItems = async () => {
  try {
    const res = await getFileList({ page: 0, size: 100 })
    const list = res?.content || res || []
    const files = list.map(f => ({
      id: f.id,
      name: f.originalFilename,
      type: 'file',
      size: f.size
    }))
    const foldersRes = await getFolderList({ parentId: null })
    const folders = (foldersRes || []).map(f => ({
      id: f.id,
      name: f.name,
      type: 'folder',
      size: 0
    }))
    availableItems.value = [...files, ...folders]
    applyPrefillFromRoute()
  } catch (error) {
    console.error('加载可选项目失败:', error)
  }
}

const applyPrefillFromRoute = () => {
  const qId = route.query?.id
  if (!qId) return
  const typeParam = (route.query?.type || '').toString().toLowerCase()
  const type = typeParam === 'folder' ? 'folder' : 'file'
  shareForm.type = type
  shareForm.itemId = Number(qId) || qId
  shareForm.shareMode = 'PUBLIC'
  permissionSelections.value = ['preview', 'download']
  showShareDialog.value = true
}

// 刷新分享列表
const refreshShares = () => {
  pagination.page = 1
  loadShares()
}

const handlePageChange = async (p) => {
  pagination.page = p
  await loadShares()
}

// 搜索分享
const searchShares = () => {
  pagination.page = 1
}

// 复制分享链接
const copyShareLink = async (share) => {
  try {
    await navigator.clipboard.writeText(share.shareUrl)
    ElMessage.success('分享链接已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制分享链接失败')
  }
}

// 查看分享
const viewShare = (share) => {
  window.open(share.shareUrl, '_blank')
}

// 编辑分享
const editShare = (share) => {
  editForm.id = share.id
  editForm.type = share.type
  editForm.expireType = share.expireTime ? 'custom' : 'never'
  editForm.expireTime = share.expireTime ? new Date(share.expireTime) : null
  editForm.shareMode = share.shareMode || 'PUBLIC'
  editForm.requireCode = false
  editForm.code = ''
  editPermissionSelections.value = []
  if (share.allowPreview) editPermissionSelections.value.push('preview')
  if (share.allowDownload) editPermissionSelections.value.push('download')
  if (share.allowUpload && share.type === 'folder') editPermissionSelections.value.push('upload')
  showEditDialog.value = true
}

// 删除分享
const deleteShare = async (share) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消分享 "${share.name}" 吗？`,
      '取消分享',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await revokeShare(share.id)
    ElMessage.success('分享已取消')
    await loadShares()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消分享失败')
    }
  }
}

// 创建分享
const createShareFunc = async () => {
  if (!shareForm.itemId) {
    ElMessage.warning('请选择要分享的文件或文件夹')
    return
  }
  if (shareForm.requireCode) {
    if (!shareForm.code || shareForm.code.length < 4 || shareForm.code.length > 8) {
      ElMessage.warning('提取码长度需 4-8 位')
      return
    }
  }

  creating.value = true
  try {
    const newItem = availableItems.value.find(item => item.id === shareForm.itemId)
    const payload = {
      resourceType: shareForm.type === 'folder' ? 'FOLDER' : 'FILE',
      resourceId: shareForm.itemId,
      expireTime: shareForm.expireType === 'custom' ? shareForm.expireTime : null,
      code: shareForm.requireCode ? shareForm.code : null,
      shareMode: shareForm.shareMode,
      allowPreview: permissionSelections.value.includes('preview'),
      allowDownload: permissionSelections.value.includes('download'),
      allowUpload: permissionSelections.value.includes('upload'),
      allowReshare: false,
      allowDeleteMove: false
    }
    await createShare(payload)
    ElMessage.success('分享创建成功')
    showShareDialog.value = false
    // 重置表单
    shareForm.itemId = ''
    shareForm.expireType = 'never'
    shareForm.expireTime = null
    shareForm.requireCode = false
    shareForm.code = ''
    permissionSelections.value = ['preview', 'download']
    shareForm.shareMode = 'PUBLIC'
    await loadShares()
  } catch (error) {
    ElMessage.error('创建分享失败')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadShares()
  loadAvailableItems()
})

watch(() => shareForm.shareMode, (mode) => {
  if (mode === 'PUBLIC') {
    permissionSelections.value = permissionSelections.value.filter(p => p !== 'upload' && p !== 'reshare' && p !== 'deleteMove')
  }
})

watch(() => editForm.shareMode, (mode) => {
  if (mode === 'PUBLIC') {
    editPermissionSelections.value = editPermissionSelections.value.filter(p => p !== 'upload' && p !== 'reshare' && p !== 'deleteMove')
  }
})

const updateShareFunc = async () => {
  if (!editForm.id) {
    return
  }
  if (editForm.requireCode) {
    if (!editForm.code || editForm.code.length < 4 || editForm.code.length > 8) {
      ElMessage.warning('提取码长度需 4-8 位')
      return
    }
  }
  updating.value = true
  try {
    const payload = {
      expireTime: editForm.expireType === 'custom' ? editForm.expireTime : null,
      code: editForm.requireCode ? editForm.code : null,
      shareMode: editForm.shareMode,
      allowPreview: editPermissionSelections.value.includes('preview'),
      allowDownload: editPermissionSelections.value.includes('download'),
      allowUpload: editForm.type === 'folder' && editPermissionSelections.value.includes('upload'),
      allowReshare: false,
      allowDeleteMove: false
    }
    await updateShare(editForm.id, payload)
    ElMessage.success('保存成功')
    showEditDialog.value = false
    await loadShares()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    updating.value = false
  }
}

const openAclDialog = async (shareId) => {
  if (!shareId) return
  aclDialogShareId.value = shareId
  try {
    const res = await getShareAcl(shareId)
    aclList.value = (res || []).map(a => ({
      principalType: a.principalType,
      principalValue: a.principalValue,
      allowPreview: a.allowPreview,
      allowDownload: a.allowDownload,
      allowUpload: a.allowUpload
    }))
    showAclDialog.value = true
  } catch (e) {
    ElMessage.error('加载 ACL 失败')
  }
}

const addAcl = () => {
  if (!newAcl.principalValue || newAcl.principalValue.trim() === '') {
    ElMessage.warning('请输入标识')
    return
  }
  if (newAcl.principalType === 'EMAIL' && !newAcl.principalValue.includes('@')) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  if (!newAcl.perms.length) {
    ElMessage.warning('请选择权限')
    return
  }
  aclList.value.push({
    principalType: newAcl.principalType,
    principalValue: newAcl.principalValue.trim(),
    allowPreview: newAcl.perms.includes('preview'),
    allowDownload: newAcl.perms.includes('download'),
    allowUpload: newAcl.perms.includes('upload')
  })
  newAcl.principalValue = ''
  newAcl.perms = ['preview', 'download']
}

const removeAcl = (idx) => {
  aclList.value.splice(idx, 1)
}

const saveAcl = async () => {
  if (!aclDialogShareId.value) return
  try {
    const payload = aclList.value.map(a => ({
      principalType: a.principalType,
      principalValue: a.principalValue,
      allowPreview: a.allowPreview,
      allowDownload: a.allowDownload,
      allowUpload: a.allowUpload,
      allowReshare: false,
      allowDeleteMove: false
    }))
    await replaceShareAcl(aclDialogShareId.value, payload)
    ElMessage.success('ACL 已保存')
    showAclDialog.value = false
  } catch (e) {
    ElMessage.error('保存 ACL 失败')
  }
}
</script>

<style scoped>
.share-container {
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
  color: #409EFF;
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

.share-list {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  overflow: hidden;
}

.share-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.share-icon {
  font-size: 18px;
  color: #409EFF;
}

.share-details {
  flex: 1;
}

.share-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.share-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.share-size {
  font-size: 12px;
  color: #666;
}

.share-stats {
  display: flex;
  gap: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: #666;
}

.expired {
  color: #f56c6c;
  font-weight: bold;
}

.no-expire {
  color: #67c23a;
  font-weight: bold;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-size {
  margin-left: auto;
  font-size: 12px;
  color: #666;
}

.hint-small {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
}

.acl-form {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

:deep(.el-table) {
  height: 100%;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

.pager {
  padding: 12px 16px;
  text-align: right;
  border-top: 1px solid #f0f0f0;
}
</style>
