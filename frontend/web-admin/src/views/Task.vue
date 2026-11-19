<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  CircleClose,
  Tickets,
  CircleCheckFilled,
  Loading,
  WarningFilled,
  Timer,
  Document,
} from '@element-plus/icons-vue'
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

// 顶部状态统计：基于当前加载的任务列表计算
const counts = computed(() => ({
  total: list.value.length,
  completed: list.value.filter((r) => r.status === 'COMPLETED').length,
  processing: list.value.filter((r) => r.status === 'PROCESSING').length,
  failed: list.value.filter((r) => r.status === 'FAILED').length,
  pending: list.value.filter((r) => r.status === 'PENDING').length,
}))

const statCards = computed(() => [
  { label: '任务总数', value: counts.value.total, icon: Tickets, tone: 'purple' },
  { label: '已完成', value: counts.value.completed, icon: CircleCheckFilled, tone: 'success' },
  { label: '处理中', value: counts.value.processing, icon: Loading, tone: 'primary' },
  { label: '失败', value: counts.value.failed, icon: WarningFilled, tone: 'danger' },
  { label: '排队中', value: counts.value.pending, icon: Timer, tone: 'warning' },
])

// 彩色状态 chip
function statusClass(status: string) {
  const map: Record<string, string> = {
    COMPLETED: 'done',
    PROCESSING: 'processing',
    FAILED: 'failed',
    PENDING: 'pending',
    CANCELLED: 'cancelled',
  }
  return map[status] || 'pending'
}

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
  <div class="page-container task-page">
    <div class="page-header">
      <h2 class="page-title">解析任务管理</h2>
      <p class="page-desc">集中查看解析任务的状态与进度，支持重试、取消与日志查看。</p>
    </div>

    <!-- 状态统计卡片 -->
    <div class="stat-grid">
      <div v-for="card in statCards" :key="card.label" class="stat-tile" :class="`tone-${card.tone}`">
        <div class="tile-head">
          <div class="tile-icon"><el-icon><component :is="card.icon" /></el-icon></div>
          <span class="tile-accent"></span>
        </div>
        <div class="tile-value">{{ card.value }}</div>
        <div class="tile-label">{{ card.label }}</div>
      </div>
    </div>

    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="list-head">
          <div class="head-copy">
            <div class="section-title"><el-icon><Document /></el-icon> 任务列表</div>
            <div class="section-sub">共 {{ total }} 条解析任务</div>
          </div>
          <div class="toolbar">
            <el-select v-model="kbStore.currentKbId" placeholder="请选择知识库" style="width: 200px" @change="loadData">
              <el-option v-for="kb in kbStore.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <el-input v-model="keyword" placeholder="搜索文档名称 / 任务 ID" clearable style="width: 220px" :prefix-icon="Search" />
            <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="loadData">
              <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button :icon="Search" @click="loadData">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredList()" v-loading="loading" border stripe>
        <el-table-column prop="id" label="任务 ID" width="130" show-overflow-tooltip />
        <el-table-column label="文档名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ documentNames[row.document_id] || row.document_id }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="status-chip" :class="statusClass(row.status)">{{ statusMap[row.status]?.label || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="170">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress"
              :color="row.status === 'FAILED' ? '#f56c6c' : '#6c5ce7'"
              :stroke-width="8"
            />
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
.task-page {
  padding-bottom: 40px;
}

/* 状态统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.stat-tile {
  position: relative;
  overflow: hidden;
  padding: 18px;
  background: var(--kb-surface);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
  transition: transform 0.2s ease;
}
.stat-tile:hover {
  transform: translateY(-3px);
}
.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tile-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #fff;
}
.tile-accent {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.07;
}
.tile-value {
  margin-top: 12px;
  font-size: 26px;
  font-weight: 700;
  color: #171b2b;
  line-height: 1.1;
}
.tile-label {
  margin-top: 5px;
  font-size: 13px;
  color: var(--kb-muted);
}
.tone-purple .tile-icon { background: linear-gradient(135deg, #6c5ce7, #8b7cf1); }
.tone-success .tile-icon { background: linear-gradient(135deg, #26c6a2, #5cdbbd); }
.tone-primary .tile-icon { background: linear-gradient(135deg, #4a90e2, #74b4f2); }
.tone-danger .tile-icon { background: linear-gradient(135deg, #f56c6c, #f89393); }
.tone-warning .tile-icon { background: linear-gradient(135deg, #ff9f43, #ffbe76); }

/* 列表卡片 */
.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.head-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.section-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #1f2434;
}
.section-title .el-icon {
  color: var(--kb-primary);
  font-size: 18px;
}
.section-sub {
  font-size: 12px;
  color: var(--kb-muted);
}
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

/* 状态 chip */
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.status-chip::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-chip.done { background: #e8f8f0; color: #16a34a; }
.status-chip.processing { background: var(--kb-primary-soft); color: var(--kb-primary); }
.status-chip.failed { background: #fef0f0; color: #e74c3c; }
.status-chip.pending { background: #f4f5f7; color: #7b8399; }
.status-chip.cancelled { background: #f4f5f7; color: #9aa1b2; }

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

@media (max-width: 1200px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
