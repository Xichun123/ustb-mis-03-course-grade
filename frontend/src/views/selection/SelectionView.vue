<template>
  <ModulePlaceholder
    title="业务② 学生选课"
    owner="张文浩"
    api-prefix="selection"
    :fallback-leaves="[
      '2.1 课程查询与教学班选择',
      '2.2 选课与退选办理',
      '2.3 选课校验（时间冲突/容量/学分上限/限选/研究生方向/重修冲突免听）',
      '2.4 录取与超额筛选',
      '2.5 停开处理',
      '2.6 选课结果与上课安排查询',
      '2.7 上课名单调整（教师本班 + 教务处全校）'
    ]"
  />
</template>

<script setup>
import ModulePlaceholder from '../../components/ModulePlaceholder.vue'
</script>
