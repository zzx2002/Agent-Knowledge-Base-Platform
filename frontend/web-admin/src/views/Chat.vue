<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Delete, Document } from '@element-plus/icons-vue'
import { chatStream, type ChatSource } from '@/api/search'
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

onMounted(() => {
  if (!kbStore.knowledgeBases.length) kbStore.fetchKnowledgeBases()
})

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

async function send() {
  const q = question.value.trim()
  if (!q || answering.value) return
  if (!kbStore.currentKbId) {
    ElMessage.warning('请先选择知识库')
    return
  }

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  const assistantMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)
  answering.value = true
  await scrollToBottom()

  abortController.value = new AbortController()
  try {
    await chatStream(
      { knowledgeBaseId: kbStore.currentKbId, question: q, topK: 5 },
      (text) => {
        assistantMsg.content += text
        scrollToBottom()
      },
      (sources) => {
        assistantMsg.sources = sources
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
          <el-select v-model="kbStore.currentKbId" placeholder="请选择知识库" style="width: 240px">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :icon="Delete" @click="clear">清空对话</el-button>
        </div>
      </template>

      <div ref="scrollRef" class="message-list">
        <el-empty v-if="!messages.length" description="输入问题开始对话" />
        <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content || (answering ? '正在思考…' : '') }}<span
                v-if="msg.role === 'assistant' && answering && index === messages.length - 1"
                class="typing-cursor"
              >▌</span></div>
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
