<template>
  <div>
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane v-if="canViewClasses" :label="isTeacher ? '我的开课计划' : '开课计划与限选设置'" name="classes">
        <div v-if="syncSummary" class="sync-banner">
          <div class="sync-banner-left">
            <span class="sync-banner-title">开课与排课概况（{{ classQuery.term || '全部学期' }}）</span>
            <el-tag size="small" type="info">总教学班 {{ syncSummary.total_classes }}</el-tag>
            <el-tag size="small" type="success">已发布且已排课 {{ syncSummary.scheduled_classes }}</el-tag>
            <el-tag v-if="syncSummary.unscheduled_published_classes > 0" size="small" type="warning">
              已发布但排课待确认 {{ syncSummary.unscheduled_published_classes }}
            </el-tag>
            <el-tag v-if="syncSummary.stopped_classes > 0" size="small" type="danger">
              已停开/取消 {{ syncSummary.stopped_classes }}
            </el-tag>
          </div>
          <span class="sync-banner-note">课程和排课来自第 2 组，任课教师来自第 4 组。</span>
        </div>

        <div class="toolbar">
          <el-select v-model="classQuery.term" placeholder="全部学期" clearable style="width: 170px" @change="refreshClasses">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="classQuery.keyword" placeholder="教学班号 / 课程名 / 任课教师" style="width: 240px"
            clearable @keyup.enter="refreshClasses" @clear="refreshClasses" />
          <el-select v-model="classQuery.tc_status" placeholder="教学班状态" clearable style="width: 130px">
            <el-option label="已发布" value="4" />
            <el-option label="不开课" value="2" />
            <el-option label="已取消" value="5" />
          </el-select>
          <el-select v-model="classQuery.scheduled" placeholder="排课状态" clearable style="width: 130px">
            <el-option label="已排课" :value="true" />
            <el-option label="排课待确认" :value="false" />
          </el-select>
          <el-button type="primary" @click="refreshClasses">查询</el-button>
        </div>

        <el-table :data="classList" border stripe v-loading="loadingClasses">
          <el-table-column label="教学班" min-width="140">
            <template #default="{ row }">
              <div>{{ row.tc_id }}</div>
              <div v-if="row.class_name" class="cell-sub">{{ row.class_name }}</div>
              <div class="cell-sub">{{ row.term }}</div>
            </template>
          </el-table-column>
          <el-table-column label="课程名称" min-width="165">
            <template #default="{ row }">
              <div>{{ row.course_name }}</div>
              <div class="cell-sub">{{ row.course_type_label }} · {{ row.credit }} 学分 · {{ Number(row.hours) }} 学时</div>
              <div v-if="classFormatLabel(row)" class="cell-sub">{{ classFormatLabel(row) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="任课教师 / 开课学院" min-width="135">
            <template #default="{ row }">
              <div>{{ row.teacher_name || '待定' }}</div>
              <div class="cell-sub">{{ row.open_college_name || getCollegeName(row.open_college) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="排课安排（时间 / 教室）" min-width="210">
            <template #default="{ row }">
              <span v-if="row.has_schedule">{{ row.schedule_summary }}</span>
              <el-tag v-else-if="row.tc_status === '4'" size="small" type="warning">排课待确认</el-tag>
              <span v-else>—</span>
              <div v-if="row.has_schedule && row.week_count" class="cell-sub">计划 {{ row.week_count }} 周</div>
            </template>
          </el-table-column>
          <el-table-column label="面向班级 / 课堂号" min-width="130">
            <template #default="{ row }">
              <div>{{ row.class_ids || '—' }}</div>
              <div v-if="row.lesson_no" class="cell-sub">{{ row.lesson_no }}</div>
            </template>
          </el-table-column>
          <el-table-column label="已选 / 容量" width="105">
            <template #default="{ row }">
              <div>{{ row.actual_num ?? 0 }} / {{ row.capacity }}</div>
              <div v-if="row.open_lower != null" class="cell-sub">开课下限 {{ row.open_lower }} 人</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.tc_status === '4' ? 'success' : 'info'" size="small">
                {{ row.tc_status === '4' ? '已发布' : row.tc_status === '2' ? '不开课' : row.tc_status === '5' ? '已取消' : '其他' }}
              </el-tag>
              <div v-if="row.no_open_reason && row.tc_status !== '4'" class="cell-sub">{{ row.no_open_reason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="限选条件" min-width="105" fixed="right">
            <template #default="{ row }">
              <div>{{ row.limit_count || 0 }} 条</div>
              <el-tag v-if="row.special_requirement" size="small" type="warning">含特殊要求</el-tag>
              <el-button v-if="!isTeacher" type="primary" link class="limit-action" @click="openLimitDialog(row.tc_id)">
                {{ canEditLimitRole ? '设置限选' : '查看限选' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="classTotal > classPageSize"
          :current-page="classPage"
          class="class-pagination"
          layout="total, prev, pager, next"
          :page-size="classPageSize"
          :total="classTotal"
          @update:current-page="changeClassPage"
        />
      </el-tab-pane>

      <!-- Tab 2: 选课轮次设置、审批与发布 -->
      <el-tab-pane :label="isStudent ? '选课轮次与通知公告' : '选课轮次、审批与发布'" name="rounds">
        <div class="toolbar">
          <el-select v-model="roundQuery.term" placeholder="全部学年学期" clearable style="width: 170px">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-if="!isStudent" v-model="roundQuery.round_status" placeholder="轮次状态" clearable style="width: 150px">
            <el-option v-for="(label, val) in ROUND_STATUS_MAP" :key="val" :label="label" :value="Number(val)" />
          </el-select>
          <el-button type="primary" @click="loadRounds">查询</el-button>
          <el-button v-if="canManageRound" type="success" @click="openRoundDialog()">+ 新建轮次草稿</el-button>
        </div>

        <el-table :data="roundList" border stripe v-loading="loadingRounds">
          <el-table-column v-if="!isStudent" prop="round_id" label="编号" width="60" />
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="round_name" label="轮次名称" min-width="175" />
          <el-table-column label="类型/对象" width="120">
            <template #default="{ row }">
              {{ ROUND_TYPE_MAP[row.round_type] }} / {{ LEVEL_MAP[row.target_level] }}
            </template>
          </el-table-column>
          <el-table-column label="重修政策" width="125">
            <template #default="{ row }">
              <el-tag v-if="row.allow_retake === 1" size="small" type="warning">
                开放重修{{ row.retake_ignore_conflict === 1 ? '·免听' : '' }}
              </el-tag>
              <span v-else style="color: var(--app-text-tertiary)">仅初修</span>
            </template>
          </el-table-column>
          <el-table-column prop="max_credit" label="学分上限" width="85" />
          <el-table-column v-if="!isStudent" prop="class_count" label="教学班数" width="85" />
          <el-table-column label="状态" width="125">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.round_status)" size="small">
                {{ ROUND_STATUS_MAP[row.round_status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="canManageRound || canApproveRound ? 165 : 100" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openRoundDialog(row.round_id)">
                {{ row.round_status === 0 && canManageRound ? '编辑' : '查看通知' }}
              </el-button>
              <el-button v-if="row.round_status === 0 && canManageRound" type="warning" link @click="handleSubmitRound(row.round_id)">
                提交审批
              </el-button>
              <el-button v-if="row.round_status === 1 && canApproveRound" type="success" link @click="openApproveDialog(row)">
                处长审批
              </el-button>
              <el-button v-if="row.round_status === 5 && canManageRound" type="success" link @click="handlePublishRound(row.round_id)">
                发布通知
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 限选条件弹窗 -->
    <el-dialog
      v-model="limitVisible"
      :title="`${limitEditable ? '限选条件设置' : '限选条件详情'} - ${limitDetail?.teaching_class?.tc_id || ''}`"
      width="640px"
    >
      <el-alert
        v-if="canEditLimitRole && limitDetail && !limitDetail.editable && limitDetail.lock_reason"
        :title="limitDetail.lock_reason"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-if="limitDetail?.pre_courses?.length"
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
        :title="`校内先修课程要求：${limitDetail.pre_courses.join('、')}（学生须先修读并通过，系统选课时自动校验）`"
      />
      <template v-if="limitEditable">
        <div v-for="(item, idx) in editLimits" :key="idx" class="limit-row">
          <el-select v-model="item.limit_type" style="width: 160px">
            <el-option label="限年级" :value="1" />
            <el-option label="限专业" :value="2" />
            <el-option label="限学院" :value="3" />
            <el-option label="限培养层次" :value="4" />
          </el-select>
          <el-input v-model="item.limit_value" placeholder="如 2024 / 120102 / 0001 / 本科" />
          <el-button type="danger" link @click="editLimits.splice(idx, 1)">删除</el-button>
        </div>
        <el-button @click="editLimits.push({ limit_type: 1, limit_value: '' })">
          + 添加一条限选条件
        </el-button>
        <div style="margin-top: 16px">
          <div class="special-req-label">特殊要求说明（如大学英语四六级成绩等，选填）</div>
          <el-input
            v-model="editSpecialRequirement"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="例如：大学英语六级成绩 >= 425 分，或四级成绩 >= 500 分（留空表示无特殊要求）"
          />
          <div class="special-req-hint">说明：设置后将在学生选课界面公示，学生须勾选确认本人符合要求方可提交，开课后由学院或任课教师核验。</div>
        </div>
      </template>
      <template v-else>
        <div v-if="editLimits.length" class="limit-readonly-list">
          <div v-for="(item, idx) in editLimits" :key="idx" class="limit-readonly-item">
            <el-tag size="small" type="info" effect="light">{{ LIMIT_TYPE_MAP[item.limit_type] || '限选' }}</el-tag>
            <span class="limit-readonly-val">{{ item.limit_value }}</span>
          </div>
        </div>
        <div v-else class="round-classes-empty">暂无限选条件</div>
        <div v-if="editSpecialRequirement" class="limit-readonly-item" style="margin-top: 10px">
          <el-tag size="small" type="warning" effect="light">特殊要求</el-tag>
          <span class="limit-readonly-val">{{ editSpecialRequirement }}</span>
        </div>
      </template>
      <template #footer>
        <el-button @click="limitVisible = false">关闭</el-button>
        <el-button v-if="limitEditable" type="primary" @click="saveLimits">保存限选</el-button>
      </template>
    </el-dialog>

    <!-- 轮次详情 / 新建编辑弹窗 -->
    <el-dialog
      v-model="roundDialogVisible"
      width="740px"
      class="round-dialog"
      :align-center="true"
    >
      <template #header>
        <div v-if="roundReadOnly && roundDetail" class="round-dialog-header">
          <div class="round-dialog-title-wrap">
            <span class="round-dialog-title">{{ roundDetail.round_name || '选课通知详情' }}</span>
            <el-tag :type="statusTagType(roundDetail.round_status)" size="small" effect="light">
              {{ ROUND_STATUS_MAP[roundDetail.round_status] }}
            </el-tag>
          </div>
          <span class="round-term-badge">{{ roundDetail.term }}</span>
        </div>
        <span v-else class="round-dialog-title">
          {{ roundForm.round_id ? '编辑选课轮次草稿' : '新建选课轮次草稿' }}
        </span>
      </template>

      <!-- 模式 A：只读通知详情视图 -->
      <div v-if="roundReadOnly && roundDetail" class="round-detail-view">
        <div class="notice-callout">
          <div class="notice-callout-title">
            <el-icon class="notice-callout-icon"><InfoFilled /></el-icon>
            <span>选课通知</span>
          </div>
          <div class="notice-callout-body">{{ roundDetail.notice_content || '暂无通知说明' }}</div>
        </div>

        <div class="round-meta-grid">
          <div class="round-meta-item">
            <span class="round-meta-label">学年学期</span>
            <span class="round-meta-val">{{ roundDetail.term || '—' }}</span>
          </div>
          <div class="round-meta-item">
            <span class="round-meta-label">轮次类型</span>
            <span class="round-meta-val">{{ ROUND_TYPE_MAP[roundDetail.round_type] || '—' }}</span>
          </div>
          <div class="round-meta-item">
            <span class="round-meta-label">适用学生</span>
            <span class="round-meta-val">{{ LEVEL_MAP[roundDetail.target_level] || '—' }}</span>
          </div>
          <div class="round-meta-item">
            <span class="round-meta-label">选课方式</span>
            <span class="round-meta-val">{{ SELECT_MODE_MAP[roundDetail.select_mode] || '—' }}</span>
          </div>
          <div class="round-meta-item">
            <span class="round-meta-label">学分上限</span>
            <span class="round-meta-val">{{ roundDetail.max_credit }} 学分</span>
          </div>
          <div class="round-meta-item">
            <span class="round-meta-label">重修政策</span>
            <div class="round-meta-tags">
              <template v-if="roundDetail.allow_retake === 1">
                <el-tag size="small" type="warning" effect="light">开放重修</el-tag>
                <el-tag v-if="roundDetail.retake_ignore_conflict === 1" size="small" type="success" effect="light">
                  支持冲突免听
                </el-tag>
              </template>
              <el-tag v-else size="small" type="info" effect="light">仅初修</el-tag>
            </div>
          </div>
          <div class="round-meta-item round-meta-item-full">
            <span class="round-meta-label">选课时间</span>
            <span class="round-meta-val">
              {{ formatDateTime(roundDetail.start_time) }} 至 {{ formatDateTime(roundDetail.end_time) }}
            </span>
          </div>
        </div>

        <div class="round-classes-section">
          <div class="round-classes-header">
            开放教学班（共 {{ (roundDetail.classes || []).length }} 个）
          </div>
          <el-table
            v-if="(roundDetail.classes || []).length"
            :data="roundDetail.classes"
            size="small"
            border
            stripe
            max-height="220"
          >
            <el-table-column prop="tc_id" label="教学班号" width="120" />
            <el-table-column prop="course_name" label="课程名称" min-width="145" />
            <el-table-column prop="teacher_name" label="任课教师" width="90" />
            <el-table-column prop="credit" label="学分" width="65" align="center" />
            <el-table-column label="排课时间与教室" min-width="180">
              <template #default="{ row }">{{ row.schedule_summary || '排课待确认' }}</template>
            </el-table-column>
            <el-table-column label="已选 / 容量" width="95" align="center">
              <template #default="{ row }">
                {{ row.actual_num ?? 0 }} / {{ row.capacity }}
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="round-classes-empty">本轮暂未绑定开放教学班</div>
        </div>
      </div>

      <!-- 模式 B：草稿新建 / 编辑表单 -->
      <el-form v-else :model="roundForm" label-position="top">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="学年学期">
              <el-select
                v-model="roundForm.term"
                placeholder="选择学年学期"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="changeRoundTerm"
              >
                <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="轮次名称">
              <el-input v-model="roundForm.round_name" placeholder="请输入轮次名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="轮次类型">
              <el-select v-model="roundForm.round_type" style="width: 100%">
                <el-option label="预选" :value="1" />
                <el-option label="正选" :value="2" />
                <el-option label="补退选" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用学生">
              <el-select v-model="roundForm.target_level" style="width: 100%">
                <el-option label="本科生" :value="1" />
                <el-option label="研究生" :value="2" />
                <el-option label="全部" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="选课方式">
              <el-select v-model="roundForm.select_mode" style="width: 100%">
                <el-option label="先到先得" :value="1" />
                <el-option label="超额筛选" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-input v-model="roundForm.start_time" placeholder="2026-12-01 09:00:00" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-input v-model="roundForm.end_time" placeholder="2026-12-05 17:00:00" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="学分上限">
              <el-input-number
                v-model="roundForm.max_credit"
                :min="0.5"
                :max="60"
                :step="0.5"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开放重修">
              <div class="switch-row">
                <el-switch v-model="roundForm.allow_retake" :active-value="1" :inactive-value="0" />
                <span class="switch-hint">{{ roundForm.allow_retake === 1 ? '允许重修选课' : '仅限初修' }}</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重修冲突免听">
              <div class="switch-row">
                <el-switch
                  v-model="roundForm.retake_ignore_conflict"
                  :active-value="1"
                  :inactive-value="0"
                  :disabled="roundForm.allow_retake !== 1"
                />
                <span class="switch-hint">{{ roundForm.retake_ignore_conflict === 1 ? '支持免听' : '不免听' }}</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="开放教学班">
          <div class="import-schedule-bar">
            <el-button size="small" type="primary" plain :disabled="!roundForm.term"
              :loading="loadingRoundClassOptions" @click="importScheduledClassesForRound">
              加入本学期已排课教学班
            </el-button>
            <span class="switch-hint">已选 {{ roundForm.tc_ids.length }} 个教学班</span>
          </div>
          <el-select v-model="roundForm.tc_ids" multiple filterable collapse-tags collapse-tags-tooltip
            :max-collapse-tags="3" :loading="loadingRoundClassOptions" style="width: 100%" placeholder="选择已发布的教学班">
            <el-option
              v-for="tc in publishedClassOptions"
              :key="tc.tc_id"
              :label="`${tc.tc_id} · ${tc.course_name} · ${tc.teacher_name || '教师待定'}${tc.has_schedule ? '' : '（排课待确认）'}`"
              :value="tc.tc_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选课通知内容">
          <el-input v-model="roundForm.notice_content" type="textarea" :rows="3" placeholder="请输入向学生发布的选课说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <template v-if="roundReadOnly">
          <el-button @click="roundDialogVisible = false">关闭</el-button>
          <el-button
            v-if="isStudent && roundDetail?.round_status === 3"
            type="primary"
            @click="goToSelection"
          >
            前往选课
          </el-button>
        </template>
        <template v-else>
          <el-button @click="roundDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveRound">保存草稿</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 教务处处长审批弹窗 -->
    <el-dialog v-model="approveVisible" title="选课方案审批" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审批决定">
          <el-radio-group v-model="approveForm.decision">
            <el-radio :value="1">通过</el-radio>
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
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { prepApi } from '../../api/prep'
import { userState } from '../../stores/user'
import { TERM_OPTIONS } from '../../constants/terms'
import { getCollegeName } from '../../constants/dicts'

const ROUND_TYPE_MAP = { 1: '预选', 2: '正选', 3: '补退选' }
const LEVEL_MAP = { 1: '本科生', 2: '研究生', 3: '全部' }
const SELECT_MODE_MAP = { 1: '先到先得', 2: '超额筛选' }
const ROUND_STATUS_MAP = { 0: '草稿', 1: '待审批', 2: '已发布', 3: '进行中', 4: '已结束', 5: '已审批待发布' }
const LIMIT_TYPE_MAP = { 1: '限年级', 2: '限专业', 3: '限学院', 4: '限培养层次' }

const router = useRouter()
const role = computed(() => userState.currentUser?.role)
const isStudent = computed(() => role.value === 'STUDENT')
const isTeacher = computed(() => role.value === 'TEACHER')
const canViewClasses = computed(() => ['ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR', 'COLLEGE_SECRETARY', 'TEACHER', 'SYS_ADMIN'].includes(role.value))
const canManageRound = computed(() => ['ACADEMIC_OFFICE', 'SYS_ADMIN'].includes(role.value))
const canApproveRound = computed(() => ['ACADEMIC_DIRECTOR', 'SYS_ADMIN'].includes(role.value))
const canEditLimitRole = computed(() => ['COLLEGE_SECRETARY', 'ACADEMIC_OFFICE', 'SYS_ADMIN'].includes(role.value))
const limitEditable = computed(() => Boolean(canEditLimitRole.value && limitDetail.value?.editable))

const activeTab = ref('rounds')
const loadingClasses = ref(false)
const classList = ref([])
const classPage = ref(1)
const classPageSize = 20
const classTotal = ref(0)
const syncSummary = ref(null)
const loadingRoundClassOptions = ref(false)
const publishedClassOptions = ref([])
const classQuery = reactive({ term: '2026-2027-1', keyword: '', tc_status: '', scheduled: '' })
let classesRequest = 0
let roundOptionsRequest = 0

const loadingRounds = ref(false)
const roundList = ref([])
const roundQuery = reactive({ term: '', round_status: null })

const limitVisible = ref(false)
const limitDetail = ref(null)
const editLimits = ref([])
const editSpecialRequirement = ref('')

const roundDialogVisible = ref(false)
const roundReadOnly = ref(false)
const roundDetail = ref(null)
const roundForm = reactive({
  round_id: null, term: '2026-2027-1', round_name: '', round_type: 2, target_level: 1,
  start_time: '2026-12-01 09:00:00', end_time: '2026-12-05 17:00:00', max_credit: 30,
  select_mode: 1, allow_retake: 0, retake_ignore_conflict: 0, notice_content: '', tc_ids: []
})

const approveVisible = ref(false)
const approveForm = reactive({ round_id: null, decision: 1, approve_opinion: '' })

function statusTagType(st) {
  return ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'info', 5: 'success' })[st] || 'info'
}

function formatDateTime(val) {
  return String(val || '').replace('T', ' ').slice(0, 16) || '—'
}

function classFormatLabel(row) {
  const teaching = { 1: '讲课', 2: '实验', 3: '上机', 4: '实践', 5: '研讨' }[Number(row.teach_type)]
  const assessment = { 1: '考试', 2: '考查' }[Number(row.assess_type)]
  return [teaching, assessment].filter(Boolean).join(' · ')
}

function changeClassPage(page) {
  classPage.value = page
  return loadClasses()
}

function refreshClasses() {
  syncSummary.value = null
  return changeClassPage(1)
}

async function loadClasses() {
  const request = ++classesRequest
  loadingClasses.value = true
  try {
    const params = {
      term: classQuery.term || undefined,
      keyword: classQuery.keyword || undefined,
      tc_status: classQuery.tc_status || undefined,
      scheduled: typeof classQuery.scheduled === 'boolean' ? classQuery.scheduled : undefined,
      page: classPage.value,
      page_size: classPageSize
    }
    const [res, sumRes] = await Promise.all([
      prepApi.listTeachingClasses(params),
      prepApi.getScheduleSyncSummary(classQuery.term || undefined)
    ])
    if (request === classesRequest) {
      classList.value = res.data?.items || []
      classTotal.value = res.data?.total || 0
      syncSummary.value = sumRes.data || null
    }
  } finally {
    if (request === classesRequest) loadingClasses.value = false
  }
}

async function loadRoundClassOptions(term) {
  const request = ++roundOptionsRequest
  loadingRoundClassOptions.value = Boolean(term)
  if (!term) return
  try {
    const { data } = await prepApi.listRoundClassOptions(term)
    if (request !== roundOptionsRequest || !roundDialogVisible.value) return
    publishedClassOptions.value = (data || []).map(tc => ({ ...tc, has_schedule: Number(tc.has_schedule) === 1 }))
    return publishedClassOptions.value
  } finally {
    if (request === roundOptionsRequest) loadingRoundClassOptions.value = false
  }
}

function changeRoundTerm(term) {
  roundForm.tc_ids = []
  publishedClassOptions.value = []
  return loadRoundClassOptions(term)
}

async function importScheduledClassesForRound() {
  const options = await loadRoundClassOptions(roundForm.term)
  if (!options) return
  const scheduled = options.filter(tc => tc.has_schedule)
  const alreadySelected = new Set(roundForm.tc_ids)
  const added = scheduled.filter(tc => !alreadySelected.has(tc.tc_id)).map(tc => tc.tc_id)
  if (!scheduled.length) {
    ElMessage.warning('本学期暂无已发布且排课已确认的教学班')
    return
  }
  roundForm.tc_ids = [...roundForm.tc_ids, ...added]
  ElMessage.success(added.length ? `已加入 ${added.length} 个已排课教学班` : '已排课的教学班已全部选中')
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
  editSpecialRequirement.value = res.data.special_requirement || ''
  limitVisible.value = true
}

async function saveLimits() {
  await prepApi.replaceLimits(limitDetail.value.teaching_class.tc_id, editLimits.value, editSpecialRequirement.value)
  ElMessage.success('限选条件与特殊要求已保存')
  limitVisible.value = false
  loadClasses()
}

async function openRoundDialog(roundId) {
  publishedClassOptions.value = []
  if (!roundId) {
    roundDetail.value = null
    Object.assign(roundForm, {
      round_id: null, term: '2026-2027-1', round_name: '', round_type: 2, target_level: 1,
      start_time: '2026-12-01 09:00:00', end_time: '2026-12-05 17:00:00', max_credit: 30,
      select_mode: 1, allow_retake: 0, retake_ignore_conflict: 0, notice_content: '', tc_ids: []
    })
    roundReadOnly.value = false
    roundDialogVisible.value = true
    await loadRoundClassOptions(roundForm.term)
    return
  }
  const res = await prepApi.getRound(roundId)
  const d = res.data
  roundDetail.value = d
  Object.assign(roundForm, {
    round_id: d.round_id, term: d.term, round_name: d.round_name, round_type: d.round_type,
    target_level: d.target_level, start_time: String(d.start_time).replace('T', ' '),
    end_time: String(d.end_time).replace('T', ' '), max_credit: Number(d.max_credit),
    select_mode: d.select_mode, allow_retake: d.allow_retake,
    retake_ignore_conflict: d.retake_ignore_conflict, notice_content: d.notice_content || '',
    tc_ids: (d.classes || []).map(c => c.tc_id)
  })
  roundReadOnly.value = d.round_status !== 0 || !canManageRound.value
  roundDialogVisible.value = true
  if (!roundReadOnly.value) await loadRoundClassOptions(roundForm.term)
}

function goToSelection() {
  roundDialogVisible.value = false
  router.push('/selection')
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

watch(() => userState.currentUser, user => {
  if (!user) return
  activeTab.value = canViewClasses.value ? 'classes' : 'rounds'
  classesRequest++
  classList.value = []
  classTotal.value = 0
  syncSummary.value = null
  if (canViewClasses.value) {
    refreshClasses()
  }
  loadRounds()
}, { immediate: true })
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.class-pagination { margin-top: 14px; justify-content: flex-end; }
.limit-action { display: flex; margin-top: 4px; }
.limit-row { display: flex; gap: 8px; margin-bottom: 10px; align-items: center; }
.limit-readonly-list { display: flex; flex-direction: column; gap: 8px; }
.limit-readonly-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
}
.limit-readonly-val {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--app-text-primary);
}
.special-req-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-primary);
  margin-bottom: 6px;
}
.special-req-hint {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-top: 6px;
}
.sync-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 12px;
  border-radius: 10px;
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
}
.sync-banner-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.sync-banner-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--app-text-primary);
}
.sync-banner-note {
  font-size: 12px;
  color: var(--app-text-secondary);
}
.cell-sub {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-top: 2px;
}
.import-schedule-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  width: 100%;
}

.round-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 24px;
}
.round-dialog-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: wrap;
}
.round-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-primary);
  line-height: 1.4;
}
.round-term-badge {
  font-size: 12px;
  color: var(--app-text-secondary);
  background: var(--app-card-muted-bg);
  border: 1px solid var(--app-border);
  padding: 2px 10px;
  border-radius: 999px;
  flex-shrink: 0;
}

.round-detail-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.notice-callout {
  padding: 14px 18px;
  border-radius: 12px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
}
.notice-callout-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-primary);
  margin-bottom: 6px;
}
.notice-callout-icon {
  font-size: 15px;
}
.notice-callout-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--app-text-primary);
  white-space: pre-wrap;
}

.round-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--app-border);
  background: var(--app-card-bg);
}
.round-meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.round-meta-item-full {
  grid-column: 1 / -1;
  padding-top: 10px;
  border-top: 1px dashed var(--app-border);
}
.round-meta-label {
  font-size: 12px;
  color: var(--app-text-tertiary);
}
.round-meta-val {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}
.round-meta-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.round-classes-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.round-classes-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
}
.round-classes-empty {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--app-text-tertiary);
  background: var(--app-card-muted-bg);
  border-radius: 8px;
}

.switch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
}
.switch-hint {
  font-size: 12.5px;
  color: var(--app-text-secondary);
}

@media (max-width: 640px) {
  .round-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
