<template>
  <el-dialog
    class="application-dialog application-dialog-primary"
    :model-value="modelValue"
    :title="application ? '编辑投递' : '记一笔投递'"
    width="min(560px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <template #header>
      <div class="dialog-heading">
        <span class="dialog-heading-icon"><Promotion /></span>
        <div><h2>{{ application ? '编辑投递记录' : '记一笔新投递' }}</h2><p>记录目标岗位与投递进展，重要信息集中管理</p></div>
      </div>
    </template>
    <el-form class="application-form compact-application-form" label-position="top">
      <div v-if="companies.length" class="quick-select-block">
        <span class="quick-select-label">快速选择常用公司</span>
        <div class="quick-picks">
          <button v-for="company in companies.slice(0, 6)" :key="company.id" type="button" :class="{ selected: form.company === company.id }" @click="form.company = company.id">{{ company.name }}</button>
        </div>
      </div>
      <div class="form-row">
        <el-form-item class="field-company" label="公司" required>
          <el-select v-model="form.company" filterable allow-create default-first-option aria-label="公司" placeholder="选择或输入公司" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="field-position" label="岗位" :required="application?.status !== 'SAVED'">
          <el-input v-model="form.position" :prefix-icon="Suitcase" aria-label="岗位" placeholder="如：后端开发实习生" />
        </el-form-item>
      </div>
      <div class="form-row">
        <el-form-item class="field-city" label="城市">
          <el-select v-model="form.city" filterable allow-create default-first-option aria-label="城市" placeholder="选择常用城市或直接输入" style="width: 100%">
            <el-option v-for="city in CITIES" :key="city" :label="city" :value="city" />
          </el-select>
        </el-form-item>
        <el-form-item class="field-salary" label="薪资范围">
          <el-input v-model="form.salaryRange" :prefix-icon="Money" aria-label="薪资范围" placeholder="如：300/天 或 20K*14薪" />
        </el-form-item>
      </div>
      <div class="form-row">
        <el-form-item class="field-source" label="投递渠道">
          <el-select
            v-model="form.source"
            filterable
            allow-create
            default-first-option
            aria-label="投递渠道"
            placeholder="选择常用渠道或直接输入"
            style="width: 100%"
          >
            <el-option v-for="s in SOURCES" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item class="field-date" label="投递日期">
          <el-date-picker
            v-model="form.appliedAt"
            type="date"
            value-format="YYYY-MM-DD"
            aria-label="投递日期"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
      </div>
      <el-form-item class="field-link" label="岗位链接">
        <el-input v-model="form.jobUrl" :prefix-icon="Link" aria-label="岗位链接" placeholder="https://..." />
      </el-form-item>
      <el-form-item class="field-notes" label="备注">
        <el-input v-model="form.notes" aria-label="备注" type="textarea" :rows="1" placeholder="JD 重点、内推人、注意事项…" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button class="dialog-primary-btn" :icon="Check" :loading="submitting" @click="submit">
        {{ application ? '保存修改' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect
} from 'element-plus'
import { Check, Link, Money, Promotion, Suitcase } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/select/style/css'
import api from '../api'
import { CITIES, SOURCES } from '../constants'

const props = defineProps({
  modelValue: Boolean,
  companies: Array,
  application: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const submitting = ref(false)
const form = reactive({
  company: null,
  position: '',
  city: '',
  salaryRange: '',
  source: '',
  appliedAt: '',
  jobUrl: '',
  notes: ''
})

function reset() {
  form.company = props.application?.companyId ?? null
  form.position = props.application?.position ?? ''
  form.city = props.application?.city ?? ''
  form.salaryRange = props.application?.salaryRange ?? ''
  form.source = props.application?.source ?? ''
  form.appliedAt = props.application?.appliedAt ?? ''
  form.jobUrl = props.application?.jobUrl ?? ''
  form.notes = props.application?.notes ?? ''
}

watch(() => [props.modelValue, props.application?.id], ([visible]) => {
  if (visible) reset()
}, { immediate: true })

async function submit() {
  if (submitting.value) return
  if (!form.company) return ElMessage.warning('请填写公司')
  if (props.application?.status !== 'SAVED' && !form.position.trim()) return ElMessage.warning('请填写岗位')
  submitting.value = true
  try {
    // el-select 的 allow-create 会让新公司以字符串形式出现，需要先建档
    let companyId = form.company
    if (typeof companyId === 'string') {
      const created = await api.createCompany({ name: companyId.trim() })
      companyId = created.id
    }
    const payload = {
      companyId,
      position: form.position.trim(),
      city: form.city || null,
      salaryRange: form.salaryRange || null,
      source: form.source || null,
      jobUrl: form.jobUrl || null,
      appliedAt: form.appliedAt || null,
      notes: form.notes || null,
      ...(!props.application ? { status: 'APPLIED' } : {})
    }
    if (props.application) {
      await api.updateApplication(props.application.id, payload)
      ElMessage.success('投递信息已更新')
    } else {
      await api.createApplication(payload)
      ElMessage.success('已记录，祝顺利！')
    }
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>
