<template>
  <el-drawer
    class="application-detail-drawer"
    :model-value="modelValue"
    size="min(620px, 100vw)"
    :with-header="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="application" class="drawer">
      <div class="drawer-head">
        <div class="drawer-company-avatar" :style="statusCss" aria-hidden="true">{{ companyInitial }}</div>
        <div class="drawer-company-copy">
          <div class="drawer-company-line">
            <h2>{{ companyName }}</h2>
            <span class="drawer-status-badge" :style="statusCss"><i></i>{{ currentStatus.label }}</span>
          </div>
          <p class="drawer-position">{{ application.position || '岗位待确定' }}</p>
        </div>
        <div class="drawer-head-actions">
          <el-tooltip content="编辑投递" placement="bottom">
            <el-button :icon="Edit" circle aria-label="编辑投递" :disabled="statusUpdating || deleting || addingRound" @click="emit('edit', application)" />
          </el-tooltip>
          <el-tooltip content="关闭详情" placement="bottom">
            <el-button :icon="Close" circle aria-label="关闭详情" @click="emit('update:modelValue', false)" />
          </el-tooltip>
        </div>
      </div>

      <div class="drawer-scroll-area">
        <section class="detail-section status-flow-section">
          <div class="detail-section-heading"><span><Promotion /></span><div><h3>投递进度</h3><p>点击状态即可调整当前卡片位置</p></div></div>
          <div class="status-flow" :class="{ updating: statusUpdating }">
            <button
              v-for="status in EDITABLE_STATUSES"
              :key="status.key"
              type="button"
              :aria-label="`移动到${status.label}`"
              :class="{ active: normalizedStatus === status.key }"
              :style="{ '--flow-color': status.color }"
              :disabled="statusUpdating || deleting"
              @click="changeStatus(status.key)"
            ><i></i><span>{{ status.label }}</span></button>
          </div>
        </section>

        <section class="detail-section info-section">
          <div class="detail-section-heading"><span><Suitcase /></span><div><h3>投递信息</h3><p>岗位相关的关键资料</p></div></div>
          <div class="detail-info-grid">
            <article class="detail-info detail-info-city"><span><Location /></span><div><small>目标城市</small><strong>{{ application.city || '暂未填写' }}</strong></div></article>
            <article class="detail-info detail-info-source"><span><CollectionTag /></span><div><small>投递渠道</small><strong>{{ application.source || '暂未填写' }}</strong></div></article>
            <article class="detail-info detail-info-salary"><span><Money /></span><div><small>薪资范围</small><strong>{{ salaryLabel }}</strong></div></article>
            <article class="detail-info detail-info-date"><span><Calendar /></span><div><small>投递日期</small><strong>{{ application.appliedAt || '尚未投递' }}</strong></div></article>
          </div>
          <a v-if="application.jobUrl" class="detail-job-link" :href="application.jobUrl" target="_blank" rel="noopener noreferrer"><Link />查看岗位原始链接</a>
        </section>

        <section class="detail-section notes-section">
          <div class="detail-section-heading"><span><Document /></span><div><h3>备忘录与关注重点</h3><p>JD 要点、联系人或后续计划</p></div></div>
          <p class="notes">{{ application.notes || '暂无备注信息，可通过右上角编辑按钮补充。' }}</p>
        </section>

        <section class="detail-section interview-section">
          <div class="detail-section-heading interview-heading"><span><Clock /></span><div><h3>面试轮次记录 <b>{{ interviews.length }}</b></h3><p>安排面试并持续记录每轮结果</p></div></div>
          <el-timeline v-if="interviews.length" v-loading="loadingInterviews" class="timeline">
            <el-timeline-item v-for="r in interviews" :key="r.id" :timestamp="formatTime(r.scheduledAt)" :color="resultOf(r.result).color">
              <article class="interview-record" :class="`result-${r.result.toLowerCase()}`">
                <div class="round-line">
                  <span class="round-name">第 {{ r.roundNo }} 轮 · {{ typeOf(r.type).label }}</span>
                  <el-tag round size="small" :type="resultOf(r.result).tag">{{ resultOf(r.result).label }}</el-tag>
                </div>
                <p v-if="r.feedback" class="round-feedback">{{ r.feedback }}</p>
                <div v-if="r.result === 'PENDING'" class="round-actions">
                  <el-button text size="small" type="success" :loading="updatingRoundId === r.id" :disabled="updatingRoundId !== null || addingRound || deleting" @click="setResult(r, 'PASS')">通过</el-button>
                  <el-button text size="small" type="danger" :loading="updatingRoundId === r.id" :disabled="updatingRoundId !== null || addingRound || deleting" @click="setResult(r, 'FAIL')">未通过</el-button>
                </div>
              </article>
            </el-timeline-item>
          </el-timeline>
          <div v-else v-loading="loadingInterviews" class="interview-empty"><Clock /><span>还没有面试记录</span><small>在下方添加第一轮面试安排</small></div>

          <div class="add-interview-panel">
            <div class="add-interview-title"><Plus />添加面试安排</div>
            <div class="add-round-form">
              <el-select v-model="roundForm.type" :disabled="addingRound || deleting" placeholder="面试类型" style="width: 150px">
                <el-option v-for="t in INTERVIEW_TYPES" :key="t.key" :label="t.label" :value="t.key" />
              </el-select>
              <el-date-picker v-model="roundForm.scheduledAt" type="datetime" placeholder="面试时间" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="addingRound || deleting" style="flex: 1" />
            </div>
            <el-input v-model="roundForm.feedback" placeholder="备注（考了点啥、感受如何）" class="round-feedback-input" type="textarea" :rows="2" :disabled="addingRound || deleting" />
            <el-button class="add-round-btn" :icon="Plus" :loading="addingRound" :disabled="!roundForm.type || deleting" @click="addRound">添加第 {{ interviews.length + 1 }} 轮</el-button>
          </div>
        </section>
      </div>

      <div class="drawer-footer">
        <el-popconfirm
          title="确定删除这条投递？关联的面试记录也会删除"
          :disabled="deleting || addingRound || statusUpdating"
          @confirm="remove"
        >
          <template #reference>
            <el-button
              :icon="Delete"
              type="danger"
              :loading="deleting"
              :disabled="addingRound || statusUpdating"
            >删除记录</el-button>
          </template>
        </el-popconfirm>
        <span class="drawer-id">记录 #{{ application.id }}</span>
        <el-button class="drawer-close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElInput,
  ElLoading,
  ElMessage,
  ElOption,
  ElPopconfirm,
  ElSelect,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElTooltip
} from 'element-plus'
import { Calendar, Clock, Close, CollectionTag, Delete, Document, Edit, Link, Location, Money, Plus, Promotion, Suitcase } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/popconfirm/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/tooltip/style/css'
import api from '../api'
import { EDITABLE_STATUSES, INTERVIEW_TYPES, INTERVIEW_RESULTS } from '../constants'
import { formatSalary } from '../formatters'

