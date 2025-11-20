import request from '@/utils/request'

export function getStorageSummary() {
  return request({
    url: '/admin/storage/summary',
    method: 'get'
  })
}

export function cleanupGarbageChunks() {
  return request({
    url: '/admin/storage/cleanup/garbage-chunks',
    method: 'post'
  })
}

export function cleanupGarbageChunksByUser(body) {
  return request({
    url: '/admin/storage/cleanup/garbage-chunks/by-user',
    method: 'post',
    data: body
  })
}

