<template>
  <el-config-provider :locale="zhCn">
    <div class="app" :class="{ 'dark-mode': darkMode }">
      <header class="app-header">
        <div class="brand">
          <span class="brand-mark" aria-hidden="true"><Briefcase /></span>
          <div class="brand-text">
            <div class="brand-title"><h1>Offer Tracker</h1><span>灵动版</span></div>
            <p>求职投递与面试进度全追踪</p>
          </div>
        </div>
        <StatsBar :stats="stats" />
        <div class="header-actions">
          <el-tooltip :content="darkMode ? '切换为浅色模式' : '切换为深色模式'">
            <el-button :icon="darkMode ? Sunny : Moon" circle :aria-label="darkMode ? '切换为浅色模式' : '切换为深色模式'" @click="toggleDarkMode" />
          </el-tooltip>
          <el-tooltip content="简历库">
            <el-button :icon="Document" circle aria-label="简历库" @click="resumeVisible = true" />
          </el-tooltip>
          <el-tooltip content="公司管理">
            <el-button :icon="OfficeBuilding" circle aria-label="公司管理" @click="companyVisible = true" />
          </el-tooltip>
          <el-tooltip content="数据备份与恢复">
            <el-button :icon="Files" circle aria-label="数据备份与恢复" @click="dataVisible = true" />
          </el-tooltip>
          <el-button class="save-btn" :icon="Star" @click="savedDialogVisible = true">收藏公司</el-button>
          <el-button class="dark-btn add-btn" :icon="Plus" @click="openAdd">记一笔投递</el-button>
        </div>
      </header>

      <section class="filter-bar" aria-label="投递筛选">
        <el-input
          v-model="filters.keyword"
          class="filter-keyword"
          :prefix-icon="Search"
          clearable
          aria-label="搜索公司、岗位、城市或渠道"
          placeholder="搜索公司、岗位、城市或渠道"
          @keyup.enter="applyFilters"
        />
        <el-popover placement="bottom-start" :width="220" trigger="click">
          <template #reference><el-button class="filter-trigger" :class="{ active: filters.status }" :icon="Filter" aria-label="选择状态">{{ selectedStatusLabel }}</el-button></template>
          <div class="filter-menu">
            <button :class="{ selected: !filters.status }" @click="filters.status = ''">全部状态</button>
            <button v-for="status in BOARD_STATUSES" :key="status.key" :class="{ selected: filters.status === status.key }" @click="filters.status = status.key"><span class="filter-dot" :style="{ background: status.color }"></span>{{ status.label }}</button>
          </div>
        </el-popover>
        <el-popover placement="bottom-start" :width="250" trigger="click">
          <template #reference><el-button class="filter-trigger" :class="{ active: filters.city }" :icon="MapLocation" aria-label="选择城市">{{ filters.city || '城市' }}</el-button></template>
          <el-input v-model="filters.city" clearable aria-label="筛选城市" placeholder="输入城市" @keyup.enter="applyFilters" />
          <div class="filter-chips"><button v-for="city in commonCities" :key="city" @click="filters.city = city">{{ city }}</button></div>
        </el-popover>
        <el-popover placement="bottom-start" :width="250" trigger="click">
          <template #reference><el-button class="filter-trigger" :class="{ active: filters.source }" :icon="CollectionTag" aria-label="选择渠道">{{ filters.source || '全部渠道' }}</el-button></template>
          <el-select v-model="filters.source" clearable filterable allow-create aria-label="筛选渠道" placeholder="选择或输入渠道" style="width: 100%"><el-option v-for="source in SOURCES" :key="source" :label="source" :value="source" /></el-select>
        </el-popover>
        <el-popover placement="bottom-start" :width="340" trigger="click">
          <template #reference><el-button class="filter-trigger filter-date-trigger" :class="{ active: filters.appliedRange?.length }" :icon="Calendar" aria-label="选择日期">{{ dateFilterLabel }}</el-button></template>
          <el-date-picker class="filter-date" v-model="filters.appliedRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" aria-label="筛选投递日期" />
        </el-popover>
        <div class="filter-actions">
          <el-button :icon="Search" :loading="refreshing" @click="applyFilters">筛选</el-button>
          <el-tooltip content="清空筛选">
            <el-button
              :icon="Refresh"
              circle
              aria-label="清空筛选"
              :disabled="!hasActiveFilters"
              @click="clearFilters"
            />
          </el-tooltip>
          <span class="filter-count" aria-live="polite">{{ applications.length }} 条结果</span>
        </div>
        <el-segmented v-model="viewMode" class="view-switcher" :options="viewOptions" aria-label="切换数据视图" />
      </section>

      <main class="content">
        <div v-if="initialLoading" class="board board-skeleton" aria-label="正在加载投递看板">
          <div v-for="index in 4" :key="index" class="column skeleton-column">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="text" class="skeleton-title" />
                <el-skeleton-item variant="rect" class="skeleton-card" />
                <el-skeleton-item variant="rect" class="skeleton-card" />
              </template>
            </el-skeleton>
          </div>
        </div>

        <el-result v-else-if="loadError" icon="error" title="看板加载失败" :sub-title="loadError">
          <template #extra>
            <el-button class="dark-btn" :icon="Refresh" @click="refresh({ initial: true })">重新加载</el-button>
          </template>
        </el-result>

        <el-empty
          v-else-if="!applications.length"
          :description="hasActiveFilters ? '没有符合筛选条件的投递' : '还没有投递记录'"
        >
          <el-button v-if="hasActiveFilters" :icon="Refresh" @click="clearFilters">清空筛选</el-button>
          <el-button v-else class="dark-btn" :icon="Plus" @click="openAdd">记录第一笔投递</el-button>
        </el-empty>

        <div v-else-if="viewMode === 'kanban'" v-loading="refreshing || boardUpdating" class="board" :class="{ 'board-updating': boardUpdating }">
          <KanbanColumn
            v-for="column in BOARD_STATUSES"
            :key="column.key"
            :column="column"
            :cards="groups[column.key]"
            :company-map="companyMap"
            :disabled="refreshing || boardUpdating"
            @update:cards="(value) => (groups[column.key] = value)"
            @moved="onMoved"
            @open="openDetail"
          />
        </div>
        <AnalyticsView v-else-if="viewMode === 'analytics'" :applications="applications" />
        <CalendarView
          v-else-if="viewMode === 'calendar'"
          :applications="applications"
          :company-map="companyMap"
          :interviews="calendarInterviews"
          :loading="calendarLoading"
          @open="openDetail"
        />
        <TableView v-else :applications="applications" :company-map="companyMap" @open="openDetail" @edit="openEdit" />
      </main>

      <AddApplicationDialog
        v-if="applicationDialogVisible"
        v-model="applicationDialogVisible"
        :companies="companies"
        :application="editingApplication"
        @saved="afterApplicationSaved"
      />
      <SaveCompanyDialog
        v-if="savedDialogVisible"
        v-model="savedDialogVisible"
        :companies="companies"
        @saved="afterApplicationSaved"
      />
      <ApplicationDrawer
        v-if="drawerVisible"
        v-model="drawerVisible"
        :application="current"
        :company-map="companyMap"
        @changed="refresh"
        @edit="openEdit"
      />
      <CompanyManagerDialog
        v-if="companyVisible"
        v-model="companyVisible"
        :companies="companies"
        @changed="refresh"
      />
      <DataManagerDialog
        v-if="dataVisible"
        v-model="dataVisible"
        @imported="afterImport"
      />
      <ResumeManagerDialog
        v-if="resumeVisible"
        v-model="resumeVisible"
        :applications="applications"
        :company-map="companyMap"
      />
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue'
import {
  ElButton,
  ElConfigProvider,
  ElDatePicker,
  ElEmpty,
  ElInput,
  ElLoading,
  ElMessage,
  ElOption,
  ElPopover,
  ElResult,
  ElSelect,
  ElSegmented,
  ElSkeleton,
  ElSkeletonItem,
  ElTooltip
} from 'element-plus'
import { Briefcase, Calendar, CollectionTag, Document, Files, Filter, MapLocation, Moon, OfficeBuilding, Plus, Refresh, Search, Star, Sunny } from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/config-provider/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/popover/style/css'
import 'element-plus/es/components/result/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/segmented/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/skeleton-item/style/css'
import 'element-plus/es/components/tooltip/style/css'
import api from './api'
import { BOARD_STATUSES, CITIES, SOURCES } from './constants'
import StatsBar from './components/StatsBar.vue'
import KanbanColumn from './components/KanbanColumn.vue'

