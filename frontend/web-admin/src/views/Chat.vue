<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Delete, Document, Plus } from '@element-plus/icons-vue'
import {
  chatStream,
  listChatSessions,
  createChatSession,
  getChatMessages,
  deleteChatSession,
  type ChatSession,
  type ChatSource,
} from '@/api/search'
import { useKbStore } from '@/stores/kb'

interface Message {
  role: 'user' | 'assistant'
  content: string
  sources?: ChatSource[]
}

const kbStore = useKbStore()
const messages = ref<Message[]>([])
const question = ref('')
const answering = ref(false)
const abortController = ref<AbortController | null>(null)
const scrollRef = ref<HTMLDivElement>()
const debugVisible = ref(false)
const debugSources = ref<ChatSource[]>([])
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref('')

onMounted(async () => {
  if (!kbStore.knowledgeBases.length) await kbStore.fetchKnowledgeBases()
  await loadSessions()
})

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

async function loadSessions() {
  if (!kbStore.currentKbId) {
    sessions.value = []
    return
  }
  const res = await listChatSessions(kbStore.currentKbId)
  sessions.value = ((res as any).data ?? res) as ChatSession[]
}

async function openSession(sessionId: string) {
  currentSessionId.value = sessionId
  if (!sessionId) {
    messages.value = []
    return
  }
  const res = await getChatMessages(sessionId)
  const list = ((res as any).data ?? res) as Array<{
    role: 'user' | 'assistant'
    content: string
    sources?: ChatSource[]
  }>
  messages.value = list.map((m) => ({ role: m.role, content: m.content, sources: m.sources || [] }))
  await scrollToBottom()
}

function startNewSession() {
  currentSessionId.value = ''
  messages.value = []
}

async function removeSession() {
  if (!currentSessionId.value) return
  await deleteChatSession(currentSessionId.value)
  startNewSession()
  await loadSessions()
}

async function send() {
  const q = question.value.trim()
  if (!q || answering.value) return
  if (!kbStore.currentKbId) {
    ElMessage.warning('请先选择知识库')
    return
  }

  if (!currentSessionId.value) {
    const created = await createChatSession({
      knowledgeBaseId: kbStore.currentKbId,
      title: q.slice(0, 30),
    })
    const session = ((created as any).data ?? created) as ChatSession
    currentSessionId.value = session.id
    await loadSessions()
  }

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  const assistantMsg: Message = { role: 'assistant', content: '', sources: [] }
  messages.value.push(assistantMsg)
  const assistantIndex = messages.value.length - 1
  const liveMsg = messages.value[assistantIndex] as Message
  answering.value = true
  await scrollToBottom()

  abortController.value = new AbortController()
  try {
    await chatStream(
      { knowledgeBaseId: kbStore.currentKbId, question: q, topK: 5, sessionId: currentSessionId.value },
      (text) => {
        liveMsg.content += text
        scrollToBottom()
      },
      (sources) => {
        liveMsg.sources = sources
      },
      abortController.value.signal,
    )
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      assistantMsg.content = assistantMsg.content || `回答失败：${e?.message || '请稍后重试'}`
    }
  } finally {
    answering.value = false
    abortController.value = null
    await scrollToBottom()
  }
}

function stop() {
  abortController.value?.abort()
}

function clear() {
  messages.value = []
}

function viewSources(sources: ChatSource[] = []) {
  debugSources.value = sources
  debugVisible.value = true
}

interface ContentPart {
  text: string
  citation?: number
}

function parseContent(content: string): ContentPart[] {
  const parts: ContentPart[] = []
  const regex = /\[(\d+)\]/g
  let last = 0
  let match: RegExpExecArray | null
  while ((match = regex.exec(content)) !== null) {
    if (match.index > last) parts.push({ text: content.slice(last, match.index) })
    parts.push({ text: match[0], citation: Number(match[1]) })
    last = match.index + match[0].length
  }
  if (last < content.length) parts.push({ text: content.slice(last) })
  return parts
}

function openCitation(sources: ChatSource[] = [], index?: number) {
  const source = index ? sources[index - 1] : undefined
  if (!source) return
  debugSources.value = [source]
  debugVisible.value = true
}
</script>

