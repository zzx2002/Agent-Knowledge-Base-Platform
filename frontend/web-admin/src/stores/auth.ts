import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, type UserInfo } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const storedToken = localStorage.getItem('kb_token')
  const initialToken = storedToken && storedToken !== 'undefined' ? storedToken : ''
  if (!initialToken) {
    localStorage.removeItem('kb_token')
  }
  const token = ref<string>(initialToken)
  const username = ref<string>('')
  const roles = ref<string[]>([])

  function setToken(value: string) {
    token.value = value
    localStorage.setItem('kb_token', value)
  }

  /** 登录：兼容后端 camelCase 与 snake_case 两种令牌字段 */
  async function login(params: { username: string; password: string }) {
    const res = await loginApi(params)
    const data = (res as any).data ?? res
    const tokenValue = data.accessToken || data.access_token
    if (!tokenValue) {
      throw new Error('登录响应缺少访问令牌')
    }
    setToken(tokenValue)
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
