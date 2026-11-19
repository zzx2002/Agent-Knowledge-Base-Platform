<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  reindexKnowledgeBase,
  type KnowledgeBase as KB,
} from '@/api/knowledgeBase'

const list = ref<KB[]>([])
const loading = ref(false)
const keyword = ref('')
const reindexingId = ref('')

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

    <el-card shadow="never">
      <template #header>
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
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="知识库名称" min-width="180" />
        <el-table-column prop="embedding_model" label="Embedding 模型" width="180" />
        <el-table-column prop="document_count" label="文档数" width="100" />
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
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
