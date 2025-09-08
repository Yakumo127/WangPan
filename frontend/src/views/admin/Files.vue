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
      
      <el-table :data="files" style="width: 100%">
        <el-table-column prop="fileName" label="文件名" width="200" />
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="scope">
            {{ formatFileSize(scope.row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="文件类型" width="120" />
        <el-table-column prop="downloadCount" label="下载次数" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="downloadFile(scope.row)">
              下载
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
import { getFiles, deleteFile as deleteFileApi } from '@/api/file'
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
    
    const uploadHeaders = {
      'Authorization': `Bearer ${getToken()}`
    }
    
    const loadFiles = async () => {
      try {
        const response = await getFiles({
          page: currentPage.value - 1,
          size: pageSize.value
        })
        files.value = response.data.content
        total.value = response.data.totalElements
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
    
    const downloadFile = (file) => {
      window.open(`/api/files/download/${file.id}`, '_blank')
    }
    
    const deleteFile = async (file) => {
      try {
        await ElMessageBox.confirm('确定要删除这个文件吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        await deleteFileApi(file.id)
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
      formatDate
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