const AddApplicationDialog = defineAsyncComponent(() => import('./components/AddApplicationDialog.vue'))
const SaveCompanyDialog = defineAsyncComponent(() => import('./components/SaveCompanyDialog.vue'))
const ApplicationDrawer = defineAsyncComponent(() => import('./components/ApplicationDrawer.vue'))
const CompanyManagerDialog = defineAsyncComponent(() => import('./components/CompanyManagerDialog.vue'))
const DataManagerDialog = defineAsyncComponent(() => import('./components/DataManagerDialog.vue'))
const ResumeManagerDialog = defineAsyncComponent(() => import('./components/ResumeManagerDialog.vue'))
const AnalyticsView = defineAsyncComponent(() => import('./components/AnalyticsView.vue'))
const CalendarView = defineAsyncComponent(() => import('./components/CalendarView.vue'))
const TableView = defineAsyncComponent(() => import('./components/TableView.vue'))
const vLoading = ElLoading.directive

const applications = ref([])
const companies = ref([])
const stats = ref(null)
const current = ref(null)
const editingApplication = ref(null)
const applicationDialogVisible = ref(false)
const savedDialogVisible = ref(false)
const drawerVisible = ref(false)
const companyVisible = ref(false)
const dataVisible = ref(false)
const resumeVisible = ref(false)
const viewMode = ref('kanban')
const darkMode = ref(localStorage.getItem('offer-tracker-theme') === 'dark' || (!localStorage.getItem('offer-tracker-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches))
const calendarInterviews = ref({})
const calendarLoading = ref(false)
const initialLoading = ref(true)
const refreshing = ref(false)
const boardUpdating = ref(false)
const loadError = ref('')
const activeFilters = ref({})
const filters = reactive({ keyword: '', status: '', city: '', source: '', appliedRange: [] })
const commonCities = CITIES
const viewOptions = [
  { label: '看板', value: 'kanban' },
  { label: '分析', value: 'analytics' },
  { label: '日历', value: 'calendar' },
  { label: '表格', value: 'table' }
]
let refreshVersion = 0

const groups = reactive(Object.fromEntries(BOARD_STATUSES.map((status) => [status.key, []])))
const companyMap = computed(() => Object.fromEntries(companies.value.map((company) => [company.id, company.name])))
const hasActiveFilters = computed(() => Object.values(activeFilters.value).some(Boolean))
const selectedStatusLabel = computed(() => BOARD_STATUSES.find((status) => status.key === filters.status)?.label || '全部状态')
const dateFilterLabel = computed(() => filters.appliedRange?.length ? `${filters.appliedRange[0].slice(5)} 至 ${filters.appliedRange[1].slice(5)}` : '投递日期')

function rebuildGroups() {
  BOARD_STATUSES.forEach((status) => { groups[status.key] = [] })
  applications.value.forEach((application) => {
    const boardStatus = application.status === 'WRITTEN_TEST' ? 'INTERVIEWING' : application.status
    groups[boardStatus]?.push(application)
  })
}

function filterParams() {
  return {
    keyword: filters.keyword.trim() || undefined,
    status: filters.status || undefined,
    city: filters.city.trim() || undefined,
    source: filters.source || undefined,
    appliedFrom: filters.appliedRange?.[0] || undefined,
    appliedTo: filters.appliedRange?.[1] || undefined
  }
}

async function refresh(options = {}) {
  const version = ++refreshVersion
  const showInitial = options.initial === true || initialLoading.value
  if (showInitial) initialLoading.value = true
  else refreshing.value = true
  loadError.value = ''
  try {
    const [apps, companyList, overview] = await Promise.all([
      api.listApplications(activeFilters.value),
      api.listCompanies(),
      api.stats()
    ])
    if (version !== refreshVersion) return
    applications.value = apps.records ?? apps
    companies.value = companyList
    stats.value = overview
    rebuildGroups()
    if (current.value) {
      current.value = applications.value.find((application) => application.id === current.value.id) ?? null
      if (!current.value) drawerVisible.value = false
    }
  } catch (error) {
    if (version === refreshVersion) {
      loadError.value = error.message || '请确认后端已启动'
      if (applications.value.length) ElMessage.error('刷新失败：' + loadError.value)
    }
  } finally {
    if (version === refreshVersion) {
      initialLoading.value = false
      refreshing.value = false
    }
  }
}

function applyFilters() {
  activeFilters.value = filterParams()
  refresh()
}

function clearFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.city = ''
  filters.source = ''
  filters.appliedRange = []
  activeFilters.value = {}
  refresh()
}

async function onMoved(id, newStatus) {
  if (!newStatus) return refresh()
  if (boardUpdating.value) return refresh()
  boardUpdating.value = true
  try {
    await api.updateStatus(id, newStatus)
    await refresh()
  } catch (error) {
    ElMessage.error('状态更新失败：' + (error.message || '未知错误'))
    await refresh()
  } finally {
    boardUpdating.value = false
  }
}

function openAdd() {
  editingApplication.value = null
  applicationDialogVisible.value = true
}

function openEdit(application) {
  drawerVisible.value = false
  editingApplication.value = application
  applicationDialogVisible.value = true
}

function openDetail(application) {
  current.value = application
  drawerVisible.value = true
}

async function afterApplicationSaved() {
  await refresh()
  editingApplication.value = null
}

async function afterImport() {
  clearFilters()
}

function toggleDarkMode() {
  darkMode.value = !darkMode.value
  localStorage.setItem('offer-tracker-theme', darkMode.value ? 'dark' : 'light')
}

async function loadCalendarInterviews() {
  if (calendarLoading.value) return
  calendarLoading.value = true
  try {
    const results = await Promise.all(applications.value.map(async (application) => [application.id, await api.listInterviews(application.id)]))
    calendarInterviews.value = Object.fromEntries(results)
  } catch (error) {
    ElMessage.error('面试日历加载失败：' + (error.message || '未知错误'))
  } finally {
    calendarLoading.value = false
  }
}

watch(viewMode, (mode) => {
  if (mode === 'calendar') loadCalendarInterviews()
})

onMounted(() => refresh({ initial: true }))
</script>
