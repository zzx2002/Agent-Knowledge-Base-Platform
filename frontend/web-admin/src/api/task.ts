import request, { type ApiResult, type PageResult } from './request'

export interface ParseTask {
  id: string
  document_id: string
  kb_id: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  error_code?: string
  error_message?: string
  retry_count: number
  started_at?: string
  finished_at?: string
  created_at?: string
}

export interface TaskQuery {
  knowledge_base_id?: string
  status?: string
  page?: number
  page_size?: number
}

/** 任务列表 GET /tasks */
export function listTasks(params: TaskQuery) {
  return request.get<ApiResult<PageResult<ParseTask>>>('/tasks', { params })
}

/** 重试任务 POST /tasks/{id}/retry */
export function retryTask(id: string) {
  return request.post<ApiResult<ParseTask>>(`/tasks/${id}/retry`)
}

/** 取消任务 POST /tasks/{id}/cancel */
export function cancelTask(id: string) {
  return request.post<ApiResult<ParseTask>>(`/tasks/${id}/cancel`)
}
