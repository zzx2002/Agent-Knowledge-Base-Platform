<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Connection, DocumentCopy } from '@element-plus/icons-vue'

// Agent 接入管理占位数据（对应文档 9.6）
interface AgentClient {
  id: string
  name: string
  client_id: string
  grant_type: string
  allowed_kbs: string[]
  status: '启用' | '停用'
  created_at: string
}

const mcpEndpoint = `${location.origin}/mcp`
const apiEndpoint = `${location.origin}/api/v1`

const list = ref<AgentClient[]>([
  {
    id: 'agent_001',
    name: 'WorkBuddy 助手',
    client_id: 'wb_client_demo',
    grant_type: 'Authorization Code + PKCE',
    allowed_kbs: ['产品文档库', '研发知识库'],
    status: '启用',
    created_at: '2026-11-18 10:00:00',
  },
  {
    id: 'agent_002',
    name: 'Codex 编码助手',
    client_id: 'codex_client_demo',
    grant_type: 'Authorization Code + PKCE',
    allowed_kbs: ['研发知识库'],
    status: '停用',
    created_at: '2026-11-17 15:30:00',
  },
])

const dialogVisible = ref(false)
const form = ref({ name: '', allowed_kbs: [] as string[] })
const tokenVisible = ref(false)
const generatedToken = ref('')

function openCreate() {
  form.value = { name: '', allowed_kbs: [] }
  dialogVisible.value = true
}

function createAgent() {
  if (!form.value.name) {
    ElMessage.warning('请输入客户端名称')
    return
  }
  list.value.push({
    id: `agent_${Date.now()}`,
    name: form.value.name,
    client_id: `client_${Math.random().toString(36).slice(2, 10)}`,
    grant_type: 'Authorization Code + PKCE',
    allowed_kbs: form.value.allowed_kbs,
    status: '启用',
    created_at: new Date().toLocaleString('zh-CN'),
  })
  dialogVisible.value = false
  ElMessage.success('Agent 客户端创建成功（占位）')
}

async function remove(row: AgentClient) {
  await ElMessageBox.confirm(`确认删除客户端「${row.name}」？`, '提示', { type: 'warning' })
  list.value = list.value.filter((item) => item.id !== row.id)
  ElMessage.success('已删除（占位）')
}

function copy(text: string) {
  navigator.clipboard?.writeText(text)
  ElMessage.success('已复制')
}

function createToken(row: AgentClient) {
  generatedToken.value = `kb_${row.id}_${Math.random().toString(36).slice(2)}`
  tokenVisible.value = true
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">Agent 接入管理</h2>
      <p class="page-desc">查看 MCP Endpoint，管理 Agent 客户端、OAuth 授权与访问令牌（占位数据）</p>
    </div>

    <el-card shadow="never" class="endpoint-card">
      <template #header>
        <div class="card-title"><el-icon><Connection /></el-icon> 服务端接入地址</div>
      </template>
      <div class="endpoint-row">
        <span class="endpoint-label">MCP Endpoint：</span>
        <code>{{ mcpEndpoint }}</code>
        <el-button link type="primary" :icon="DocumentCopy" @click="copy(mcpEndpoint)">复制</el-button>
      </div>
      <div class="endpoint-row">
        <span class="endpoint-label">REST API：</span>
        <code>{{ apiEndpoint }}</code>
        <el-button link type="primary" :icon="DocumentCopy" @click="copy(apiEndpoint)">复制</el-button>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="toolbar">
          <span class="card-title">Agent 客户端</span>
          <el-button type="primary" :icon="Plus" @click="openCreate">创建客户端</el-button>
        </div>
      </template>

      <el-table :data="list" border stripe>
        <el-table-column prop="name" label="客户端名称" min-width="160" />
        <el-table-column prop="client_id" label="Client ID" min-width="180" />
        <el-table-column prop="grant_type" label="授权方式" min-width="200" />
        <el-table-column label="可访问知识库" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="kb in row.allowed_kbs" :key="kb" size="small" style="margin-right: 6px">{{ kb }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="createToken(row)">Token 管理</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="创建 Agent 客户端" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="客户端名称">
          <el-input v-model="form.name" placeholder="例如：WorkBuddy 助手" />
        </el-form-item>
        <el-form-item label="可访问知识库">
          <el-select v-model="form.allowed_kbs" multiple style="width: 100%" placeholder="请选择知识库">
            <el-option label="产品文档库" value="产品文档库" />
            <el-option label="研发知识库" value="研发知识库" />
            <el-option label="运维手册库" value="运维手册库" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createAgent">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tokenVisible" title="Access Token（占位）" width="500px">
      <el-alert type="warning" :closable="false" show-icon title="生产环境请通过服务端 OAuth 流程签发令牌，此处为占位展示" />
      <pre class="token-box">{{ generatedToken }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.endpoint-card {
  margin-bottom: 0;
}
.card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}
.endpoint-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.endpoint-label {
  width: 110px;
  color: var(--kb-muted);
}
code {
  background: #f4f4f5;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.token-box {
  margin-top: 12px;
  padding: 12px;
  background: #f4f4f5;
  border-radius: 4px;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
