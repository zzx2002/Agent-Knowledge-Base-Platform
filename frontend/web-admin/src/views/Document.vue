<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Search, Refresh, Delete, Download, View } from '@element-plus/icons-vue'
import type { UploadRawFile } from 'element-plus'
import {
  uploadDocuments,
  listDocuments,
  reparseDocument,
  deleteDocument,
  getDocumentContent,
  getDocumentChunks,
  type DocumentItem,
  type DocumentElementItem,
  type DocumentChunkItem,
} from '@/api/document'
import { useKbStore } from '@/stores/kb'

const kbStore = useKbStore()
const list = ref<DocumentItem[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const contentVisible = ref(false)
const contentTitle = ref('')
const content = ref('')
const elements = ref<DocumentElementItem[]>([])
const chunks = ref<DocumentChunkItem[]>([])
const previewTab = ref('content')
const chunkTotal = ref(0)

async function loadData() {
  if (!kbStore.currentKbId) await kbStore.fetchKnowledgeBases()
  loading.value = true
  try {
    const res = await listDocuments({
      knowledge_base_id: kbStore.currentKbId,
      keyword: keyword.value,
      page: page.value,
      page_size: pageSize.value,
    })
    const data = (res as any).data ?? res
    list.value = data.records ?? []
    total.value = Number(data.total ?? 0)
  } catch {
    // 统一错误处理
  } finally {
    loading.value = false
  }
}

async function handleUpload(file: UploadRawFile) {
  if (!kbStore.currentKbId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  await uploadDocuments([file], kbStore.currentKbId)
  ElMessage.success('上传成功，解析任务已创建')
  loadData()
}

async function reparse(row: DocumentItem) {
  await reparseDocument(row.id)
  ElMessage.success('已重新提交解析')
  loadData()
}

async function remove(row: DocumentItem) {
  await ElMessageBox.confirm(`确认删除文档「${row.file_name}」？`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function preview(row: DocumentItem) {
  const res = await getDocumentContent(row.id)
  const data = (res as any).data ?? res
  contentTitle.value = `解析结果 - ${row.file_name}`
  content.value = data.content || '暂无内容'
  elements.value = data.elements || []
  previewTab.value = 'content'
  contentVisible.value = true
  await loadChunks(row.id)
}

async function loadChunks(id: string) {
  const res = await getDocumentChunks(id, { page_no: 1, page_size: 200 })
  const data = (res as any).data ?? res
  chunks.value = data.records || []
  chunkTotal.value = data.total || 0
}

function download(row: DocumentItem) {
  ElMessage.info(`下载地址：/api/v1/documents/${row.id}/download（后端实现后可用）`)
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">文档管理</h2>
      <p class="page-desc">上传、检索与维护知识库文档，查看解析结果与分块</p>
    </div>

    <el-card shadow="never" class="upload-card">
      <el-select
        v-model="kbStore.currentKbId"
        placeholder="请选择知识库"
        style="width: 240px; margin-bottom: 12px"
        @change="loadData"
      >
        <el-option
          v-for="kb in kbStore.knowledgeBases"
          :key="kb.id"
          :label="kb.name"
          :value="kb.id"
        />
      </el-select>
      <el-upload
        class="upload-area"
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="(options: any) => handleUpload(options.file)"
        accept=".doc,.docx,.ppt,.pptx,.md,.txt,.pdf,.xls,.xlsx,.csv,.png,.jpg,.jpeg"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 DOC/DOCX/PPT/PPTX/MD/TXT/PDF/XLS/XLSX/CSV/PNG/JPG，支持批量上传
          </div>
        </template>
      </el-upload>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索文档名称"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadData"
          />
          <el-button :icon="Search" @click="loadData">查询</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="file_name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="file_type" label="类型" width="90" />
        <el-table-column prop="status" label="解析状态" width="120" />
        <el-table-column prop="file_size" label="大小(Byte)" width="120" />
        <el-table-column prop="created_at" label="上传时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="preview(row)">解析结果</el-button>
            <el-button link :icon="Refresh" @click="reparse(row)">重新解析</el-button>
            <el-button link :icon="Download" @click="download(row)">下载</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="contentVisible" :title="contentTitle" width="820px">
      <el-tabs v-model="previewTab">
        <el-tab-pane label="全文" name="content">
          <pre class="content-box">{{ content }}</pre>
        </el-tab-pane>
        <el-tab-pane :label="`元素 (${elements.length})`" name="elements">
          <el-table :data="elements" border size="small" max-height="55vh">
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="level" label="层级" width="70" />
            <el-table-column prop="page" label="页码" width="70" />
            <el-table-column prop="heading_path" label="标题路径" width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`分块 (${chunkTotal})`" name="chunks">
          <div v-for="chunk in chunks" :key="chunk.chunk_index" class="chunk-card">
            <div class="chunk-meta">
              #{{ chunk.chunk_index }} · {{ chunk.chunk_type }} · {{ chunk.token_count || 0 }} Token
              <span v-if="chunk.page_start"> · P{{ chunk.page_start }}-{{ chunk.page_end }}</span>
            </div>
            <div class="chunk-content">{{ chunk.content }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.upload-area {
  width: 100%;
}
.content-box {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}
.chunk-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.chunk-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.chunk-content {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 160px;
  overflow: auto;
}
</style>
