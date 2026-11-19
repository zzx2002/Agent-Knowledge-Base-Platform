<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      // 调用 POST /api/v1/auth/login
      await auth.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    } catch {
      ElMessage.error('登录失败，请检查用户名和密码')
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-header">
        <div class="login-logo">KB</div>
        <h1>Agent 知识库平台</h1>
        <p>私有化知识管理与 RAG 问答基础设施</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">请使用管理员分配的账号登录</div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d3c6e 0%, #409eff 100%);
}
.login-box {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 36px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-logo {
  width: 52px;
  height: 52px;
  line-height: 52px;
  margin: 0 auto 12px;
  background: #409eff;
  color: #fff;
  border-radius: 12px;
  font-weight: 700;
  font-size: 20px;
}
.login-header h1 {
  font-size: 22px;
  margin: 0 0 6px;
}
.login-header p {
  color: var(--kb-muted);
  font-size: 13px;
  margin: 0;
}
.login-btn {
  width: 100%;
}
.login-footer {
  text-align: center;
  color: var(--kb-muted);
  font-size: 12px;
  margin-top: 8px;
}
</style>
