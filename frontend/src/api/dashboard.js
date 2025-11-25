import request from '@/utils/request'

export function getDashboardSummary(params) {
  return request({
    url: '/dashboard/summary',
    method: 'get',
    params
  })
}
