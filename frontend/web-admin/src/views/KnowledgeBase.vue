<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete, Lock, Refresh } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  listKbPermissions,
  grantKbPermission,
  revokeKbPermission,
  type KnowledgeBase as KB,
  type KbPermissionItem,
} from '@/api/knowledgeBase'

const list = ref<KB[]>([])
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('创建知识库')
const formRef = ref<FormInstance>()
const editingId = ref<string>('')
const form = reactive({
  name: '',
  description: '',
  embedding_model: 'bge-m3',
  chunk_strategy: '结构优先',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
}

// 授权管理
const permissionVisible = ref(false)
const permissionKb = ref<KB | null>(null)
const permissions = ref<KbPermissionItem[]>([])
const permissionForm = reactive({
  subject_type: 'USER' as 'USER' | 'ROLE',
  subject_id: '',
  permission: 'READ' as KbPermissionItem['permission'],
})
const permissionLoading = ref(false)

async function openPermissions(row: KB) {
  permissionKb.value = row
  permissionVisible.value = true
  await loadPermissions()
}

async function loadPermissions() {
  if (!permissionKb.value) return
  permissionLoading.value = true
  try {
    const res = await listKbPermissions(permissionKb.value.id)
    const data = (res as any).data ?? res
    permissions.value = Array.isArray(data) ? data : []
  } finally {
    permissionLoading.value = false
  }
}

async function submitPermission() {
  if (!permissionKb.value || !permissionForm.subject_id) {
    ElMessage.warning('请填写主体 ID')
    return
  }
  await grantKbPermission(permissionKb.value.id, {
    subject_type: permissionForm.subject_type,
    subject_id: permissionForm.subject_id,
    permission: permissionForm.permission,
  })
  ElMessage.success('授权成功')
  permissionForm.subject_id = ''
  await loadPermissions()
}

async function removePermission(row: KbPermissionItem) {
  if (!permissionKb.value) return
  await ElMessageBox.confirm(
    `确认撤销 ${row.subject_type} ${row.subject_id} 的 ${row.permission} 权限？`,
    '提示',
    { type: 'warning' },
  )
  await revokeKbPermission(permissionKb.value.id, row.subject_type, row.subject_id, row.permission)
  ElMessage.success('已撤销')
  await loadPermissions()
}

async function loadData() {
  loading.value = true
  try {
    const res = await listKnowledgeBases({
      keyword: keyword.value,
      page: page.value,
      page_size: pageSize.value,
    })
    const data = (res as any).data ?? res
    list.value = data.records ?? []
    total.value = data.total ?? 0
  } catch {
    // 拦截器已统一提示错误
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  dialogTitle.value = '创建知识库'
  Object.assign(form, { name: '', description: '', embedding_model: 'bge-m3', chunk_strategy: '结构优先' })
  dialogVisible.value = true
}

function openEdit(row: KB) {
  editingId.value = row.id
  dialogTitle.value = '编辑知识库'
  Object.assign(form, {
    name: row.name,
    description: row.description || '',
    embedding_model: row.embedding_model || 'bge-m3',
    chunk_strategy: row.chunk_strategy || '结构优先',
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (editingId.value) {
      await updateKnowledgeBase(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createKnowledgeBase({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function remove(row: KB) {
  await ElMessageBox.confirm(`确认删除知识库「${row.name}」？`, '提示', { type: 'warning' })
  await deleteKnowledgeBase(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">知识库管理</h2>
      <p class="page-desc">创建与维护知识库，配置 Embedding 模型与分块策略；每个知识库独立隔离，可按需授权</p>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <el-input
            v-model="keyword"
            placeholder="搜索知识库名称"
            clearable
            style="width: 240px"
            :prefix-icon="Search"
            @keyup.enter="loadData"
          />
          <el-button type="primary" :icon="Plus" @click="openCreate">创建知识库</el-button>
          <el-button :icon="Search" @click="loadData">查询</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="name" label="知识库名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="embedding_model" label="Embedding 模型" width="160" />
        <el-table-column prop="chunk_strategy" label="分块策略" width="140" />
        <el-table-column prop="owner_id" label="归属用户ID" width="110" />
        <el-table-column prop="document_count" label="文档数" width="100" />
        <el-table-column prop="updated_at" label="更新时间" width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" :icon="Lock" @click="openPermissions(row)">授权</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="知识库名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="Embedding 模型">
          <el-select v-model="form.embedding_model" style="width: 100%">
            <el-option label="bge-m3" value="bge-m3" />
            <el-option label="bge-large-zh" value="bge-large-zh" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块策略">
          <el-select v-model="form.chunk_strategy" style="width: 100%">
            <el-option label="结构优先" value="结构优先" />
            <el-option label="固定长度" value="固定长度" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permissionVisible"
      :title="`知识库授权 - ${permissionKb?.name || ''}`"
      width="720px"
    >
      <div class="permission-grant">
        <el-select v-model="permissionForm.subject_type" style="width: 120px">
          <el-option label="用户" value="USER" />
          <el-option label="角色" value="ROLE" />
        </el-select>
        <el-input
          v-model="permissionForm.subject_id"
          :placeholder="permissionForm.subject_type === 'USER' ? '用户 ID' : '角色 ID'"
          style="width: 180px"
        />
        <el-select v-model="permissionForm.permission" style="width: 130px">
          <el-option label="只读 READ" value="READ" />
          <el-option label="写入 WRITE" value="WRITE" />
          <el-option label="删除 DELETE" value="DELETE" />
          <el-option label="管理 ADMIN" value="ADMIN" />
        </el-select>
        <el-button type="primary" @click="submitPermission">授权</el-button>
        <el-button :icon="Refresh" @click="loadPermissions">刷新</el-button>
      </div>

      <el-table :data="permissions" v-loading="permissionLoading" border size="small">
        <el-table-column prop="subject_type" label="主体类型" width="100" />
        <el-table-column prop="subject_id" label="主体 ID" width="120" />
        <el-table-column prop="permission" label="权限" width="110" />
        <el-table-column prop="granted" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.granted ? 'success' : 'info'">
              {{ row.granted ? '已授权' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button link type="danger" @click="removePermission(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
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
.permission-grant {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}
</style>
