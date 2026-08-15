<template>
  <el-dialog
    :model-value="modelValue"
    title="公司管理"
    width="min(680px, calc(100vw - 24px))"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="company-create-row">
      <el-input v-model="newCompany.name" placeholder="公司名称" maxlength="128" @keyup.enter="create" />
      <el-input v-model="newCompany.website" placeholder="官网（可选）" @keyup.enter="create" />
      <el-button :icon="Plus" :loading="creating" aria-label="新增公司" @click="create">新增</el-button>
    </div>

    <div v-if="companies.length" class="company-list">
      <div v-for="company in companies" :key="company.id" class="company-row">
        <template v-if="editingId === company.id">
          <div class="company-edit-fields">
            <el-input v-model="editForm.name" aria-label="公司名称" maxlength="128" />
            <el-input v-model="editForm.website" aria-label="公司官网" placeholder="官网" />
            <el-input v-model="editForm.notes" aria-label="公司备注" placeholder="备注" />
          </div>
          <div class="company-actions">
            <el-tooltip content="保存">
              <el-button :icon="Check" circle aria-label="保存公司" :loading="saving" @click="save(company.id)" />
            </el-tooltip>
            <el-tooltip content="取消">
              <el-button :icon="Close" circle aria-label="取消编辑" @click="editingId = null" />
            </el-tooltip>
          </div>
        </template>
        <template v-else>
          <div class="company-summary">
            <strong>{{ company.name }}</strong>
            <a v-if="company.website" :href="company.website" target="_blank" rel="noreferrer">{{ company.website }}</a>
            <span v-if="company.notes">{{ company.notes }}</span>
          </div>
          <div class="company-actions">
            <el-tooltip content="编辑">
              <el-button :icon="Edit" circle aria-label="编辑公司" @click="startEdit(company)" />
            </el-tooltip>
            <el-popconfirm title="仅未关联投递的公司可以删除，确认继续？" @confirm="remove(company)">
              <template #reference>
                <el-button :icon="Delete" circle type="danger" plain :aria-label="`删除${company.name}`" />
              </template>
            </el-popconfirm>
          </div>
        </template>
      </div>
    </div>
    <el-empty v-else description="还没有公司" :image-size="72" />
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { Check, Close, Delete, Edit, Plus } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElMessage,
  ElPopconfirm,
  ElTooltip
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/tooltip/style/css'
import api from '../api'

defineProps({ modelValue: Boolean, companies: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'changed'])

const newCompany = reactive({ name: '', website: '' })
const editForm = reactive({ name: '', website: '', notes: '' })
const editingId = ref(null)
const creating = ref(false)
const saving = ref(false)

async function create() {
  if (creating.value || !newCompany.name.trim()) return ElMessage.warning('请填写公司名称')
  creating.value = true
  try {
    await api.createCompany({
      name: newCompany.name.trim(),
      website: newCompany.website.trim() || null,
      notes: null
    })
    newCompany.name = ''
    newCompany.website = ''
    emit('changed')
    ElMessage.success('公司已新增')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    creating.value = false
  }
}

function startEdit(company) {
  editingId.value = company.id
  editForm.name = company.name
  editForm.website = company.website || ''
  editForm.notes = company.notes || ''
}

async function save(id) {
  if (saving.value || !editForm.name.trim()) return ElMessage.warning('请填写公司名称')
  saving.value = true
  try {
    await api.updateCompany(id, {
      name: editForm.name.trim(),
      website: editForm.website.trim() || null,
      notes: editForm.notes.trim() || null
    })
    editingId.value = null
    emit('changed')
    ElMessage.success('公司信息已更新')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function remove(company) {
  try {
    await api.deleteCompany(company.id)
    emit('changed')
    ElMessage.success('公司已删除')
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>
