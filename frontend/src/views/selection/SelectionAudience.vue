<template>
  <dl v-if="audience" class="audience">
    <div v-for="{ label, value } in rows" :key="label">
      <dt>{{ label }}</dt>
      <dd>{{ value }}</dd>
    </div>
  </dl>
  <span v-else class="audience-fallback">面向对象待确认</span>
</template>

<script setup>
import { computed } from 'vue'
import { getCollegeName, getMajorName } from '../../constants/dicts'

const props = defineProps({
  audience: Object,
  detailed: Boolean
})

const rows = computed(() => {
  const audience = props.audience
  if (!audience) return []

  const grades = audience.grades.map(grade => /^\d{4}$/.test(grade) ? `${grade}级` : grade)
  const rows = [
    { label: '年级', value: grades.join('或') || '未单独限选' },
    { label: '专业', value: audience.majors.map(getMajorName).join('或') || '未单独限选' }
  ]
  if (audience.colleges.length) rows.push({ label: '学院', value: audience.colleges.map(getCollegeName).join('或') })
  if (audience.levels.length) rows.push({ label: '培养层次', value: audience.levels.join('或') })
  if (audience.class_names) rows.push({ label: '班级', value: audience.class_names.join('、') || '待确认' })
  if (audience.special_requirement || props.detailed) {
    rows.push({ label: '特殊要求', value: audience.special_requirement || '未公布' })
  }
  return rows
})
</script>

<style scoped>
.audience {
  display: grid;
  gap: 3px;
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.5;
}

.audience > div {
  display: grid;
  grid-template-columns: 4em minmax(0, 1fr);
  gap: 8px;
}

.audience dt,
.audience-fallback {
  color: var(--app-text-secondary);
}

.audience dd {
  margin: 0;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
</style>
