<template>
  <el-dialog v-model="visible" :title="mode === 'login' ? '登录 Offer Tracker' : '创建账户'" width="420px" @closed="reset">
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="手机号">
        <el-input v-model.trim="form.phone" autocomplete="tel" inputmode="tel" maxlength="19" placeholder="输入手机号" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" show-password :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 8 位" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item v-if="mode === 'register'" label="确认密码">
        <el-input v-model="form.confirmPassword" show-password autocomplete="new-password" placeholder="再次输入密码" @keyup.enter="submit" />
      </el-form-item>
      <p v-if="mode === 'register'" class="auth-note">手机号目前仅作为登录名，尚未验证号码归属。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-button class="submit-button" type="primary" :loading="loading" @click="submit">
        {{ mode === 'login' ? '登录' : '注册并登录' }}
      </el-button>
    </el-form>
    <template #footer>
      <el-button text @click="switchMode">
        {{ mode === 'login' ? '还没有账户？创建账户' : '已有账户？返回登录' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import api, { setAuthToken } from '../api'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'authenticated'])
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const mode = ref('login')
const loading = ref(false)
const error = ref('')
const form = reactive({ phone: '', password: '', confirmPassword: '' })

watch(() => props.modelValue, (open) => { if (open) error.value = '' })

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
}

function reset() {
  error.value = ''
  form.password = ''
  form.confirmPassword = ''
}

async function submit() {
  error.value = ''
  if (!/^\+?[0-9]{6,18}$/.test(form.phone)) return (error.value = '请输入有效的手机号')
  if (form.password.length < 8 || form.password.length > 128) return (error.value = '密码长度必须为 8 到 128 位')
  if (mode.value === 'register' && form.password !== form.confirmPassword) return (error.value = '两次输入的密码不一致')

  loading.value = true
  try {
    const response = mode.value === 'login'
      ? await api.login({ phone: form.phone, password: form.password })
      : await api.register({ phone: form.phone, password: form.password })
    setAuthToken(response.accessToken)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '账户已创建')
    visible.value = false
    emit('authenticated')
  } catch (cause) {
    error.value = cause.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-note {
  margin: -4px 0 16px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.submit-button {
  width: 100%;
  margin-top: 18px;
}
</style>
