<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh as RefreshIcon, Lock, CircleCheck, CircleClose, User } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  listKbPermissions,
  grantKbPermission,
  revokeKbPermission,
  type KnowledgeBase as KB,
  type KbPermissionItem,
} from '@/api/knowledgeBase'

const knowledgeBases = ref<KB[]>([])
const kbId = ref('')
const permissions = ref<KbPermissionItem[]>([])
const loading = ref(false)
const kbLoading = ref(false)

const form = reactive({
  subject_type: 'USER' as 'USER' | 'ROLE',
  subject_id: '',
  permission: 'READ' as KbPermissionItem['permission'],
})

const stats = computed(() => ({
  total: permissions.value.length,
  enabled: permissions.value.filter((item) => item.granted).length,
  disabled: permissions.value.filter((item) => !item.granted).length,
  users: permissions.value.filter((item) => item.subject_type === 'USER').length,
}))

function permissionLabel(permission: string) {
  switch (permission) {
    case 'READ':
      return '只读'
    case 'WRITE':
      return '写入'
    case 'DELETE':
      return '删除'
    case 'ADMIN':
      return '管理'
    default:
      return permission
  }
}

function permissionClass(permission: string) {
  switch (permission) {
    case 'READ':
      return 'perm-read'
    case 'WRITE':
      return 'perm-write'
    case 'DELETE':
      return 'perm-delete'
    case 'ADMIN':
      return 'perm-admin'
    default:
      return 'perm-read'
  }
}

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    const res = await listKnowledgeBases({ page: 1, page_size: 200 })
    const data = (res as any).data ?? res
    knowledgeBases.value = data.records ?? []
    if (!kbId.value && knowledgeBases.value.length) {
      kbId.value = knowledgeBases.value[0].id
      await loadPermissions()
    }
  } finally {
    kbLoading.value = false
  }
}

async function loadPermissions() {
  if (!kbId.value) {
    permissions.value = []
    return
  }
  loading.value = true
  try {
    const res = await listKbPermissions(kbId.value)
    const data = (res as any).data ?? res
    permissions.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!kbId.value || !form.subject_id) {
    ElMessage.warning('请选择知识库并填写主体 ID')
    return
  }
  await grantKbPermission(kbId.value, { ...form })
  ElMessage.success('授权成功')
  form.subject_id = ''
  await loadPermissions()
}

async function remove(row: KbPermissionItem) {
  await ElMessageBox.confirm(
    `确认撤销 ${row.subject_type} ${row.subject_id} 的 ${row.permission} 权限？`,
    '提示',
    { type: 'warning' },
  )
  await revokeKbPermission(kbId.value, row.subject_type, row.subject_id, row.permission)
  ElMessage.success('已撤销')
  await loadPermissions()
}

onMounted(loadKnowledgeBases)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">权限管理</h2>
      <p class="page-desc">按知识库配置用户与角色权限，控制读取、写入、删除和管理能力</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-primary"><el-icon><Lock /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">授权总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-success"><el-icon><CircleCheck /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.enabled }}</div>
          <div class="stat-label">已启用</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-muted"><el-icon><CircleClose /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.disabled }}</div>
          <div class="stat-label">已禁用</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-soft"><el-icon><User /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.users }}</div>
          <div class="stat-label">用户主体</div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="perm-card">
      <div class="card-head">
        <div class="card-head-text">
          <h3 class="section-title">选择知识库</h3>
          <p class="section-sub">权限按知识库隔离，请先切换到目标知识库再配置</p>
        </div>
        <div class="toolbar">
          <el-select v-model="kbId" placeholder="请选择知识库" style="width: 240px" @change="loadPermissions">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :icon="RefreshIcon" :loading="kbLoading" @click="loadKnowledgeBases">刷新知识库</el-button>
          <el-button :icon="RefreshIcon" :loading="loading" @click="loadPermissions">刷新权限</el-button>
        </div>
      </div>

      <div class="section-divider"></div>

      <div class="grant-block">
        <h3 class="section-title">新增授权</h3>
        <p class="section-sub">指定主体并分配权限级别，即可授予对该知识库的访问能力</p>
        <div class="grant-bar">
          <el-select v-model="form.subject_type" style="width: 120px">
            <el-option label="用户" value="USER" />
            <el-option label="角色" value="ROLE" />
          </el-select>
          <el-input
            v-model="form.subject_id"
            :placeholder="form.subject_type === 'USER' ? '用户 ID' : '角色 ID'"
            style="width: 200px"
          />
          <el-select v-model="form.permission" style="width: 150px">
            <el-option label="只读 READ" value="READ" />
            <el-option label="写入 WRITE" value="WRITE" />
            <el-option label="删除 DELETE" value="DELETE" />
            <el-option label="管理 ADMIN" value="ADMIN" />
          </el-select>
          <el-button type="primary" @click="submit">新增授权</el-button>
        </div>
      </div>

      <div class="list-block">
        <h3 class="section-title">权限列表</h3>
        <p class="section-sub">当前知识库下的全部授权记录，可随时撤销</p>
        <el-table :data="permissions" v-loading="loading">
          <el-table-column label="主体类型" width="120">
            <template #default="{ row }">
              <span class="subject-chip">{{ row.subject_type === 'USER' ? '用户' : '角色' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="subject_id" label="主体 ID" min-width="180" />
          <el-table-column label="权限" width="130">
            <template #default="{ row }">
              <span class="perm-chip" :class="permissionClass(row.permission)">{{ permissionLabel(row.permission) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <span class="status-tag" :class="row.granted ? 'status-on' : 'status-off'">
                <i class="status-dot"></i>{{ row.granted ? '已授权' : '已禁用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="danger" @click="remove(row)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.stat-icon-success {
  background: #eafaf2;
  color: #1a9d68;
}
.stat-icon-muted {
  background: #f1f2f7;
  color: #7a8397;
}
.stat-icon-soft {
  background: #fff5e3;
  color: #c98a12;
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
.perm-card {
  padding: 0;
}
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 18px;
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
.section-divider {
  height: 1px;
  background: var(--kb-border);
  margin: 4px 0 20px;
}
.grant-block,
.list-block {
  margin-bottom: 24px;
}
.grant-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 16px;
  padding: 16px;
  border-radius: 14px;
  background: #fafafe;
}
.subject-chip {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 8px;
  background: #f4f5fa;
  color: #6b7285;
  font-size: 12px;
}
.perm-chip {
  display: inline-block;
  padding: 4px 11px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}
.perm-read {
  color: #6c5ce7;
  background: var(--kb-primary-soft);
}
.perm-write {
  color: #1a9d68;
  background: #eafaf2;
}
.perm-delete {
  color: #e0505b;
  background: #ffeef0;
}
.perm-admin {
  color: #c98a12;
  background: #fff5e3;
}
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-on {
  color: #1a9d68;
  background: #eafaf2;
}
.status-off {
  color: #7a8397;
  background: #f1f2f7;
}
@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
