// 文件上传队列管理（基础骨架实现）
// 说明：当前实现优先支持直传与秒传流程，分片上传与断点续传后续按设计文档逐步补全

import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { computeFileSha256 } from '@/utils/fileHash'
import { getUploadTimeoutConfig } from '@/api/file'
import { checkFastUpload, directUpload, uploadChunk, getChunkStatus, mergeChunks } from '@/services/uploadService'

const MAX_CONCURRENT_FILES = 2
const CHUNK_THRESHOLD = 10 * 1024 * 1024 // 10MB 以上采用分片
const CHUNK_SIZE = 4 * 1024 * 1024      // 每片 4MB
const MAX_CHUNK_CONCURRENCY = 3
const MAX_CHUNK_RETRY = 3
const STORAGE_KEY = 'efm_upload_queue_v1'
const DEFAULT_TIMEOUT_CONFIG = { mode: 'auto', timeoutSeconds: 150 }
const SMALL_SIZE = 30 * 1024 * 1024
const MID_SIZE = 300 * 1024 * 1024
const SPEED_BYTES_PER_SEC = 2 * 1024 * 1024 // 2 MB/s
const BUFFER_SECONDS = 180

export function useUploadQueue(options = {}) {
  const {
    getCurrentFolderId = () => null,
    onTaskCompleted = () => {},
    resolveParentId = () => null
  } = options

  const uploadQueue = ref([])
  const activeUploadsCount = ref(0)
  const uploadTimeoutConfig = ref({ ...DEFAULT_TIMEOUT_CONFIG })

  const hasRunningTasks = computed(() =>
    uploadQueue.value.some((t) => t.status === 'hashing' || t.status === 'checking_fast' || t.status === 'uploading')
  )

  const fetchUploadTimeoutConfig = async () => {
    try {
      const res = await getUploadTimeoutConfig()
      if (res && res.mode) {
        uploadTimeoutConfig.value = {
          mode: (res.mode || 'auto').toString().trim().toLowerCase() === 'manual' ? 'manual' : 'auto',
          timeoutSeconds: Number(res.timeoutSeconds) > 0 ? Number(res.timeoutSeconds) : DEFAULT_TIMEOUT_CONFIG.timeoutSeconds
        }
      }
    } catch (e) {
      // 保持默认配置
      uploadTimeoutConfig.value = { ...DEFAULT_TIMEOUT_CONFIG }
    }
  }

  // 初始化时拉取一次配置（失败则使用默认）
  fetchUploadTimeoutConfig()

  const computeUploadTimeoutMs = (fileSize) => {
    const cfg = uploadTimeoutConfig.value || DEFAULT_TIMEOUT_CONFIG
    const size = Number(fileSize) || 0
    if (cfg.mode === 'manual' && cfg.timeoutSeconds > 0) {
      return Math.max(1, Math.ceil(cfg.timeoutSeconds)) * 1000
    }
    if (size <= SMALL_SIZE) return 150 * 1000
    if (size <= MID_SIZE) return 700 * 1000
    const seconds = size / SPEED_BYTES_PER_SEC + BUFFER_SECONDS
    return Math.max(1, Math.ceil(seconds)) * 1000
  }

  const persistState = () => {
    try {
      const tasks = uploadQueue.value
        .filter(t => t.status !== 'completed' && t.status !== 'canceled')
        .map(t => ({
          id: t.id,
          name: t.name,
          size: t.size,
          folderId: t.folderId,
          hash: t.hash,
          useChunks: t.useChunks,
          parentId: t.parentId ?? null,
          createdAt: t.createdAt,
          lastStatus: t.status
        }))
      const payload = { version: 1, tasks }
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('持久化上传队列失败:', e)
    }
  }

  const restoreFromStorage = () => {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw)
      if (!parsed || !Array.isArray(parsed.tasks)) return
      const now = Date.now()
      for (const t of parsed.tasks) {
        const task = {
          id: t.id || `${now}-${Math.random().toString(16).slice(2)}`,
          file: null,
          name: t.name,
          size: t.size,
          folderId: t.folderId ?? null,
          parentId: t.parentId ?? null,
          hash: t.hash || null,
          status: 'paused',
          progress: 0,
          uploadedBytes: 0,
          errorMessage: '',
          isFastUploaded: false,
          useChunks: !!t.useChunks,
          totalChunks: 0,
          chunks: [],
          createdAt: t.createdAt || now,
          updatedAt: now,
          controllers: {
            hashingController: null,
            requestControllers: new Set()
          }
        }
        uploadQueue.value.push(task)
      }
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('恢复上传队列失败:', e)
    }
  }

  const enqueueFiles = (files, folderId) => {
    const fid = folderId != null ? folderId : getCurrentFolderId()
    const list = Array.from(files || [])
    if (!list.length) return
    const now = Date.now()
    for (const file of list) {
      const useChunks = file.size >= CHUNK_THRESHOLD
      const task = {
        id: `${now}-${Math.random().toString(16).slice(2)}`,
        file,
        name: file.name,
        size: file.size,
        folderId: fid,
        parentId: file.__parentId ?? null,
        hash: null,
        status: 'pending',
        progress: 0,
        uploadedBytes: 0,
        errorMessage: '',
        isFastUploaded: false,
        useChunks,
        totalChunks: 0,
        chunks: [],
        createdAt: now,
        updatedAt: now,
        controllers: {
          hashingController: null,
          requestControllers: new Set()
        }
      }
      uploadQueue.value.push(task)
    }
    persistState()
    schedule()
  }

  const updateTask = (task, patch) => {
    Object.assign(task, patch, { updatedAt: Date.now() })
    persistState()
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
    if (!task.file) {
      // 任务恢复后尚未绑定文件，跳过
      return
    }
    activeUploadsCount.value += 1
    try {
      let resolvedParentId = task.parentId ?? null
      try {
        const candidate = resolveParentId(task.name, task.folderId)
        if (candidate !== undefined && candidate !== null) {
          resolvedParentId = candidate
        }
      } catch (e) {
        // ignore resolver errors，继续使用已有值
      }
      updateTask(task, { parentId: resolvedParentId })
      // 1. 计算哈希
      const hashingController = new AbortController()
      if (!task.controllers) {
        task.controllers = { hashingController, requestControllers: new Set() }
      } else {
        task.controllers.hashingController = hashingController
      }
      updateTask(task, { status: 'hashing', progress: 0 })
      const hash = await computeFileSha256(task.file, {
        signal: hashingController.signal,
        onProgress: (loaded, total) => {
          // 仅作为额外信息，不改变主逻辑
          if (total > 0) {
            const pct = Math.round((loaded / total) * 10)
            if (pct > 0 && pct < 10 && task.status === 'hashing') {
              updateTask(task, { progress: pct })
            }
          }
        }
      })
      updateTask(task, { hash, status: 'checking_fast' })
      task.controllers.hashingController = null

      // 2. 秒传检查
      try {
        const res = await checkFastUpload({
          hash,
          filename: task.name,
          folderId: task.folderId,
          parentId: resolvedParentId
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

      // 3. 上传：小文件直传，大文件分片
      if (!task.useChunks) {
        await uploadDirect(task)
      } else {
        await uploadByChunks(task)
      }
      // 若上传过程未抛出异常，则视为完成
      if (task.status === 'uploading' || task.status === 'merging') {
        // 防御：确保完成状态
        updateTask(task, { status: 'completed', progress: 100, uploadedBytes: task.size })
      }
      onTaskCompleted()
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('上传任务失败:', e)
      if (task.status === 'paused' || task.status === 'canceled') {
        // 用户主动暂停/取消，不视为失败
      } else if (e && e.code === 'HASH_ABORTED') {
        // 哈希阶段被中断，同样不视为失败
        updateTask(task, {
          status: 'paused'
        })
      } else {
        updateTask(task, {
          status: 'failed',
          errorMessage: e?.message || '上传失败'
        })
        ElMessage.error(`上传失败：${task.name}`)
      }
    } finally {
      activeUploadsCount.value -= 1
      schedule()
    }
  }

  const uploadDirect = async (task) => {
    updateTask(task, { status: 'uploading', progress: 0 })
    const controller = new AbortController()
    task.controllers?.requestControllers.add(controller)
    await directUpload({
      file: task.file,
      folderId: task.folderId,
      filename: task.name,
      parentId: task.parentId ?? null,
      onUploadProgress: (evt) => {
        if (evt.total > 0) {
          const pct = Math.round((evt.loaded / evt.total) * 100)
          updateTask(task, { progress: pct, uploadedBytes: evt.loaded })
        }
      },
      signal: controller.signal,
      timeout: computeUploadTimeoutMs(task.size)
    }).finally(() => {
      task.controllers?.requestControllers.delete(controller)
    })
  }

  const uploadByChunks = async (task) => {
    // 初始化分片信息
    const totalChunks = Math.max(1, Math.ceil(task.size / CHUNK_SIZE))
    const chunks = []
    for (let i = 1; i <= totalChunks; i++) {
      chunks.push({
        index: i,
        uploaded: false,
        uploading: false,
        retryCount: 0,
        errorMessage: ''
      })
    }
    updateTask(task, {
      status: 'uploading',
      totalChunks,
      chunks,
      uploadedBytes: 0,
      progress: 0
    })

    // 断点续传：查询已上传分片
    try {
      const status = await getChunkStatus({ hash: task.hash, total: totalChunks })
      const uploadedIndexes = Array.isArray(status?.uploaded) ? status.uploaded : []
      if (uploadedIndexes.length > 0) {
        let doneBytes = 0
        for (const idx of uploadedIndexes) {
          const chunk = chunks.find(c => c.index === idx)
          if (chunk) {
            chunk.uploaded = true
            // 粗略估算已上传字节：除最后一片外均按 CHUNK_SIZE，最后一片用剩余大小
            const start = (idx - 1) * CHUNK_SIZE
            const end = Math.min(task.size, idx * CHUNK_SIZE)
            doneBytes += (end - start)
          }
        }
        updateTask(task, {
          uploadedBytes: doneBytes,
          progress: Math.round((doneBytes / task.size) * 100)
        })
      }
    } catch (e) {
      // 查询失败不影响后续上传，仅记录日志
      // eslint-disable-next-line no-console
      console.warn('查询分片状态失败，按全新上传处理:', e)
    }

    // 分片上传调度
    const doUploadChunk = async (chunkState) => {
      if (task.status === 'paused' || task.status === 'canceled') return
      if (chunkState.uploaded) return
      if (chunkState.uploading) return
      if (chunkState.retryCount > MAX_CHUNK_RETRY) {
        throw new Error(`分片 #${chunkState.index} 重试次数超限`)
      }
      chunkState.uploading = true
      const controller = new AbortController()
      task.controllers?.requestControllers.add(controller)
      try {
        const start = (chunkState.index - 1) * CHUNK_SIZE
        const end = Math.min(task.size, chunkState.index * CHUNK_SIZE)
        const blob = task.file.slice(start, end)
        await uploadChunk({
          hash: task.hash,
          index: chunkState.index,
          total: totalChunks,
          chunk: blob,
          signal: controller.signal,
          timeout: computeUploadTimeoutMs(task.size)
        })
        chunkState.uploaded = true
        chunkState.errorMessage = ''
        const uploadedBytes = (task.chunks || []).reduce((sum, c) => {
          if (!c.uploaded) return sum
          const cs = (c.index === totalChunks)
            ? (task.size - CHUNK_SIZE * (totalChunks - 1))
            : CHUNK_SIZE
          return sum + cs
        }, 0)
        updateTask(task, {
          uploadedBytes,
          progress: Math.round((uploadedBytes / task.size) * 100)
        })
      } catch (e) {
        const aborted = task.status === 'paused' || task.status === 'canceled' || controller.signal?.aborted || e?.code === 'ERR_CANCELED'
        if (aborted) {
          // 用户主动暂停/取消，不计入失败重试
          return
        }
        chunkState.retryCount += 1
        chunkState.errorMessage = e?.message || '分片上传失败'
        // eslint-disable-next-line no-console
        console.error(`分片上传失败 #${chunkState.index}:`, e)
        if (chunkState.retryCount > MAX_CHUNK_RETRY) {
          throw e
        }
      } finally {
        chunkState.uploading = false
        task.controllers?.requestControllers.delete(controller)
      }
    }

    // 并发调度循环
    const runChunks = async () => {
      // 简单并发控制：每轮挑选未上传且未在上传中的分片
      while (true) {
        if (task.status === 'paused' || task.status === 'canceled') break
        const pending = (task.chunks || []).filter(c => !c.uploaded && !c.uploading)
        if (!pending.length) break
        const batch = pending.slice(0, MAX_CHUNK_CONCURRENCY)
        await Promise.all(batch.map(c => doUploadChunk(c)))
      }
    }

    await runChunks()

    // 所有分片已上传，开始合并
    if ((task.chunks || []).some(c => !c.uploaded)) {
      throw new Error('仍有分片未完成，无法合并')
    }
    updateTask(task, { status: 'merging' })
    await mergeChunks({
      hash: task.hash,
      total: totalChunks,
      filename: task.name,
      folderId: task.folderId,
      parentId: task.parentId ?? null,
      timeout: computeUploadTimeoutMs(task.size)
    })
  }

  const pauseTask = (taskId) => {
    const task = uploadQueue.value.find((t) => t.id === taskId)
    if (!task) return
    if (task.status === 'uploading' || task.status === 'hashing' || task.status === 'checking_fast') {
      updateTask(task, { status: 'paused' })
      // 中断当前哈希与请求
      if (task.controllers?.hashingController) {
        task.controllers.hashingController.abort()
        task.controllers.hashingController = null
      }
      if (task.controllers?.requestControllers) {
        for (const c of task.controllers.requestControllers) {
          c.abort()
        }
        task.controllers.requestControllers.clear()
      }
    }
  }

  const resumeTask = (taskId) => {
    const task = uploadQueue.value.find((t) => t.id === taskId)
    if (!task) return
    if (task.status === 'paused') {
      updateTask(task, { status: 'pending' })
      schedule()
    }
  }

  const cancelTask = (taskId) => {
    const idx = uploadQueue.value.findIndex((t) => t.id === taskId)
    if (idx === -1) return
    const task = uploadQueue.value[idx]
    if (task.status === 'completed') return
    updateTask(task, { status: 'canceled' })
    if (task.controllers?.hashingController) {
      task.controllers.hashingController.abort()
      task.controllers.hashingController = null
    }
    if (task.controllers?.requestControllers) {
      for (const c of task.controllers.requestControllers) {
        c.abort()
      }
      task.controllers.requestControllers.clear()
    }
    uploadQueue.value.splice(idx, 1)
    persistState()
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

  const attachFileToTask = (taskId, file) => {
    const task = uploadQueue.value.find((t) => t.id === taskId)
    if (!task) return
    if (!file) {
      ElMessage.error('请选择文件')
      return
    }
    if (file.name !== task.name || file.size !== task.size) {
      ElMessage.error('选择的文件与原任务不一致')
      return
    }
    task.file = file
    // 根据实际大小重新判断是否分片
    const useChunks = file.size >= CHUNK_THRESHOLD
    task.useChunks = useChunks
    task.totalChunks = 0
    task.chunks = []
    updateTask(task, {
      status: 'pending',
      progress: 0,
      uploadedBytes: 0,
      errorMessage: ''
    })
    schedule()
  }

  // 初始化：尝试恢复上次会话中的未完成任务
  restoreFromStorage()

  return {
    uploadQueue,
    hasRunningTasks,
    enqueueFiles,
    pauseTask,
    resumeTask,
    cancelTask,
    retryTask
    ,
    attachFileToTask
  }
}