<template>
  <div class="page-container chat-page">
    <div class="page-header">
      <h2 class="page-title">知识库对话</h2>
      <p class="page-desc">基于检索结果生成回答，支持流式输出与原文来源引用</p>
    </div>

    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="kbStore.currentKbId"
            placeholder="请选择知识库"
            style="width: 200px"
            @change="startNewSession(); loadSessions()"
          >
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-select
            :model-value="currentSessionId"
            placeholder="新对话"
            style="width: 220px"
            clearable
            @change="openSession"
          >
            <el-option v-for="session in sessions" :key="session.id" :label="session.title" :value="session.id" />
          </el-select>
          <el-button :icon="Plus" @click="startNewSession">新对话</el-button>
          <el-button :icon="Delete" @click="removeSession">删除会话</el-button>
          <el-button @click="clear">清空屏幕</el-button>
        </div>
      </template>

      <div ref="scrollRef" class="message-list">
        <el-empty v-if="!messages.length" description="输入问题开始对话" />
        <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
          <div class="message-bubble">
            <div class="message-content">
              <template v-if="msg.content">
                <template v-for="(part, partIndex) in parseContent(msg.content)" :key="partIndex">
                  <button
                    v-if="part.citation"
                    class="citation"
                    type="button"
                    @click="openCitation(msg.sources, part.citation)"
                  >[{{ part.citation }}]</button>
                  <span v-else>{{ part.text }}</span>
                </template>
              </template>
              <span v-else-if="answering">正在思考…</span>
              <span
                v-if="msg.role === 'assistant' && answering && index === messages.length - 1"
                class="typing-cursor"
              >▌</span>
            </div>
            <div v-if="msg.role === 'assistant' && msg.sources?.length" class="source-bar">
              <el-button link type="primary" :icon="Document" @click="viewSources(msg.sources)">
                查看 {{ msg.sources.length }} 条来源
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          placeholder="输入问题，Ctrl + Enter 发送"
          :disabled="answering"
          @keydown.ctrl.enter.prevent="send"
        />
        <div class="actions">
          <el-button v-if="answering" type="danger" plain @click="stop">停止生成</el-button>
          <el-button v-else type="primary" :icon="Promotion" :loading="answering" @click="send">发送</el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="debugVisible" title="引用来源" width="700px">
      <div v-for="(source, i) in debugSources" :key="source.chunkId + i" class="source-item">
        <div class="source-meta">
          <strong>[{{ i + 1 }}] {{ source.fileName || source.documentId }}</strong>
          <span v-if="source.page">第 {{ source.page }} 页</span>
          <span>相关度 {{ source.score.toFixed(4) }}</span>
        </div>
        <div class="source-content">{{ source.content }}</div>
      </div>
      <el-empty v-if="!debugSources.length" description="本次回答没有来源" />
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 120px);
}
.chat-card {
  height: calc(100% - 72px);
  display: flex;
  flex-direction: column;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message-row {
  display: flex;
  margin-bottom: 16px;
}
.message-row.user {
  justify-content: flex-end;
}
.message-bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--kb-bg, #f5f7fa);
  border: 1px solid var(--kb-border, #e4e7ed);
}
.message-row.user .message-bubble {
  background: #ecf5ff;
}
.message-content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.typing-cursor {
  margin-left: 2px;
  animation: blink 0.8s step-end infinite;
}
.citation {
  display: inline;
  padding: 0 1px;
  border: 0;
  background: transparent;
  color: var(--el-color-primary, #409eff);
  font-size: inherit;
  line-height: inherit;
  cursor: pointer;
  text-decoration: underline;
}
.citation:hover {
  color: var(--el-color-primary-light-3, #79bbff);
}
@keyframes blink {
  50% { opacity: 0; }
}
.source-bar {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--kb-border, #e4e7ed);
}
.input-area {
  border-top: 1px solid var(--kb-border, #e4e7ed);
  padding-top: 12px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
.source-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--kb-border, #e4e7ed);
}
.source-meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: var(--kb-muted, #909399);
  margin-bottom: 6px;
}
.source-content {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
</style>
