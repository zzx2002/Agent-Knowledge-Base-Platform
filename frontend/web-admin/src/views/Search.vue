<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search as SearchIcon, Document } from '@element-plus/icons-vue'
import { debugSearch, search, type SearchHit, type SearchResult } from '@/api/search'
import { useKbStore } from '@/stores/kb'

const kbStore = useKbStore()
const query = ref('')
const topK = ref(5)
const rerank = ref(true)
const loading = ref(false)
const result = ref<SearchResult | null>(null)
const showDebug = ref(false)

onMounted(() => {
  if (!kbStore.knowledgeBases.length) kbStore.fetchKnowledgeBases()
})

async function runDebug() {
  if (!validate()) return
  loading.value = true
  try {
    const res = await debugSearch({
      knowledgeBaseId: kbStore.currentKbId,
      query: query.value,
      topK: topK.value,
      rerank: rerank.value,
    })
    result.value = ((res as any).data ?? res) as SearchResult
    showDebug.value = true
  } finally {
    loading.value = false
  }
}

async function runSearch() {
  if (!validate()) return
  loading.value = true
  try {
    const res = await search({
      knowledgeBaseId: kbStore.currentKbId,
      query: query.value,
      topK: topK.value,
      rerank: rerank.value,
    })
    result.value = ((res as any).data ?? res) as SearchResult
    showDebug.value = false
  } finally {
    loading.value = false
  }
}

function validate() {
  if (!kbStore.currentKbId) {
    ElMessage.warning('请先选择知识库')
    return false
  }
  if (!query.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return false
  }
  return true
}

function scoreText(hit: SearchHit) {
  return hit.score.toFixed(4)
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">知识检索</h2>
      <p class="page-desc">测试关键词召回、向量召回、RRF 融合与重排效果</p>
    </div>

    <div class="search-hero card-panel">
      <p class="hero-caption">输入问题，立即在知识库中查找相关内容</p>
      <div class="hero-search">
        <el-select v-model="kbStore.currentKbId" placeholder="选择知识库" class="hero-kb">
          <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input
          v-model="query"
          size="large"
          placeholder="输入检索问题，例如：如何申请年假？"
          clearable
          class="hero-input"
          :prefix-icon="SearchIcon"
          @keyup.enter="runSearch"
        />
        <el-button type="primary" size="large" class="hero-search-btn" :icon="SearchIcon" :loading="loading" @click="runSearch">
          检索
        </el-button>
      </div>
      <div class="hero-options">
        <span class="option-label">返回条数</span>
        <el-input-number v-model="topK" :min="1" :max="50" size="small" class="option-number" />
        <span class="option-label">语义重排</span>
        <el-switch v-model="rerank" />
        <span class="option-divider"></span>
        <el-button text :loading="loading" @click="runDebug">调试明细</el-button>
      </div>
    </div>

    <div v-if="!result || !result.hits.length" class="result-empty card-panel">
      <el-empty description="输入问题开始检索，结果将在这里展示" />
    </div>

    <div v-else class="result-section">
      <div class="result-head">
        <span class="result-title">检索结果</span>
        <span class="result-count">共 {{ result.hits.length }} 条命中</span>
      </div>

      <div v-for="(hit, i) in result.hits" :key="hit.chunkId" class="result-card">
        <div class="result-card-head">
          <span class="rank-badge" :class="{ 'rank-top': i < 3 }">#{{ i + 1 }}</span>
          <div class="result-file">
            <div class="result-name">
              <el-icon><Document /></el-icon>
              {{ hit.fileName || hit.documentId }}
            </div>
            <div class="result-sub">
              <span v-if="hit.page">第 {{ hit.page }} 页</span>
              <span v-if="hit.headingPath">{{ hit.headingPath }}</span>
            </div>
          </div>
          <span class="score-primary">{{ scoreText(hit) }}</span>
        </div>

        <div class="score-chips">
          <span class="score-chip score-chip-main">综合 {{ scoreText(hit) }}</span>
          <span v-if="hit.keywordScore !== undefined" class="score-chip">关键词 {{ hit.keywordScore.toFixed(3) }}</span>
          <span v-if="hit.vectorScore !== undefined" class="score-chip">向量 {{ hit.vectorScore.toFixed(3) }}</span>
          <span v-if="hit.rerankScore !== undefined" class="score-chip">重排 {{ hit.rerankScore.toFixed(3) }}</span>
        </div>

        <div class="result-content">{{ hit.content }}</div>
      </div>
    </div>

    <el-dialog v-model="showDebug" title="检索调试明细" width="800px">
      <pre class="debug-box">{{ result?.debug ? JSON.stringify(result.debug, null, 2) : '无调试数据' }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-hero {
  text-align: center;
  padding: 34px 28px 28px;
  margin-bottom: 22px;
}
.hero-caption {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--kb-muted);
}
.hero-search {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  max-width: 860px;
  margin: 0 auto;
}
.hero-kb {
  width: 180px;
  flex: 0 0 180px;
}
.hero-input {
  flex: 1 1 380px;
  min-width: 240px;
}
.hero-input :deep(.el-input__inner) {
  font-size: 15px;
  height: 50px;
  line-height: 50px;
}
.hero-search-btn {
  height: 50px;
  padding: 0 26px;
  border-radius: 999px !important;
  font-size: 15px;
}
.hero-options {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 20px;
}
.option-label {
  font-size: 13px;
  color: var(--kb-muted);
}
.option-number {
  width: 120px;
}
.option-divider {
  width: 1px;
  height: 18px;
  background: var(--kb-border);
  margin: 0 6px;
}
.result-empty {
  padding: 40px 20px;
}
.result-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 4px 2px 16px;
}
.result-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--kb-text);
}
.result-count {
  font-size: 13px;
  color: var(--kb-muted);
}
.result-card {
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
  padding: 20px 22px;
  margin-bottom: 16px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.result-card:hover {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 14px 32px rgba(108, 92, 231, 0.12);
}
.result-card-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.rank-badge {
  flex: 0 0 auto;
  min-width: 38px;
  height: 38px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #f2f3f8;
  color: #7a8397;
  font-size: 14px;
  font-weight: 700;
}
.rank-top {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
}
.result-file {
  min-width: 0;
  flex: 1;
}
.result-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--kb-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.result-name .el-icon {
  color: var(--kb-primary);
}
.result-sub {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--kb-muted);
}
.score-primary {
  font-size: 18px;
  font-weight: 700;
  color: var(--kb-primary);
}
.score-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 12px;
}
.score-chip {
  font-size: 12px;
  color: #6b7285;
  background: #f4f5fa;
  padding: 4px 10px;
  border-radius: 8px;
}
.score-chip-main {
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  font-weight: 600;
}
.result-content {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.75;
  color: #454b5e;
  background: #fafafe;
  border-radius: 12px;
  padding: 14px 16px;
  max-height: 160px;
  overflow: auto;
}
.debug-box {
  max-height: 60vh;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
}
</style>
