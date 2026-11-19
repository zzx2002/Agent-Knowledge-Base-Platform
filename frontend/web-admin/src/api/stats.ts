import request, { type ApiResult } from './request'

export interface StatOverview {
  kbCount: number
  documentCount: number
  storageBytes: number
  taskCount: number
  successRate: number
  searchCalls: number
  chatCalls: number
}

export interface TrendPoint {
  day: string
  search: number
  chat: number
}

export interface TypePoint {
  name: string
  value: number
}

export interface RecentTask {
  id: string
  fileName: string
  status: string
  progress: number
  errorCode?: string
  createdAt?: string
}

export interface RecentKnowledgeBase {
  id: string
  name: string
  embeddingModel?: string
  status?: string
  updatedAt?: string
}

export interface DashboardData {
  overview: StatOverview
  trend: TrendPoint[]
  documentTypes: TypePoint[]
  recentTasks: RecentTask[]
  recentKnowledgeBases: RecentKnowledgeBase[]
}

export function getDashboardStats() {
  return request.get<ApiResult<DashboardData>>('/admin/stats/overview')
}
