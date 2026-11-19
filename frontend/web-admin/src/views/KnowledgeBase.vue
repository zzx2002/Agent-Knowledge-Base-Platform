<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete, Collection, Document, Clock } from '@element-plus/icons-vue'
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

function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
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

    <div class="toolbar-card card-panel">
      <div class="toolbar-left">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称"
          clearable
          style="width: 280px"
          :prefix-icon="Search"
          @keyup.enter="loadData"
        />
        <el-button :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">创建知识库</el-button>
    </div>

    <div v-loading="loading" class="kb-grid">
      <div v-for="kb in list" :key="kb.id" class="kb-card">
        <div class="kb-card-top">
          <div class="kb-icon"><el-icon><Collection /></el-icon></div>
          <div class="kb-heading">
            <h3 class="kb-name" :title="kb.name">{{ kb.name }}</h3>
            <span class="kb-model">{{ kb.embedding_model || '默认模型' }}</span>
          </div>
        </div>

        <p class="kb-desc">{{ kb.description || '暂无描述，点击编辑可补充说明信息' }}</p>

        <div class="kb-metrics">
          <div class="kb-metric">
            <div class="kb-metric-value">
              <el-icon><Document /></el-icon>
              {{ kb.document_count ?? 0 }}
            </div>
            <span class="kb-metric-label">文档数</span>
          </div>
          <div class="kb-metric">
            <div class="kb-metric-value kb-metric-time">
              <el-icon><Clock /></el-icon>
              {{ formatTime(kb.updated_at) }}
            </div>
            <span class="kb-metric-label">更新时间</span>
          </div>
        </div>

        <div class="kb-card-footer">
          <span class="kb-strategy">{{ kb.chunk_strategy || '结构优先' }}</span>
          <div class="kb-actions">
            <el-button link type="primary" :icon="Edit" @click="openEdit(kb)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="remove(kb)">删除</el-button>
          </div>
        </div>
      </div>

      <div class="kb-card kb-create-card" @click="openCreate">
        <div class="kb-create-plus"><el-icon><Plus /></el-icon></div>
        <div class="kb-create-title">新建知识库</div>
        <div class="kb-create-sub">点击创建一个全新的知识库</div>
      </div>
    </div>

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
.toolbar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
  min-height: 120px;
}
.kb-card {
  display: flex;
  flex-direction: column;
  min-height: 230px;
  padding: 22px;
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: var(--kb-radius);
  box-shadow: var(--kb-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.kb-card:hover {
  transform: translateY(-4px);
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 16px 36px rgba(108, 92, 231, 0.14);
}
.kb-card-top {
  display: flex;
  align-items: center;
  gap: 14px;
}
.kb-icon {
  flex: 0 0 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-size: 22px;
}
.kb-heading {
  min-width: 0;
}
.kb-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--kb-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kb-model {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  padding: 2px 8px;
  border-radius: 999px;
}
.kb-desc {
  margin: 16px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--kb-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kb-metrics {
  display: flex;
  gap: 28px;
  padding: 14px 0;
  border-top: 1px solid var(--kb-border);
  border-bottom: 1px solid var(--kb-border);
}
.kb-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.kb-metric-value {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 15px;
  font-weight: 600;
  color: var(--kb-text);
}
.kb-metric-time {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.kb-metric-label {
  font-size: 12px;
  color: var(--kb-muted);
}
.kb-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 14px;
}
.kb-strategy {
  font-size: 12px;
  color: var(--kb-muted);
  background: #f6f7fb;
  padding: 4px 10px;
  border-radius: 8px;
}
.kb-actions {
  display: flex;
  gap: 4px;
}
.kb-create-card {
  align-items: center;
  justify-content: center;
  text-align: center;
  cursor: pointer;
  border: 2px dashed var(--el-color-primary-light-7);
  background: rgba(255, 255, 255, 0.6);
  box-shadow: none;
  color: var(--kb-primary);
}
.kb-create-card:hover {
  transform: translateY(-4px);
  background: var(--kb-primary-soft);
  border-color: var(--kb-primary);
  box-shadow: 0 16px 36px rgba(108, 92, 231, 0.12);
}
.kb-create-plus {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 1px solid var(--el-color-primary-light-7);
  font-size: 26px;
  box-shadow: 0 6px 16px rgba(108, 92, 231, 0.12);
}
.kb-create-title {
  margin-top: 16px;
  font-size: 16px;
  font-weight: 700;
}
.kb-create-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--kb-muted);
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
