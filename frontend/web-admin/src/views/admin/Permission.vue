<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh as RefreshIcon } from '@element-plus/icons-vue'
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

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-select v-model="kbId" placeholder="请选择知识库" style="width: 260px" @change="loadPermissions">
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>
          <el-button :icon="RefreshIcon" :loading="kbLoading" @click="loadKnowledgeBases">刷新知识库</el-button>
          <el-button :icon="RefreshIcon" :loading="loading" @click="loadPermissions">刷新权限</el-button>
        </div>
      </template>

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

      <el-table :data="permissions" v-loading="loading" border stripe>
        <el-table-column prop="subject_type" label="主体类型" width="120" />
        <el-table-column prop="subject_id" label="主体 ID" min-width="180" />
        <el-table-column prop="permission" label="权限" width="130" />
        <el-table-column prop="granted" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.granted ? 'success' : 'info'">
              {{ row.granted ? '已授权' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" @click="remove(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar,
.grant-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.grant-bar {
  margin-bottom: 16px;
}
</style>
