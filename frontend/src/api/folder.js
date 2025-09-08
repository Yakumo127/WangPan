import request from '@/utils/request'

export function getFolderList(params) {
  return request({
    url: '/folders/list',
    method: 'get',
    params
  })
}

export function createFolder(data) {
  return request({
    url: '/folders/create',
    method: 'post',
    data
  })
}

export function deleteFolder(id) {
  return request({
    url: `/folders/${id}`,
    method: 'delete'
  })
}

export function renameFolder(id, name) {
  return request({
    url: `/folders/${id}/rename`,
    method: 'put',
    data: { name }
  })
}

export function moveFolder(id, targetParentId) {
  return request({
    url: `/folders/${id}/move`,
    method: 'post',
    data: { targetParentId }
  })
}

export function getFolderPath(id) {
  return request({
    url: `/folders/${id}/path`,
    method: 'get'
  })
}