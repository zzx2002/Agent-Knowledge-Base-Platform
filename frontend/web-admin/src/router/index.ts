import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/workspace/knowledge-bases',
    children: [
      // 用户工作台：独立页面
      {
        path: 'workspace/knowledge-bases',
        name: 'WorkspaceKnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '我的知识库', group: 'workspace' },
      },
      {
        path: 'workspace/documents',
        name: 'WorkspaceDocument',
        component: () => import('@/views/Document.vue'),
        meta: { title: '文档上传', group: 'workspace' },
      },
      {
        path: 'workspace/chat',
        name: 'WorkspaceChat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: '知识库对话', group: 'workspace' },
      },
      {
        path: 'workspace/search',
        name: 'WorkspaceSearch',
        component: () => import('@/views/Search.vue'),
        meta: { title: '知识检索', group: 'workspace' },
      },

      // 管理后台
      {
        path: 'admin/dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据面板', group: 'admin' },
      },
      {
        path: 'admin/permissions',
        name: 'AdminPermission',
        component: () => import('@/views/admin/Permission.vue'),
        meta: { title: '权限管理', group: 'admin' },
      },
      {
        path: 'admin/users',
        name: 'AdminUser',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', group: 'admin' },
      },
      {
        path: 'admin/audit-logs',
        name: 'AdminAuditLog',
        component: () => import('@/views/admin/AuditLog.vue'),
        meta: { title: '操作审计', group: 'admin' },
      },
      {
        path: 'admin/indexes',
        name: 'AdminIndex',
        component: () => import('@/views/admin/IndexOps.vue'),
        meta: { title: '索引运维', group: 'admin' },
      },
      {
        path: 'admin/tasks',
        name: 'AdminTask',
        component: () => import('@/views/Task.vue'),
        meta: { title: '解析任务', group: 'admin' },
      },
      {
        path: 'admin/agents',
        name: 'AdminAgent',
        component: () => import('@/views/Agent.vue'),
        meta: { title: 'Agent 接入', group: 'admin' },
      },

      // 兼容旧路径
      { path: 'workspace', redirect: '/workspace/knowledge-bases' },
      { path: 'dashboard', redirect: '/admin/dashboard' },
      { path: 'knowledge-bases', redirect: '/workspace/knowledge-bases' },
      { path: 'documents', redirect: '/workspace/documents' },
      { path: 'search', redirect: '/workspace/search' },
      { path: 'chat', redirect: '/workspace/chat' },
      { path: 'tasks', redirect: '/admin/tasks' },
      { path: 'agents', redirect: '/admin/agents' },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title ? to.meta.title + ' - ' : ''}Agent Knowledge Base Platform`
  const token = localStorage.getItem('kb_token')
  if (!to.meta.public && !token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
