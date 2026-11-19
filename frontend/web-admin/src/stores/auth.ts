import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, type UserInfo } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('kb_token') || '')
  const username = ref<string>('')
  const roles = ref<string[]>([])

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('kb_token', value)
  }

  /** 登录（占位：保存返回的 access_token） */
  async function login(params: { username: string; password: string }) {
    const res = await loginApi(params)
    const data = (res as any).data ?? res
    setToken(data.access_token)
    username.value = params.username
    return data
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      username.value = ''
      roles.value = []
      localStorage.removeItem('kb_token')
    }
  }

  function setUser(user: UserInfo) {
    username.value = user.username
    roles.value = user.roles || []
  }

  return { token, username, roles, login, logout, setToken, setUser }
})
