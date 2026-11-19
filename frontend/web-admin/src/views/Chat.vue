<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Promotion, Delete } from '@element-plus/icons-vue'
import { chatStream, type SearchHit } from '@/api/search'
import { useKbStore } from '@/stores/kb'

interface Message {
  role: 'user' | 'assistant'
  content: string
  sources?: SearchHit[]
}

const kbStore = useKbStore()
const messages = ref<Message[]>([])
const question = ref('')
const answering = ref(false)
const abortController = ref<AbortController | null>(null)
const scrollRef = ref<HTMLDivElement>()
const debugVisible = ref(false)
const debugSources = ref<SearchHit[]>([])

onMounted(() => {
  if (!kbStore.knowledgeBases.length) kbStore.fetchKnowledgeBases()
})

async function scrollToBottom() {
  await nextTick()
  if (scrollRef.value) scrollRef.value.scrollTop = scrollRef.value.scrollHeight
}

async function send() {
  const q = question.value.trim()
  if (!q) return
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
    // 流式回答占位：逐块写入回答内容
    await chatStream(
      { knowledge_base_id: kbStore.currentKbId, question: q, top_k: 5 },
      (text) => {
        assistantMsg.content += text
        scrollToBottom()
      },
      abortController.value.signal,
    )
    assistantMsg.sources = []
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      assistantMsg.content = assistantMsg.content || '回答失败，请稍后重试。'
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

function viewSources(sources: SearchHit[] = []) {
  debugSources.value = sources
  debugVisible.value = true
}
</script>

<template>
  <div class="page-container chat-page">
    <div class="page-header">
      <h2 class="page-title">知识库对话</h2>
      <p class="page-desc">基于选定知识库进行 RAG 问答，支持流式回答与来源引用</p>
    </div>

    <el-card shadow="never" class="chat-card">
      <template #header>
        <div class="toolbar">
          <el-select
            v-model="kbStore.currentKbId"
            placeholder="请选择知识库"
            style="width: 240px"
          >
            <el-option
              v-for="kb in kbStore.knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>
          <el-button :icon="Delete" @click="clear">清空对话</el-button>
        </div>
      </template>

      <div ref="scrollRef" class="message-list">
        <div v-if="!messages.length" class="empty-tip">
          <el-icon :size="40"><ChatDotRound /></el-icon>
          <p>请输入问题，开始与知识库对话</p>
        </div>
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message-row"
          :class="msg.role"
        >
          <div class="bubble">
            <div class="role-label">{{ msg.role === 'user' ? '我' : 'AI 助手' }}</div>
            <div class="bubble-content">{{ msg.content || '正在思考...' }}</div>
            <el-button
              v-if="msg.role === 'assistant'"
              link
              type="primary"
              size="small"
              @click="viewSources(msg.sources)"
            >
              来源引用
            </el-button>
          </div>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          placeholder="请输入您的问题，例如：公司的 Redis 限流方案是什么？"
          :disabled="answering"
          @keyup.ctrl.enter="send"
        />
        <div class="input-actions">
          <span class="text-muted">Ctrl + Enter 发送</span>
          <el-button v-if="answering" @click="stop">停止生成</el-button>
          <el-button type="primary" :icon="Promotion" :loading="answering" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="debugVisible" title="检索结果调试" width="700px">
      <el-empty v-if="!debugSources.length" description="暂无来源（后端接入后将展示引用原文）" />
      <div v-for="hit in debugSources" :key="hit.chunk_id" class="source-item">
        <div class="source-title">
          {{ hit.file_name }} · 第 {{ hit.page || '-' }} 页 · 得分 {{ hit.score?.toFixed?.(3) }}
        </div>
        <div class="source-content">{{ hit.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
}
.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.toolbar {
  display: flex;
  gap: 12px;
}
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafafa;
}
.empty-tip {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--kb-muted);
}
.message-row {
  display: flex;
  margin-bottom: 16px;
}
.message-row.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 75%;
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: 10px 14px;
}
.message-row.user .bubble {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
.role-label {
  font-size: 12px;
  color: var(--kb-muted);
  margin-bottom: 6px;
}
.message-row.user .role-label {
  color: rgba(255, 255, 255, 0.85);
}
.bubble-content {
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
}
.input-area {
  border-top: 1px solid var(--kb-border);
  padding-top: 12px;
}
.input-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.source-item {
  border-bottom: 1px solid var(--kb-border);
  padding: 12px 0;
}
.source-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
}
.source-content {
  font-size: 13px;
  color: var(--kb-muted);
  white-space: pre-wrap;
}
</style>
