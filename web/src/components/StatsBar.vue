<template>
  <div v-if="stats" class="stats">
    <div class="stat">
      <span class="stat-num">{{ stats.total }}</span>
      <span class="stat-label">总投递</span>
    </div>
    <span class="stat-divider"></span>
    <div class="stat">
      <span class="stat-num">{{ inProgress }}</span>
      <span class="stat-label">进行中</span>
    </div>
    <span class="stat-divider"></span>
    <div class="stat">
      <span class="stat-num stat-offer">{{ stats.offerCount }}</span>
      <span class="stat-label">Offer</span>
    </div>
    <span class="stat-divider"></span>
    <div class="stat">
      <span class="stat-num">{{ offerRate }}</span>
      <span class="stat-label">Offer 率</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ stats: Object })

const inProgress = computed(() => {
  const by = props.stats?.byStatus ?? {}
  return (by.WRITTEN_TEST ?? 0) + (by.INTERVIEWING ?? 0)
})

const offerRate = computed(() =>
  props.stats ? Math.round(props.stats.offerRate * 100) + '%' : '0%'
)
</script>
