import request, { type ApiResult, type PageResult } from './request'

export interface AuditLog {
  id: string
  user_id?: string
  username?: string
  operation: string
  resource_type?: string
  resource_id?: string
  request_ip?: string
  success?: boolean
  detail?: Record<string, unknown>
  created_at?: string
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
