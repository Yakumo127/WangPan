import request from "@/utils/request"

export function getLogs(params) {
  return request({
    url: "/admin/logs",
    method: "get",
    params
  })
}

export function exportLogs(params, format = 'csv') {
  return request({
    url: "/admin/logs/export",
    method: "get",
    params: { ...params, format },
    responseType: 'blob'
  })
}

export function getLogDictionaries() {
  return request({
    url: "/admin/logs/actions",
    method: "get"
  })
}
