import request, { type ApiResult, type PageResult } from './request'

export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  embedding_model?: string
  chunk_strategy?: string
  document_count?: number
  owner_id?: string
  created_at?: string
  updated_at?: string
}

export interface KbPermissionItem {
  id: string
  subject_type: 'USER' | 'ROLE'
  subject_id: string
  kb_id: string
  permission: 'READ' | 'WRITE' | 'DELETE' | 'ADMIN'
  granted: boolean
}

export interface KbPermissionQuery {
  subject_type: 'USER' | 'ROLE'
  subject_id: string
  permission: 'READ' | 'WRITE' | 'DELETE' | 'ADMIN'
  granted?: boolean
}

export interface KnowledgeBaseQuery {
  keyword?: string
  page?: number
  page_size?: number
}

/** 创建知识库 POST /knowledge-bases */
export function createKnowledgeBase(data: Partial<KnowledgeBase>) {
  return request.post<ApiResult<KnowledgeBase>>('/knowledge-bases', data)
}

/** 知识库列表 GET /knowledge-bases */
export function listKnowledgeBases(params: KnowledgeBaseQuery) {
  return request.get<ApiResult<PageResult<KnowledgeBase>>>('/knowledge-bases', { params })
}

/** 知识库详情 GET /knowledge-bases/{id} */
export function getKnowledgeBase(id: string) {
  return request.get<ApiResult<KnowledgeBase>>(`/knowledge-bases/${id}`)
}

/** 更新知识库 PUT /knowledge-bases/{id} */
export function updateKnowledgeBase(id: string, data: Partial<KnowledgeBase>) {
  return request.put<ApiResult<KnowledgeBase>>(`/knowledge-bases/${id}`, data)
}

/** 删除知识库 DELETE /knowledge-bases/{id} */
export function deleteKnowledgeBase(id: string) {
  return request.delete<ApiResult<null>>(`/knowledge-bases/${id}`)
}

/** 查询知识库授权列表 */
export function listKbPermissions(id: string) {
  return request.get<ApiResult<KbPermissionItem[]>>(`/knowledge-bases/${id}/permissions`)
}

/** 授予知识库权限 */
export function grantKbPermission(id: string, data: KbPermissionQuery) {
  return request.post<ApiResult<KbPermissionItem>>(`/knowledge-bases/${id}/permissions`, data)
}

/** 撤销知识库权限 */
export function revokeKbPermission(
  id: string,
  subjectType: string,
  subjectId: string,
  permission: string,
) {
  return request.delete<ApiResult<null>>(
    `/knowledge-bases/${id}/permissions/${subjectType}/${subjectId}/${permission}`,
  )
}
