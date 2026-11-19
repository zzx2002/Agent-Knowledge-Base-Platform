import request, { type ApiResult } from './request'

export interface UserInfo {
  userId: string
  username: string
  displayName: string
  email?: string
  phone?: string
  roleCode?: string
  roleName?: string
  status?: string
  createdAt?: string
}

export interface RoleOption {
  roleCode: string
  roleName: string
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
