<template>
  <el-dialog
    :model-value="modelValue"
    title="数据备份与恢复"
    width="min(560px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <section class="data-section" aria-labelledby="export-title">
      <h3 id="export-title">导出</h3>
      <div class="data-actions">
        <el-button tag="a" href="/api/data/export" :icon="Download">完整 JSON 备份</el-button>
        <el-button tag="a" href="/api/data/export.csv" :icon="Download">投递 CSV</el-button>
      </div>
    </section>

    <section class="data-section" aria-labelledby="import-title">
      <h3 id="import-title">恢复 JSON 备份</h3>
      <input ref="fileInput" class="visually-hidden" type="file" accept="application/json,.json" @change="readFile" />
      <el-button :icon="Upload" :loading="validating" @click="fileInput?.click()">选择备份文件</el-button>
      <span v-if="fileName" class="selected-file">{{ fileName }}</span>

      <el-alert
        v-if="preview"
        class="import-preview"
        :type="preview.valid ? 'success' : 'error'"
        :closable="false"
        :title="preview.valid ? '校验通过' : '校验未通过'"
      >
        <template #default>
          <p>公司 {{ preview.companyCount }}，投递 {{ preview.applicationCount }}，面试 {{ preview.interviewCount }}</p>
          <ul v-if="preview.errors?.length">
            <li v-for="error in preview.errors" :key="error">{{ error }}</li>
          </ul>
        </template>
      </el-alert>

      <el-checkbox v-if="preview?.valid" v-model="replaceExisting" class="replace-check">
        清空现有数据后恢复
      </el-checkbox>
      <el-popconfirm
        v-if="preview?.valid"
        :title="replaceExisting ? '这会清空当前所有数据，确认恢复？' : '确认将备份追加到当前数据？'"
        width="280"
        @confirm="restore"
      >
        <template #reference>
          <el-button class="dark-btn restore-btn" :loading="restoring">开始恢复</el-button>
        </template>
      </el-popconfirm>
    </section>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { Download, Upload } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElCheckbox, ElDialog, ElMessage, ElPopconfirm } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import api from '../api'

defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'imported'])

const fileInput = ref(null)
const fileName = ref('')
const backup = ref(null)
const preview = ref(null)
const replaceExisting = ref(false)
const validating = ref(false)
const restoring = ref(false)

async function readFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  fileName.value = file.name
  preview.value = null
  validating.value = true
  try {
    backup.value = JSON.parse(await file.text())
    preview.value = await api.validateImport(backup.value)
  } catch (error) {
    backup.value = null
    ElMessage.error(error instanceof SyntaxError ? '文件不是有效的 JSON' : error.message)
  } finally {
    validating.value = false
  }
}

async function restore() {
  if (restoring.value || !backup.value || !preview.value?.valid) return
  restoring.value = true
  try {
    const result = await api.importBackup(backup.value, replaceExisting.value)
    ElMessage.success(`恢复完成：新增 ${result.applicationsCreated} 条投递`)
    emit('imported')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error('恢复失败：' + error.message)
  } finally {
    restoring.value = false
  }
}
</script>
