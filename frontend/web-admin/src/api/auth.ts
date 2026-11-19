import request, { type ApiResult } from './request'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  access_token: string
  refresh_token?: string
  token_type?: string
}

export interface UserInfo {
  id: string
  username: string
  nickname?: string
  roles: string[]
}

/** 用户登录 POST /auth/login */
export function login(params: LoginParams) {
  return request.post<ApiResult<LoginResult>>('/auth/login', params)
}

/** 用户退出 POST /auth/logout */
export function logout() {
  return request.post<ApiResult<null>>('/auth/logout')
}

/** 刷新 Token POST /auth/refresh */
export function refreshToken(refresh_token: string) {
  return request.post<ApiResult<LoginResult>>('/auth/refresh', { refresh_token })
}

/** 获取用户信息 GET /auth/me */
export function getCurrentUser() {
  return request.get<ApiResult<UserInfo>>('/auth/me')
}
