// 文件上传队列管理（基础骨架实现）
// 说明：当前实现优先支持直传与秒传流程，分片上传与断点续传后续按设计文档逐步补全

import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { computeFileSha256 } from '@/utils/fileHash'
import { checkFastUpload, directUpload } from '@/services/uploadService'

const MAX_CONCURRENT_FILES = 2

export function useUploadQueue(options = {}) {
  const {
    getCurrentFolderId = () => null,
    onTaskCompleted = () => {}
  } = options

  const uploadQueue = ref([])
  const activeUploadsCount = ref(0)

  const hasRunningTasks = computed(() =>
    uploadQueue.value.some((t) => t.status === 'hashing' || t.status === 'checking_fast' || t.status === 'uploading')
  )

  const enqueueFiles = (files, folderId) => {
    const fid = folderId != null ? folderId : getCurrentFolderId()
    const list = Array.from(files || [])
    if (!list.length) return
    const now = Date.now()
    for (const file of list) {
      const task = {
        id: `${now}-${Math.random().toString(16).slice(2)}`,
        file,
        name: file.name,
        size: file.size,
        folderId: fid,
        hash: null,
        status: 'pending',
        progress: 0,
        uploadedBytes: 0,
        errorMessage: '',
        isFastUploaded: false,
        useChunks: false,
        createdAt: now,
        updatedAt: now
      }
      uploadQueue.value.push(task)
    }
    schedule()
  }

  const updateTask = (task, patch) => {
    Object.assign(task, patch, { updatedAt: Date.now() })
  }

  const schedule = () => {
    // 控制同时上传的文件任务数
    while (activeUploadsCount.value < MAX_CONCURRENT_FILES) {
      const next = uploadQueue.value.find((t) => t.status === 'pending')
      if (!next) break
      startTask(next)
    }
  }

  const startTask = async (task) => {
    activeUploadsCount.value += 1
    try {
      // 1. 计算哈希
      updateTask(task, { status: 'hashing', progress: 0 })
      const hash = await computeFileSha256(task.file)
      updateTask(task, { hash, status: 'checking_fast' })

      // 2. 秒传检查
      try {
        const res = await checkFastUpload({
          hash,
          size: task.size,
          folderId: task.folderId
        })
        if (res && res.exists) {
          updateTask(task, {
            status: 'completed',
            isFastUploaded: true,
            progress: 100,
            uploadedBytes: task.size
          })
          onTaskCompleted()
          return
        }
      } catch (e) {
        // 秒传检查失败不阻塞上传，仅记录日志
        // eslint-disable-next-line no-console
        console.warn('秒传检查失败，回退为普通上传:', e)
      }

      // 3. 直传（当前阶段先实现直传，后续再扩展分片）
      updateTask(task, { status: 'uploading', progress: 0 })
      await directUpload({
        file: task.file,
        folderId: task.folderId,
        filename: task.name,
        onUploadProgress: (evt) => {
          if (evt.total > 0) {
            const pct = Math.round((evt.loaded / evt.total) * 100)
            updateTask(task, { progress: pct, uploadedBytes: evt.loaded })
          }
        }
      })
      updateTask(task, { status: 'completed', progress: 100, uploadedBytes: task.size })
      onTaskCompleted()
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('上传任务失败:', e)
      updateTask(task, {
        status: 'failed',
        errorMessage: e?.message || '上传失败'
      })
      ElMessage.error(`上传失败：${task.name}`)
    } finally {
      activeUploadsCount.value -= 1
      schedule()
    }
  }

  const pauseTask = () => {
    // 预留：当前基础版暂不支持暂停，后续迭代中实现
    ElMessage.info('暂停/恢复功能将在后续迭代中提供')
  }

  const resumeTask = () => {
    ElMessage.info('暂停/恢复功能将在后续迭代中提供')
  }

  const cancelTask = () => {
    ElMessage.info('取消上传功能将在后续迭代中提供')
  }

  const retryTask = (taskId) => {
    const task = uploadQueue.value.find((t) => t.id === taskId)
    if (!task) return
    if (task.status !== 'failed') return
    updateTask(task, {
      status: 'pending',
      progress: 0,
      uploadedBytes: 0,
      errorMessage: ''
    })
    schedule()
  }

  return {
    uploadQueue,
    hasRunningTasks,
    enqueueFiles,
    pauseTask,
    resumeTask,
    cancelTask,
    retryTask
  }
}

