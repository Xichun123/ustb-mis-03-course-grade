<template>
  <div>
    <el-alert v-if="!canView" title="请切换至系统管理员或教务处经办账号查看系统维护模块（写入操作仅限系统管理员）。" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-tabs v-else v-model="activeTab" type="border-card">
      <!-- 6.1 编码维护 -->
      <el-tab-pane label="6.1 编码参数维护" name="codes">
        <div class="toolbar">
          <el-input v-model="codeQuery.group" placeholder="编码分组，如 system_param" style="width: 230px" clearable />
          <el-input v-model="codeQuery.keyword" placeholder="编码键 / 名称" style="width: 200px" clearable />
          <el-button type="primary" @click="loadCodes">查询</el-button>
          <el-button v-if="isAdmin" type="success" @click="openCode">+ 新增编码</el-button>
        </div>
        <el-table :data="codes" border stripe v-loading="loadingCodes">
          <el-table-column prop="dict_group" label="分组" width="180" />
          <el-table-column prop="dict_code" label="编码键" width="170" />
          <el-table-column prop="dict_name" label="名称/取值" min-width="160" />
          <el-table-column prop="remark" label="说明" min-width="180" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-tag :type="row.is_enabled === 1 ? 'success' : 'info'" size="small">
                {{ row.is_enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="update_time" label="更新时间" min-width="160" />
          <el-table-column v-if="isAdmin" label="操作" width="140">
            <template #default="{ row }">
              <el-button type="primary" link @click="openCode(row)">编辑</el-button>
              <el-button :type="row.is_enabled === 1 ? 'danger' : 'success'" link @click="toggleCode(row)">
                {{ row.is_enabled === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 6.2 备份与恢复 -->
      <el-tab-pane label="6.2 数据备份与恢复" name="backups">
        <div class="toolbar">
          <el-button type="primary" @click="loadBackups">刷新备份日志</el-button>
          <el-button v-if="isAdmin" type="success" @click="openBackup">+ 登记备份</el-button>
        </div>
        <el-table :data="backups" border stripe v-loading="loadingBackups">
          <el-table-column prop="backup_id" label="编号" width="80" />
          <el-table-column label="类型" width="130">
            <template #default="{ row }">{{ row.backup_type === 2 ? '恢复前应急快照' : '手工备份' }}</template>
          </el-table-column>
          <el-table-column prop="file_name" label="备份文件" min-width="220" />
          <el-table-column prop="file_size_kb" label="大小(KB)" width="100" />
          <el-table-column prop="operator_id" label="操作人" width="100" />
          <el-table-column prop="backup_time" label="时间" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" />
          <el-table-column v-if="isAdmin" label="操作" width="110">
            <template #default="{ row }">
              <el-button type="warning" link @click="restore(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="codeVisible" :title="codeForm.dict_id ? '编辑编码' : '新增编码'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="分组"><el-input v-model="codeForm.dict_group" :disabled="!!codeForm.dict_id" /></el-form-item>
        <el-form-item label="编码键"><el-input v-model="codeForm.dict_code" :disabled="!!codeForm.dict_id" /></el-form-item>
        <el-form-item label="名称/取值"><el-input v-model="codeForm.dict_name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="codeForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="codeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCode">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="backupVisible" title="登记备份" width="440px">
      <el-form label-width="100px">
        <el-form-item label="备份类型">
          <el-select v-model="backupForm.backup_type">
            <el-option :value="1" label="手工备份" />
            <el-option :value="2" label="恢复前应急快照" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名"><el-input v-model="backupForm.file_name" placeholder="course_grade_20261124.sql" /></el-form-item>
        <el-form-item label="大小(KB)"><el-input-number v-model="backupForm.file_size_kb" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="backupForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="backupVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBackup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api/admin'
import { userState } from '../../stores/user'

const role = computed(() => userState.currentUser?.role)
const isAdmin = computed(() => role.value === 'SYS_ADMIN')
const canView = computed(() => role.value === 'SYS_ADMIN' || role.value === 'ACADEMIC_OFFICE')

const activeTab = ref('codes')
const codes = ref([])
const loadingCodes = ref(false)
const codeQuery = reactive({ group: '', keyword: '' })
const codeVisible = ref(false)
const codeForm = reactive({ dict_id: null, dict_group: '', dict_code: '', dict_name: '', remark: '' })

async function loadCodes() {
  loadingCodes.value = true
  try {
    const res = await adminApi.listCodes({
      group: codeQuery.group || undefined,
      keyword: codeQuery.keyword || undefined
    })
    codes.value = res.data || []
  } finally {
    loadingCodes.value = false
  }
}

function openCode(row) {
  Object.assign(codeForm, row || { dict_id: null, dict_group: '', dict_code: '', dict_name: '', remark: '' })
  codeVisible.value = true
}

async function saveCode() {
  if (codeForm.dict_id) {
    await adminApi.updateCode(codeForm.dict_id, { dict_name: codeForm.dict_name, remark: codeForm.remark })
  } else {
    await adminApi.createCode({ ...codeForm })
  }
  ElMessage.success('已保存')
  codeVisible.value = false
  await loadCodes()
}

async function toggleCode(row) {
  await adminApi.updateCode(row.dict_id, { is_enabled: row.is_enabled === 1 ? 0 : 1 })
  ElMessage.success('状态已更新')
  await loadCodes()
}

const backups = ref([])
const loadingBackups = ref(false)
const backupVisible = ref(false)
const backupForm = reactive({ backup_type: 1, file_name: '', file_size_kb: 1024, remark: '' })

async function loadBackups() {
  loadingBackups.value = true
  try {
    const res = await adminApi.listBackups()
    backups.value = res.data || []
  } finally {
    loadingBackups.value = false
  }
}

function openBackup() {
  backupVisible.value = true
}

async function saveBackup() {
  await adminApi.createBackup({ ...backupForm })
  ElMessage.success('已登记')
  backupVisible.value = false
  await loadBackups()
}

async function restore(row) {
  await ElMessageBox.confirm(`确认用 ${row.file_name} 恢复？系统会先写恢复前应急快照。`, '恢复确认', { type: 'warning' })
  const res = await adminApi.restore(row.backup_id)
  ElMessage.success(res.data.message)
  await loadBackups()
}

function initByRole() {
  if (!canView.value) return
  loadCodes()
  loadBackups()
}

watch(() => userState.currentUser?.userId, () => {
  initByRole()
})

onMounted(() => {
  initByRole()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
