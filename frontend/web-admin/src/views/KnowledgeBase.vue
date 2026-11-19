<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  type KnowledgeBase as KB,
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
    total.value = Number(data.total ?? 0)
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
      <h2 class="page-title">我的知识库</h2>
      <p class="page-desc">创建知识库后即可上传文档并开始对话，每个知识库的数据相互隔离</p>
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
        <el-table-column prop="document_count" label="文档数" width="100" />
        <el-table-column prop="updated_at" label="更新时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
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
</style>
