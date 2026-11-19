import request, { type ApiResult } from './request'

export interface UserInfo {
  user_id: string
  username: string
  display_name: string
  email?: string
  phone?: string
  role_code?: string
  role_name?: string
  status?: string
  created_at?: string
}

export interface RoleOption {
  role_code: string
  role_name: string
}

export interface CreateUserPayload {
  username: string
  password: string
  displayName?: string
  email?: string
  roleCode: string
}

export interface UpdateUserPayload {
  roleCode?: string
  status?: string
  password?: string
}

export function listUsers() {
  return request.get<ApiResult<UserInfo[]>>('/users')
}

export function listRoles() {
  return request.get<ApiResult<RoleOption[]>>('/users/roles')
}

export function createUser(data: CreateUserPayload) {
  return request.post<ApiResult<UserInfo>>('/users', data)
}

export function updateUser(id: string, data: UpdateUserPayload) {
  return request.put<ApiResult<UserInfo>>(`/users/${id}`, data)
}

export function deleteUser(id: string) {
  return request.delete<ApiResult<null>>(`/users/${id}`)
}
