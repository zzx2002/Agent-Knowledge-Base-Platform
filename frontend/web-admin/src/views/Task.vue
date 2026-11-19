<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, CircleClose } from '@element-plus/icons-vue'

// 解析任务占位数据（对应文档 9.5：状态、进度、日志、失败原因、重试、取消）
interface TaskItem {
  id: string
  document_name: string
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  progress: number
  fail_reason?: string
  created_at: string
}

const statusMap: Record<string, { label: string; type: string }> = {
  PENDING: { label: '等待中', type: 'info' },
  PROCESSING: { label: '解析中', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

const keyword = ref('')
const statusFilter = ref('')
const list = ref<TaskItem[]>([
  { id: 'task_1001', document_name: '技术方案.docx', status: 'COMPLETED', progress: 100, created_at: '2026-11-19 09:12:00' },
  { id: 'task_1002', document_name: '季度报表.xlsx', status: 'PROCESSING', progress: 63, created_at: '2026-11-19 09:15:00' },
  { id: 'task_1003', document_name: '扫描件.pdf', status: 'FAILED', progress: 42, fail_reason: 'OCR 服务超时', created_at: '2026-11-19 08:50:00' },
  { id: 'task_1004', document_name: '会议纪要.md', status: 'PENDING', progress: 0, created_at: '2026-11-19 09:20:00' },
])

const logVisible = ref(false)
const logContent = ref('')

function retry(row: TaskItem) {
  row.status = 'PROCESSING'
  row.progress = 0
  row.fail_reason = ''
  ElMessage.success('任务已重新提交')
}

async function cancel(row: TaskItem) {
  await ElMessageBox.confirm(`确认取消任务「${row.document_name}」？`, '提示', { type: 'warning' })
  row.status = 'CANCELLED'
  ElMessage.success('任务已取消')
}

function viewLog(row: TaskItem) {
  logContent.value = `[INFO] 任务 ${row.id} 开始解析\n[INFO] 文档：${row.document_name}\n[INFO] 状态：${statusMap[row.status]?.label}\n${row.fail_reason ? `[ERROR] 失败原因：${row.fail_reason}` : '[INFO] 暂无异常日志'}`
  logVisible.value = true
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">解析任务管理</h2>
      <p class="page-desc">查看解析任务状态与进度，支持查看日志、重试与取消（当前为占位数据）</p>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索文档名称" clearable style="width: 240px" :prefix-icon="Search" />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 160px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-button :icon="Search">查询</el-button>
        </div>
      </template>

      <el-table :data="list" border stripe>
        <el-table-column prop="id" label="任务 ID" width="120" />
        <el-table-column prop="document_name" label="文档名称" min-width="180" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type">{{ statusMap[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :status="row.status === 'FAILED' ? 'exception' : undefined" />
          </template>
        </el-table-column>
        <el-table-column prop="fail_reason" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewLog(row)">日志</el-button>
            <el-button link type="warning" :icon="Refresh" :disabled="row.status !== 'FAILED'" @click="retry(row)">重试</el-button>
            <el-button link type="danger" :icon="CircleClose" :disabled="row.status !== 'PROCESSING' && row.status !== 'PENDING'" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="logVisible" title="解析日志" width="600px">
      <pre class="log-box">{{ logContent }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.log-box {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
}
</style>
