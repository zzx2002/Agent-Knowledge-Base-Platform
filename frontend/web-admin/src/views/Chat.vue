<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
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

// 右侧来源面板状态
const sourcesPanelVisible = ref(true)
const activeSource = ref<ChatSource | null>(null)

/** 最近一条含来源的助手消息所对应的来源列表 */
const recentSources = computed<ChatSource[]>(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const msg = messages.value[i]
    if (msg.sources?.length) return msg.sources
  }
  return []
})

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
  activeSource.value = null
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
  activeSource.value = null
}

async function removeSession() {
  if (!currentSessionId.value) return
  await removeSessionById(currentSessionId.value)
}

async function removeSessionById(id: string) {
  await deleteChatSession(id)
  if (id === currentSessionId.value) startNewSession()
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
        if (sources.length) setActiveSource(sources[0], false)
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
  activeSource.value = null
}

/** 设置右侧展示的来源，默认同时展开面板 */
function setActiveSource(source: ChatSource, reveal = true) {
  activeSource.value = source
  if (reveal) sourcesPanelVisible.value = true
}

function viewSources(sources: ChatSource[] = []) {
  debugSources.value = sources
  debugVisible.value = true
  if (sources.length) setActiveSource(sources[0])
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
  setActiveSource(source)
}
</script>

<template>
  <div class="page-container chat-page">
    <div class="page-header">
      <h2 class="page-title">知识库对话</h2>
      <p class="page-desc">基于知识库检索生成回答，支持流式输出与原文来源引用</p>
    </div>

    <div class="workspace">
      <!-- 左栏：会话列表 -->
      <aside class="panel session-panel">
        <div class="panel-head">
          <span class="panel-head-title">会话列表</span>
          <el-button class="new-chat-btn" size="small" :icon="Plus" @click="startNewSession">新建会话</el-button>
        </div>
        <div class="session-list">
          <div v-if="!sessions.length" class="session-empty">暂无会话，发起对话后将自动创建</div>
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === currentSessionId }"
            @click="openSession(session.id)"
          >
            <div class="session-item-main">
              <span class="session-dot"></span>
              <span class="session-title" :title="session.title">{{ session.title }}</span>
            </div>
            <el-button
              link
              class="session-del"
              :icon="Delete"
              title="删除会话"
              @click.stop="removeSessionById(session.id)"
            />
          </div>
        </div>
        <div class="session-panel-foot">共 {{ sessions.length }} 个会话</div>
      </aside>

      <!-- 中栏：对话区 -->
      <section class="panel chat-panel">
        <div class="toolbar">
          <el-select
            v-model="kbStore.currentKbId"
            placeholder="请选择知识库"
            style="width: 180px"
            @change="startNewSession(); loadSessions()"
          >
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-select
            :model-value="currentSessionId"
            placeholder="新对话"
            style="width: 200px"
            clearable
            @change="openSession"
          >
            <el-option v-for="session in sessions" :key="session.id" :label="session.title" :value="session.id" />
          </el-select>
          <el-button :icon="Plus" @click="startNewSession">新建</el-button>
          <el-button :icon="Delete" @click="removeSession">删除</el-button>
          <el-button @click="clear">清空</el-button>
          <span class="toolbar-spacer"></span>
          <el-button text type="primary" @click="sourcesPanelVisible = !sourcesPanelVisible">
            {{ sourcesPanelVisible ? '收起来源' : '展开来源' }}
          </el-button>
        </div>

        <div ref="scrollRef" class="message-list">
          <el-empty v-if="!messages.length" description="输入问题开始对话" />
          <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
            <div class="message-bubble">
              <div class="message-role">{{ msg.role === 'user' ? '我' : '智能助手' }}</div>
              <div class="message-content">
                <template v-if="msg.content">
                  <template v-for="(part, partIndex) in parseContent(msg.content)" :key="partIndex">
                    <button
                      v-if="part.citation"
                      class="citation"
                      type="button"
                      :title="'查看来源 [' + part.citation + ']'"
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
                <el-button link type="primary" class="source-link" :icon="Document" @click="viewSources(msg.sources)">
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
            :rows="2"
            resize="none"
            placeholder="输入你的问题，按 Ctrl + Enter 发送"
            :disabled="answering"
            @keydown.ctrl.enter.prevent="send"
          />
          <el-button v-if="answering" class="stop-btn" type="danger" plain @click="stop">停止生成</el-button>
          <el-button v-else class="send-btn" type="primary" :icon="Promotion" title="发送" @click="send" />
        </div>
      </section>

      <!-- 右栏：来源原文预览 -->
      <aside v-if="sourcesPanelVisible" class="panel source-panel">
        <div class="panel-head">
          <span class="panel-head-title">来源原文</span>
          <el-button link type="primary" @click="sourcesPanelVisible = false">收起</el-button>
        </div>
        <div class="source-panel-body">
          <div v-if="!recentSources.length" class="source-empty">
            <el-empty description="暂无来源，回答后自动展示" :image-size="70" />
          </div>
          <div
            v-for="(source, i) in recentSources"
            :key="source.chunkId + i"
            class="source-card"
            :class="{ active: activeSource?.chunkId === source.chunkId }"
            @click="setActiveSource(source)"
          >
            <div class="source-card-head">
              <span class="source-index">{{ i + 1 }}</span>
              <span class="source-name" :title="source.fileName || source.documentId">
                {{ source.fileName || source.documentId }}
              </span>
              <span v-if="source.page" class="source-page">第 {{ source.page }} 页</span>
            </div>
            <div class="source-content">{{ source.content }}</div>
            <div class="source-score">相关度 {{ source.score.toFixed(4) }}</div>
          </div>
        </div>
      </aside>
    </div>

    <el-dialog v-model="debugVisible" title="引用来源" width="700px">
      <div v-for="(source, i) in debugSources" :key="source.chunkId + i" class="source-item">
        <div class="source-meta">
          <strong>[{{ i + 1 }}] {{ source.fileName || source.documentId }}</strong>
          <span v-if="source.page">第 {{ source.page }} 页</span>
          <span>相关度 {{ source.score.toFixed(4) }}</span>
        </div>
        <div class="source-dialog-content">{{ source.content }}</div>
      </div>
      <el-empty v-if="!debugSources.length" description="本次回答没有来源" />
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}
.chat-page .page-header {
  margin-bottom: 16px;
}

