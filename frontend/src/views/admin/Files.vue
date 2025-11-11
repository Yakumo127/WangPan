<template>
  <div class="files-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>文件管理</span>
          <el-button type="primary" @click="showUploadDialog = true">
            上传文件
          </el-button>
        </div>
      </template>
      
      <div style="margin-bottom:12px; display:flex; gap:8px; align-items:center;">
        <el-input v-model="keyword" placeholder="搜索文件名" style="max-width:240px;" clearable @keyup.enter="onSearch" />
        <el-select v-model="status" placeholder="状态" style="width:140px;" @change="onStatusChange">
          <el-option label="未删除" value="active" />
          <el-option label="已删除" value="deleted" />
          <el-option label="全部" value="all" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <el-table :data="files" style="width: 100%">
        <el-table-column prop="originalFilename" label="文件名" width="240" />
        <el-table-column prop="size" label="文件大小" width="140">
          <template #default="scope">
            {{ formatFileSize(scope.row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="文件类型" width="160" />
        <el-table-column prop="downloadCount" label="下载次数" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="ownerUsername" label="上传者" width="160" />
        <el-table-column prop="deleted" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.deleted ? 'warning' : 'success'">{{ scope.row.deleted ? '已删除' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" @click="downloadFile(scope.row)">
              下载
            </el-button>
            <el-button size="small" type="success" v-if="scope.row.deleted" @click="async () => { await adminRestoreFile(scope.row.id); ElMessage.success('已恢复'); loadFiles(); }">
              恢复
            </el-button>
            <el-button size="small" type="danger" @click="deleteFile(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
    
    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="500px">
      <el-upload
        class="upload-demo"
        drag
        action="/api/files/upload"
        :headers="uploadHeaders"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :before-upload="beforeUpload"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getAdminFileList as getFiles, adminDownloadFile, deleteFile as deleteFileApi, adminRestoreFile, adminPermanentDeleteFile } from '@/api/file'
import { getToken } from '@/utils/auth'

export default {
  name: 'Files',
  components: {
    UploadFilled
  },
  setup() {
    const files = ref([])
    const currentPage = ref(1)
    const pageSize = ref(20)
    const total = ref(0)
    const showUploadDialog = ref(false)
    const keyword = ref('')
    const status = ref('active') // active | deleted | all
    
    const uploadHeaders = {
      'Authorization': `Bearer ${getToken()}`
    }
    
	    const loadFiles = async () => {
	      try {
        const response = await getFiles({
          page: currentPage.value - 1,
          size: pageSize.value,
          keyword: keyword.value || undefined,
          status: status.value
        })
        // Page response
        files.value = response.content || []
        total.value = response.totalElements || 0
      } catch (error) {
        ElMessage.error('加载文件列表失败')
      }
    }
    
    const handleSizeChange = (val) => {
      pageSize.value = val
      loadFiles()
    }
    
    const handleCurrentChange = (val) => {
      currentPage.value = val
      loadFiles()
    }
    
    const downloadFile = async (file) => {
      // 管理员下载使用 admin 接口
      const res = await adminDownloadFile(file.id)
      const blob = new Blob([res.data], { type: res.headers['content-type'] || 'application/octet-stream' })
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = file.originalFilename || 'download'
      a.click()
      window.URL.revokeObjectURL(url)
    }
    
    const deleteFile = async (file) => {
      try {
        await ElMessageBox.confirm('确定要删除这个文件吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        // 管理员彻底删除（回收站也有专用接口，这里按需求用管理员永久删除）
        await adminPermanentDeleteFile(file.id)
        ElMessage.success('删除成功')
        loadFiles()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败')
        }
      }
    }
    
    const beforeUpload = (file) => {
      const isLt100M = file.size / 1024 / 1024 < 100
      if (!isLt100M) {
        ElMessage.error('文件大小不能超过 100MB!')
      }
      return isLt100M
    }
    
    const handleUploadSuccess = () => {
      ElMessage.success('上传成功')
      showUploadDialog.value = false
      loadFiles()
    }
    
    const handleUploadError = () => {
      ElMessage.error('上传失败')
    }
    
    const formatFileSize = (bytes) => {
      if (bytes === 0) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    }
    
    const formatDate = (dateString) => {
      return new Date(dateString).toLocaleString()
    }
    
    onMounted(() => {
      loadFiles()
    })

    const onSearch = () => {
      currentPage.value = 1
      loadFiles()
    }

    const onStatusChange = () => {
      currentPage.value = 1
      loadFiles()
    }

    const onReset = () => {
      keyword.value = ''
      status.value = 'active'
      currentPage.value = 1
      loadFiles()
    }

    return {
      files,
      currentPage,
      pageSize,
      total,
      showUploadDialog,
      uploadHeaders,
      handleSizeChange,
      handleCurrentChange,
      downloadFile,
      deleteFile,
      beforeUpload,
      handleUploadSuccess,
      handleUploadError,
      formatFileSize,
      formatDate,
      keyword,
      status,
      onSearch,
      onStatusChange,
      onReset
    }
  }
}
</script>

<style scoped>
.files-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.upload-demo {
  text-align: center;
}
</style>
