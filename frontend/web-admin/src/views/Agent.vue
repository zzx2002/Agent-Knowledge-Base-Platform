<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Delete,
  Connection,
  DocumentCopy,
  Key,
  Link,
  Lock,
  MagicStick,
  Clock,
  Collection,
} from '@element-plus/icons-vue'

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
  ElMessage.success('Agent 客户端创建成功')
}

async function remove(row: AgentClient) {
  await ElMessageBox.confirm(`确认删除客户端「${row.name}」？`, '提示', { type: 'warning' })
  list.value = list.value.filter((item) => item.id !== row.id)
  ElMessage.success('客户端已删除')
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
  <div class="page-container agent-page">
    <div class="page-header">
      <h2 class="page-title">Agent 接入管理</h2>
      <p class="page-desc">配置 MCP 与 REST API 接入地址，管理 Agent 客户端、OAuth 授权与访问令牌。</p>
    </div>

    <!-- 接入地址卡片 -->
    <el-card shadow="never" class="endpoint-card">
      <template #header>
        <div class="endpoint-head">
          <div class="endpoint-head-icon"><el-icon><Connection /></el-icon></div>
          <div class="endpoint-head-copy">
            <div class="section-title">服务端接入地址</div>
            <div class="section-sub">将以下地址配置到你的 Agent 客户端（示例配置）</div>
          </div>
          <span class="section-tag">接入配置</span>
        </div>
      </template>
      <div class="endpoint-grid">
        <div class="endpoint-item">
          <div class="endpoint-item-head">
            <span class="endpoint-name"><el-icon><Link /></el-icon> MCP Endpoint</span>
            <el-button link type="primary" :icon="DocumentCopy" @click="copy(mcpEndpoint)">复制</el-button>
          </div>
          <code>{{ mcpEndpoint }}</code>
        </div>
        <div class="endpoint-item">
          <div class="endpoint-item-head">
            <span class="endpoint-name"><el-icon><Collection /></el-icon> REST API</span>
            <el-button link type="primary" :icon="DocumentCopy" @click="copy(apiEndpoint)">复制</el-button>
          </div>
          <code>{{ apiEndpoint }}</code>
        </div>
      </div>
    </el-card>

    <!-- Agent 客户端卡片网格 -->
    <div class="clients-head">
      <div class="head-copy">
        <div class="section-title"><el-icon><Key /></el-icon> Agent 客户端</div>
        <div class="section-sub">共 {{ list.length }} 个客户端</div>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">创建客户端</el-button>
    </div>

    <div class="agent-grid">
      <div v-for="row in list" :key="row.id" class="agent-card">
        <div class="agent-top">
          <div class="agent-avatar"><el-icon><MagicStick /></el-icon></div>
          <div class="agent-identity">
            <div class="agent-name">{{ row.name }}</div>
            <div class="agent-client"><span>Client ID</span> {{ row.client_id }}</div>
          </div>
          <span class="state-chip" :class="row.status === '启用' ? 'on' : 'off'">{{ row.status }}</span>
        </div>

        <div class="agent-body">
          <div class="meta-line">
            <span class="meta-key"><el-icon><Lock /></el-icon> 授权方式</span>
            <span class="meta-val">{{ row.grant_type }}</span>
          </div>
          <div class="meta-line align-top">
            <span class="meta-key"><el-icon><Collection /></el-icon> 允许知识库</span>
            <span class="kb-chips">
              <span v-for="kb in row.allowed_kbs" :key="kb" class="kb-chip">{{ kb }}</span>
              <span v-if="!row.allowed_kbs.length" class="no-kb">未配置</span>
            </span>
          </div>
          <div class="meta-line">
            <span class="meta-key"><el-icon><Clock /></el-icon> 创建时间</span>
            <span class="meta-val">{{ row.created_at }}</span>
          </div>
        </div>

        <div class="agent-footer">
          <el-button link type="primary" :icon="DocumentCopy" @click="copy(row.client_id)">复制 Client ID</el-button>
          <div class="footer-actions">
            <el-button type="primary" plain :icon="Key" @click="createToken(row)">生成令牌</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

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

    <el-dialog v-model="tokenVisible" title="Access Token" width="500px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="生产环境请通过服务端 OAuth 流程签发令牌，此处为示例令牌，请勿用于真实环境"
      />
      <pre class="token-box">{{ generatedToken }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.agent-page {
  padding-bottom: 40px;
}

/* 接入地址卡片 */
.endpoint-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.endpoint-head-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6c5ce7, #8b7cf1);
  color: #fff;
  font-size: 19px;
  box-shadow: 0 6px 14px rgba(108, 92, 231, 0.25);
}
.endpoint-head-copy {
  flex: 1;
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
.section-tag {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 11px;
  font-weight: 600;
}
.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.endpoint-item {
  padding: 14px 16px;
  border: 1px solid var(--kb-border);
  border-radius: 14px;
  background: #faf9ff;
}
.endpoint-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.endpoint-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #5c6377;
}
.endpoint-name .el-icon {
  color: var(--kb-primary);
}
.endpoint-item code {
  display: block;
  padding: 9px 12px;
  border-radius: 9px;
  background: #fff;
  border: 1px dashed #d9d3ff;
  color: var(--kb-primary);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  word-break: break-all;
}

/* 客户端头部 */
.clients-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 24px 0 16px;
}
.head-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 客户端卡片网格 */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 16px;
}
.agent-card {
  display: flex;
  flex-direction: column;
  background: var(--kb-surface);
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.agent-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 38px rgba(31, 36, 52, 0.1);
}
.agent-top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 14px;
}
.agent-avatar {
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6c5ce7, #9a8cf5);
  color: #fff;
  font-size: 21px;
  box-shadow: 0 6px 14px rgba(108, 92, 231, 0.25);
}
.agent-identity {
  flex: 1;
  min-width: 0;
}
.agent-name {
  font-size: 15px;
  font-weight: 700;
  color: #1f2434;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-client {
  margin-top: 3px;
  font-size: 12px;
  color: var(--kb-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-client span {
  color: #a5aabc;
  margin-right: 4px;
}
.state-chip {
  flex: 0 0 auto;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.state-chip.on {
  background: #e8f8f0;
  color: #16a34a;
}
.state-chip.off {
  background: #f4f5f7;
  color: #7b8399;
}

.agent-body {
  flex: 1;
  padding: 4px 18px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.meta-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}
.meta-line.align-top {
  align-items: flex-start;
}
.meta-key {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  color: var(--kb-muted);
}
.meta-key .el-icon {
  color: #a5aabc;
  font-size: 14px;
}
.meta-val {
  color: #1f2434;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.kb-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}
.kb-chip {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 11px;
  font-weight: 600;
}
.no-kb {
  color: var(--kb-muted);
  font-size: 12px;
}

.agent-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid var(--kb-border);
  background: #fcfcfe;
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.token-box {
  margin-top: 12px;
  padding: 12px;
  background: #f4f4f5;
  border-radius: 4px;
  word-break: break-all;
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .endpoint-grid { grid-template-columns: 1fr; }
  .agent-footer { flex-wrap: wrap; }
}
</style>
