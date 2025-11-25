<template>
  <div class="history-page">
    <div class="history-header">
      <div>
        <div class="history-title">历史版本</div>
        <div class="history-sub">文件：{{ fileName || ('#' + fileId) }}</div>
      </div>
      <el-button @click="goBack">
        返回
      </el-button>
    </div>

    <el-table
      :data="versions"
      v-loading="loading"
      border
      style="width: 100%"
    >
      <el-table-column prop="createTime" label="时间" min-width="180">
        <template #default="{ row }">
          <div class="time-cell">
            <span>{{ formatDateTime(row.createTime) }}</span>
            <el-tag
              v-if="row.versionNo === latestVersionNo"
              size="small"
              type="success"
              effect="plain"
            >
              当前版本
            </el-tag>
            <el-tag
              size="small"
              effect="plain"
              style="margin-left: 6px;"
            >
              v{{ row.versionNo }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="修改者" min-width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="28" :icon="UserFilled">
              {{ getUserLabel(row.createdBy).slice(0, 1).toUpperCase() }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">{{ getUserLabel(row.createdBy) }}</div>
              <div class="user-id">ID: {{ row.createdBy }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="size" label="大小" width="140">
        <template #default="{ row }">
          {{ formatFileSize(row.size) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ row }">
          <el-dropdown trigger="click">
            <el-button circle text>
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="downloadVersion(row)">下载</el-dropdown-item>
                <el-dropdown-item @click="previewVersion(row)">查看</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, MoreFilled } from '@element-plus/icons-vue'
import { getFileVersions, downloadFileVersion } from '@/api/file'
import { getUserInfo } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const fileId = Number(route.params.id)
const fileName = route.query.name || ''

const versions = ref([])
const loading = ref(false)
const currentUser = ref({ id: null, username: '' })

const latestVersionNo = computed(() => {
  return versions.value.length ? Math.max(...versions.value.map(v => v.versionNo || 0)) : null
})

const goBack = () => {
  router.back()
}

const loadUser = async () => {
  try {
    const info = await getUserInfo()
    currentUser.value = { id: info?.id || info?.userId || null, username: info?.username || '' }
  } catch (e) {
    currentUser.value = { id: null, username: '' }
  }
}

const loadVersions = async () => {
  loading.value = true
  try {
    const res = await getFileVersions(fileId)
    versions.value = Array.isArray(res) ? res : []
  } catch (e) {
    versions.value = []
    ElMessage.error('加载历史版本失败')
  } finally {
    loading.value = false
  }
}

const formatDateTime = (datetime) => {
  if (!datetime) return ''
  const d = new Date(datetime)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString()
}

const formatFileSize = (bytes) => {
  const n = Number(bytes)
  if (!n || n <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(n) / Math.log(k))
  return `${parseFloat((n / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`
}

const getUserLabel = (id) => {
  if (currentUser.value.id && id === currentUser.value.id) {
    return currentUser.value.username || `用户#${id}`
  }
  return id ? `用户#${id}` : '未知用户'
}

const downloadVersion = async (row) => {
  try {
    const resp = await downloadFileVersion(fileId, row.versionNo)
    const blob = resp?.data instanceof Blob ? resp.data : resp
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    const base = fileName || `file_${fileId}`
    a.href = url
    a.download = `${base}_v${row.versionNo}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    console.error('下载版本失败', e)
    ElMessage.error('下载失败')
  }
}

const isPreviewable = (row, blob) => {
  const ct = (row.contentType || blob?.type || '').toLowerCase()
  if (ct.startsWith('image/')) return true
  if (ct === 'application/pdf') return true
  if (ct.startsWith('text/')) return true
  return false
}

const previewVersion = async (row) => {
  try {
    const resp = await downloadFileVersion(fileId, row.versionNo)
    const blob = resp?.data instanceof Blob ? resp.data : resp
    if (!isPreviewable(row, blob)) {
      await downloadVersion(row)
      return
    }
    const url = window.URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => {
      try { window.URL.revokeObjectURL(url) } catch (e) {}
    }, 60 * 1000)
  } catch (e) {
    console.error('预览版本失败', e)
    ElMessage.error('预览失败')
  }
}

onMounted(async () => {
  await loadUser()
  await loadVersions()
})
</script>

<style scoped>
.history-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-title {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.history-sub {
  color: #64748b;
  font-size: 13px;
  margin-top: 4px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 600;
  color: #1e293b;
}

.user-id {
  font-size: 12px;
  color: #94a3b8;
}
</style>
