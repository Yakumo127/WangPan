// 上传服务封装：直传 / 秒传 / 分片上传 / 分片状态 / 合并
// 说明：当前先提供接口骨架，具体 URL 与请求体结构将与后端接口最终定义对齐

import request from '@/utils/request'

// 秒传检查 + 快速创建文件（基于已有 Blob）
// 返回结构约定：{ exists: boolean, fileId?: number }
export async function checkFastUpload({ hash, filename, folderId, parentId }) {
  // 1. 先检查 Blob 是否存在（全局）
  const existsResp = await request({
    url: '/files/exists-global',
    method: 'post',
    data: { fileHash: hash }
  })
  if (!existsResp || !existsResp.exists) {
    return { exists: false }
  }
  // 2. Blob 已存在，调用 quick-create 在当前目录创建文件或新版本
  const qcResp = await request({
    url: '/files/quick-create',
    method: 'post',
    data: {
      fileHash: hash,
      filename,
      folderId: folderId ?? null,
      parentId: parentId ?? null
    }
  })
  return {
    exists: true,
    fileId: qcResp && qcResp.fileId
  }
}

// 直传小文件
export function directUpload({ file, folderId, filename, onUploadProgress, signal, timeout }) {
  const formData = new FormData()
  formData.append('file', file)
  if (folderId != null) formData.append('folderId', folderId)
  if (filename) formData.append('filename', filename)
  return request({
    url: '/files/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    signal,
    timeout
  })
}

// 分片上传单个分片
export function uploadChunk({ hash, index, total, chunk, onUploadProgress, signal, timeout }) {
  const formData = new FormData()
  formData.append('file', chunk)
  formData.append('fileHash', hash)
  formData.append('chunkNumber', String(index))
  formData.append('totalChunks', String(total))
  return request({
    url: '/files/chunk',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    signal,
    timeout
  })
}

// 查询分片状态（用于断点续传）
export function getChunkStatus({ hash, total }) {
  return request({
    url: '/files/chunk/status',
    method: 'get',
    params: {
      fileHash: hash,
      totalChunks: total
    }
  })
}

// 合并分片
export function mergeChunks({ hash, total, filename, folderId, parentId, timeout }) {
  return request({
    url: '/files/merge',
    method: 'post',
    data: {
      fileHash: hash,
      totalChunks: total,
      filename,
      folderId: folderId ?? null,
      parentId: parentId ?? null
    },
    timeout
  })
}
