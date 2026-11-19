<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  FolderOpened,
  Document,
  ChatDotRound,
  Search,
  DataBoard,
  Lock,
  Connection,
  Coin,
  List,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const workspaceMenus = [
  { path: '/workspace/knowledge-bases', title: '我的知识库', icon: FolderOpened },
  { path: '/workspace/documents', title: '文档上传', icon: Document },
  { path: '/workspace/chat', title: '知识库对话', icon: ChatDotRound },
  { path: '/workspace/search', title: '知识检索', icon: Search },
]

const adminMenus = [
  { path: '/admin/dashboard', title: '数据面板', icon: DataBoard },
  { path: '/admin/permissions', title: '权限管理', icon: Lock },
  { path: '/admin/indexes', title: '索引运维', icon: Coin },
  { path: '/admin/tasks', title: '解析任务', icon: List },
  { path: '/admin/agents', title: 'Agent 接入', icon: Connection },
]

const activeMenu = computed(() => route.path)
const groupTitle = computed(() => (route.meta.group === 'admin' ? '管理后台' : '用户工作台'))

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark">KB</span>
        <span class="logo-text">Agent 知识库平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#001529"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <el-menu-item-group title="用户工作台">
          <el-menu-item v-for="m in workspaceMenus" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="管理后台">
          <el-menu-item v-for="m in adminMenus" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="breadcrumb">{{ groupTitle }} / {{ route.meta.title || '首页' }}</div>
        <div class="header-right">
          <span class="username">{{ auth.username || '管理员' }}</span>
          <el-button text :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #001529;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.logo-mark {
  background: #409eff;
  border-radius: 6px;
  padding: 4px 8px;
  font-weight: 700;
  font-size: 14px;
}
.logo-text {
  font-size: 14px;
  white-space: nowrap;
}
.el-menu {
  border-right: none;
}
.header {
  background: #fff;
  border-bottom: 1px solid var(--kb-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.breadcrumb {
  color: var(--kb-muted);
  font-size: 14px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.username {
  font-size: 14px;
}
.main {
  background: var(--kb-bg);
  padding: 0;
}
</style>
