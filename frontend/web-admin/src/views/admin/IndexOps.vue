<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Collection, Document, DataAnalysis } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  reindexKnowledgeBase,
  type KnowledgeBase as KB,
} from '@/api/knowledgeBase'

const list = ref<KB[]>([])
const loading = ref(false)
const keyword = ref('')
const reindexingId = ref('')

const stats = computed(() => ({
  total: list.value.length,
  documents: list.value.reduce((sum, kb) => sum + (kb.document_count || 0), 0),
  models: new Set(list.value.map((kb) => kb.embedding_model).filter(Boolean)).size,
}))

async function loadData() {
  loading.value = true
  try {
    const res = await listKnowledgeBases({ keyword: keyword.value, page: 1, page_size: 200 })
    const data = (res as any).data ?? res
    list.value = data.records ?? []
  } finally {
    loading.value = false
  }
}

async function reindex(row: KB) {
  await ElMessageBox.confirm(
    `确认重建「${row.name}」的全部索引？该操作会重新向量化所有分块。`,
    '重建索引',
    { type: 'warning' },
  )
  reindexingId.value = row.id
  try {
    const res = await reindexKnowledgeBase(row.id)
    const data = (res as any).data ?? res
    ElMessage.success(`已重建 ${data?.reindexed ?? 0} 个分块`)
  } finally {
    reindexingId.value = ''
  }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">索引运维</h2>
      <p class="page-desc">按知识库重建 ES 关键词索引与 Qdrant 向量索引，用于模型切换或索引异常后的修复</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-primary"><el-icon><Collection /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">知识库总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-soft"><el-icon><Document /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.documents }}</div>
          <div class="stat-label">文档总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-success"><el-icon><DataAnalysis /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.models }}</div>
          <div class="stat-label">Embedding 模型</div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="ops-card">
      <div class="card-head">
        <div class="card-head-text">
          <h3 class="section-title">重建索引</h3>
          <p class="section-sub">选择知识库后重建其全部索引，重建期间检索可能短暂不可用</p>
        </div>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索知识库名称"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadData"
          />
          <el-button :icon="Search" @click="loadData">查询</el-button>
        </div>
      </div>

      <el-table :data="list" v-loading="loading">
        <el-table-column prop="name" label="知识库名称" min-width="180" />
        <el-table-column label="Embedding 模型" width="180">
          <template #default="{ row }">
            <span class="model-chip">{{ row.embedding_model || '默认模型' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文档数" width="110">
          <template #default="{ row }">
            <span class="doc-count">{{ row.document_count ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updated_at" label="更新时间" width="180" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="Refresh"
              :loading="reindexingId === row.id"
              @click="reindex(row)"
            >重建索引</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
}
.stat-icon {
  flex: 0 0 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  font-size: 22px;
}
.stat-icon-primary {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
}
.stat-icon-soft {
  background: #fff5e3;
  color: #c98a12;
}
.stat-icon-success {
  background: #eafaf2;
  color: #1a9d68;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--kb-text);
  line-height: 1.2;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--kb-muted);
}
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--kb-text);
}
.section-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--kb-muted);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.model-chip {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 8px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 12px;
  font-weight: 600;
}
.doc-count {
  font-weight: 600;
  color: var(--kb-text);
}
@media (max-width: 900px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
