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

  const grades = (audience.grades || []).map(grade => /^\d{4}$/.test(grade) ? `${grade}级` : grade)
  const majors = audience.majors || []
  const colleges = audience.colleges || []
  const levels = audience.levels || []
  const rows = [
    { label: '年级', value: grades.join('或') || '未单独限选' },
    { label: '专业', value: majors.map(getMajorName).join('或') || '未单独限选' }
  ]
  if (colleges.length) rows.push({ label: '学院', value: colleges.map(getCollegeName).join('或') })
  if (levels.length) rows.push({ label: '培养层次', value: levels.join('或') })
  if (audience.class_names) rows.push({ label: '班级', value: audience.class_names.join('、') || '待确认' })
  if (audience.pre_courses?.length) rows.push({ label: '先修课程', value: audience.pre_courses.join('、') })
  if (audience.special_requirement || props.detailed) {
    rows.push({ label: '特殊要求', value: audience.special_requirement || '无' })
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
