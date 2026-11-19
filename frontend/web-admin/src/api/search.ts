import request, { type ApiResult } from './request'

export interface SearchParams {
  knowledgeBaseId?: string
  query: string
  topK?: number
  rerank?: boolean
}

export interface SearchHit {
  chunkId: string
  documentId: string
  fileName?: string
  content: string
  headingPath?: string
  page?: number
  score: number
  keywordScore?: number
  vectorScore?: number
  rerankScore?: number
}

export interface SearchResult {
  query: string
  hits: SearchHit[]
  debug?: Record<string, Array<Record<string, unknown>>>
}

export interface ChatSource {
  chunkId: string
  documentId: string
  fileName?: string
  page?: number
  content: string
  score: number
}

export interface ChatParams {
  knowledgeBaseId: string
  question: string
  topK?: number
}

export interface ChatResult {
  answer: string
  sources: ChatSource[]
}

/** 混合检索 POST /search */
export function search(params: SearchParams) {
  return request.post<ApiResult<SearchResult>>('/search', params)
}

/** 检索调试 GET /search/debug */
export function debugSearch(params: SearchParams) {
  return request.get<ApiResult<SearchResult>>('/search/debug', { params })
}

/** RAG 问答 POST /chat */
export function chat(params: ChatParams) {
  return request.post<ApiResult<ChatResult>>('/chat', params)
}

/**
 * 流式问答 POST /chat/stream。
 * 后端 SSE 事件：delta（回答增量）、sources（来源引用）、error（错误）。
 */
export async function chatStream(
  params: ChatParams,
  onChunk: (text: string) => void,
  onSources?: (sources: ChatSource[]) => void,
  signal?: AbortSignal,
): Promise<void> {
  const base = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
  const token = localStorage.getItem('kb_token')

  const response = await fetch(`${base}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
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
  let buffer = ''

  const handleEvent = (block: string) => {
    if (!block.trim()) return
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
    }
    const data = dataLines.join('\n')
    if (!data) return
    if (event === 'delta') {
      onChunk(data)
    } else if (event === 'sources') {
      try {
        onSources?.(JSON.parse(data) as ChatSource[])
      } catch {
        // 忽略非法来源数据
      }
    } else if (event === 'error') {
      throw new Error(data)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    blocks.forEach(handleEvent)
    if (done) {
      handleEvent(buffer)
      break
    }
  }
}
