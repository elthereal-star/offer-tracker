<template>
  <el-dialog :model-value="modelValue" width="min(560px, calc(100vw - 24px))" @update:model-value="emit('update:modelValue', $event)" @open="load">
    <template #header>
      <div class="dialog-heading"><span class="dialog-heading-icon"><Setting /></span><div><h2>AI 面试设置</h2><p>配置 OpenAI 兼容接口，API Key 只保存在本机</p></div></div>
    </template>
    <el-alert v-if="configured" type="success" :closable="false" title="AI 服务已配置">
      <template #default>当前模型：{{ form.model }}，Key：{{ maskedApiKey }}</template>
    </el-alert>
    <el-form label-position="top" class="application-form ai-settings-form">
      <el-form-item label="服务地址" required><el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com/v1" /></el-form-item>
      <el-form-item label="模型名称" required><el-input v-model="form.model" placeholder="deepseek-chat" /></el-form-item>
      <el-form-item label="API Key" required><el-input v-model="form.apiKey" type="password" show-password :placeholder="configured ? '留空表示保持当前 Key' : 'sk-...'" /></el-form-item>
      <p class="ai-settings-hint">支持 DeepSeek、OpenAI、通义等兼容 Chat Completions 协议的服务。Key 会写入本机 data 目录，不会提交到 GitHub。</p>
    </el-form>
    <template #footer>
      <el-button v-if="configured" type="danger" text :loading="clearing" @click="clear">清除配置</el-button>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button class="dialog-primary-btn" :loading="saving" @click="save">保存设置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import api from '../api'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const form = reactive({ baseUrl: '', model: '', apiKey: '' })
const configured = ref(false)
const maskedApiKey = ref('')
const saving = ref(false)
const clearing = ref(false)

async function load() {
  try {
    const config = await api.getAiConfig()
    configured.value = config.configured
    form.baseUrl = config.baseUrl || 'https://api.deepseek.com/v1'
    form.model = config.model || 'deepseek-chat'
    form.apiKey = ''
    maskedApiKey.value = config.maskedApiKey || ''
  } catch (error) { ElMessage.error('AI 配置加载失败：' + error.message) }
}

async function save() {
  if (!form.baseUrl.trim() || !form.model.trim() || (!configured.value && !form.apiKey.trim())) return ElMessage.warning('请填写服务地址、模型和 API Key')
  saving.value = true
  try {
    const config = await api.saveAiConfig({ ...form })
    configured.value = config.configured
    maskedApiKey.value = config.maskedApiKey
    form.apiKey = ''
    ElMessage.success('AI 设置已保存')
  } catch (error) { ElMessage.error('保存失败：' + error.message) } finally { saving.value = false }
}

async function clear() {
  clearing.value = true
  try { await api.clearAiConfig(); configured.value = false; maskedApiKey.value = ''; form.apiKey = ''; ElMessage.success('AI 配置已清除') }
  catch (error) { ElMessage.error('清除失败：' + error.message) } finally { clearing.value = false }
}
</script>
