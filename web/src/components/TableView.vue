<template>
  <section class="view-panel table-view" aria-label="投递表格">
    <div class="view-heading"><div class="view-title-with-icon"><span><Tickets /></span><div><h2>投递明细</h2><p>共 {{ applications.length }} 条记录</p></div></div></div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>公司</th><th>岗位</th><th>状态</th><th>城市</th><th>渠道</th><th>投递日期</th><th>薪资</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="item in applications" :key="item.id">
            <td><button class="table-company" @click="emit('open', item)">{{ companyMap[item.companyId] || '未知公司' }}</button></td>
            <td>{{ item.position || '岗位待确定' }}</td>
            <td><span class="status-pill" :style="pillStyle(item.status)">{{ statusOf(item.status).label }}</span></td>
            <td>{{ item.city || '—' }}</td><td>{{ item.source || '—' }}</td><td>{{ item.appliedAt || '—' }}</td><td class="table-salary">{{ formatSalary(item.salaryRange) }}</td>
            <td><el-button text size="small" @click="emit('open', item)">查看</el-button><el-button text size="small" @click="emit('edit', item)">编辑</el-button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { ElButton } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import 'element-plus/es/components/button/style/css'
import { BOARD_STATUSES } from '../constants'
import { formatSalary } from '../formatters'
defineProps({ applications: Array, companyMap: Object })
const emit = defineEmits(['open', 'edit'])
const statusOf = (key) => BOARD_STATUSES.find((item) => item.key === (key === 'WRITTEN_TEST' ? 'INTERVIEWING' : key)) || BOARD_STATUSES[0]
const pillStyle = (key) => ({ color: statusOf(key).color, borderColor: `${statusOf(key).color}55`, background: `${statusOf(key).color}12` })
</script>
