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
  User,
  Connection,
  Coin,
  List,
  SwitchButton,
  Bell,
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
  { path: '/admin/users', title: '用户管理', icon: User },
  { path: '/admin/audit-logs', title: '操作审计', icon: Document },
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
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">KB</div>
        <div class="brand-copy">
          <strong>知识库平台</strong>
          <span>Agent Knowledge Base</span>
        </div>
      </div>

      <nav class="nav">
        <div class="nav-group">
          <div class="nav-title">用户工作台</div>
          <router-link
            v-for="item in workspaceMenus"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: activeMenu === item.path }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </router-link>
        </div>

        <div class="nav-group">
          <div class="nav-title">管理后台</div>
          <router-link
            v-for="item in adminMenus"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: activeMenu === item.path }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="avatar">{{ (auth.username || '管').slice(0, 1).toUpperCase() }}</div>
        <div class="user-copy">
          <strong>{{ auth.username || '管理员' }}</strong>
          <span>平台管理员</span>
        </div>
        <el-button text circle :icon="SwitchButton" title="退出登录" @click="handleLogout" />
      </div>
    </aside>

    <section class="content-shell">
      <header class="topbar">
        <div class="crumb">
          <span class="crumb-group">{{ groupTitle }}</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-page">{{ route.meta.title || '首页' }}</span>
        </div>
        <div class="topbar-right">
          <div class="top-search">
            <el-icon><Search /></el-icon>
            <span>搜索功能、文档或设置</span>
          </div>
          <el-button text circle :icon="Bell" />
          <div class="top-avatar">{{ (auth.username || '管').slice(0, 1).toUpperCase() }}</div>
        </div>
      </header>
      <main class="main">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  display: flex;
  gap: 14px;
  padding: 14px;
  background: #ebedf4;
}

.sidebar {
  width: 238px;
  flex: 0 0 238px;
  background: #fff;
  border: 1px solid var(--kb-border);
  border-radius: 22px;
  box-shadow: var(--kb-shadow);
  display: flex;
  flex-direction: column;
  padding: 22px 14px 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 8px 24px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: linear-gradient(135deg, #6c5ce7, #9a8cf5);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 15px;
  box-shadow: 0 8px 18px rgba(108, 92, 231, 0.3);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-copy strong {
  font-size: 15px;
  color: #1c2030;
}

.brand-copy span {
  font-size: 11px;
  color: var(--kb-muted);
}

.nav {
  flex: 1;
  overflow: auto;
}

.nav-group + .nav-group {
  margin-top: 26px;
}

.nav-title {
  padding: 0 14px 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #a3a9b8;
}

.nav-item {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 14px;
  margin-bottom: 4px;
  border-radius: 12px;
  color: #6d7486;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.18s ease;
}

.nav-item:hover {
  background: #f7f6ff;
  color: var(--kb-primary);
}

.nav-item.active {
  background: var(--kb-primary-soft);
  color: var(--kb-primary);
  font-weight: 600;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px 2px;
  border-top: 1px solid var(--kb-border);
}

.avatar,
.top-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ffb86b, #ff8e72);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.user-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-copy strong {
  font-size: 13px;
}

.user-copy span {
  font-size: 11px;
  color: var(--kb-muted);
}

.content-shell {
  flex: 1;
  min-width: 0;
  background: #f7f8fc;
  border: 1px solid var(--kb-border);
  border-radius: 22px;
  box-shadow: var(--kb-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 64px;
  flex: 0 0 64px;
  background: #fff;
  border-bottom: 1px solid var(--kb-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.crumb-group {
  color: var(--kb-muted);
}

.crumb-sep {
  color: #c7cbd6;
}

.crumb-page {
  color: #1c2030;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-search {
  width: 260px;
  height: 36px;
  border-radius: 18px;
  background: #f5f6fa;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  color: #a1a7b6;
  font-size: 12px;
}

.main {
  flex: 1;
  overflow: auto;
}

@media (max-width: 900px) {
  .sidebar {
    width: 72px;
    flex-basis: 72px;
    padding: 18px 10px;
  }
  .brand-copy,
  .nav-title,
  .nav-item span,
  .user-copy,
  .top-search {
    display: none;
  }
  .nav-item {
    justify-content: center;
    padding: 0;
  }
}
</style>
