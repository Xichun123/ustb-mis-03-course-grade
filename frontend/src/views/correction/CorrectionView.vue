<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 5.1 教师发起更正申请 -->
      <el-tab-pane v-if="isTeacherOrOffice" label="发起成绩更正" name="candidates">
        <div class="toolbar">
          <el-input v-model="candQuery.term" placeholder="学期" style="width: 160px" clearable />
          <el-input v-model="candQuery.keyword" placeholder="学号 / 姓名 / 课程 / 课堂号" style="width: 240px" clearable />
          <el-button type="primary" @click="loadCandidates">检索</el-button>
        </div>
        <el-table :data="candidates" border stripe v-loading="loadingCand">
          <el-table-column prop="lesson_no" label="课堂号" width="130" />
          <el-table-column prop="course_name" label="课程名称" min-width="140" />
          <el-table-column prop="student_id" label="学号" width="130" />
          <el-table-column prop="student_name" label="姓名" width="90" />
          <el-table-column label="当前成绩" width="110">
            <template #default="{ row }">
              {{ row.score_type === 1 ? row.total_score : row.score_level }}
            </template>
          </el-table-column>
          <el-table-column prop="grade_point" label="绩点" width="80" />
          <el-table-column label="发布时间" width="170">
            <template #default="{ row }">{{ String(row.publish_time || '—').replace('T', ' ') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button type="primary" link :disabled="row.pending_count > 0" @click="openApplyDialog(row)">
                {{ row.pending_count > 0 ? '审批中' : '发起更正' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 5.2 / 5.3 更正申请台账与两级审批 -->
      <el-tab-pane v-if="isStaff" label="更正申请与两级审批" name="list">
        <div class="toolbar">
          <el-select v-model="listQuery.apply_status" placeholder="申请状态" clearable style="width: 160px">
            <el-option label="待学院审核" :value="0" />
            <el-option label="待教务处审批" :value="1" />
            <el-option label="已通过生效" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
          <el-button type="primary" @click="loadList">刷新</el-button>
        </div>
        <el-table :data="corrections" border stripe v-loading="loadingList">
          <el-table-column prop="apply_id" label="申请号" width="80" />
          <el-table-column prop="course_name" label="课程" min-width="130" />
          <el-table-column label="学生" width="150">
            <template #default="{ row }">{{ row.student_name }} ({{ row.student_id }})</template>
          </el-table-column>
          <el-table-column label="原值 → 新值" width="130">
            <template #default="{ row }">
              <span v-if="row.score_type === 1">{{ row.old_score }} → <b>{{ row.new_score }}</b></span>
              <span v-else>{{ row.old_level }} → <b>{{ row.new_level }}</b></span>
            </template>
          </el-table-column>
          <el-table-column prop="apply_reason" label="更正原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.apply_status)" size="small">
                {{ STATUS_LABEL[row.apply_status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.apply_status === 0 && canCollegeAudit"
                type="warning"
                link
                @click="openAuditDialog(row, 'college')"
              >
                学院审核
              </el-button>
              <el-button
                v-if="row.apply_status === 1 && canOfficeAudit"
                type="success"
                link
                @click="openAuditDialog(row, 'office')"
              >
                教务处终审
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 5.4 学生查看本人已通过更正通知 -->
      <el-tab-pane v-if="isStudent" label="我的成绩更正通知" name="my">
        <el-table :data="myResults" border stripe v-loading="loadingMy">
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="course_name" label="课程名称" min-width="150" />
          <el-table-column label="成绩变更" width="140">
            <template #default="{ row }">
              <span v-if="row.score_type === 1">{{ row.old_score }} → <b>{{ row.new_score }}</b></span>
              <span v-else>{{ row.old_level }} → <b>{{ row.new_level }}</b></span>
            </template>
          </el-table-column>
          <el-table-column prop="new_grade_point" label="更新后绩点" width="110" />
          <el-table-column prop="apply_reason" label="更正原因" min-width="200" />
          <el-table-column label="生效时间" width="170">
            <template #default="{ row }">{{ String(row.audit_time || '—').replace('T', ' ') }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 5.1 发起更正弹窗 -->
    <el-dialog v-model="applyVisible" title="提交成绩更正申请" width="500px">
      <el-form label-width="100px">
        <el-form-item label="学生/课程">
          <span>{{ applyTarget?.student_name }}（{{ applyTarget?.student_id }}） · {{ applyTarget?.course_name }}</span>
        </el-form-item>
        <el-form-item label="当前原成绩">
          <b>{{ applyTarget?.score_type === 1 ? applyTarget?.total_score : applyTarget?.score_level }}</b>
        </el-form-item>
        <el-form-item v-if="applyTarget?.score_type === 1" label="更正后分数">
          <el-input-number v-model="applyForm.new_score" :min="0" :max="100" :precision="1" :step="0.5" />
        </el-form-item>
        <el-form-item v-else label="更正后等级">
          <el-select v-model="applyForm.new_level">
            <el-option
              v-for="lv in (applyTarget?.score_type === 2 ? ['优','良','中','及格','不及格'] : ['合格','不合格'])"
              :key="lv"
              :label="lv"
              :value="lv"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="更正原因">
          <el-input v-model="applyForm.apply_reason" type="textarea" :rows="3" placeholder="请说明复核依据（必填，≤200字）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 5.2 / 5.3 审核弹窗 -->
    <el-dialog v-model="auditVisible" :title="auditStage === 'college' ? '学院审核' : '教务处终审（通过后立即生效）'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审批决定">
          <el-radio-group v-model="auditForm.decision">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="3" placeholder="驳回时必填意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { correctionApi } from '../../api/correction'
import { userState } from '../../stores/user'

const STATUS_LABEL = { 0: '待学院审核', 1: '待教务处审批', 2: '已通过生效', 3: '已驳回' }
const role = computed(() => userState.currentUser?.role)
const isStudent = computed(() => role.value === 'STUDENT')
const isStaff = computed(() => role.value && role.value !== 'STUDENT')
const isTeacherOrOffice = computed(() => ['TEACHER', 'ACADEMIC_OFFICE', 'SYS_ADMIN'].includes(role.value))
const canCollegeAudit = computed(() => ['COLLEGE_SECRETARY', 'SYS_ADMIN'].includes(role.value))
const canOfficeAudit = computed(() => ['ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR', 'GRADUATE_SCHOOL', 'SYS_ADMIN'].includes(role.value))

const activeTab = ref('list')
const candQuery = reactive({ term: '2026-2027-1', keyword: '' })
const listQuery = reactive({ apply_status: null })
const candidates = ref([])
const corrections = ref([])
const myResults = ref([])
const loadingCand = ref(false)
const loadingList = ref(false)
const loadingMy = ref(false)

const applyVisible = ref(false)
const applyTarget = ref(null)
const applyForm = reactive({ new_score: 85, new_level: '优', apply_reason: '' })

const auditVisible = ref(false)
const auditStage = ref('college')
const auditApplyId = ref(null)
const auditForm = reactive({ decision: 1, opinion: '' })

function statusTag(s) {
  return ({ 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' })[s] || 'info'
}

async function loadCandidates() {
  if (!isTeacherOrOffice.value) return
  loadingCand.value = true
  try {
    const res = await correctionApi.listCandidates(candQuery)
    candidates.value = res.data || []
  } finally {
    loadingCand.value = false
  }
}

async function loadList() {
  if (!isStaff.value) return
  loadingList.value = true
  try {
    const res = await correctionApi.list(listQuery)
    corrections.value = res.data || []
  } finally {
    loadingList.value = false
  }
}

async function loadMy() {
  if (!isStudent.value) return
  loadingMy.value = true
  try {
    const res = await correctionApi.myResults()
    myResults.value = res.data || []
  } finally {
    loadingMy.value = false
  }
}

function openApplyDialog(row) {
  applyTarget.value = row
  applyForm.new_score = Number(row.total_score || 80)
  applyForm.new_level = row.score_level || '良'
  applyForm.apply_reason = ''
  applyVisible.value = true
}

async function submitApply() {
  const t = applyTarget.value
  const payload = {
    score_id: t.score_id,
    apply_reason: applyForm.apply_reason,
    ...(t.score_type === 1
      ? { new_score: applyForm.new_score, expected_score: t.total_score }
      : { new_level: applyForm.new_level, expected_level: t.score_level })
  }
  await correctionApi.submit(payload)
  ElMessage.success('更正申请已提交学院审核')
  applyVisible.value = false
  loadCandidates()
  loadList()
}

function openAuditDialog(row, stage) {
  auditApplyId.value = row.apply_id
  auditStage.value = stage
  auditForm.decision = 1
  auditForm.opinion = ''
  auditVisible.value = true
}

async function confirmAudit() {
  if (auditStage.value === 'college') {
    await correctionApi.collegeDecision(auditApplyId.value, {
      decision: auditForm.decision,
      college_audit_opinion: auditForm.opinion
    })
  } else {
    await correctionApi.officeDecision(auditApplyId.value, {
      decision: auditForm.decision,
      audit_opinion: auditForm.opinion
    })
  }
  ElMessage.success('审批已提交')
  auditVisible.value = false
  loadList()
}

let initializedRole = null
watch(role, (val) => {
  if (!val || val === initializedRole) return
  initializedRole = val
  if (isStudent.value) {
    activeTab.value = 'my'
    loadMy()
  } else {
    activeTab.value = isTeacherOrOffice.value ? 'candidates' : 'list'
    loadCandidates()
    loadList()
  }
}, { immediate: true })
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
</style>
