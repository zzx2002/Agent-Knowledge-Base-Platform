<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Edit, Delete, User } from '@element-plus/icons-vue'
import {
  listUsers,
  listRoles,
  createUser,
  updateUser,
  deleteUser,
  type UserInfo,
  type RoleOption,
} from '@/api/user'

const users = ref<UserInfo[]>([])
const roles = ref<RoleOption[]>([])
const loading = ref(false)

const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  password: '',
  displayName: '',
  email: '',
  roleCode: 'VIEWER',
})

const createRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const editVisible = ref(false)
const editingUser = ref<UserInfo | null>(null)
const editForm = reactive({ roleCode: '', status: 'ACTIVE', password: '' })
const editFormRef = ref<FormInstance>()

const editRules: FormRules = {
  password: [{ min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }],
}

async function loadData() {
  loading.value = true
  try {
    const [userRes, roleRes] = await Promise.all([listUsers(), listRoles()])
    users.value = ((userRes as any).data ?? userRes) as UserInfo[]
    roles.value = ((roleRes as any).data ?? roleRes) as RoleOption[]
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { username: '', password: '', displayName: '', email: '', roleCode: 'VIEWER' })
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    await createUser({ ...createForm })
    ElMessage.success('用户创建成功')
    createVisible.value = false
    await loadData()
  })
}

function openEdit(row: UserInfo) {
  editingUser.value = row
  Object.assign(editForm, {
    roleCode: row.roleCode || 'VIEWER',
    status: row.status || 'ACTIVE',
    password: '',
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editingUser.value || !editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    const payload: Record<string, string> = {
      roleCode: editForm.roleCode,
      status: editForm.status,
    }
    if (editForm.password) payload.password = editForm.password
    await updateUser(editingUser.value!.userId, payload)
    ElMessage.success('用户信息已更新')
    editVisible.value = false
    await loadData()
  })
}

async function remove(row: UserInfo) {
  await ElMessageBox.confirm(`确认删除用户「${row.displayName || row.username}」？`, '提示', { type: 'warning' })
  await deleteUser(row.userId)
  ElMessage.success('用户已删除')
  await loadData()
}

function roleType(code?: string) {
  if (code === 'ADMIN') return 'danger'
  if (code === 'KB_ADMIN') return 'warning'
  if (code === 'EDITOR') return 'primary'
  return 'info'
}

function statusType(status?: string) {
  return status === 'ACTIVE' ? 'success' : 'info'
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">用户与角色管理</h2>
      <p class="page-desc">管理系统账号、角色与状态，不同角色拥有不同的知识库操作权限</p>
    </div>

    <div class="summary-row">
      <div class="summary-card">
        <span class="summary-label">用户总数</span>
        <strong>{{ users.length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">启用用户</span>
        <strong>{{ users.filter((u) => u.status === 'ACTIVE').length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">管理员</span>
        <strong>{{ users.filter((u) => u.roleCode === 'ADMIN').length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">角色类型</span>
        <strong>{{ roles.length }}</strong>
      </div>
    </div>

    <el-card shadow="never" class="users-card">
      <template #header>
        <div class="toolbar">
          <div class="section-title"><el-icon><User /></el-icon> 用户列表</div>
          <span class="toolbar-spacer"></span>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" border stripe>
        <el-table-column prop="username" label="用户名" min-width="150" />
        <el-table-column prop="displayName" label="显示名称" min-width="150" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="roleType(row.roleCode)">{{ row.roleName || row.roleCode || '未分配' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status === 'ACTIVE' ? '已启用' : '已禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建用户" width="520px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="createForm.displayName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" placeholder="可选" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="createForm.roleCode" style="width: 100%">
            <el-option v-for="role in roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑用户" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="角色">
          <el-select v-model="editForm.roleCode" style="width: 100%">
            <el-option v-for="role in roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="已启用" value="ACTIVE" />
            <el-option label="已禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="重置密码" prop="password">
          <el-input v-model="editForm.password" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}
.summary-card {
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: 18px;
  padding: 18px 20px;
  box-shadow: var(--kb-shadow);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.summary-label {
  font-size: 13px;
  color: var(--kb-muted);
}
.summary-card strong {
  font-size: 26px;
  color: #1c2030;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.toolbar-spacer {
  flex: 1;
}
@media (max-width: 900px) {
  .summary-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
