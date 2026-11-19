import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

// API 基础地址，默认 /api/v1，可通过 .env 的 VITE_API_BASE_URL 覆盖
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const request: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
})

// 请求拦截器：附带访问令牌
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('kb_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一解包与错误处理
request.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('kb_token')
      ElMessage.error('登录已过期，请重新登录')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    } else {
      const msg = error.response?.data?.message || error.message || '请求失败'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

export default request

// 通用响应结构（与后端约定）
export interface ApiResult<T> {
  success: boolean
  message?: string
  data: T
}

// 分页结构
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  page_size: number
}
