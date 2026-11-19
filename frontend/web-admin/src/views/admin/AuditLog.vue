<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search, Document } from '@element-plus/icons-vue'
import { listAuditLogs, type AuditLog } from '@/api/audit'

const list = ref<AuditLog[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const resourceType = ref('')
const success = ref<boolean | undefined>(undefined)
const page = ref(1)
const pageSize = ref(10)

const detailVisible = ref(false)
const current = ref<AuditLog | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await listAuditLogs({
      keyword: keyword.value,
      resourceType: resourceType.value,
      success: success.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    const data = (res as any).data ?? res
    list.value = data.records ?? []
    total.value = Number(data.total ?? 0)
  } finally {
    loading.value = false
  }
}

function viewDetail(row: AuditLog) {
  current.value = row
  detailVisible.value = true
}

function reset() {
  keyword.value = ''
  resourceType.value = ''
  success.value = undefined
  page.value = 1
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">操作审计日志</h2>
      <p class="page-desc">记录用户与 Agent 的关键请求，包含操作人、资源、结果、耗时与来源 IP</p>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索操作路径"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadData"
          />
          <el-select v-model="resourceType" placeholder="全部资源" clearable style="width: 160px">
            <el-option label="知识库" value="knowledge-bases" />
            <el-option label="文档" value="documents" />
            <el-option label="检索" value="search" />
            <el-option label="问答" value="chat" />
            <el-option label="任务" value="tasks" />
            <el-option label="用户" value="users" />
          </el-select>
          <el-select v-model="success" placeholder="全部结果" clearable style="width: 140px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
          <el-button :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="created_at" label="时间" width="180" />
        <el-table-column prop="username" label="操作人" width="130">
          <template #default="{ row }">{{ row.username || '匿名' }}</template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" min-width="220" show-overflow-tooltip />
        <el-table-column prop="resource_type" label="资源" width="140" />
        <el-table-column prop="request_ip" label="来源 IP" width="140" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ row.detail?.duration_ms ?? '—' }} ms</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Document" @click="viewDetail(row)">详情</el-button>
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
          @size-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="审计详情" width="720px">
      <el-descriptions :column="2" border v-if="current">
        <el-descriptions-item label="操作">{{ current.operation }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ current.username || '匿名' }}</el-descriptions-item>
        <el-descriptions-item label="资源类型">{{ current.resource_type || '—' }}</el-descriptions-item>
        <el-descriptions-item label="资源 ID">{{ current.resource_id || '—' }}</el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ current.request_ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ current.success ? '成功' : '失败' }}</el-descriptions-item>
      </el-descriptions>
      <div class="detail-title">请求明细</div>
      <pre class="detail-box">{{ JSON.stringify(current?.detail ?? {}, null, 2) }}</pre>
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
.detail-title {
  margin: 18px 0 8px;
  font-weight: 600;
}
.detail-box {
  margin: 0;
  padding: 14px;
  background: #f6f7fb;
  border-radius: 12px;
  max-height: 50vh;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>
