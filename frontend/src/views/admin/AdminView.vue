<template>
  <ModulePlaceholder
    title="业务⑥ 系统维护"
    owner="蒋作"
    api-prefix="admin"
    :fallback-leaves="[
      '6.1 编码与系统参数维护（sys_code_dict）',
      '6.2 数据备份与恢复（sys_backup_log 与应急快照）'
    ]"
  />
</template>

<script setup>
import ModulePlaceholder from '../../components/ModulePlaceholder.vue'
</script>
