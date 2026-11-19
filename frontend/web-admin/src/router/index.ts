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
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '系统首页', icon: 'DataBoard' },
      },
      {
        path: 'knowledge-bases',
        name: 'KnowledgeBase',
        component: () => import('@/views/KnowledgeBase.vue'),
        meta: { title: '知识库管理', icon: 'FolderOpened' },
      },
      {
        path: 'documents',
        name: 'Document',
        component: () => import('@/views/Document.vue'),
        meta: { title: '文档管理', icon: 'Document' },
      },
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/Search.vue'),
        meta: { title: '知识检索', icon: 'Search' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/Chat.vue'),
        meta: { title: '知识库对话', icon: 'ChatDotRound' },
      },
      {
        path: 'tasks',
        name: 'Task',
        component: () => import('@/views/Task.vue'),
        meta: { title: '解析任务', icon: 'List' },
      },
      {
        path: 'agents',
        name: 'Agent',
        component: () => import('@/views/Agent.vue'),
        meta: { title: 'Agent 接入', icon: 'Connection' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 简单的登录守卫：未登录时跳转登录页（占位实现）
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