/* 三栏工作区 */
.workspace {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
}
.panel {
  background: var(--kb-surface, #fff);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--kb-border);
}
.panel-head-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--kb-text);
}

/* 左栏 */
.session-panel {
  flex: 0 0 240px;
  width: 240px;
}
.new-chat-btn {
  border-radius: 10px !important;
}
.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
}
.session-empty {
  padding: 24px 8px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: var(--kb-muted);
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.session-item:hover {
  background: var(--kb-bg);
}
.session-item.active {
  background: var(--kb-primary-soft);
}
.session-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.session-dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c9cbe0;
}
.session-item.active .session-dot {
  background: var(--kb-primary);
}
.session-title {
  font-size: 13px;
  color: var(--kb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-item.active .session-title {
  color: var(--kb-primary);
  font-weight: 600;
}
.session-del {
  flex: none;
  visibility: hidden;
  font-size: 14px;
}
.session-item:hover .session-del {
  visibility: visible;
}
.session-panel-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--kb-border);
  font-size: 12px;
  color: var(--kb-muted);
}

/* 中栏 */
.chat-panel {
  flex: 1;
  min-width: 0;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--kb-border);
}
.toolbar-spacer {
  flex: 1;
}
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
}
.message-row {
  display: flex;
  margin-bottom: 18px;
}
.message-row.user {
  justify-content: flex-end;
}
.message-bubble {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 16px;
}
.message-row.user .message-bubble {
  background: linear-gradient(135deg, #6c5ce7, #8b7cf1);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 6px 16px rgba(108, 92, 231, 0.2);
}
.message-row.assistant .message-bubble {
  background: #f4f5fa;
  color: var(--kb-text);
  border: 1px solid var(--kb-border);
  border-bottom-left-radius: 4px;
}
.message-role {
  margin-bottom: 4px;
  font-size: 12px;
  opacity: 0.72;
}
.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.75;
  font-size: 14px;
}
.typing-cursor {
  margin-left: 2px;
  animation: blink 0.8s step-end infinite;
}
.citation {
  display: inline-block;
  height: 20px;
  margin: 0 2px;
  padding: 0 7px;
  border: 0;
  border-radius: 10px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  cursor: pointer;
}
.citation:hover {
  background: var(--kb-primary);
  color: #fff;
}
.source-bar {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--kb-border);
}
.source-link {
  font-size: 12px;
}

/* 输入区 */
.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--kb-border);
  background: var(--kb-surface, #fff);
}
.input-area :deep(.el-textarea) {
  flex: 1;
}
.input-area :deep(.el-textarea__inner) {
  min-height: 56px !important;
  padding: 12px 16px;
  border-radius: 16px !important;
  line-height: 1.6;
  box-shadow: 0 0 0 1px var(--kb-border) inset !important;
  background: #fbfbfe !important;
}
.send-btn {
  flex: none;
  width: 48px;
  height: 48px;
  padding: 0;
  font-size: 18px;
  border-radius: 50% !important;
}
.stop-btn {
  flex: none;
  height: 48px;
  padding: 0 18px;
  border-radius: 24px !important;
}

/* 右栏 */
.source-panel {
  flex: 0 0 360px;
  width: 360px;
}
.source-panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
}
.source-empty {
  padding-top: 20px;
}
.source-card {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  background: #fbfbfe;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}
.source-card:hover {
  border-color: var(--el-color-primary-light-7);
}
.source-card.active {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft);
  box-shadow: 0 4px 14px rgba(108, 92, 231, 0.12);
}
.source-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.source-index {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--kb-primary);
  color: #fff;
  font-size: 12px;
}
.source-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-page,
.source-score {
  flex: none;
  font-size: 12px;
  color: var(--kb-muted);
}
.source-content {
  font-size: 13px;
  line-height: 1.7;
  color: #4a5063;
  white-space: pre-wrap;
  word-break: break-word;
}
.source-card:not(.active) .source-content {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.source-score {
  margin-top: 8px;
}

/* 引用弹窗 */
.source-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--kb-border);
}
.source-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--kb-muted);
}
.source-dialog-content {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}
</style>
