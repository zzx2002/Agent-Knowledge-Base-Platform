import request, { type ApiResult, type PageResult } from './request'

export interface AuditLog {
  id: string
  userId?: string
  username?: string
  operation: string
  resourceType?: string
  resourceId?: string
  requestIp?: string
  success?: boolean
  detail?: Record<string, unknown>
  createdAt?: string
}

export interface AuditLogQuery {
  keyword?: string
  resourceType?: string
  success?: boolean
  page?: number
  pageSize?: number
}

export function listAuditLogs(params: AuditLogQuery) {
  return request.get<ApiResult<PageResult<AuditLog>>>('/admin/audit-logs', { params })
}
