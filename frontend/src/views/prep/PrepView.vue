<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1: 1.1 开课计划导入 & 1.2 限选条件设置 -->
      <el-tab-pane label="1.1 / 1.2 开课计划与限选条件设置" name="classes">
        <div class="toolbar">
          <el-input v-model="classQuery.term" placeholder="学期如 2026-2027-1" style="width: 170px" clearable />
          <el-input v-model="classQuery.keyword" placeholder="教学班号 / 课程号 / 课程名" style="width: 240px" clearable />
          <el-select v-model="classQuery.tc_status" placeholder="教学班状态" clearable style="width: 140px">
            <el-option label="4 已发布" value="4" />
            <el-option label="2 不开课" value="2" />
            <el-option label="5 取消" value="5" />
          </el-select>
          <el-button type="primary" @click="loadClasses">查询</el-button>
        </div>

        <el-table :data="classList" border stripe v-loading="loadingClasses">
          <el-table-column prop="tc_id" label="教学班号" width="130" />
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="course_name" label="课程名称" min-width="150" />
          <el-table-column prop="credit" label="学分" width="70" />
          <el-table-column prop="open_college" label="开课学院" width="95" />
          <el-table-column prop="lesson_no" label="合班课堂号" width="130" />
          <el-table-column prop="class_ids" label="面向班级" min-width="130" />
          <el-table-column label="容量/已选" width="105">
            <template #default="{ row }">{{ row.actual_num ?? 0 }} / {{ row.capacity }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.tc_status === '4' ? 'success' : 'info'" size="small">
                {{ row.tc_status === '4' ? '已发布' : row.tc_status === '2' ? '不开课' : '其他' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="limit_count" label="限选条数" width="90" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" link @click="openLimitDialog(row.tc_id)">设置限选</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 2: 1.3 选课轮次设置 / 1.4 审批 / 1.5 发布 -->
      <el-tab-pane label="1.3 / 1.4 / 1.5 选课轮次、审批与发布" name="rounds">
        <div class="toolbar">
          <el-input v-model="roundQuery.term" placeholder="学年学期" style="width: 170px" clearable />
          <el-select v-model="roundQuery.round_status" placeholder="轮次状态" clearable style="width: 150px">
            <el-option v-for="(label, val) in ROUND_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
          <el-button type="primary" @click="loadRounds">查询</el-button>
          <el-button type="success" @click="openRoundDialog()">+ 新建轮次草稿</el-button>
        </div>

        <el-table :data="roundList" border stripe v-loading="loadingRounds">
          <el-table-column prop="round_id" label="编号" width="70" />
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="round_name" label="轮次名称" min-width="180" />
          <el-table-column label="类型/对象" width="130">
            <template #default="{ row }">
              {{ ROUND_TYPE_MAP[row.round_type] }} / {{ LEVEL_MAP[row.target_level] }}
            </template>
          </el-table-column>
          <el-table-column label="重修配置" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.allow_retake === 1" size="small" type="warning">
                开放重修{{ row.retake_ignore_conflict === 1 ? '·免听' : '' }}
              </el-tag>
              <span v-else style="color: #94a3b8">仅初修</span>
            </template>
          </el-table-column>
          <el-table-column prop="max_credit" label="学分上限" width="90" />
          <el-table-column prop="class_count" label="教学班数" width="85" />
          <el-table-column label="状态" width="115">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.round_status)" size="small">
                {{ ROUND_STATUS_MAP[row.round_status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openRoundDialog(row.round_id)">
                {{ row.round_status === 0 ? '编辑' : '查看' }}
              </el-button>
              <el-button v-if="row.round_status === 0" type="warning" link @click="handleSubmitRound(row.round_id)">
                提交审批
              </el-button>
              <el-button v-if="row.round_status === 1" type="success" link @click="openApproveDialog(row)">
                处长审批
              </el-button>
              <el-button v-if="row.round_status === 5" type="success" link @click="handlePublishRound(row.round_id)">
                发布通知
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 限选条件弹窗 -->
    <el-dialog v-model="limitVisible" :title="`限选条件设置 - ${limitDetail?.teaching_class?.tc_id || ''}`" width="640px">
      <el-alert
        v-if="limitDetail && !limitDetail.editable"
        :title="limitDetail.lock_reason"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />
      <div v-for="(item, idx) in editLimits" :key="idx" class="limit-row">
        <el-select v-model="item.limit_type" :disabled="!limitDetail?.editable" style="width: 160px">
          <el-option label="1 限年级" :value="1" />
          <el-option label="2 限专业" :value="2" />
          <el-option label="3 限学院" :value="3" />
          <el-option label="4 限培养层次" :value="4" />
        </el-select>
        <el-input v-model="item.limit_value" :disabled="!limitDetail?.editable" placeholder="如 2024 / 120102 / 0001 / 本科" />
        <el-button v-if="limitDetail?.editable" type="danger" link @click="editLimits.splice(idx, 1)">删除</el-button>
      </div>
      <el-button v-if="limitDetail?.editable" @click="editLimits.push({ limit_type: 1, limit_value: '' })">
        + 添加一条限选条件
      </el-button>
      <template #footer>
        <el-button @click="limitVisible = false">关闭</el-button>
        <el-button v-if="limitDetail?.editable" type="primary" @click="saveLimits">保存限选</el-button>
      </template>
    </el-dialog>

    <!-- 轮次新建/编辑弹窗 -->
    <el-dialog v-model="roundDialogVisible" :title="roundForm.round_id ? `轮次 #${roundForm.round_id}` : '新建选课轮次草稿'" width="680px">
      <el-form :model="roundForm" label-width="120px" :disabled="roundReadOnly">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="学年学期"><el-input v-model="roundForm.term" placeholder="2026-2027-1" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮次名称"><el-input v-model="roundForm.round_name" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="轮次类型">
              <el-select v-model="roundForm.round_type">
                <el-option label="1 预选" :value="1" />
                <el-option label="2 正选" :value="2" />
                <el-option label="3 补退选" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用学生">
              <el-select v-model="roundForm.target_level">
                <el-option label="1 本科生" :value="1" />
                <el-option label="2 研究生" :value="2" />
                <el-option label="3 全部" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="选课方式">
              <el-select v-model="roundForm.select_mode">
                <el-option label="1 先到先得" :value="1" />
                <el-option label="2 超额筛选" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间"><el-input v-model="roundForm.start_time" placeholder="2026-12-01 09:00:00" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间"><el-input v-model="roundForm.end_time" placeholder="2026-12-05 17:00:00" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="学分上限"><el-input-number v-model="roundForm.max_credit" :min="0.5" :max="60" :step="0.5" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开放重修">
              <el-switch v-model="roundForm.allow_retake" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重修冲突免听">
              <el-switch
                v-model="roundForm.retake_ignore_conflict"
                :active-value="1"
                :inactive-value="0"
                :disabled="roundForm.allow_retake !== 1"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="开放教学班号">
          <el-select v-model="roundForm.tc_ids" multiple filterable style="width: 100%" placeholder="选择已发布教学班">
            <el-option
              v-for="tc in publishedClassOptions"
              :key="tc.tc_id"
              :label="`${tc.tc_id} · ${tc.course_name} (${tc.term})`"
              :value="tc.tc_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选课通知内容">
          <el-input v-model="roundForm.notice_content" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roundDialogVisible = false">关闭</el-button>
        <el-button v-if="!roundReadOnly" type="primary" @click="saveRound">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 1.4 教务处处长审批弹窗 -->
    <el-dialog v-model="approveVisible" title="1.4 选课方案审批（教务处处长）" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审批决定">
          <el-radio-group v-model="approveForm.decision">
            <el-radio :value="1">通过（进入待发布）</el-radio>
            <el-radio :value="2">退回草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.approve_opinion" type="textarea" :rows="3" placeholder="退回时必填原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmApprove">确认提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { prepApi } from '../../api/prep'

const ROUND_TYPE_MAP = { 1: '预选', 2: '正选', 3: '补退选' }
const LEVEL_MAP = { 1: '本科生', 2: '研究生', 3: '全部' }
const ROUND_STATUS_MAP = { 0: '0 草稿', 1: '1 待审批', 2: '2 已发布', 3: '3 进行中', 4: '4 已结束', 5: '5 已审批待发布' }

const activeTab = ref('classes')
const loadingClasses = ref(false)
const classList = ref([])
const publishedClassOptions = ref([])
const classQuery = reactive({ term: '2026-2027-1', keyword: '', tc_status: '' })

const loadingRounds = ref(false)
const roundList = ref([])
const roundQuery = reactive({ term: '', round_status: null })

const limitVisible = ref(false)
const limitDetail = ref(null)
const editLimits = ref([])

const roundDialogVisible = ref(false)
const roundReadOnly = ref(false)
const roundForm = reactive({
  round_id: null, term: '2026-2027-1', round_name: '', round_type: 2, target_level: 1,
  start_time: '2026-12-01 09:00:00', end_time: '2026-12-05 17:00:00', max_credit: 30,
  select_mode: 1, allow_retake: 0, retake_ignore_conflict: 0, notice_content: '', tc_ids: []
})

const approveVisible = ref(false)
const approveForm = reactive({ round_id: null, decision: 1, approve_opinion: '' })

function statusTagType(st) {
  return ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: '', 5: 'success' })[st] || 'info'
}

async function loadClasses() {
  loadingClasses.value = true
  try {
    const res = await prepApi.listTeachingClasses(classQuery)
    classList.value = res.data?.items || []
    publishedClassOptions.value = classList.value.filter(i => i.tc_status === '4')
  } finally {
    loadingClasses.value = false
  }
}

async function loadRounds() {
  loadingRounds.value = true
  try {
    const res = await prepApi.listRounds(roundQuery)
    roundList.value = res.data?.items || []
  } finally {
    loadingRounds.value = false
  }
}

async function openLimitDialog(tcId) {
  const res = await prepApi.getLimits(tcId)
  limitDetail.value = res.data
  editLimits.value = (res.data.limits || []).map(l => ({ limit_type: l.limit_type, limit_value: l.limit_value }))
  limitVisible.value = true
}

async function saveLimits() {
  await prepApi.replaceLimits(limitDetail.value.teaching_class.tc_id, editLimits.value)
  ElMessage.success('限选条件已保存')
  limitVisible.value = false
  loadClasses()
}

async function openRoundDialog(roundId) {
  if (!roundId) {
    Object.assign(roundForm, {
      round_id: null, term: '2026-2027-1', round_name: '', round_type: 2, target_level: 1,
      start_time: '2026-12-01 09:00:00', end_time: '2026-12-05 17:00:00', max_credit: 30,
      select_mode: 1, allow_retake: 0, retake_ignore_conflict: 0, notice_content: '', tc_ids: []
    })
    roundReadOnly.value = false
    roundDialogVisible.value = true
    return
  }
  const res = await prepApi.getRound(roundId)
  const d = res.data
  Object.assign(roundForm, {
    round_id: d.round_id, term: d.term, round_name: d.round_name, round_type: d.round_type,
    target_level: d.target_level, start_time: String(d.start_time).replace('T', ' '),
    end_time: String(d.end_time).replace('T', ' '), max_credit: Number(d.max_credit),
    select_mode: d.select_mode, allow_retake: d.allow_retake,
    retake_ignore_conflict: d.retake_ignore_conflict, notice_content: d.notice_content || '',
    tc_ids: (d.classes || []).map(c => c.tc_id)
  })
  roundReadOnly.value = d.round_status !== 0
  roundDialogVisible.value = true
}

async function saveRound() {
  if (roundForm.round_id) {
    await prepApi.updateRound(roundForm.round_id, roundForm)
    ElMessage.success('轮次草稿已更新')
  } else {
    await prepApi.createRound(roundForm)
    ElMessage.success('轮次草稿已创建')
  }
  roundDialogVisible.value = false
  loadRounds()
}

async function handleSubmitRound(roundId) {
  await ElMessageBox.confirm('确认提交教务处处长审批？提交后将锁定关联教学班的限选条件。', '提交审批')
  await prepApi.submitRound(roundId)
  ElMessage.success('已提交审批')
  loadRounds()
}

function openApproveDialog(row) {
  approveForm.round_id = row.round_id
  approveForm.decision = 1
  approveForm.approve_opinion = ''
  approveVisible.value = true
}

async function confirmApprove() {
  await prepApi.decideRound(approveForm.round_id, {
    decision: approveForm.decision,
    approve_opinion: approveForm.approve_opinion
  })
  ElMessage.success(approveForm.decision === 1 ? '审批通过' : '已退回草稿')
  approveVisible.value = false
  loadRounds()
}

async function handlePublishRound(roundId) {
  await ElMessageBox.confirm('确认发布该选课轮次并向学生公开通知？', '发布确认')
  await prepApi.publishRound(roundId)
  ElMessage.success('选课通知已发布')
  loadRounds()
}

onMounted(() => {
  loadClasses()
  loadRounds()
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.limit-row { display: flex; gap: 8px; margin-bottom: 10px; align-items: center; }
</style>
