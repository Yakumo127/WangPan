<template>
  <div class="public-share">
    <h2>文件分享</h2>
    <div v-if="loading" class="hint">加载中...</div>
    <div v-else>
      <div class="card">
        <div><strong>资源类型：</strong>{{ shareInfo.resourceType === 'FOLDER' ? '文件夹' : '文件' }}</div>
        <div><strong>过期时间：</strong>{{ shareInfo.expireTime ? formatDateTime(shareInfo.expireTime) : '永久' }}</div>
        <div><strong>需要提取码：</strong>{{ shareInfo.requireCode ? '是' : '否' }}</div>
      </div>

      <div v-if="shareInfo.requireCode && !sessionToken" class="code-box">
        <el-input v-model="code" placeholder="请输入提取码" style="width: 220px; margin-right: 8px;" />
        <el-button type="primary" @click="validate">验证</el-button>
      </div>

      <div v-else class="content">
        <div v-if="shareInfo.resourceType === 'FILE'" class="single">
          <div class="name">{{ singleItem?.name }}</div>
          <el-button type="primary" @click="download(singleItem)">下载</el-button>
        </div>
        <div v-else>
          <el-table :data="items" style="width: 100%">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                {{ row.type === 'folder' ? '文件夹' : '文件' }}
              </template>
            </el-table-column>
            <el-table-column prop="size" label="大小" width="120">
              <template #default="{ row }">{{ formatSize(row.size) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="download(row)" :disabled="row.type === 'folder'">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPublicShare, validateShare, listShareContent, getShareDownloadUrl } from '@/api/share'
import { ElMessage } from 'element-plus'

const route = useRoute()
const shareId = route.params.id

const shareInfo = ref({})
const sessionToken = ref('')
const code = ref('')
const loading = ref(false)
const items = ref([])
const singleItem = ref(null)

const formatDateTime = (dt) => dt ? new Date(dt).toLocaleString() : ''
const formatSize = (size) => {
  if (!size) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(size) / Math.log(k))
  return `${(size / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

const loadShareInfo = async () => {
  loading.value = true
  try {
    const res = await getPublicShare(shareId)
    shareInfo.value = res
    if (!res.requireCode) {
      await validate()
    }
  } catch (e) {
    ElMessage.error(e?.message || '分享已失效')
  } finally {
    loading.value = false
  }
}

const validate = async () => {
  try {
    const payload = {}
    if (code.value) payload.code = code.value
    const res = await validateShare(shareId, payload)
    sessionToken.value = res.sessionToken
    if (shareInfo.value.resourceType === 'FILE') {
      singleItem.value = { id: shareInfo.value.resourceId, name: res.name || '文件', type: 'file', size: res.size || 0 }
    } else {
      await loadList()
    }
    ElMessage.success('验证成功')
  } catch (e) {
    ElMessage.error(e?.message || '验证失败')
  }
}

const loadList = async () => {
  if (!sessionToken.value) return
  try {
    const res = await listShareContent(shareId, { token: sessionToken.value })
    items.value = res || []
  } catch (e) {
    ElMessage.error(e?.message || '加载列表失败')
  }
}

const download = async (row) => {
  if (!row || row.type === 'folder') return
  try {
    const res = await getShareDownloadUrl(shareId, row.id, sessionToken.value)
    const url = res.url
    if (!url) throw new Error('链接为空')
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

onMounted(() => {
  loadShareInfo()
})
</script>

<style scoped>
.public-share { padding: 20px; }
.card { background: #fff; padding: 12px; border-radius: 8px; margin-bottom: 12px; }
.code-box { margin: 12px 0; display: flex; align-items: center; }
.hint { color: #888; }
.single { display: flex; align-items: center; gap: 12px; }
</style>
