<template>
  <ModulePlaceholder
    title="业务③ 成绩录入与审核"
    owner="蒋作"
    api-prefix="score"
    :fallback-leaves="[
      '3.1 成绩录入（按教师本人合班课堂号 lesson_no 列表点选录分）',
      '3.2 总评计算与提交（生成 6 位随机核对码与纸质成绩登记表）',
      '3.3 学院核对（按纸质单输入随机码核验通过/退回）',
      '3.4 成绩发布（重标 is_final 并同事务触发 4.3）',
      '3.5 校外交流成绩换算与认定（v8 新增，关联第15组视图）'
    ]"
  />
</template>

<script setup>
import ModulePlaceholder from '../../components/ModulePlaceholder.vue'
</script>
