<template>
  <section class="view-panel analytics-view" aria-label="投递分析">
    <header class="analytics-insight">
      <span class="insight-icon"><MagicStick /></span>
      <div class="insight-copy">
        <h2>投递分析</h2>
        <p>
          累计关注 <b>{{ applications.length }}</b> 家企业，面试转化率为
          <b class="text-purple">{{ interviewRate }}%</b>，已收获
          <b class="text-green">{{ offerCount }}</b> 份 Offer
        </p>
      </div>
      <div class="insight-tip"><Opportunity /> 建议优先关注“{{ topChannel }}”渠道</div>
    </header>

    <div class="metric-grid analytics-metrics">
      <article v-for="item in metrics" :key="item.label" class="metric-tile">
        <div>
          <span class="metric-label">{{ item.label }}</span>
          <strong :style="{ color: item.color }">{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </div>
        <span class="metric-icon" :style="{ color: item.color, background: `${item.color}15` }"><component :is="item.icon" /></span>
      </article>
    </div>

    <div class="analytics-grid">
      <section class="analytics-card funnel-card">
        <div class="chart-heading">
          <h3><Histogram /> 求职流程分布</h3>
          <span>各阶段卡片数量</span>
        </div>
        <div class="funnel-chart" role="img" aria-label="求职流程各阶段数量">
          <div v-for="status in BOARD_STATUSES" :key="status.key" class="funnel-row">
            <span class="funnel-label">{{ status.label }}</span>
            <div class="funnel-track">
              <span :style="{ width: funnelPercent(status.key), background: status.color }"></span>
            </div>
            <b :style="{ color: status.color }">{{ countOf(status.key) }}</b>
          </div>
        </div>
      </section>

      <section class="analytics-card channel-card">
        <div class="chart-heading">
          <h3><PieChart /> 投递渠道分布</h3>
          <span>渠道占比</span>
        </div>
        <div v-if="channelSegments.length" class="channel-chart" role="img" aria-label="投递渠道占比">
          <div class="channel-donut" :style="{ background: channelGradient }">
            <div><strong>{{ sourcedCount }}</strong><span>条记录</span></div>
          </div>
          <div class="channel-legend">
            <div v-for="item in channelSegments" :key="item.name">
              <i :style="{ background: item.color }"></i>
              <span>{{ item.name }}</span>
              <b>{{ item.percent }}%</b>
            </div>
          </div>
        </div>
        <p v-else class="view-empty">暂无渠道数据</p>
      </section>

      <section class="analytics-card city-card">
        <div class="chart-heading">
          <h3><Location /> 目标城市分布</h3>
          <span>主要目标地区</span>
        </div>
        <div v-if="cities.length" class="city-chart" role="img" aria-label="目标城市投递数量">
          <div v-for="item in cities" :key="item.name" class="city-column">
            <span class="city-value" :style="{ color: item.color }">{{ item.count }}</span>
            <div class="city-bar-wrap"><span :style="{ height: item.height, background: item.color }"></span></div>
            <small>{{ item.name }}</small>
          </div>
        </div>
        <p v-else class="view-empty">暂无城市数据</p>
      </section>

      <section class="analytics-card trend-card">
        <div class="chart-heading">
          <h3><TrendCharts /> 近期投递趋势</h3>
          <span>近 14 天新增记录</span>
        </div>
        <div class="line-chart" role="img" aria-label="近十四天投递趋势折线图">
          <svg viewBox="0 0 600 210" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient id="trendArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.28" />
                <stop offset="100%" stop-color="#8b5cf6" stop-opacity="0.02" />
              </linearGradient>
            </defs>
            <line v-for="y in [36, 79, 122, 165]" :key="y" x1="28" :y1="y" x2="576" :y2="y" class="chart-grid-line" />
            <path :d="trendAreaPath" class="trend-area" />
            <polyline :points="trendLinePoints" class="trend-line" />
            <g v-for="point in trendPoints" :key="point.key" class="trend-point">
              <circle :cx="point.x" :cy="point.y" r="5" />
              <text v-if="point.count" :x="point.x" :y="point.y - 11">{{ point.count }}</text>
            </g>
          </svg>
          <div class="trend-axis">
            <span v-for="item in trendAxisLabels" :key="item.key">{{ item.label }}</span>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import {
  DataAnalysis,
  Histogram,
  Location,
  MagicStick,
  Medal,
  Opportunity,
  PieChart,
  Promotion,
  Suitcase,
  TrendCharts
} from '@element-plus/icons-vue'
import { BOARD_STATUSES } from '../constants'

const props = defineProps({ applications: Array })
const COLORS = ['#10b981', '#8b5cf6', '#3b82f6', '#f59e0b', '#ec4899', '#06b6d4', '#6366f1']
const normalizedStatus = (status) => status === 'WRITTEN_TEST' ? 'INTERVIEWING' : status
const countOf = (key) => props.applications.filter((item) => normalizedStatus(item.status) === key).length
const active = computed(() => props.applications.filter((item) => item.status !== 'SAVED').length)
const offerCount = computed(() => countOf('OFFER'))
const interviewCount = computed(() => props.applications.filter((item) => ['INTERVIEWING', 'WRITTEN_TEST', 'OFFER'].includes(item.status)).length)
const interviewRate = computed(() => active.value ? Math.round(interviewCount.value / active.value * 100) : 0)
const offerRate = computed(() => active.value ? Math.round(offerCount.value / active.value * 100) : 0)

const metrics = computed(() => [
  { label: '投递企业总数', value: props.applications.length, hint: `包含 ${countOf('SAVED')} 家收藏公司`, color: '#2563eb', icon: DataAnalysis },
  { label: '推进中面试', value: countOf('INTERVIEWING'), hint: `面试转化率 ${interviewRate.value}%`, color: '#7c3aed', icon: Suitcase },
  { label: '已收 Offer', value: offerCount.value, hint: '阶段成果持续积累', color: '#059669', icon: Medal },
  { label: '投递 Offer 率', value: `${offerRate.value}%`, hint: '基于真实投递基数', color: '#ea580c', icon: Promotion }
])

const funnelMax = computed(() => Math.max(1, ...BOARD_STATUSES.map((status) => countOf(status.key))))
const funnelPercent = (key) => `${countOf(key) ? Math.max(10, countOf(key) / funnelMax.value * 100) : 0}%`

const channelSegments = computed(() => {
  const counts = new Map()
  props.applications.forEach((item) => {
    if (item.source) counts.set(item.source, (counts.get(item.source) || 0) + 1)
  })
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1])
  const visible = sorted.slice(0, 5).map(([name, count]) => ({ name, count }))
  const otherCount = sorted.slice(5).reduce((sum, [, count]) => sum + count, 0)
  if (otherCount) visible.push({ name: '其他', count: otherCount })
  const total = visible.reduce((sum, item) => sum + item.count, 0)
  return visible.map((item, index) => ({
    ...item,
    color: COLORS[index % COLORS.length],
    percent: total ? Math.round(item.count / total * 100) : 0
  }))
})
const sourcedCount = computed(() => channelSegments.value.reduce((sum, item) => sum + item.count, 0))
const channelGradient = computed(() => {
  let cursor = 0
  const stops = channelSegments.value.map((item) => {
    const start = cursor
    cursor += sourcedCount.value ? item.count / sourcedCount.value * 100 : 0
    return `${item.color} ${start}% ${cursor}%`
  })
  return `conic-gradient(${stops.join(', ')})`
})
const topChannel = computed(() => channelSegments.value[0]?.name || '高转化')

