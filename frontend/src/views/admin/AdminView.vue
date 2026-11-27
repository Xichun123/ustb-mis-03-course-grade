<template>
  <div>
    <el-alert v-if="!canView" title="当前账号无权访问系统维护模块。" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-tabs v-else v-model="activeTab" type="border-card">
      <!-- 6.1 运行参数与跨组开关 -->
      <el-tab-pane label="运行参数与跨组开关" name="params">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
          title="系统参数直接读写公共字典表（al_dict_item），支持实时切换财务欠费选课拦截开关、最低开班人数、平时成绩最低占比与默认学期。"
        />
        <div class="toolbar">
          <el-select v-model="codeGroup" style="width: 210px" @change="loadCodes">
            <el-option label="系统运行参数与跨组开关" value="system_param" />
            <el-option label="全部公共字典项" value="" />
          </el-select>
          <el-button type="primary" @click="loadCodes">刷新参数</el-button>
        </div>
        <el-table :data="codes" border stripe v-loading="loadingCodes">
          <el-table-column prop="dict_group" label="参数分组" width="160" />
          <el-table-column prop="dict_code" label="参数键" width="210" />
          <el-table-column label="当前值 / 开关" min-width="220">
            <template #default="{ row }">
              <template v-if="row.dict_group === 'system_param' && row.dict_code === 'block_selection_on_arrears'">
                <el-switch
                  v-if="isAdmin"
                  :model-value="row.dict_name === '1'"
                  active-text="欠费拦截选课"
                  inactive-text="欠费仅提醒"
                  @change="(val) => toggleArrearsBlock(row, val)"
                />
                <el-tag v-else :type="row.dict_name === '1' ? 'danger' : 'success'" size="small">
                  {{ row.dict_name === '1' ? '已开启欠费拦截选课' : '欠费仅温馨提醒（允许选课）' }}
                </el-tag>
              </template>
              <span v-else><b>{{ row.dict_name }}</b></span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="参数说明" min-width="220" />
          <el-table-column v-if="isAdmin" label="操作" width="110">
            <template #default="{ row }">
              <el-button
                v-if="!(row.dict_group === 'system_param' && row.dict_code === 'block_selection_on_arrears')"
                type="primary"
                link
                @click="editCode(row)"
              >修改参数</el-button>
              <span v-else class="muted">直接切换</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 6.2 备份与恢复 -->
      <el-tab-pane label="数据备份与恢复" name="backups">
        <div class="toolbar">
          <el-button type="primary" @click="loadBackups">刷新</el-button>
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
          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">{{ String(row.backup_time || '—').replace('T', ' ') }}</template>
          </el-table-column>
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

    <el-dialog v-model="backupVisible" title="登记备份" width="440px">
      <el-form label-width="100px">
        <el-form-item label="备份类型">
          <el-select v-model="backupForm.backup_type" style="width: 100%">
            <el-option :value="1" label="手工备份" />
            <el-option :value="2" label="恢复前应急快照" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名"><el-input v-model="backupForm.file_name" placeholder="course_grade_20261124.sql" /></el-form-item>
        <el-form-item label="大小(KB)"><el-input-number v-model="backupForm.file_size_kb" :min="0" controls-position="right" style="width: 100%" /></el-form-item>
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

const activeTab = ref('params')
const codeGroup = ref('system_param')
const codes = ref([])
const loadingCodes = ref(false)

const backups = ref([])
const loadingBackups = ref(false)
const backupVisible = ref(false)
const backupForm = reactive({ backup_type: 1, file_name: '', file_size_kb: 1024, remark: '' })

async function loadCodes() {
  loadingCodes.value = true
  try {
    const res = await adminApi.listCodes({ group: codeGroup.value || undefined })
    codes.value = res.data || []
  } finally {
    loadingCodes.value = false
  }
}

async function toggleArrearsBlock(row, enabled) {
  const nextVal = enabled ? '1' : '0'
  await adminApi.updateCode(row.dict_id, { dict_name: nextVal })
  row.dict_name = nextVal
  ElMessage.success(enabled ? '已开启：存在第 5 组财务欠费的学生将暂缓选课' : '已切换为：存在财务欠费时仅温馨提醒，允许选课')
}

async function editCode(row) {
  const { value } = await ElMessageBox.prompt(`请输入参数「${row.remark || row.dict_code}」的新值`, '修改系统参数', {
    inputValue: String(row.dict_name || ''),
    inputValidator: (v) => (v && v.trim() ? true : '参数值不能为空')
  })
  await adminApi.updateCode(row.dict_id, { dict_name: value.trim() })
  ElMessage.success('参数已更新')
  await loadCodes()
}

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
  await ElMessageBox.confirm(`确认使用 ${row.file_name} 恢复数据？系统将自动创建恢复前快照。`, '恢复确认', { type: 'warning' })
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
.muted {
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
