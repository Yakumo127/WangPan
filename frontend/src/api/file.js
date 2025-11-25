import request from "@/utils/request"

export function getFileList(params) {
  return request({
    url: "/files/list",
    method: "get",
    params
  })
}

// 管理员：分页获取全量文件
export function getAdminFileList(params) {
  return request({
    url: "/files/admin/list",
    method: "get",
    params
  })
}

export function uploadFile(data, onUploadProgress, options = {}) {
  const { timeout } = options
  return request({
    url: "/files/upload",
    method: "post",
    data,
    onUploadProgress,
    timeout: timeout ?? 300000
  })
}

// 秒传：根据文件哈希检查是否已存在
export function checkFileExists(fileHash) {
  return request({
    url: "/files/exists",
    method: "post",
    data: { fileHash },
    timeout: 30000
  })
}

// 分片上传：上传单个分片
export function uploadChunk(data, onUploadProgress, options = {}) {
  const { timeout } = options
  return request({
    url: "/files/chunk",
    method: "post",
    data,
    onUploadProgress,
    timeout: timeout ?? 300000
  })
}

// 分片合并：在服务端合并所有分片
export function mergeChunks(payload, options = {}) {
  const { timeout } = options
  return request({
    url: "/files/merge",
    method: "post",
    data: payload,
    timeout: timeout ?? 300000
  })
}

// 获取已上传分片编号列表（用于断点续传）
export function getChunkStatus(fileHash, totalChunks) {
  return request({
    url: "/files/chunk/status",
    method: "get",
    params: totalChunks ? { fileHash, totalChunks } : { fileHash },
    timeout: 30000
  })
}

// 获取普通用户文件下载直链
export function getDownloadUrl(id) {
  return request({
    url: `/files/${id}/download-url`,
    method: "get"
  })
}

export function downloadFile(id) {
  return request({
    url: `/files/download/${id}`,
    method: "get",
    responseType: "blob"
  })
}

// 管理员下载任意文件
export function adminDownloadFile(id) {
  return request({
    url: `/files/admin/download/${id}`,
    method: "get",
    responseType: "blob"
  })
}

// 获取管理员文件下载直链
export function getAdminDownloadUrl(id) {
  return request({
    url: `/files/admin/${id}/download-url`,
    method: "get"
  })
}

// 获取文件历史版本列表
export function getFileVersions(fileId) {
  return request({
    url: `/files/${fileId}/versions`,
    method: "get"
  })
}

export function deleteFile(id) {
  return request({
    url: `/files/${id}`,
    method: "delete"
  })
}

export function renameFile(id, name) {
  return request({
    url: `/files/${id}/rename`,
    method: "put",
    data: { name }
  })
}

export function moveFile(id, folderId, newName) {
  return request({
    url: `/files/${id}/move`,
    method: "post",
    data: { folderId, newName }
  })
}

export function copyFile(id, folderId, newName) {
  return request({
    url: `/files/${id}/copy`,
    method: "post",
    data: { folderId, newName }
  })
}

export function searchFiles(keyword) {
  return request({
    url: "/files/search",
    method: "get",
    params: { keyword }
  })
}

export function getFileThumbnail(id) {
  return request({
    url: `/files/thumbnail/${id}`,
    method: "get",
    responseType: "blob"
  })
}

export function getRecycleBinFiles() {
  return request({
    url: "/files/recycle/bin",
    method: "get"
  })
}

export function restoreFile(id) {
  return request({
    url: `/files/${id}/restore`,
    method: "put"
  })
}

export function permanentDeleteFile(id) {
  return request({
    url: `/files/recycle/bin/${id}`,
    method: "delete"
  })
}

export function emptyRecycleBin() {
  return request({
    url: "/files/recycle/bin/empty",
    method: "delete"
  })
}

// 管理员回收站API

// 管理员回收站API
export function getAllRecycleBinFiles(params = {}) {
  return request({
    url: "/files/admin/recycle/bin",
    method: "get",
    params
  })
}

export function adminRestoreFile(id) {
  return request({
    url: `/files/admin/${id}/restore`,
    method: "put"
  })
}

export function adminScheduleDeleteFile(id, reason) {
  return request({
    url: `/files/admin/recycle/bin/${id}/schedule-delete`,
    method: "post",
    data: { reason }
  })
}

// 普通用户获取可预览后缀配置
export function getPreviewConfigForUser() {
  return request({
    url: "/files/preview/config",
    method: "get"
  })
}

// 上传超时策略（普通用户读取）
export function getUploadTimeoutConfig() {
  return request({
    url: "/files/upload-timeout-config",
    method: "get"
  })
}

// 获取文件预览内容（blob）
export function previewFile(id) {
  return request({
    url: `/files/${id}/preview`,
    method: "get",
    responseType: "blob",
    timeout: 0
  })
}

// 注释：后端未开放该路由，前端暂不提供此API。
// export function adminEmptyAllRecycleBin() {
//   return request({
//     url: "/files/admin/recycle/bin/empty",
//     method: "delete"
//   })
// }
