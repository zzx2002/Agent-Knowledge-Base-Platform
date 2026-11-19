import request, { type ApiResult } from './request'

export interface SearchParams {
  knowledge_base_id?: string
  query: string
  top_k?: number
  rerank?: boolean
}

export interface SearchHit {
  chunk_id: string
  document_id: string
  file_name: string
  page?: number
  score: number
  content: string
}

export interface ChatParams {
  knowledge_base_id: string
  question: string
  top_k?: number
}

export interface ChatResult {
  answer: string
  sources: SearchHit[]
}

/** 混合检索 POST /search */
export function search(params: SearchParams) {
  return request.post<ApiResult<SearchHit[]>>('/search', params)
}

/** RAG 问答 POST /chat */
export function chat(params: ChatParams) {
  return request.post<ApiResult<ChatResult>>('/chat', params)
}

/**
 * 流式问答 POST /chat/stream（占位实现）
 * 通过 fetch 读取 ReadableStream，逐块回调，实际协议按后端 SSE/流式约定调整。
 */
export async function chatStream(
  params: ChatParams,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const base = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
  const token = localStorage.getItem('kb_token')

  const response = await fetch(`${base}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(params),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error('流式问答请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    onChunk(decoder.decode(value, { stream: true }))
  }
}
