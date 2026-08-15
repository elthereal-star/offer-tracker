<template>
  <el-dialog
    class="application-dialog application-dialog-saved"
    :model-value="modelValue"
    title="收藏公司"
    width="min(560px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <template #header>
      <div class="dialog-heading">
        <span class="dialog-heading-icon"><StarFilled /></span>
        <div><h2>收藏心仪公司</h2><p>先建立关注清单，准备好后再转为正式投递</p></div>
      </div>
    </template>
    <el-form class="application-form" label-position="top">
      <div v-if="companies.length" class="quick-select-block">
        <span class="quick-select-label">快速选择常用公司</span>
        <div class="quick-picks quick-picks-saved">
          <button v-for="company in companies.slice(0, 8)" :key="company.id" type="button" :class="{ selected: form.company === company.id }" @click="form.company = company.id">{{ company.name }}</button>
        </div>
      </div>
      <el-form-item label="公司" required>
        <el-select
          v-model="form.company"
          filterable
          allow-create
          default-first-option
          aria-label="收藏公司"
          placeholder="选择已有公司，或直接输入新公司名"
          style="width: 100%"
        >
          <el-option v-for="company in companies" :key="company.id" :label="company.name" :value="company.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="意向岗位（可选）">
        <el-input v-model="form.position" aria-label="意向岗位" placeholder="暂未确定可留空" />
      </el-form-item>
      <div class="form-row">
        <el-form-item label="目标城市（可选）">
          <el-select v-model="form.city" filterable allow-create default-first-option aria-label="收藏城市" placeholder="选择常用城市或直接输入" style="width: 100%">
            <el-option v-for="city in CITIES" :key="city" :label="city" :value="city" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划渠道（可选）">
          <el-select v-model="form.source" filterable allow-create default-first-option aria-label="收藏投递渠道" placeholder="选择常用渠道或直接输入" style="width: 100%">
            <el-option v-for="source in SOURCES" :key="source" :label="source" :value="source" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item label="岗位链接（可选）">
        <el-input v-model="form.jobUrl" aria-label="收藏岗位链接" placeholder="https://..." />
      </el-form-item>
      <el-form-item label="备注（可选）">
        <el-input v-model="form.notes" aria-label="收藏备注" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button class="dialog-saved-btn" :icon="Check" :loading="submitting" @click="submit">加入收藏</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElSelect } from 'element-plus'
import { Check, StarFilled } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/select/style/css'
import api from '../api'
import { CITIES, SOURCES } from '../constants'

const props = defineProps({ modelValue: Boolean, companies: Array })
const emit = defineEmits(['update:modelValue', 'saved'])
const submitting = ref(false)
const form = reactive({ company: null, position: '', city: '', source: '', jobUrl: '', notes: '' })

function reset() {
  form.company = null
  form.position = ''
  form.city = ''
  form.source = ''
  form.jobUrl = ''
  form.notes = ''
}

watch(() => props.modelValue, (visible) => {
  if (visible) reset()
}, { immediate: true })

async function submit() {
  if (submitting.value) return
  if (!form.company || (typeof form.company === 'string' && !form.company.trim())) {
    return ElMessage.warning('请填写公司')
  }
  submitting.value = true
  try {
    let companyId = form.company
    if (typeof companyId === 'string') {
      const company = await api.createCompany({ name: companyId.trim() })
      companyId = company.id
    }
    await api.createApplication({
      companyId,
      position: form.position.trim() || null,
      city: form.city.trim() || null,
      source: form.source.trim() || null,
      jobUrl: form.jobUrl.trim() || null,
      notes: form.notes.trim() || null,
      appliedAt: null,
      status: 'SAVED'
    })
    ElMessage.success('公司已收藏')
    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    ElMessage.error('收藏失败：' + (error.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}
</script>
