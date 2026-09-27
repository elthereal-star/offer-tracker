<template>
  <el-dialog
    :model-value="modelValue"
    width="min(680px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-heading">
        <span class="dialog-heading-icon"><ChatDotRound /></span>
        <div><h2>AI 模拟面试</h2><p>根据你的简历生成个性化面试问题</p></div>
      </div>
    </template>
    <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
    <section v-if="session" class="ai-interview-question">
      <div class="ai-interview-meta"><span>第 {{ session.questions?.length || 1 }} 题</span><el-tag type="success">进行中</el-tag></div>
      <h3>{{ session.questions?.[0]?.content || '正在准备问题…' }}</h3>
      <el-input v-model="answer" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="输入你的回答" :disabled="answerSaved" />
      <div class="ai-interview-actions">
        <span v-if="answerSaved" class="ai-interview-saved">回答已保存</span>
        <el-button type="primary" :loading="saving" :disabled="!answer.trim() || answerSaved" @click="submitAnswer">提交回答</el-button>
      </div>
      <div v-if="question?.score !== null && question?.score !== undefined" class="ai-interview-feedback">
        <strong>AI 评分：{{ question.score }} 分</strong>
        <p>{{ question.feedback }}</p>
      </div>
      <el-button v-else class="ai-interview-evaluate" type="success" plain :loading="evaluating" :disabled="!answerSaved" @click="evaluateAnswer">获取 AI 评分</el-button>
    </section>
    <el-skeleton v-else animated :rows="3" />
  </el-dialog>
</template>

<script setup>
import { ChatDotRound } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElDialog, ElInput, ElMessage, ElSkeleton, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/tag/style/css'
import api from '../api'
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  session: { type: Object, default: null },
  errorMessage: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
const answer = ref('')
const saving = ref(false)
const answerSaved = ref(false)
const evaluating = ref(false)
const question = ref(null)
watch(() => props.session, (value) => { question.value = value?.questions?.[0] || null; answer.value = question.value?.answer || ''; answerSaved.value = Boolean(answer.value) }, { immediate: true })

async function submitAnswer() {
  const question = props.session?.questions?.[0]
  if (!question) return
  saving.value = true
  try { const updated = await api.answerAiInterview(props.session.id, question.id, { answer: answer.value }); question.value = updated.questions?.[0] || question.value; answerSaved.value = true; ElMessage.success('回答已保存') }
  catch (error) { ElMessage.error(error.message || '回答保存失败') }
  finally { saving.value = false }
}
async function evaluateAnswer() {
  if (!props.session?.id || !question.value) return
  evaluating.value = true
  try { const updated = await api.evaluateAiInterview(props.session.id, question.value.id); question.value = updated.questions?.[0] || question.value; ElMessage.success('AI 评分完成') }
  catch (error) { ElMessage.error(error.message || 'AI 评分失败') }
  finally { evaluating.value = false }
}
</script>
