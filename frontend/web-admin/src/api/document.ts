import request, { type ApiResult, type PageResult } from './request'

export interface DocumentItem {
  id: string
  knowledge_base_id: string
  file_name: string
  file_type: string
  file_size?: number
  status?: string
  created_at?: string
}

export interface DocumentQuery {
  knowledge_base_id?: string
  keyword?: string
  page?: number
  page_size?: number
}

/** 上传文档 POST /documents/upload（支持单个/批量） */
export function uploadDocuments(files: File[], knowledgeBaseId: string) {
  const formData = new FormData()
  formData.append('knowledge_base_id', knowledgeBaseId)
  files.forEach((file) => formData.append('files', file))
  return request.post<ApiResult<DocumentItem[]>>('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 文档列表 GET /documents */
export function listDocuments(params: DocumentQuery) {
  return request.get<ApiResult<PageResult<DocumentItem>>>('/documents', { params })
}

/** 文档详情 GET /documents/{id} */
export function getDocument(id: string) {
  return request.get<ApiResult<DocumentItem>>(`/documents/${id}`)
}

export interface DocumentElementItem {
  element_id: string
  type: string
  content: string
  level?: number
  page?: number
  heading_path?: string
  table_data?: string
}

export interface DocumentChunkItem {
  chunk_index: number
  chunk_type: string
  content: string
  token_count?: number
  heading_path?: string
  page_start?: number
  page_end?: number
}

export interface DocumentContent {
  document_id: string
  version: number
  status: string
  content: string
  elements: DocumentElementItem[]
}

/** 获取文档内容 GET /documents/{id}/content */
export function getDocumentContent(id: string) {
  return request.get<ApiResult<DocumentContent>>(`/documents/${id}/content`)
}

/** 获取分块 GET /documents/{id}/chunks */
export function getDocumentChunks(id: string, params?: { page_no?: number; page_size?: number }) {
  return request.get<ApiResult<PageResult<DocumentChunkItem>>>(`/documents/${id}/chunks`, { params })
}

/** 重新解析 POST /documents/{id}/reparse */
export function reparseDocument(id: string) {
  return request.post<ApiResult<null>>(`/documents/${id}/reparse`)
}

/** 删除文档 DELETE /documents/{id} */
export function deleteDocument(id: string) {
  return request.delete<ApiResult<null>>(`/documents/${id}`)
}
