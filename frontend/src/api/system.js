import request from "@/utils/request"

export function getRecycleSettings() {
  return request({
    url: "/admin/settings/recycle",
    method: "get"
  })
}

export function updateRecycleSettings(data) {
  return request({
    url: "/admin/settings/recycle",
    method: "put",
    data
  })
}

// 上传策略
export function getUploadPolicy() {
  return request({
    url: "/admin/settings/upload-policy",
    method: "get"
  })
}

export function updateUploadPolicy(data) {
  return request({
    url: "/admin/settings/upload-policy",
    method: "put",
    data
  })
}

// 上传超时策略
export function getUploadTimeoutSetting() {
  return request({
    url: "/admin/settings/upload-timeout",
    method: "get"
  })
}

export function updateUploadTimeoutSetting(data) {
  return request({
    url: "/admin/settings/upload-timeout",
    method: "put",
    data
  })
}

// 预览策略
export function getPreviewConfig() {
  return request({
    url: "/admin/settings/preview-config",
    method: "get"
  })
}

export function updatePreviewConfig(data) {
  return request({
    url: "/admin/settings/preview-config",
    method: "put",
    data
  })
}

// 系统信息
export function getSystemInfo() {
  return request({
    url: "/admin/settings/system-info",
    method: "get"
  })
}
