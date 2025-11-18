import request from '@/utils/request'

// 下载导出（返回 axios response，调用方负责触发浏览器下载）
export function exportDownload(params) {
  return request({
    url: '/admin/settings/backup/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

// 导出到服务器目录
export function exportToServer(data) {
  return request({
    url: '/admin/settings/backup/export-to-server',
    method: 'post',
    data
  })
}

// 获取/更新备份配置（白名单、定时与保留策略）
export function getBackupConfig() {
  return request({ url: '/admin/settings/backup/config', method: 'get' })
}

export function updateBackupConfig(data) {
  return request({ url: '/admin/settings/backup/config', method: 'put', data })
}

// 预检导入包
export function precheck(file) {
  const form = new FormData()
  form.append('file', file)
  return request({ url: '/admin/settings/backup/precheck', method: 'post', data: form })
}

// 执行导入
export function importBackup(file, { mode = 'full', confirm = true, rebuildThumbnails = false } = {}) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: `/admin/settings/backup/import?mode=${encodeURIComponent(mode)}&confirm=${confirm ? 'true' : 'false'}&rebuildThumbnails=${rebuildThumbnails ? 'true' : 'false'}`,
    method: 'post',
    data: form
  })
}

// 作业与进度
export function createExportJob(data) {
  return request({ url: '/admin/settings/backup/jobs/export-to-server', method: 'post', data })
}
export function createImportJob(file, { rebuildThumbnails = false } = {}) {
  const form = new FormData()
  form.append('file', file)
  return request({ url: `/admin/settings/backup/jobs/import?rebuildThumbnails=${rebuildThumbnails ? 'true' : 'false'}`, method: 'post', data: form })
}
export function listJobs(params) {
  return request({ url: '/admin/settings/backup/jobs', method: 'get', params })
}
export function getJob(id) {
  return request({ url: `/admin/settings/backup/jobs/${id}`, method: 'get' })
}
export function cancelJob(id) {
  return request({ url: `/admin/settings/backup/jobs/${id}/cancel`, method: 'post' })
}
