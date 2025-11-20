import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from './auth'

// 创建axios实例
const service = axios.create({
  baseURL: '/api', // 使用代理路径
  timeout: 10000
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器（统一成功/失败处理，失败仅弹一次，优先使用后端 message/code）
service.interceptors.response.use(
  response => {
    const res = response.data
    // 文件下载（blob）直接返回由调用方处理
    if (response.config.responseType === 'blob') return response
    // 常规成功：后端返回 token / code=200 / 200 / 数组
    if (res?.token || res?.code === 200 || response.status === 200 || Array.isArray(res)) return res
    // 其他情况视为失败（但尽量用后端 message）
    const msg = res?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(Object.assign(new Error(msg), { response }))
  },
  async error => {
    console.error('Response error:', error)
    // 主动取消（如上传暂停/取消）不提示错误
    if (error && error.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }
    let msg = '请求失败'
    const { response, request } = error || {}

    if (response) {
      let data = response.data
      // 兼容后端错误为 Blob 的情况
      if (data instanceof Blob) {
        try { const text = await data.text(); data = JSON.parse(text) } catch (_) {}
      } else if (typeof data === 'string') {
        try { data = JSON.parse(data) } catch (_) {}
      }

      // 1) 优先使用后端 code 映射成准确文案
      if (data && data.code) {
        switch (data.code) {
          case 'UPLOAD_TYPE_NOT_ALLOWED':
            msg = data.message || '不允许上传该类型文件'
            break
          case 'QUOTA_EXCEEDED': {
            const required = data.required != null ? `，需 ${data.required}` : ''
            const available = data.available != null ? `，可用 ${data.available}` : ''
            msg = data.message || (`存储空间不足${required}${available}`)
            break
          }
          default:
            msg = data.message || `请求失败：${response.status}`
        }
      } else if (data && data.message) {
        // 2) 其次使用后端 message
        msg = data.message
      } else {
        // 3) 最后按状态码 fallback（不覆盖上面的2种）
        switch (response.status) {
          case 401:
            msg = '未授权，请重新登录'
            // 清除token并跳转到登录页
            removeToken(); window.location.href = '/login'
            break
          case 413:
            msg = '文件过大，超出限制'
            break
          case 403:
            msg = '拒绝访问'
            break
          case 404:
            msg = '请求的资源不存在'
            break
          case 409:
            msg = '请求冲突'
            break
          case 422:
            msg = '请求不合法'
            break
          case 500:
            msg = '服务器错误'
            break
          default:
            msg = `请求失败：${response.status}`
        }
      }
    } else if (request) {
      msg = '网络错误，请检查您的网络连接'
    } else {
      msg = '请求配置错误'
    }

    ElMessage.error(msg)
    error.message = msg
    return Promise.reject(error)
  }
)

export default service
