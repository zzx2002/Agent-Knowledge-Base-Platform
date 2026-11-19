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
    redirect: '/workspace',
    children: [
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/Workspace.vue'),
        meta: { title: '知识工作台', group: 'workspace' },
      },
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
      // 旧路径兼容
      { path: 'dashboard', redirect: '/admin/dashboard' },
      { path: 'knowledge-bases', redirect: '/workspace' },
      { path: 'documents', redirect: '/workspace' },
      { path: 'search', redirect: '/workspace' },
      { path: 'chat', redirect: '/workspace' },
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
