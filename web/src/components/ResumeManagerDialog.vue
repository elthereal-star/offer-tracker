<template>
  <el-dialog
    class="resume-manager-dialog"
    :model-value="modelValue"
    width="min(640px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
  >
    <template #header>
      <div class="dialog-heading">
        <span class="dialog-heading-icon"><Document /></span>
        <div><h2>简历库</h2><p>上传 PDF 简历，后续可用于 AI 面试</p></div>
      </div>
    </template>

    <section class="resume-upload-panel">
      <input ref="fileInput" class="visually-hidden" type="file" accept="application/pdf,.pdf" @change="upload" />
      <div class="resume-upload-copy">
        <strong>添加一份 PDF 简历</strong>
        <span>系统会自动提取文字内容，单个文件不超过 20MB</span>
      </div>
      <el-select v-model="applicationId" clearable filterable placeholder="可选：关联投递" aria-label="关联投递" style="min-width: 220px">
        <el-option v-for="application in applications" :key="application.id" :label="applicationLabel(application)" :value="application.id" />
      </el-select>
      <el-button class="dark-btn" :icon="Upload" :loading="uploading" @click="fileInput?.click()">选择 PDF</el-button>
    </section>

    <el-alert v-if="errorMessage" type="error" :closable="false" :title="errorMessage" />
    <div v-if="lastInterviewId" class="resume-recovery-row">
      <span>最近一次面试会话：{{ lastInterviewId }}</span>
      <el-button text type="primary" :loading="interviewLoading" @click="restoreInterview">恢复面试</el-button>
    </div>
    <div v-if="interviewSessions.length" class="resume-session-history">
      <strong>面试记录</strong>
      <div v-for="item in interviewSessions" :key="item.id" class="resume-session-item">
        <span>会话 #{{ item.id }} · {{ item.status === 'COMPLETED' ? `已完成，平均 ${item.averageScore ?? '-'} 分` : '进行中' }}</span>
        <el-button text type="primary" size="small" :loading="interviewLoading" @click="restoreSession(item.id)">打开</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="resumes" class="resume-table" empty-text="还没有上传简历">
      <el-table-column label="文件" min-width="220">
        <template #default="{ row }">
          <div class="resume-file-cell"><Document /><span>{{ row.originalFilename }}</span></div>
        </template>
      </el-table-column>
      <el-table-column label="内容" width="110">
        <template #default="{ row }">{{ row.extractedCharacterCount }} 字</template>
      </el-table-column>
      <el-table-column label="操作" width="235" align="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" :loading="interviewLoading" :icon="ChatDotRound" @click="startInterview(row)">AI 面试</el-button>
          <el-button text type="primary" size="small" tag="a" :href="`/api/resumes/${row.id}/file`" target="_blank" :icon="View">预览</el-button>
          <el-popconfirm title="确定删除这份简历？" @confirm="remove(row)">
            <template #reference><el-button text type="danger" size="small" :icon="Delete">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <AiInterviewDialog
      v-model="interviewVisible"
      :session="interview"
      :error-message="interviewError"
    />
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ChatDotRound, Delete, Document, Upload, View } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElDialog, ElMessage, ElOption, ElPopconfirm, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import api from '../api'
import AiInterviewDialog from './AiInterviewDialog.vue'

const props = defineProps({ modelValue: Boolean, applications: { type: Array, default: () => [] }, companyMap: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue'])
const fileInput = ref(null)
const applicationId = ref(null)
const resumes = ref([])
const loading = ref(false)
const uploading = ref(false)
const errorMessage = ref('')
const interviewLoading = ref(false)
const interviewError = ref('')
const interview = ref(null)
const interviewVisible = ref(false)
const lastInterviewId = ref(localStorage.getItem('offer-tracker-last-ai-interview') || '')
const interviewSessions = ref([])

const applicationLabel = (application) => `${props.companyMap[application.companyId] || '未知公司'} · ${application.position || '岗位待确定'}`

async function load() {
  loading.value = true
  errorMessage.value = ''
  try { resumes.value = await api.listResumes(); interviewSessions.value = await api.listAiInterviews() } catch (error) { errorMessage.value = error.message || '简历列表加载失败' } finally { loading.value = false }
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  errorMessage.value = ''
  try {
    await api.uploadResume(file, applicationId.value)
    ElMessage.success('简历上传并解析成功')
    await load()
  } catch (error) {
    errorMessage.value = error.message || '简历上传失败'
  } finally { uploading.value = false }
}

async function remove(row) {
  try {
    await api.deleteResume(row.id)
    resumes.value = resumes.value.filter((item) => item.id !== row.id)
    ElMessage.success('简历已删除')
  } catch (error) { ElMessage.error('删除失败：' + (error.message || '未知错误')) }
}

async function startInterview(row) {
  interviewLoading.value = true
  interviewError.value = ''
  try {
    interview.value = await api.createAiInterview({
      resumeId: row.id,
      applicationId: row.applicationId || applicationId.value || null
    })
    lastInterviewId.value = String(interview.value.id)
    localStorage.setItem('offer-tracker-last-ai-interview', lastInterviewId.value)
    interviewVisible.value = true
    interviewSessions.value = await api.listAiInterviews()
  } catch (error) {
    interviewError.value = error.message || 'AI 面试启动失败'
  } finally {
    interviewLoading.value = false
  }
}

async function restoreInterview() {
  await restoreSession(lastInterviewId.value)
}

async function restoreSession(id) {
  interviewLoading.value = true
  interviewError.value = ''
  try {
    interview.value = await api.getAiInterview(id)
    interviewVisible.value = true
  } catch (error) {
    interviewError.value = error.message || '面试会话恢复失败'
  } finally {
    interviewLoading.value = false
  }
}
</script>
