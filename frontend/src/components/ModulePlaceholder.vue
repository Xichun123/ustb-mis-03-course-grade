<template>
  <el-card shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span><b>{{ title }}</b> · 负责人：{{ owner }}</span>
        <el-tag type="info">独立模块骨架已就绪</el-tag>
      </div>
    </template>
    <el-alert
      :title="`请 ${owner} 在后端 module/${apiPrefix}/ 与前端 views/${apiPrefix}/ 中实现以下模块（可参考 业务① PrepView / PrepService 样板）`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />
    <el-descriptions :column="2" border style="margin-bottom: 16px">
      <el-descriptions-item label="后端包路径"><code>com.ustb.coursegrade.module.{{ apiPrefix }}</code></el-descriptions-item>
      <el-descriptions-item label="前端目录"><code>frontend/src/views/{{ apiPrefix }}/</code></el-descriptions-item>
      <el-descriptions-item label="接口前缀"><code>/api/v1/{{ apiPrefix }}/**</code></el-descriptions-item>
      <el-descriptions-item label="后端连通检查">
        <el-tag :type="backendReady ? 'success' : 'warning'">
          {{ backendReady ? '后端骨架接口已通' : '待启动后端服务' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>
    <el-table :data="leafRows" border stripe>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="name" label="需实现的叶子模块清单（严格对齐设计报告 v9）" />
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { systemApi } from '../api/modules'

const props = defineProps({
  title: String,
  owner: String,
  apiPrefix: String,
  fallbackLeaves: Array
})

const backendReady = ref(false)
const leafRows = ref((props.fallbackLeaves || []).map(name => ({ name })))

onMounted(async () => {
  try {
    const res = await systemApi.moduleInfo(props.apiPrefix)
    backendReady.value = true
    if (res.data?.leaf_modules?.length) {
      leafRows.value = res.data.leaf_modules.map(name => ({ name }))
    }
  } catch {
    backendReady.value = false
  }
})
</script>
