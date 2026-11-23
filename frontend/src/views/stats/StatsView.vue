<template>
  <ModulePlaceholder
    title="业务④ 成绩查询与统计"
    owner="黎家俊"
    api-prefix="stats"
    :fallback-leaves="[
      '4.1 学生评教（最简评教）',
      '4.2 成绩查询（先评教后查分拦截）',
      '4.3 学业统计计算（GPA/学分/排名，排除辅修 study_type=3 并联合外校认定）',
      '4.4 成绩单打印（含来校交换生中英文正式成绩单）',
      '4.5 成绩统计报表（成绩分布/不及格名单/评优排名）',
      '4.6 评教结果汇总（按教学班汇总，供第4组读取）'
    ]"
  />
</template>

<script setup>
import ModulePlaceholder from '../../components/ModulePlaceholder.vue'
</script>
