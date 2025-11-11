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

