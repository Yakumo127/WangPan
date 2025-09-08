<template>
  <div class="users-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
        </div>
      </template>
      
      <!-- 搜索和操作栏 -->
      <div class="operation-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名或邮箱"
          style="width: 200px;"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="success" @click="handleAdd">添加用户</el-button>
        <el-button type="warning" @click="handleImport">导入</el-button>
        <el-button type="info" @click="handleExport">导出</el-button>
      </div>

      <!-- 用户表格 -->
      <el-table :data="userList" style="width: 100%" v-loading="loading">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="displayName" label="显示名称" width="120" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '激活' : '未激活' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="lastLoginTime" label="最后登录" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 用户表单对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!userForm.id">
          <el-input v-model="userForm.password" type="password" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option label="用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="userForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入用户"
      width="400px"
    >
      <el-upload
        ref="uploadRef"
        class="upload-demo"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        @change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只能上传xlsx/xls文件
          </div>
        </template>
      </el-upload>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleImportSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, UploadFilled } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, importUsers, exportUsers } from '@/api/admin'

// 数据定义
const userList = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const importDialogVisible = ref(false)
const uploadFile = ref(null)

// 表单数据
const userForm = reactive({
  id: null,
  username: '',
  email: '',
  displayName: '',
  password: '',
  role: 'USER',
  enabled: true
})

// 表单验证规则
const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3到20个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6到20个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const userFormRef = ref()
const uploadRef = ref()

// 加载用户列表
const loadUserList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value
    }
    const response = await getUserList(params)
    console.log('API Response:', response) // 调试日志
    // 后端直接返回数组，所以需要处理数据格式
    if (Array.isArray(response)) {
      userList.value = response
      total.value = response.length
    } else if (response.data) {
      userList.value = response.data
      total.value = response.total || response.data.length
    } else {
      userList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载用户列表失败:', error)
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索用户
const handleSearch = async () => {
  console.log('搜索用户:', searchKeyword.value) // 调试日志
  currentPage.value = 1
  await loadUserList()
}

// 添加用户
const handleAdd = () => {
  dialogTitle.value = '添加用户'
  Object.assign(userForm, {
    id: null,
    username: '',
    email: '',
    displayName: '',
    password: '',
    role: 'USER',
    enabled: true
  })
  dialogVisible.value = true
}

// 编辑用户
const handleEdit = (row) => {
  dialogTitle.value = '编辑用户'
  Object.assign(userForm, row)
  userForm.password = ''
  dialogVisible.value = true
}

// 保存用户
const handleSave = async () => {
  if (!userFormRef.value) return
  
  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (userForm.id) {
          await updateUser(userForm.id, userForm)
          ElMessage.success('用户更新成功')
        } else {
          await createUser(userForm)
          ElMessage.success('用户创建成功')
        }
        dialogVisible.value = false
        await loadUserList()
      } catch (error) {
        ElMessage.error('操作失败：' + error.message)
      }
    }
  })
}

// 删除用户
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.username}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteUser(row.id)
    ElMessage.success('用户删除成功')
    await loadUserList()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

// 导入用户
const handleImport = () => {
  importDialogVisible.value = true
  uploadFile.value = null
}

// 文件变化
const handleFileChange = (file) => {
  uploadFile.value = file.raw
}

// 提交导入
const handleImportSubmit = async () => {
  if (!uploadFile.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }
  
  try {
    await importUsers(uploadFile.value)
    ElMessage.success('用户导入成功')
    importDialogVisible.value = false
    await loadUserList()
  } catch (error) {
    ElMessage.error('导入失败：' + error.message)
  }
}

// 导出用户
const handleExport = async () => {
  try {
    const response = await exportUsers()
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '用户列表.xlsx'
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('用户导出成功')
  } catch (error) {
    ElMessage.error('导出失败：' + error.message)
  }
}

// 分页处理
const handleSizeChange = (val) => {
  console.log('页面大小改变:', val) // 调试日志
  pageSize.value = val
  currentPage.value = 1
  loadUserList()
}

const handleCurrentChange = (val) => {
  console.log('当前页改变:', val) // 调试日志
  currentPage.value = val
  loadUserList()
}

// 页面加载时初始化
onMounted(() => {
  console.log('Users.vue 组件挂载，开始加载用户列表') // 调试日志
  loadUserList()
})
</script>

<style scoped>
.users-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.operation-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.upload-demo {
  text-align: center;
}
</style>