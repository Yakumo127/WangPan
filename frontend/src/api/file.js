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

export function uploadFile(data, onUploadProgress) {
  return request({
    url: "/files/upload",
    method: "post",
    data,
    onUploadProgress,
    timeout: 300000
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

export function moveFile(id, folderId) {
  return request({
    url: `/files/${id}/move`,
    method: "post",
    data: { folderId }
  })
}

export function copyFile(id, folderId) {
  return request({
    url: `/files/${id}/copy`,
    method: "post",
    data: { folderId }
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

export function adminEmptyAllRecycleBin() {
  return request({
    url: "/files/admin/recycle/bin/empty",
    method: "delete"
  })
}
