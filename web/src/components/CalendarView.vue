<template>
  <section class="view-panel calendar-view" aria-label="面试日历">
    <div class="view-heading calendar-heading">
      <div class="view-title-with-icon"><span><Calendar /></span><div><h2>面试日历</h2><p>按日期查看真实面试安排</p></div></div>
      <div class="calendar-controls">
        <el-button size="small" @click="goToday">今天</el-button>
        <el-button-group><el-button size="small" :icon="ArrowLeft" aria-label="上个月" @click="changeMonth(-1)"/><el-button size="small" :icon="ArrowRight" aria-label="下个月" @click="changeMonth(1)"/></el-button-group>
        <strong>{{ year }} 年 {{ month + 1 }} 月</strong>
      </div>
    </div>
    <div v-if="loading" class="view-loading">正在加载面试日程…</div>
    <div v-else class="calendar-grid">
      <div v-for="day in weekDays" :key="day" class="calendar-weekday">{{ day }}</div>
      <div v-for="index in firstDay" :key="`blank-${index}`" class="calendar-day is-blank"></div>
      <div v-for="day in daysInMonth" :key="day" class="calendar-day" :class="{ 'is-today': isToday(day) }">
        <span class="day-number">{{ day }}</span>
        <button v-for="event in eventsOf(day)" :key="event.id" class="calendar-event" @click="emit('open', event.application)">
          <b>{{ event.time }}</b><span>{{ companyMap[event.application.companyId] || '未知公司' }}</span><small>{{ event.typeLabel }}</small>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElButton, ElButtonGroup } from 'element-plus'
import { ArrowLeft, ArrowRight, Calendar } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/button-group/style/css'
import { INTERVIEW_TYPES } from '../constants'

const props = defineProps({ applications: Array, companyMap: Object, interviews: Object, loading: Boolean })
const emit = defineEmits(['open'])
const cursor = ref(new Date())
const year = computed(() => cursor.value.getFullYear())
const month = computed(() => cursor.value.getMonth())
const firstDay = computed(() => new Date(year.value, month.value, 1).getDay())
const daysInMonth = computed(() => new Date(year.value, month.value + 1, 0).getDate())
const weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const changeMonth = (offset) => { cursor.value = new Date(year.value, month.value + offset, 1) }
const goToday = () => { cursor.value = new Date() }
const dateKey = (day) => `${year.value}-${String(month.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
const isToday = (day) => dateKey(day) === new Date().toISOString().slice(0, 10)
const events = computed(() => props.applications.flatMap((application) => (props.interviews[application.id] || []).filter((item) => item.scheduledAt).map((item) => ({
  ...item, application, date: item.scheduledAt.slice(0, 10), time: item.scheduledAt.slice(11, 16), typeLabel: INTERVIEW_TYPES.find((type) => type.key === item.type)?.label || item.type
}))))
const eventsOf = (day) => events.value.filter((item) => item.date === dateKey(day))
</script>
