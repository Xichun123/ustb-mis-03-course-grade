<template>
  <div>
    <el-alert
      v-if="isStaff"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 14px"
      title="成绩更正须重新提交纸质成绩登记表并重走发布流程：教师发起申请 → 学院批准并解锁该生原成绩 → 教师前往「成绩录入与审核」修改并重提生成新 6 位核对码、打印纸质单交学院 → 学院输码核对 → 教务处重新发布后自动生效并通知学生。"
    />
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 5.1 教师发起更正申请 -->
      <el-tab-pane v-if="isTeacher" label="发起成绩更正" name="candidates">
        <div class="toolbar">
          <el-select v-model="candQuery.term" placeholder="全部学期" clearable style="width: 165px">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
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
                {{ row.pending_count > 0 ? '办理中' : '发起更正' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 5.2 更正审批与重录发布进度跟踪 -->
      <el-tab-pane v-if="isStaff" :label="isTeacher ? '我的申请记录' : '更正审批与跟踪'" name="list">
        <div class="toolbar">
          <el-select v-model="listQuery.apply_status" placeholder="申请状态" clearable style="width: 170px">
            <el-option label="待学院审批" :value="0" />
            <el-option label="待重录重发" :value="1" />
            <el-option label="已重发生效" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
          <el-button type="primary" @click="loadList">刷新</el-button>
        </div>
        <el-table :data="corrections" border stripe v-loading="loadingList">
          <el-table-column prop="apply_id" label="申请号" width="78" />
          <el-table-column prop="lesson_no" label="课堂号" width="128" />
          <el-table-column prop="course_name" label="课程" min-width="130" />
          <el-table-column label="学生" width="150">
            <template #default="{ row }">{{ row.student_name }} ({{ row.student_id }})</template>
          </el-table-column>
          <el-table-column label="原值 → 拟/终值" width="130">
            <template #default="{ row }">
              <span v-if="row.score_type === 1">{{ row.old_score }} → <b>{{ row.new_score }}</b></span>
              <span v-else>{{ row.old_level }} → <b>{{ row.new_level }}</b></span>
            </template>
          </el-table-column>
          <el-table-column prop="apply_reason" label="更正原因" min-width="165" show-overflow-tooltip />
          <el-table-column label="状态与流程进度" min-width="185">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.apply_status)" size="small">
                {{ STATUS_LABEL[row.apply_status] || row.apply_status }}
              </el-tag>
              <div v-if="row.apply_status === 1" class="stage-hint">
                {{ resubmitStageText(row.current_score_status) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="145" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.apply_status === 0 && canCollegeAudit"
                type="primary"
                link
                @click="openAuditDialog(row)"
              >
                学院审批
              </el-button>
              <el-button
                v-else-if="row.apply_status === 1 && (isTeacher || isAdmin) && [0, 4].includes(row.current_score_status)"
                type="success"
                link
                @click="goScore('entry', row.lesson_no)"
              >
                前往重录成绩
              </el-button>
              <el-button
                v-else-if="row.apply_status === 1 && canCollegeAudit && row.current_score_status === 1"
                type="primary"
                link
                @click="goScore('audit', row.lesson_no)"
              >
                前往核对纸质单
              </el-button>
              <el-button
                v-else-if="row.apply_status === 1 && (isOffice || isAdmin) && row.current_score_status === 2"
                type="success"
                link
                @click="goScore('publish', row.lesson_no)"
              >
                前往重新发布
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 5.4 学生查看本人已通过更正通知 -->
      <el-tab-pane v-if="isStudent" label="成绩更正通知" name="my">
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
          <el-input-number v-model="applyForm.new_score" :min="0" :max="100" :precision="1" :step="0.5" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="更正后等级">
          <el-select v-model="applyForm.new_level" style="width: 100%">
            <el-option
              v-for="lv in (applyTarget?.score_type === 2 ? ['优','良','中','及格','不及格'] : ['合格','不合格'])"
              :key="lv"
              :label="lv"
              :value="lv"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="更正原因">
          <el-input v-model="applyForm.apply_reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请说明更正原因（必填）" />
        </el-form-item>
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="学院批准后将仅解锁该生原成绩，您需在「成绩录入与审核」中修改并重提生成新核对码，重新打印纸质成绩登记表交学院核对。"
        />
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 5.2 学院审批弹窗 -->
    <el-dialog v-model="auditVisible" title="学院审批" width="520px">
      <el-form label-width="90px">
        <el-form-item label="审批决定">
          <el-radio-group v-model="auditForm.decision">
            <el-radio :value="1">通过（解锁该生原成绩，退回重录并重交纸质单）</el-radio>
            <el-radio :value="2">驳回（维持原已发布成绩不变）</el-radio>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { correctionApi } from '../../api/correction'
import { userState } from '../../stores/user'
import { TERM_OPTIONS } from '../../constants/terms'

const router = useRouter()
const STATUS_LABEL = { 0: '待学院审批', 1: '待重录重发', 2: '已重发生效', 3: '已驳回' }
const role = computed(() => userState.currentUser?.role)
const isStudent = computed(() => role.value === 'STUDENT')
const isStaff = computed(() => role.value && role.value !== 'STUDENT')
const isTeacher = computed(() => role.value === 'TEACHER')
const isOffice = computed(() => ['ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR'].includes(role.value))
const isAdmin = computed(() => role.value === 'SYS_ADMIN')
const canCollegeAudit = computed(() => ['COLLEGE_SECRETARY', 'SYS_ADMIN'].includes(role.value))

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
const auditApplyId = ref(null)
const auditForm = reactive({ decision: 1, opinion: '' })

function statusTag(s) {
  return ({ 0: 'warning', 1: 'primary', 2: 'success', 3: 'danger' })[s] || 'info'
}

function resubmitStageText(scoreStatus) {
  if (scoreStatus === 0 || scoreStatus === 4) return '待教师重录并交纸质单'
  if (scoreStatus === 1) return '已重提，待学院核对纸质单'
  if (scoreStatus === 2) return '学院已核对，待教务处重发'
  return '重录与发布流程处理中'
}

function goScore(tab, lessonNo) {
  router.push({ path: '/score', query: { tab, lesson_no: lessonNo } })
}

async function loadCandidates() {
  if (!isTeacher.value) return
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

function openAuditDialog(row) {
  auditApplyId.value = row.apply_id
  auditForm.decision = 1
  auditForm.opinion = ''
  auditVisible.value = true
}

async function confirmAudit() {
  const res = await correctionApi.collegeDecision(auditApplyId.value, {
    decision: auditForm.decision,
    college_audit_opinion: auditForm.opinion
  })
  ElMessage.success(res.message || (auditForm.decision === 1
    ? '学院已批准更正并解锁该生成绩，等待教师重录并重走发布流程'
    : '已驳回申请'))
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
    activeTab.value = isTeacher.value ? 'candidates' : 'list'
    loadCandidates()
    loadList()
  }
}, { immediate: true })
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.stage-hint { font-size: 12px; color: var(--app-text-secondary); margin-top: 4px; }
.muted { color: var(--app-text-secondary); font-size: 12.5px; }
</style>
