<script setup lang="ts">
import { ref } from 'vue'
import KnowledgeBase from './KnowledgeBase.vue'
import Document from './Document.vue'
import Chat from './Chat.vue'
import Search from './Search.vue'

const activeTab = ref('kb')
</script>

<template>
  <div class="workspace">
    <div class="workspace-intro">
      <h2>知识工作台</h2>
      <p>按顺序创建知识库、上传文档，然后即可检索或对话</p>
    </div>

    <el-tabs v-model="activeTab" tab-position="left" class="workspace-tabs">
      <el-tab-pane name="kb">
        <template #label>
          <span class="tab-label">1. 知识库</span>
        </template>
        <KnowledgeBase />
      </el-tab-pane>
      <el-tab-pane name="documents">
        <template #label>
          <span class="tab-label">2. 文档上传</span>
        </template>
        <Document />
      </el-tab-pane>
      <el-tab-pane name="chat">
        <template #label>
          <span class="tab-label">3. 知识对话</span>
        </template>
        <Chat />
      </el-tab-pane>
      <el-tab-pane name="search">
        <template #label>
          <span class="tab-label">4. 知识检索</span>
        </template>
        <Search />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.workspace {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  padding: 16px 20px 8px;
  box-sizing: border-box;
}
.workspace-intro {
  margin-bottom: 12px;
}
.workspace-intro h2 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}
.workspace-intro p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.workspace-tabs {
  flex: 1;
  min-height: 0;
}
.workspace-tabs :deep(.el-tabs__content) {
  height: 100%;
  overflow: auto;
  padding-right: 12px;
}
.workspace-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.tab-label {
  font-size: 14px;
  font-weight: 500;
}
</style>
