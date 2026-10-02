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
      <div class="ai-interview-meta">
        <span>第 {{ question?.questionNo || 1 }} 题 / 共 {{ session.questions?.length || 1 }} 题</span>
        <el-tag :type="session.status === 'COMPLETED' ? 'info' : 'success'">{{ session.status === 'COMPLETED' ? '已结束' : '进行中' }}</el-tag>
      </div>
      <div class="ai-interview-navigation">
        <el-button text :disabled="currentQuestionIndex === 0" :icon="ArrowLeft" @click="selectQuestion(currentQuestionIndex - 1)">上一题</el-button>
        <el-button text :disabled="currentQuestionIndex >= (session.questions?.length || 1) - 1" @click="selectQuestion(currentQuestionIndex + 1)">下一题<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button>
      </div>
      <h3>{{ question?.content || '正在准备问题…' }}</h3>
      <el-input v-model="answer" type="textarea" :rows="5" maxlength="5000" placeholder="输入你的回答" :disabled="answerSaved || session.status === 'COMPLETED' || !isLatestQuestion" />
      <div class="ai-interview-actions">
        <span v-if="answerSaved" class="ai-interview-saved">回答已保存</span>
        <el-button v-if="isLatestQuestion" type="primary" :loading="saving" :disabled="!answer.trim() || answerSaved || session.status === 'COMPLETED'" @click="submitAnswer">提交回答</el-button>
      </div>
      <div v-if="question?.score !== null && question?.score !== undefined" class="ai-interview-feedback">
        <strong>AI 评分：{{ question.score }} 分</strong>
        <p>{{ question.feedback }}</p>
        <el-button v-if="isLatestQuestion && session.status !== 'COMPLETED'" type="primary" plain :loading="generating" @click="generateFollowUp">生成下一道追问</el-button>
      </div>
      <el-button v-else-if="isLatestQuestion" class="ai-interview-evaluate" type="success" plain :loading="evaluating" :disabled="!answerSaved || session.status === 'COMPLETED'" @click="evaluateAnswer">获取 AI 评分</el-button>
      <div v-if="session.status !== 'COMPLETED'" class="ai-interview-finish">
        <el-button type="warning" plain :loading="finishing" @click="finishInterview">结束面试并生成总结</el-button>
      </div>
      <div v-if="session.status === 'COMPLETED' && session.report" class="ai-interview-report">
        <strong>面试总结（平均分 {{ session.averageScore }}）</strong>
        <p>{{ session.report }}</p>
      </div>
    </section>
    <el-skeleton v-else animated :rows="3" />
  </el-dialog>
</template>

<script setup>
import { ArrowLeft, ArrowRight, ChatDotRound } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElDialog, ElInput, ElMessage, ElSkeleton, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/tag/style/css'
import api from '../api'
import { computed, ref, watch } from 'vue'

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
const generating = ref(false)
const finishing = ref(false)
const question = ref(null)
const currentQuestionIndex = ref(0)
const isLatestQuestion = computed(() => currentQuestionIndex.value === (props.session?.questions?.length || 1) - 1)

function selectQuestion(index) {
  const questions = props.session?.questions || []
  const boundedIndex = Math.max(0, Math.min(index, questions.length - 1))
  question.value = questions[boundedIndex] || null
  currentQuestionIndex.value = boundedIndex
  answer.value = question.value?.answer || ''
  answerSaved.value = Boolean(question.value?.answer)
}

function syncSession(updated, selectLast = false) {
  const selectedId = question.value?.id
  Object.assign(props.session, updated)
  const index = selectLast ? updated.questions.length - 1 : updated.questions.findIndex((item) => item.id === selectedId)
  selectQuestion(index < 0 ? updated.questions.length - 1 : index)
}

watch(() => props.session, (value) => {
  if (!value) { question.value = null; return }
  selectQuestion((value.questions?.length || 1) - 1)
}, { immediate: true })

async function submitAnswer() {
  if (!question.value || !props.session?.id) return
  saving.value = true
  try { syncSession(await api.answerAiInterview(props.session.id, question.value.id, { answer: answer.value })); ElMessage.success('回答已保存') }
  catch (error) { ElMessage.error(error.message || '回答保存失败') }
  finally { saving.value = false }
}

async function evaluateAnswer() {
  if (!props.session?.id || !question.value) return
  evaluating.value = true
  try { syncSession(await api.evaluateAiInterview(props.session.id, question.value.id)); ElMessage.success('AI 评分完成') }
  catch (error) { ElMessage.error(error.message || 'AI 评分失败') }
  finally { evaluating.value = false }
}

async function generateFollowUp() {
  if (!props.session?.id || !question.value) return
  generating.value = true
  try { syncSession(await api.followUpAiInterview(props.session.id, question.value.id), true); ElMessage.success('下一道追问已生成') }
  catch (error) { ElMessage.error(error.message || '追问生成失败') }
  finally { generating.value = false }
}

async function finishInterview() {
  if (!props.session?.id) return
  finishing.value = true
  try { syncSession(await api.finishAiInterview(props.session.id)); ElMessage.success('面试总结已生成') }
  catch (error) { ElMessage.error(error.message || '面试结束失败') }
  finally { finishing.value = false }
}
</script>
