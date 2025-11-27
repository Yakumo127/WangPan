import request from "@/utils/request"

// 创建分享
export function createShare(payload) {
  return request({
    url: "/shares",
    method: "post",
    data: payload
  })
}

// 更新分享（权限/过期/提取码/ACL）
export function updateShare(id, payload) {
  return request({
    url: `/shares/${id}`,
    method: "put",
    data: payload
  })
}

// 替换 ACL
export function replaceShareAcl(id, acl) {
  return request({
    url: `/shares/${id}/acl`,
    method: "put",
    data: acl
  })
}

// 获取 ACL
export function getShareAcl(id) {
  return request({
    url: `/shares/${id}/acl`,
    method: "get"
  })
}

// 撤销分享
export function revokeShare(id) {
  return request({
    url: `/shares/${id}`,
    method: "delete"
  })
}

// 我的分享列表
export function listShares(params) {
  return request({
    url: "/shares",
    method: "get",
    params
  })
}

// 公共分享元数据
export function getPublicShare(id) {
  return request({
    url: `/public/shares/${id}`,
    method: "get"
  })
}

// 校验提取码 / 受邀身份
export function validateShare(id, payload) {
  return request({
    url: `/public/shares/${id}/validate`,
    method: "post",
    data: payload
  })
}

// 获取下载直链
export function getShareDownloadUrl(id, fileId, sessionToken) {
  return request({
    url: `/public/shares/${id}/files/${fileId}/download-url`,
    method: "get",
    params: { token: sessionToken }
  })
}

// 列出分享内容（文件夹下一级）
export function listShareContent(id, params = {}) {
  return request({
    url: `/public/shares/${id}/list`,
    method: "get",
    params
  })
}
