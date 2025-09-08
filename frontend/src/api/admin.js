import request from "@/utils/request"

export function getUserList(params) {
  return request({
    url: "/admin/users",
    method: "get",
    params
  })
}

export function createUser(data) {
  return request({
    url: "/admin/users",
    method: "post",
    data
  })
}

export function updateUser(id, data) {
  return request({
    url: `/admin/users/${id}`,
    method: "put",
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/admin/users/${id}`,
    method: "delete"
  })
}

export function importUsers(file) {
  const formData = new FormData()
  formData.append("file", file)
  return request({
    url: "/admin/users/import",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data"
    }
  })
}

export function exportUsers(params) {
  return request({
    url: "/admin/users/export",
    method: "get",
    params,
    responseType: "blob"
  })
}

export function searchUsers(keyword) {
  return request({
    url: "/admin/users/search",
    method: "get",
    params: { keyword }
  })
}
