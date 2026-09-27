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
      <p>本阶段先完成题目生成，回答和 AI 评分将在下一步接入。</p>
    </section>
    <el-skeleton v-else animated :rows="3" />
  </el-dialog>
</template>

<script setup>
import { ChatDotRound } from '@element-plus/icons-vue'
import { ElAlert, ElDialog, ElSkeleton, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/tag/style/css'

defineProps({
  modelValue: Boolean,
  session: { type: Object, default: null },
  errorMessage: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
</script>