const props = defineProps({
  modelValue: Boolean,
  application: Object,
  companyMap: Object
})
const emit = defineEmits(['update:modelValue', 'changed', 'edit'])

const interviews = ref([])
const roundForm = reactive({ type: '', scheduledAt: '', feedback: '' })
const loadingInterviews = ref(false)
const statusUpdating = ref(false)
const addingRound = ref(false)
const deleting = ref(false)
const updatingRoundId = ref(null)
let interviewRequestVersion = 0
const vLoading = ElLoading.directive

const normalizedStatus = computed(() => props.application?.status === 'WRITTEN_TEST' ? 'INTERVIEWING' : props.application?.status)
const currentStatus = computed(() => EDITABLE_STATUSES.find((status) => status.key === normalizedStatus.value) || EDITABLE_STATUSES[0])
const companyName = computed(() => props.companyMap[props.application?.companyId] || '未知公司')
const companyInitial = computed(() => companyName.value.trim().slice(0, 1).toUpperCase() || '?')
const statusCss = computed(() => ({ '--detail-color': currentStatus.value.color }))
const salaryLabel = computed(() => formatSalary(props.application?.salaryRange, '待沟通'))

const typeOf = (key) => INTERVIEW_TYPES.find((t) => t.key === key) ?? { label: key }
const resultOf = (key) => INTERVIEW_RESULTS.find((r) => r.key === key) ?? INTERVIEW_RESULTS[0]
const formatTime = (t) => (t ? t.replace('T', ' ').slice(0, 16) : '')

async function loadInterviews() {
  if (!props.application) return
  const applicationId = props.application.id
  const version = ++interviewRequestVersion
  loadingInterviews.value = true
  try {
    const result = await api.listInterviews(applicationId)
    if (version === interviewRequestVersion && props.application?.id === applicationId) {
      interviews.value = result
    }
  } catch (e) {
    if (version === interviewRequestVersion && props.application?.id === applicationId) {
      ElMessage.error('面试记录加载失败：' + (e.message || '未知错误'))
    }
  } finally {
    if (version === interviewRequestVersion) loadingInterviews.value = false
  }
}

watch(
  () => [props.modelValue, props.application?.id],
  ([visible]) => {
    if (!visible) return
    interviews.value = []
    roundForm.type = ''
    roundForm.scheduledAt = ''
    roundForm.feedback = ''
    loadInterviews()
  },
  { immediate: true }
)

async function changeStatus(status) {
  if (statusUpdating.value || deleting.value) return
  statusUpdating.value = true
  try {
    await api.updateStatus(props.application.id, status)
    ElMessage.success('状态已更新')
    emit('changed')
  } catch (e) {
    ElMessage.error('更新失败：' + (e.message || '未知错误'))
  } finally {
    statusUpdating.value = false
  }
}

async function addRound() {
  if (addingRound.value || deleting.value || !roundForm.type) return
  addingRound.value = true
  try {
    await api.addInterview(props.application.id, {
      type: roundForm.type,
      scheduledAt: roundForm.scheduledAt || null,
      feedback: roundForm.feedback || null
    })
    roundForm.type = ''
    roundForm.scheduledAt = ''
    roundForm.feedback = ''
    await loadInterviews()
    emit('changed')
  } catch (e) {
    ElMessage.error('添加失败：' + (e.message || '未知错误'))
  } finally {
    addingRound.value = false
  }
}

async function setResult(round, result) {
  if (updatingRoundId.value !== null || deleting.value) return
  updatingRoundId.value = round.id
  try {
    const updated = await api.updateInterviewResult(round.id, result)
    round.result = updated.result
    round.feedback = updated.feedback
    if (result === 'FAIL') {
      ElMessage.success('已标记未通过，卡片已移入“未通过”')
      emit('changed')
    }
  } catch (e) {
    ElMessage.error('更新失败：' + (e.message || '未知错误'))
  } finally {
    updatingRoundId.value = null
  }
}

async function remove() {
  if (deleting.value || addingRound.value || statusUpdating.value) return
  deleting.value = true
  try {
    await api.deleteApplication(props.application.id)
    ElMessage.success('已删除')
    emit('update:modelValue', false)
    emit('changed')
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '未知错误'))
  } finally {
    deleting.value = false
  }
}
</script>
