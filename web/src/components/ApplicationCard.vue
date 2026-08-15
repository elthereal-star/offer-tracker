<template>
  <article
    class="card"
    :data-id="card.id"
    :style="{ '--status-color': color }"
    role="button"
    tabindex="0"
    :aria-label="`${companyName}，${positionLabel}`"
    @click="emit('click')"
    @keydown.enter="emit('click')"
    @keydown.space.prevent="emit('click')"
  >
    <div class="card-accent" aria-hidden="true"></div>
    <div class="card-heading">
      <span class="company-avatar" aria-hidden="true">{{ companyInitial }}</span>
      <div class="card-heading-copy">
        <div class="card-title">{{ companyName }}</div>
        <div class="card-position">{{ positionLabel }}</div>
      </div>
      <span v-if="card.status === 'OFFER'" class="offer-badge"><Medal />Offer</span>
    </div>
    <div v-if="card.salaryRange" class="card-salary"><Money />{{ salaryLabel }}</div>
    <div class="card-footer">
      <span v-if="card.source" class="card-tag"><CollectionTag />{{ card.source }}</span>
      <span v-else class="card-tag card-tag-muted">{{ statusLabel }}</span>
      <span class="card-meta-right"><Location v-if="card.city" />{{ metaRight }}</span>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { CollectionTag, Location, Medal, Money } from '@element-plus/icons-vue'
import { formatSalary } from '../formatters'

const props = defineProps({
  card: Object,
  color: String,
  companyName: String,
  statusLabel: String
})
const emit = defineEmits(['click'])
const positionLabel = computed(() => props.card.position || '岗位待确定')
const companyInitial = computed(() => props.companyName?.trim().slice(0, 1).toUpperCase() || '?')
const salaryLabel = computed(() => formatSalary(props.card.salaryRange))

const metaRight = computed(() => {
  const parts = []
  if (props.card.city) parts.push(props.card.city)
  if (props.card.appliedAt) parts.push(props.card.appliedAt.slice(5).replace('-', '/'))
  return parts.join(' · ')
})
</script>
