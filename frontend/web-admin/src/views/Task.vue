<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, CircleClose } from '@element-plus/icons-vue'
import { listTasks, retryTask, cancelTask, type ParseTask } from '@/api/task'
import { listDocuments } from '@/api/document'
import { useKbStore } from '@/stores/kb'

const kbStore = useKbStore()

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '等待中', type: 'info' },
  PROCESSING: { label: '解析中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

const keyword = ref('')
const statusFilter = ref('')
const list = ref<ParseTask[]>([])
const documentNames = ref<Record<string, string>>({})
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const logVisible = ref(false)
const logContent = ref('')

async function loadData() {
  if (!kbStore.currentKbId) await kbStore.fetchKnowledgeBases()
  if (!kbStore.currentKbId) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const [taskRes, docRes] = await Promise.all([
      listTasks({
        knowledge_base_id: kbStore.currentKbId,
        status: statusFilter.value || undefined,
        page: page.value,
        page_size: pageSize.value,
      }),
      listDocuments({ knowledge_base_id: kbStore.currentKbId, page: 1, page_size: 200 }),
    ])
    const taskData = (taskRes as any).data ?? taskRes
    const docData = (docRes as any).data ?? docRes
    list.value = taskData.records ?? []
    total.value = Number(taskData.total ?? 0)
    documentNames.value = Object.fromEntries(
      (docData.records ?? []).map((doc: any) => [doc.id, doc.file_name]),
    )
  } finally {
    loading.value = false
  }
}

function filteredList() {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((row) => {
    const name = documentNames.value[row.document_id] || ''
    return name.toLowerCase().includes(kw) || row.id.toLowerCase().includes(kw)
  })
}

async function retry(row: ParseTask) {
  await retryTask(row.id)
  ElMessage.success('任务已重新提交')
  await loadData()
}

async function cancel(row: ParseTask) {
  await ElMessageBox.confirm(`确认取消任务「${documentNames.value[row.document_id] || row.document_id}」？`, '提示', { type: 'warning' })
  await cancelTask(row.id)
  ElMessage.success('任务已取消')
  await loadData()
}

function viewLog(row: ParseTask) {
  const lines = [
    `[INFO] 任务 ${row.id}`,
    `[INFO] 文档 ${documentNames.value[row.document_id] || row.document_id}`,
    `[INFO] 状态 ${statusMap[row.status]?.label || row.status}`,
    `[INFO] 进度 ${row.progress}%`,
    `[INFO] 已重试 ${row.retry_count} 次`,
  ]
  if (row.error_code) lines.push(`[ERROR] 错误码 ${row.error_code}`)
  if (row.error_message) lines.push(`[ERROR] ${row.error_message}`)
  if (row.started_at) lines.push(`[INFO] 开始时间 ${row.started_at}`)
  if (row.finished_at) lines.push(`[INFO] 结束时间 ${row.finished_at}`)
  logContent.value = lines.join('\n')
  logVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">解析任务管理</h2>
      <p class="page-desc">查看真实解析任务状态与进度，支持重试与取消</p>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select v-model="kbStore.currentKbId" placeholder="请选择知识库" style="width: 220px" @change="loadData">
            <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-input v-model="keyword" placeholder="搜索文档名称 / 任务 ID" clearable style="width: 220px" :prefix-icon="Search" />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 150px" @change="loadData">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button :icon="Search" @click="loadData">查询</el-button>
        </div>
      </template>

      <el-table :data="filteredList()" v-loading="loading" border stripe>
        <el-table-column prop="id" label="任务 ID" width="130" show-overflow-tooltip />
        <el-table-column label="文档名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ documentNames[row.document_id] || row.document_id }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type">{{ statusMap[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="row.status === 'FAILED' ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.error_code" class="error-code">[{{ row.error_code }}]</span>
            {{ row.error_message || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewLog(row)">日志</el-button>
            <el-button link type="warning" :icon="Refresh" :disabled="row.status === 'PROCESSING'" @click="retry(row)">重试</el-button>
            <el-button link type="danger" :icon="CircleClose" :disabled="row.status !== 'PROCESSING' && row.status !== 'PENDING'" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="logVisible" title="任务详情与日志" width="600px">
      <pre class="log-box">{{ logContent }}</pre>
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
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.error-code {
  color: var(--el-color-danger);
  font-weight: 600;
}
.log-box {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
</style>
