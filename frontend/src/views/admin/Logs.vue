<template>
  <div class="logs-page">
    <el-card class="mb-12">
      <div class="filters">
        <el-select v-model="filters.actions" multiple collapse-tags placeholder="动作" style="width: 260px" clearable>
          <el-option v-for="a in actionOptionsZh" :key="a" :label="a" :value="a" />
        </el-select>
        <el-button size="small" @click="toggleSelectAll">{{ allSelected ? '全不选' : '全选' }}</el-button>
        <el-select v-model="filters.resourceTypes" multiple collapse-tags placeholder="资源类型" style="width: 200px" clearable>
          <el-option v-for="t in resourceTypeOptionsZh" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" style="width: 140px" clearable>
          <el-option label="成功" value="成功" />
          <el-option label="失败" value="失败" />
        </el-select>
        <el-date-picker
          v-model="filters.range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
        <el-input v-model="filters.keyword" placeholder="用户名/昵称关键词" style="width: 220px" clearable />
        <el-input v-model="filters.reason" placeholder="理由/描述关键词" style="width: 220px" clearable />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="success" @click="doExport('csv')" :loading="exporting">导出CSV</el-button>
        <el-button type="success" @click="doExport('xlsx')" :loading="exporting">导出XLSX</el-button>
      </div>
    </el-card>

    <el-card>
      <el-table :data="rows" stripe border :height="tableHeight">
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="昵称" width="140" />
        <el-table-column prop="actionType" label="动作" width="180">
          <template #default="scope">{{ actionEnToZh(scope.row.actionType) }}</template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源类型" width="100">
          <template #default="scope">{{ resourceEnToZh(scope.row.resourceType) }}</template>
        </el-table-column>
        <el-table-column prop="resourceId" label="资源ID" width="100" />
        <el-table-column prop="resourceName" label="资源名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status==='SUCCESS' ? 'success' : 'danger'">{{ statusEnToZh(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executionTime" label="耗时(ms)" width="110" />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="userAgent" label="UA" min-width="200" show-overflow-tooltip />
        <el-table-column prop="errorMessage" label="错误" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="mt-12 flex-center">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-size="page.size"
          :page-sizes="[10,20,50,100]"
          :current-page="page.current"
          :total="page.total"
          @size-change="val => { page.size = val; load(); }"
          @current-change="val => { page.current = val; load(); }"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getLogs, exportLogs, getLogDictionaries } from '@/api/logs'

// 英文到中文的映射
const ACTION_MAP = {
  LOGIN: '登录', LOGOUT: '登出', UPLOAD: '上传', DOWNLOAD: '下载', DELETE: '删除', COPY: '复制', MOVE: '移动', RENAME: '重命名', RESTORE: '恢复',
  CREATE_FOLDER: '创建文件夹', DELETE_FOLDER: '删除文件夹', UPDATE_PROFILE: '更新资料', CHANGE_PASSWORD: '修改密码',
  ADMIN_SCHEDULE_DELETE: '发起彻底删除', ADMIN_RESTORE: '管理员恢复', ADMIN_PURGE_EXPIRED: '到期自动删除', RECYCLE_REMOVE: '从个人回收站移除', RECYCLE_EMPTY: '清空个人回收站'
}
const RESOURCE_MAP = { FILE: '文件', FOLDER: '文件夹', USER: '用户', API: 'API', SYSTEM: '系统' }
const STATUS_MAP = { SUCCESS: '成功', FAILED: '失败', PENDING: '进行中' }

const ACTION_MAP_REV = Object.fromEntries(Object.entries(ACTION_MAP).map(([en, zh]) => [zh, en]))
const RESOURCE_MAP_REV = Object.fromEntries(Object.entries(RESOURCE_MAP).map(([en, zh]) => [zh, en]))
const STATUS_MAP_REV = Object.fromEntries(Object.entries(STATUS_MAP).map(([en, zh]) => [zh, en]))

const actionOptionsZh = ref([])
const resourceTypeOptionsZh = ref([])

const filters = reactive({
    actions: [],
    resourceTypes: [],
    status: '',
    range: [],
    keyword: '',
    reason: ''
})

const page = reactive({ current: 1, size: 20, total: 0 })
const rows = ref([])
const loading = ref(false)
const exporting = ref(false)
const tableHeight = computed(() => window.innerHeight - 320)

const buildParams = () => {
  return {
    page: page.current - 0 - 1 < 0 ? 0 : page.current - 1,
    size: page.size,
    actions: filters.actions && filters.actions.length ? filters.actions.map(a => ACTION_MAP_REV[a]).filter(Boolean).join(',') : undefined,
    resourceTypes: filters.resourceTypes && filters.resourceTypes.length ? filters.resourceTypes.map(t => RESOURCE_MAP_REV[t]).filter(Boolean).join(',') : undefined,
    status: filters.status ? STATUS_MAP_REV[filters.status] : undefined,
    keyword: filters.keyword || undefined,
    from: filters.range && filters.range.length ? filters.range[0] : undefined,
    to: filters.range && filters.range.length ? filters.range[1] : undefined,
    reason: filters.reason || undefined,
    sort: 'createTime',
    order: 'desc'
  }
}

const load = async () => {
  loading.value = true
  try {
    const res = await getLogs(buildParams())
    rows.value = res.content || []
    page.total = res.totalElements || 0
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.actions = []
  filters.resourceTypes = []
  filters.status = ''
  filters.range = []
  filters.keyword = ''
  filters.reason = ''
  page.current = 1
  load()
}

const doExport = async (fmt) => {
  exporting.value = true
  try {
    const resp = await exportLogs(buildParams(), fmt)
    const blob = new Blob([resp.data], { type: resp.headers['content-type'] || 'application/octet-stream' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ts = new Date().toISOString().replace(/[:.]/g,'')
    a.download = `user-logs_${ts}.${fmt}`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  try {
    const dict = await getLogDictionaries()
    // 将后端英文选项映射为中文显示
    const actionsEn = dict.actions || Object.keys(ACTION_MAP)
    actionOptionsZh.value = actionsEn.map(a => ACTION_MAP[a]).filter(Boolean)
    const rtypesEn = dict.resourceTypes || Object.keys(RESOURCE_MAP)
    resourceTypeOptionsZh.value = rtypesEn.map(t => RESOURCE_MAP[t]).filter(Boolean)
  } catch (e) {
    // 回退默认
    actionOptionsZh.value = Object.values(ACTION_MAP)
    resourceTypeOptionsZh.value = Object.values(RESOURCE_MAP)
  }
  await load()
})

const actionEnToZh = (en) => ACTION_MAP[en] || en
const resourceEnToZh = (en) => RESOURCE_MAP[en] || en
const statusEnToZh = (en) => STATUS_MAP[en] || en

const allSelected = computed(() => filters.actions && actionOptionsZh.value.length > 0 && filters.actions.length === actionOptionsZh.value.length)
const toggleSelectAll = () => {
  if (allSelected.value) {
    filters.actions = []
  } else {
    filters.actions = [...actionOptionsZh.value]
  }
}
</script>

<style scoped>
.logs-page { }
.filters { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }
.flex-center { display: flex; justify-content: center; }
</style>