const cities = computed(() => {
  const counts = new Map()
  props.applications.forEach((item) => {
    const city = item.city?.trim()
    if (city && /\D/.test(city)) counts.set(city, (counts.get(city) || 0) + 1)
  })
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 7)
  const max = Math.max(1, ...sorted.map(([, count]) => count))
  return sorted.map(([name, count], index) => ({
    name,
    count,
    color: COLORS[(index + 2) % COLORS.length],
    height: `${Math.max(14, count / max * 100)}%`
  }))
})

const dateKey = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const dailyTrend = computed(() => {
  const counts = new Map()
  props.applications.forEach((item) => {
    if (item.appliedAt) counts.set(item.appliedAt, (counts.get(item.appliedAt) || 0) + 1)
  })
  const today = new Date()
  return Array.from({ length: 14 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 13 + index)
    const key = dateKey(date)
    return { key, label: `${date.getMonth() + 1}/${date.getDate()}`, count: counts.get(key) || 0 }
  })
})
const trendPoints = computed(() => {
  const max = Math.max(1, ...dailyTrend.value.map((item) => item.count))
  return dailyTrend.value.map((item, index) => ({
    ...item,
    x: 28 + index * (548 / 13),
    y: 165 - item.count / max * 129
  }))
})
const trendLinePoints = computed(() => trendPoints.value.map((item) => `${item.x},${item.y}`).join(' '))
const trendAreaPath = computed(() => {
  const points = trendPoints.value
  if (!points.length) return ''
  return `M ${points[0].x} 165 L ${points.map((item) => `${item.x} ${item.y}`).join(' L ')} L ${points.at(-1).x} 165 Z`
})
const trendAxisLabels = computed(() => dailyTrend.value.filter((_, index) => index % 2 === 0 || index === dailyTrend.value.length - 1))
</script>
