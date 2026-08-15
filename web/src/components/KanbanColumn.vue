<template>
  <section class="column" :class="`column-${column.key.toLowerCase()}`" :style="{ '--status-color': column.color }" :aria-label="`${column.label}，${list.length} 条投递`">
    <header class="column-header">
      <span class="column-icon"><component :is="statusIcon" /></span>
      <span class="column-title">{{ column.label }}</span>
      <span class="column-count">{{ list.length }}</span>
    </header>
    <VueDraggable
      v-model="list"
      group="cards"
      :animation="180"
      :disabled="disabled"
      :data-status="column.key"
      :aria-label="`${column.label}投递列表`"
      draggable=".card"
      class="column-body"
      ghost-class="drag-ghost"
      @end="onEnd"
    >
      <ApplicationCard
        v-for="card in list"
        :key="card.id"
        :card="card"
        :color="column.color"
        :status-label="column.label"
        :company-name="companyMap[card.companyId] || '未知公司'"
        @click="emit('open', card)"
      />
      <p v-if="!list.length" class="empty-hint">拖拽卡片到这里</p>
    </VueDraggable>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, CircleClose, CollectionTag, Medal, Promotion, Suitcase } from '@element-plus/icons-vue'
import { VueDraggable } from 'vue-draggable-plus'
import ApplicationCard from './ApplicationCard.vue'

const props = defineProps({
  column: Object,
  cards: Array,
  companyMap: Object,
  disabled: Boolean
})
const emit = defineEmits(['update:cards', 'moved', 'open'])
const icons = { SAVED: CollectionTag, APPLIED: Promotion, INTERVIEWING: Suitcase, OFFER: Medal, REJECTED: CircleClose, WITHDRAWN: CircleCheck }
const statusIcon = computed(() => icons[props.column.key] || Suitcase)

const list = computed({
  get: () => props.cards,
  set: (v) => emit('update:cards', v)
})

function onEnd(evt) {
  if (evt.from === evt.to) return
  emit('moved', Number(evt.item.dataset.id), evt.to.dataset.status)
}
</script>
