// 上传服务封装：直传 / 秒传 / 分片上传 / 分片状态 / 合并
// 说明：当前先提供接口骨架，具体 URL 与请求体结构将与后端接口最终定义对齐

import request from '@/utils/request'

// 秒传检查
export function checkFastUpload(payload) {
  // payload: { hash, size, folderId }
  return request({
    url: '/files/fast-upload/check',
    method: 'post',
    data: payload
  })
}

// 直传小文件
export function directUpload({ file, folderId, filename, onUploadProgress, cancelToken }) {
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
    cancelToken
  })
}

// 分片上传单个分片
export function uploadChunk({ hash, index, total, chunk, folderId, onUploadProgress, cancelToken }) {
  const formData = new FormData()
  formData.append('chunk', chunk)
  formData.append('hash', hash)
  formData.append('index', String(index))
  formData.append('total', String(total))
  if (folderId != null) formData.append('folderId', folderId)
  return request({
    url: '/files/chunk',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress,
    cancelToken
  })
}

// 查询分片状态（用于断点续传）
export function getChunkStatus({ hash, total }) {
  return request({
    url: '/files/chunk/status',
    method: 'get',
    params: { hash, total }
  })
}

// 合并分片
export function mergeChunks({ hash, total, filename, folderId }) {
  return request({
    url: '/files/chunk/merge',
    method: 'post',
    data: { hash, total, filename, folderId }
  })
}

