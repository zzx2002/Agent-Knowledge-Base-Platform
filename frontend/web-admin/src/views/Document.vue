<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Search, Refresh, Delete, Download, View, Document } from '@element-plus/icons-vue'
import type { UploadRawFile } from 'element-plus'
import {
  uploadDocuments,
  listDocuments,
  reparseDocument,
  deleteDocument,
  getDocumentContent,
  getDocumentChunks,
  fetchDocumentPreview,
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

const filePreviewVisible = ref(false)
const filePreviewTitle = ref('')
const filePreviewUrl = ref('')
const filePreviewType = ref('')
const filePreviewText = ref('')
const filePreviewLoading = ref(false)

function statusType(status?: string) {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'PROCESSING':
      return 'processing'
    case 'PENDING':
      return 'pending'
    case 'FAILED':
      return 'danger'
    case 'CANCELLED':
      return 'info'
    default:
      return 'info'
  }
}

function statusLabel(status?: string) {
  switch (status) {
    case 'COMPLETED':
      return '已解析'
    case 'PROCESSING':
      return '解析中'
    case 'PENDING':
      return '待解析'
    case 'FAILED':
      return '解析失败'
    case 'CANCELLED':
      return '已取消'
    default:
      return '待处理'
  }
}

function formatSize(size?: number) {
  if (size === undefined || size === null) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

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

function releaseFilePreview() {
  if (filePreviewUrl.value) {
    URL.revokeObjectURL(filePreviewUrl.value)
    filePreviewUrl.value = ''
  }
  filePreviewText.value = ''
}

async function openFilePreview(row: DocumentItem) {
  filePreviewLoading.value = true
  try {
    releaseFilePreview()
    const { blob, url } = await fetchDocumentPreview(row.id)
    filePreviewUrl.value = url
    filePreviewType.value = blob.type
    if (blob.type.startsWith('text/')) {
      filePreviewText.value = await blob.text()
    }
    filePreviewTitle.value = `原文预览 - ${row.file_name}`
    filePreviewVisible.value = true
  } catch {
    // 请求拦截器已提示错误
  } finally {
    filePreviewLoading.value = false
  }
}

function download(row: DocumentItem) {
  ElMessage.info(`下载地址：/api/v1/documents/${row.id}/download（后端实现后可用）`)
}

onBeforeUnmount(releaseFilePreview)

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">文档管理</h2>
      <p class="page-desc">上传、检索与维护知识库文档，查看解析结果与分块</p>
    </div>

    <el-card shadow="never" class="upload-card">
      <div class="upload-header">
        <div>
          <h3 class="section-title">上传文档</h3>
          <p class="section-sub">文档上传后会自动解析并切分为分块，供检索与对话使用</p>
        </div>
        <el-select
          v-model="kbStore.currentKbId"
          placeholder="请选择知识库"
          style="width: 240px"
          @change="loadData"
        >
          <el-option
            v-for="kb in kbStore.knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
      </div>

      <el-upload
        class="upload-area"
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="(options: any) => handleUpload(options.file)"
        accept=".doc,.docx,.ppt,.pptx,.md,.txt,.pdf,.xls,.xlsx,.csv,.png,.jpg,.jpeg"
      >
        <div class="upload-inner">
          <div class="upload-icon-wrap"><el-icon><UploadFilled /></el-icon></div>
          <div class="upload-title">将文件拖拽到此处，或 <em>点击上传</em></div>
          <div class="upload-hint">支持 DOC / DOCX / PPT / PPTX / MD / TXT / PDF / XLS / XLSX / CSV / PNG / JPG，可批量上传</div>
        </div>
      </el-upload>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="filter-bar">
        <div class="filter-left">
          <span class="filter-label">筛选</span>
          <el-input
            v-model="keyword"
            placeholder="搜索文档名称"
            clearable
            style="width: 260px"
            :prefix-icon="Search"
            @keyup.enter="loadData"
          />
          <el-button :icon="Search" @click="loadData">查询</el-button>
        </div>
        <span class="filter-count">共 {{ total }} 个文档</span>
      </div>

      <el-table :data="list" v-loading="loading">
        <el-table-column prop="file_name" label="文件名" min-width="220" show-overflow-tooltip />
        <el-table-column prop="file_type" label="类型" width="100">
          <template #default="{ row }">
            <span class="type-chip">{{ row.file_type || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="解析状态" width="130">
          <template #default="{ row }">
            <span class="status-tag" :class="`status-${statusType(row.status)}`">
              <i class="status-dot"></i>{{ statusLabel(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.file_size) }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="上传时间" width="180" />
        <el-table-column label="操作" width="370" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Document" @click="openFilePreview(row)">原文预览</el-button>
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

    <el-dialog
      v-model="filePreviewVisible"
      :title="filePreviewTitle"
      width="1080px"
      top="4vh"
      class="file-preview-dialog"
      destroy-on-close
      @closed="releaseFilePreview"
    >
      <div v-loading="filePreviewLoading" class="file-preview-body">
        <iframe
          v-if="filePreviewType.includes('pdf')"
          :src="filePreviewUrl"
          class="file-preview-frame"
          title="文档预览"
        />
        <img
          v-else-if="filePreviewType.startsWith('image/')"
          :src="filePreviewUrl"
          class="file-preview-image"
          alt="文档预览"
        />
        <pre v-else class="file-preview-text">{{ filePreviewText }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.upload-card {
  margin-bottom: 18px;
}
.upload-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.section-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--kb-text);
}
.section-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--kb-muted);
}
.upload-area {
  width: 100%;
}
:deep(.upload-area .el-upload) {
  width: 100%;
}
:deep(.upload-area .el-upload-dragger) {
  width: 100%;
  padding: 34px 20px;
  border: 2px dashed var(--el-color-primary-light-7);
  border-radius: 16px;
  background: #fbfaff;
  transition: border-color 0.2s ease, background 0.2s ease;
}
:deep(.upload-area .el-upload-dragger:hover) {
  border-color: var(--kb-primary);
  background: var(--kb-primary-soft);
}
.upload-inner {
  text-align: center;
}
.upload-icon-wrap {
  width: 58px;
  height: 58px;
  margin: 0 auto 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 28px;
}
.upload-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-text);
}
.upload-title em {
  font-style: normal;
  color: var(--kb-primary);
}
.upload-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--kb-muted);
}
.table-card {
  margin-top: 0;
}
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--kb-muted);
}
.filter-count {
  font-size: 13px;
  color: var(--kb-muted);
}
.type-chip {
  display: inline-block;
  padding: 3px 9px;
  border-radius: 8px;
  background: #f4f5fa;
  color: #6b7285;
  font-size: 12px;
  text-transform: uppercase;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-success {
  color: #1a9d68;
  background: #eafaf2;
}
.status-processing {
  color: #6c5ce7;
  background: var(--kb-primary-soft);
}
.status-pending {
  color: #c98a12;
  background: #fff5e3;
}
.status-danger {
  color: #e0505b;
  background: #ffeef0;
}
.status-info {
  color: #7a8397;
  background: #f1f2f7;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
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
  border: 1px solid var(--kb-border);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 10px;
  background: #fcfcff;
}
.chunk-meta {
  font-size: 12px;
  color: var(--kb-muted);
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
.file-preview-body {
  min-height: 60vh;
  max-height: 76vh;
  background: #f5f6fa;
  border-radius: 14px;
  overflow: hidden;
}
.file-preview-frame {
  width: 100%;
  height: 72vh;
  border: none;
  display: block;
}
.file-preview-image {
  width: 100%;
  max-height: 72vh;
  object-fit: contain;
  display: block;
}
.file-preview-text {
  margin: 0;
  padding: 20px;
  height: 72vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.7;
}
</style>
