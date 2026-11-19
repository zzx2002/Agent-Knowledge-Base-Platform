<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search as SearchIcon } from '@element-plus/icons-vue'
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

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select v-model="kbStore.currentKbId" placeholder="请选择知识库" style="width: 220px">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-input v-model="query" placeholder="输入检索问题" clearable style="width: 320px" @keyup.enter="runSearch" />
          <el-input-number v-model="topK" :min="1" :max="50" style="width: 120px" />
          <el-switch v-model="rerank" active-text="重排" />
          <el-button type="primary" :icon="SearchIcon" :loading="loading" @click="runSearch">检索</el-button>
          <el-button :loading="loading" @click="runDebug">调试</el-button>
        </div>
      </template>

      <el-empty v-if="!result || !result.hits.length" description="暂无检索结果" />
      <div v-else>
        <div class="result-head">共 {{ result.hits.length }} 条命中</div>
        <div v-for="(hit, i) in result.hits" :key="hit.chunkId" class="result-item">
          <div class="result-meta">
            <span class="rank">#{{ i + 1 }}</span>
            <strong>{{ hit.fileName || hit.documentId }}</strong>
            <span v-if="hit.page">第 {{ hit.page }} 页</span>
            <span>综合 {{ scoreText(hit) }}</span>
            <span v-if="hit.keywordScore !== undefined">关键词 {{ hit.keywordScore.toFixed(3) }}</span>
            <span v-if="hit.vectorScore !== undefined">向量 {{ hit.vectorScore.toFixed(3) }}</span>
            <span v-if="hit.rerankScore !== undefined">重排 {{ hit.rerankScore.toFixed(3) }}</span>
          </div>
          <div class="result-content">{{ hit.content }}</div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="showDebug" title="检索调试明细" width="800px">
      <pre class="debug-box">{{ result?.debug ? JSON.stringify(result.debug, null, 2) : '无调试数据' }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.result-head {
  margin-bottom: 12px;
  color: var(--kb-muted, #909399);
  font-size: 13px;
}
.result-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--kb-border, #e4e7ed);
}
.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--kb-muted, #909399);
  margin-bottom: 8px;
}
.rank {
  color: var(--kb-primary, #409eff);
  font-weight: 600;
}
.result-content {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
}
.debug-box {
  max-height: 60vh;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
}
</style>
