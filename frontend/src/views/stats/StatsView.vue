<template>
  <div class="stats-page">
    <el-tabs v-model="activeTab" type="border-card" class="no-print-tabs">
      <!-- ==================== 4.1 学生评教（学生端） ==================== -->
      <el-tab-pane v-if="isStudent" label="教学评价" name="eval">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 14px"
          title="每门已选课程仅可评价一次，提交后不可修改；完成本学期全部课程评价后可查询成绩，教师端仅查看匿名汇总。"
        />

        <div class="kpi-row" v-if="evalData">
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">当前评教学期</div>
            <div class="kpi-val">{{ evalData.current_term }}</div>
          </el-card>
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">本学期有效选课</div>
            <div class="kpi-val">{{ evalData.total_count }} 门</div>
          </el-card>
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">已完成评教</div>
            <div class="kpi-val text-success">{{ evalData.evaluated_count }} 门</div>
          </el-card>
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">待评课程 / 查分门禁</div>
            <div class="kpi-val">
              <el-tag :type="evalData.all_completed ? 'success' : 'danger'" size="large">
                {{ evalData.all_completed ? '已全部完成 · 允许查分' : `尚有 ${evalData.pending_count} 门待评` }}
              </el-tag>
            </div>
          </el-card>
        </div>

        <div class="toolbar">
          <el-button type="primary" @click="loadPendingEvals">刷新评教清单</el-button>
          <el-button v-if="evalData?.all_completed" type="success" @click="activeTab = 'grades'; loadMyGrades()">
            前往查询本人成绩
          </el-button>
        </div>

        <el-table :data="evalData?.items || []" border stripe v-loading="loadingEval">
          <el-table-column prop="tc_id" label="教学班号" width="125" />
          <el-table-column prop="lesson_no" label="课堂号" width="125" />
          <el-table-column prop="course_id" label="课程号" width="105" />
          <el-table-column prop="course_name" label="课程名称" min-width="160" />
          <el-table-column prop="teacher_name" label="任课教师" width="110" />
          <el-table-column prop="credit" label="学分" width="70" />
          <el-table-column label="修读类型" width="90">
            <template #default="{ row }">{{ STUDY_TYPE_MAP[row.study_type] || '初修' }}</template>
          </el-table-column>
          <el-table-column label="评教状态" width="120">
            <template #default="{ row }">
              <el-tag :type="row.evaluated ? 'success' : 'warning'" size="small">
                {{ row.evaluated ? '已提交（锁定）' : '待评教' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="eval_time" label="提交时间" width="170">
            <template #default="{ row }">{{ row.eval_time || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="!row.evaluated"
                type="primary"
                size="small"
                @click="openEvalDialog(row)"
              >
                去评教
              </el-button>
              <span v-else class="muted">不可修改</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 4.2 本人成绩查询（学生端，先评教后查分） ==================== -->
      <el-tab-pane v-if="isStudent" label="本人成绩查询" name="grades">
        <div class="toolbar">
          <el-select v-model="gradeQuery.term" placeholder="全部学期（含当前学期）" clearable style="width: 210px">
            <el-option v-for="t in gradeTermOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-switch
            v-model="gradeQuery.only_final"
            active-text="仅看最终成绩"
            inactive-text="全部历次记录"
          />
          <el-button type="primary" @click="loadMyGrades">查询成绩</el-button>
        </div>

        <!-- 未评教拦截提示界面（界面 02） -->
        <el-card v-if="gradeGateBlocked" shadow="never" class="gate-block-card">
          <el-result
            icon="warning"
            title="当前学期尚未完成评教，已拦截查分"
            :sub-title="gradeGateMessage"
          >
            <template #extra>
              <el-button type="primary" @click="activeTab = 'eval'; loadPendingEvals()">
                立即前往完成评教
              </el-button>
            </template>
          </el-result>
          <div v-if="evalData?.pending_items?.length" style="margin-top: 8px">
            <div class="sub-heading">待评教课程清单（仅显示未评课程，不返回任何成绩或累计摘要）：</div>
            <el-table :data="evalData.pending_items" border size="small">
              <el-table-column prop="tc_id" label="教学班号" width="130" />
              <el-table-column prop="course_name" label="课程名称" />
              <el-table-column prop="teacher_name" label="任课教师" width="120" />
              <el-table-column label="操作" width="110">
                <template #default="{ row }">
                  <el-button type="primary" link @click="openEvalDialog(row)">立即评教</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <!-- 放行后的成绩与统计摘要（界面 03 & 04） -->
        <template v-else-if="gradeResult">
          <el-alert
            v-if="gradeResult.summary_status === 'HIDDEN_BY_EVAL_GATE'"
            type="warning"
            show-icon
            :closable="false"
            style="margin-bottom: 14px"
            title="历史学期查询已豁免门禁；因当前学期尚未完成评教，已自动隐藏可能包含当期成绩的累计学业统计摘要。"
          />
          <el-alert
            v-else-if="gradeResult.summary_status === 'NOT_GENERATED'"
            type="info"
            show-icon
            :closable="false"
            style="margin-bottom: 14px"
            title="累计学业统计快照尚未生成（不得推断为零分），请待成绩发布或教务处重算后查看。"
          />

          <div class="kpi-row" v-if="gradeResult.summary">
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">已获学分 / 不及格学分</div>
              <div class="kpi-val">
                {{ gradeResult.summary.earned_credit }} / <span class="text-danger">{{ gradeResult.summary.failed_credit }}</span>
              </div>
              <div class="kpi-sub">仅本阶段最终成绩，排除辅修</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">平均学分绩点（GPA）</div>
              <div class="kpi-val text-primary">{{ gradeResult.summary.gpa ?? '—' }}</div>
              <div class="kpi-sub">校发〔2021〕46号附表3</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">加权平均分</div>
              <div class="kpi-val">{{ gradeResult.summary.avg_score ?? '—' }}</div>
              <div class="kpi-sub">更新：{{ gradeResult.summary.update_time }}</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">{{ gradeResult.summary.rank_label }}</div>
              <div class="kpi-val">
                {{ gradeResult.summary.major_rank ? `${gradeResult.summary.major_rank} / ${gradeResult.summary.major_rank_total}` : '—' }}
              </div>
              <div class="kpi-sub">同专业同年级通用排名</div>
            </el-card>
          </div>

          <el-table :data="gradeResult.items || []" border stripe v-loading="loadingGrades">
            <el-table-column prop="term" label="学期" width="115" />
            <el-table-column prop="course_id" label="课程号" width="95" />
            <el-table-column label="课程名称" min-width="180">
              <template #default="{ row }">
                <span>{{ row.course_name }}</span>
                <el-tag v-if="row.record_source === 'EXCHANGE'" size="small" type="success" style="margin-left: 6px">
                  外校认定
                </el-tag>
                <el-tag v-if="row.excluded_from_major_gpa" size="small" type="warning" style="margin-left: 6px">
                  辅修·不计主修GPA
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="credit" label="学分" width="65" />
            <el-table-column label="修读/考试" width="125">
              <template #default="{ row }">
                {{ STUDY_TYPE_MAP[row.study_type] || '初修' }} · {{ row.exam_type === 2 ? '补考' : '正考' }}
              </template>
            </el-table-column>
            <el-table-column label="平时/期末" width="105">
              <template #default="{ row }">
                <span v-if="row.score_type === 1 && row.record_source === 'SCHOOL'">
                  {{ row.usual_score ?? '—' }} / {{ row.exam_score ?? '—' }}
                </span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="总评/等级" width="95">
              <template #default="{ row }">
                <b :class="row.passed ? '' : 'text-danger'">{{ row.display_score }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="grade_point" label="绩点" width="75" />
            <el-table-column label="状态/最终" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.is_final === 1" size="small" type="success">最终成绩</el-tag>
                <el-tag v-else size="small" type="info">历次记录</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publish_time" label="发布/生效时间" width="165" />
          </el-table>
        </template>
      </el-tab-pane>

      <!-- ==================== 4.3 学业统计台账与重算（教务/学院/管理员） ==================== -->
      <el-tab-pane v-if="canViewAcademicStats" label="学业统计台账" name="academic">
        <div class="toolbar">
          <el-input v-model="academicQuery.term" placeholder="统计学期如 2026-2027-1" style="width: 170px" clearable />
          <el-input v-model="academicQuery.major_id" placeholder="专业号如 120102" style="width: 145px" clearable />
          <el-input v-model="academicQuery.keyword" placeholder="学号 / 姓名 / 班级" style="width: 190px" clearable />
          <el-button type="primary" @click="loadAcademicStats">查询</el-button>
          <el-button
            v-if="canRecomputeAcademic"
            type="warning"
            :loading="recomputingAcademic"
            @click="handleRecomputeAcademic()"
          >
            全量重算学业统计（排除辅修+联合外校认定）
          </el-button>
        </div>

        <el-alert
          v-if="academicData?.rule_note"
          :title="academicData.rule_note"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />

        <el-table :data="academicData?.items || []" border stripe v-loading="loadingAcademic">
          <el-table-column prop="student_id" label="学号" width="120" />
          <el-table-column prop="student_name" label="姓名" width="90" />
          <el-table-column label="层次/年级/班级" min-width="150">
            <template #default="{ row }">{{ row.degree_level }} · {{ row.grade }} · {{ row.class_name }}</template>
          </el-table-column>
          <el-table-column prop="major_id" label="专业号" width="85" />
          <el-table-column label="累计已获/不及格" width="130">
            <template #default="{ row }">
              {{ row.earned_credit ?? '—' }} / <span class="text-danger">{{ row.failed_credit ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="GPA / 均分" width="125">
            <template #default="{ row }">
              <b>{{ row.gpa ?? '—' }}</b> / {{ row.avg_score ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="通用专业排名" width="115">
            <template #default="{ row }">
              {{ row.major_rank ? `${row.major_rank} / ${row.major_rank_total}` : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="当期已获/不及格" width="130">
            <template #default="{ row }">
              {{ row.term_credit ?? '—' }} / {{ row.term_failed_credit ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="统计口径" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.minor_score_count > 0" size="small" type="warning">
                排除辅修 {{ row.minor_score_count }} 门
              </el-tag>
              <el-tag v-if="row.exchange_score_count > 0" size="small" type="success" style="margin-left: 4px">
                含外校 {{ row.exchange_score_count }} 门
              </el-tag>
              <span v-if="!row.minor_score_count && !row.exchange_score_count" class="muted">常规主修</span>
            </template>
          </el-table-column>
          <el-table-column v-if="canRecomputeAcademic" label="操作" width="95" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="handleRecomputeAcademic(row.student_id)">重算</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 4.4 成绩单预览与打印（含来校交换生中英文版） ==================== -->
      <el-tab-pane v-if="canPrintTranscript" label="成绩单预览与打印" name="transcript">
        <div class="toolbar no-print">
          <el-select
            v-if="!isStudent"
            v-model="transcriptForm.student_id"
            filterable
            placeholder="选择学生（含来校交换生 EX20260001）"
            style="width: 280px"
          >
            <el-option
              v-for="s in transcriptStudents"
              :key="s.student_id"
              :label="`${s.student_name} (${s.student_id}) · ${s.is_incoming_exchange ? '来校交换生' : s.class_name}`"
              :value="s.student_id"
            />
          </el-select>
          <el-input v-model="transcriptForm.start_term" placeholder="起始学期（可空）" style="width: 155px" clearable />
          <el-input v-model="transcriptForm.end_term" placeholder="结束学期（可空）" style="width: 155px" clearable />
          <el-select v-model="transcriptForm.template" style="width: 205px">
            <el-option label="自动匹配版式" value="AUTO" />
            <el-option label="本校历次成绩明细单" value="STANDARD" />
            <el-option label="来校交换生中英文正式单" value="EXCHANGE_BILINGUAL" />
          </el-select>
          <el-button type="primary" :loading="loadingTranscript" @click="loadTranscriptPreview">
            生成打印预览
          </el-button>
          <el-button v-if="transcriptData" type="success" @click="handlePrint">
            打印 / 导出 PDF
          </el-button>
        </div>

        <el-alert
          v-if="transcriptGateBlocked"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 14px"
          :title="transcriptGateMessage"
        >
          <template #default>
            <el-button type="primary" size="small" style="margin-top: 8px" @click="activeTab = 'eval'; loadPendingEvals()">
              前往完成评教
            </el-button>
          </template>
        </el-alert>

        <div class="print-area">
          <TranscriptPreview v-if="transcriptData" :data="transcriptData" />
          <el-empty v-else-if="!transcriptGateBlocked" description="请选择条件后点击「生成打印预览」" />
        </div>
      </el-tab-pane>

      <!-- ==================== 4.5 成绩统计报表（教师/学院/教务处） ==================== -->
      <el-tab-pane v-if="isStaff" label="成绩统计报表" name="reports">
        <div class="toolbar">
          <el-radio-group v-model="reportQuery.report_type" @change="loadGradeReport">
            <el-radio-button value="DISTRIBUTION">成绩分布（分制独立）</el-radio-button>
            <el-radio-button value="FINAL_FAILED">当前最终不及格名单</el-radio-button>
            <el-radio-button v-if="reportOptions?.can_honor_rank" value="HONOR_RANK">
              首次考核评优专业排名（P36）
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 分布 / 不及格筛选栏 -->
        <div v-if="reportQuery.report_type !== 'HONOR_RANK'" class="toolbar">
          <el-select v-model="reportQuery.term" placeholder="全部学期" clearable style="width: 155px">
            <el-option v-for="t in reportOptions?.terms || []" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="reportQuery.tc_id" placeholder="全部授权教学班" clearable filterable style="width: 280px">
            <el-option
              v-for="tc in reportOptions?.teaching_classes || []"
              :key="tc.tc_id"
              :label="`${tc.tc_id} · ${tc.course_name} (${tc.teacher_name})`"
              :value="tc.tc_id"
            />
          </el-select>
          <el-select v-if="reportQuery.report_type === 'DISTRIBUTION'" v-model="reportQuery.exam_type" style="width: 130px">
            <el-option label="1 正常考试" :value="1" />
            <el-option label="2 补考" :value="2" />
          </el-select>
          <el-button type="primary" :loading="loadingReport" @click="loadGradeReport">生成报表</el-button>
        </div>

        <!-- 评优专业排名筛选栏 -->
        <div v-else class="toolbar">
          <el-select v-model="honorPair" placeholder="选择专业与年级" style="width: 210px" @change="applyHonorPair">
            <el-option
              v-for="mg in reportOptions?.major_grades || []"
              :key="`${mg.major_id}|${mg.grade}`"
              :label="`专业 ${mg.major_id} · ${mg.grade}级`"
              :value="`${mg.major_id}|${mg.grade}`"
            />
          </el-select>
          <el-input v-model="reportQuery.start_term" placeholder="起始学期" style="width: 145px" />
          <el-input v-model="reportQuery.end_term" placeholder="结束学期" style="width: 145px" />
          <el-button type="primary" :loading="loadingReport" @click="loadGradeReport">校验完整性并生成</el-button>
        </div>

        <!-- 分支 1：成绩分布展示 -->
        <template v-if="reportData && reportData.report_type === 'DISTRIBUTION'">
          <el-alert :title="reportData.methodology_note" type="info" show-icon :closable="false" style="margin-bottom: 14px" />
          <div class="kpi-row">
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">已发布样本总数</div>
              <div class="kpi-val">{{ reportData.total_records }} 条</div>
              <div class="kpi-sub">考试类别：{{ reportData.exam_type_label }}</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">百分制均分 / 及格率</div>
              <div class="kpi-val text-primary">
                {{ reportData.percent_group?.avg_score ?? '—' }} / {{ reportData.percent_group?.pass_rate ? `${reportData.percent_group.pass_rate}%` : '—' }}
              </div>
              <div class="kpi-sub">有效 {{ reportData.percent_group?.valid_count }} 人 · 最高 {{ reportData.percent_group?.max_score ?? '—' }} / 最低 {{ reportData.percent_group?.min_score ?? '—' }}</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">五级制 / 两级制样本</div>
              <div class="kpi-val">{{ reportData.five_level_group?.valid_count }} / {{ reportData.two_level_group?.valid_count }}</div>
              <div class="kpi-sub">独立展示，不计算等级算术均分</div>
            </el-card>
            <el-card shadow="never" class="kpi-card">
              <div class="kpi-label">特殊考试 / 缺值记录</div>
              <div class="kpi-val">{{ reportData.special_exam_count }} / {{ reportData.missing_value_count }}</div>
              <div class="kpi-sub">生成：{{ reportData.generated_at }}</div>
            </el-card>
          </div>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-card shadow="never" header="百分制分段分布（左闭右开）">
                <el-table :data="reportData.percent_group?.buckets || []" border size="small">
                  <el-table-column prop="label" label="分数段" />
                  <el-table-column prop="count" label="人数" width="80" />
                  <el-table-column label="占比" width="150">
                    <template #default="{ row }">
                      <el-progress
                        :percentage="Number(row.ratio || 0)"
                        :stroke-width="12"
                        :format="() => (row.ratio ? `${row.ratio}%` : '—')"
                      />
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card shadow="never" header="五级制分布（优/良/中/及格/不及格）" style="margin-bottom: 12px">
                <el-table :data="reportData.five_level_group?.buckets || []" border size="small">
                  <el-table-column prop="label" label="等级" width="90" />
                  <el-table-column prop="count" label="人数" width="80" />
                  <el-table-column label="占比">
                    <template #default="{ row }">{{ row.ratio ? `${row.ratio}%` : '—' }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
              <el-card shadow="never" header="两级制分布（合格/不合格）">
                <el-table :data="reportData.two_level_group?.buckets || []" border size="small">
                  <el-table-column prop="label" label="等级" width="90" />
                  <el-table-column prop="count" label="人数" width="80" />
                  <el-table-column label="占比">
                    <template #default="{ row }">{{ row.ratio ? `${row.ratio}%` : '—' }}</template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </template>

        <!-- 分支 2：当前最终不及格名单 -->
        <template v-else-if="reportData && reportData.report_type === 'FINAL_FAILED'">
          <el-alert :title="reportData.methodology_note" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
          <div style="margin-bottom: 10px">
            <el-tag type="danger">当前最终不及格记录：{{ reportData.failed_record_count }} 条</el-tag>
            <el-tag type="warning" style="margin-left: 8px">涉及去重学生：{{ reportData.distinct_student_count }} 人</el-tag>
          </div>
          <el-table :data="reportData.items || []" border stripe empty-text="当前筛选范围内暂无最终不及格记录（补考/重修已通过者不列入）">
            <el-table-column prop="term" label="学期" width="120" />
            <el-table-column prop="tc_id" label="教学班号" width="120" />
            <el-table-column prop="course_name" label="课程名称" min-width="150" />
            <el-table-column prop="credit" label="学分" width="70" />
            <el-table-column prop="student_id" label="学号" width="125" />
            <el-table-column prop="student_name" label="姓名" width="95" />
            <el-table-column prop="class_name" label="班级" width="110" />
            <el-table-column prop="best_score" label="当前最高成绩" width="120" />
          </el-table>
        </template>

        <!-- 分支 3：首次考核评优专业排名（含完整性阻断展示） -->
        <template v-else-if="reportData && reportData.report_type === 'HONOR_RANK'">
          <el-alert :title="reportData.methodology_note" type="info" show-icon :closable="false" style="margin-bottom: 12px" />

          <el-card v-if="reportData.status === 'BLOCKED'" shadow="never" class="gate-block-card">
            <el-result
              icon="error"
              title="完整性门禁未通过 · 禁止生成正式评优排名（界面 10）"
              sub-title="为防止将尚未发布或缺少计划依据的必修课程误折算为 0 分，系统已阻断正式榜单生成。"
            />
            <div style="padding: 0 20px 16px">
              <div class="sub-heading">阻断原因明细：</div>
              <ul class="reason-list">
                <li v-for="(reason, idx) in reportData.blocked_reasons" :key="idx">{{ reason }}</li>
              </ul>
              <el-descriptions title="来源完整性核验凭证" :column="3" border size="small" style="margin-top: 12px">
                <el-descriptions-item label="专业 / 年级">{{ reportData.evidence?.major_id }} / {{ reportData.evidence?.grade }}级</el-descriptions-item>
                <el-descriptions-item label="评价期间">{{ reportData.evidence?.start_term }} ~ {{ reportData.evidence?.end_term }}</el-descriptions-item>
                <el-descriptions-item label="参评学生数">{{ reportData.evidence?.population_count }} 人</el-descriptions-item>
                <el-descriptions-item label="计划必修课数">{{ reportData.evidence?.plan_course_count }} 门</el-descriptions-item>
                <el-descriptions-item label="未发布初次成绩">{{ reportData.evidence?.unpublished_count }} 条</el-descriptions-item>
                <el-descriptions-item label="未决缓考记录">{{ reportData.evidence?.deferred_count }} 条</el-descriptions-item>
              </el-descriptions>
            </div>
          </el-card>

          <el-table v-else :data="reportData.items || []" border stripe>
            <el-table-column label="评优排名" width="105">
              <template #default="{ row }">
                <b>{{ row.honor_rank ? `${row.honor_rank} / ${row.honor_rank_total}` : '—' }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="student_id" label="学号" width="130" />
            <el-table-column prop="student_name" label="姓名" width="100" />
            <el-table-column prop="class_name" label="班级" width="110" />
            <el-table-column prop="required_credit_total" label="全部必修学分" width="120" />
            <el-table-column prop="honor_avg_score" label="首次评优加权均分" width="150" />
            <el-table-column label="缺考/缺成绩必修课" min-width="180">
              <template #default="{ row }">
                {{ row.missing_courses?.length ? row.missing_courses.join('、') : '全部齐备' }}
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- ==================== 4.6 评教结果汇总（教师匿名汇总 / 教务处触发与受控明细） ==================== -->
      <el-tab-pane v-if="isStaff" label="评教结果汇总" name="evalSummary">
        <div class="toolbar">
          <el-input v-model="evalSummaryQuery.term" placeholder="学期如 2026-2027-1" style="width: 170px" clearable />
          <el-button type="primary" @click="loadEvalSummaries">刷新汇总</el-button>
          <el-button
            v-if="evalSummaryData?.can_trigger_recompute"
            type="warning"
            :loading="recomputingEvalSummary"
            @click="handleRecomputeEvalSummary"
          >
            执行本学期评教结果汇总（写 tc_eval_result）
          </el-button>
          <el-button
            v-if="evalSummaryData?.can_view_details"
            type="info"
            plain
            @click="openOfficeEvalDetails"
          >
            教务处受控明细查阅（界面 13）
          </el-button>
        </div>

        <el-alert
          v-if="evalSummaryData?.anonymity_note"
          :title="evalSummaryData.anonymity_note"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />

        <el-table :data="evalSummaryData?.items || []" border stripe v-loading="loadingEvalSummary">
          <el-table-column prop="tc_id" label="教学班号" width="125" />
          <el-table-column prop="lesson_no" label="课堂号" width="125" />
          <el-table-column prop="course_name" label="课程名称" min-width="160" />
          <el-table-column prop="teacher_name" label="任课教师" width="110" />
          <el-table-column prop="enrolled_num" label="有效选课人数" width="115" />
          <el-table-column label="参评人数" width="100">
            <template #default="{ row }">{{ row.eval_num ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="评教均分" width="130">
            <template #default="{ row }">
              <b v-if="row.avg_eval_score !== null && row.avg_eval_score !== undefined">{{ row.avg_eval_score }}</b>
              <span v-else-if="row.summary_status === 'ZERO_EVAL'" class="muted">—（暂无参评）</span>
              <span v-else class="muted">未汇总</span>
            </template>
          </el-table-column>
          <el-table-column label="汇总状态" width="140">
            <template #default="{ row }">
              <el-tag :type="evalSummaryTagType(row.summary_status)" size="small">
                {{ EVAL_SUMMARY_STATUS_MAP[row.summary_status] || row.summary_status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="calc_time" label="汇总统计时间" width="170">
            <template #default="{ row }">{{ row.calc_time || '—' }}</template>
          </el-table-column>
        </el-table>

        <!-- 第4组师资管理按参评人数加权汇总参考视图（教师端隐藏） -->
        <el-card
          v-if="evalSummaryData?.teacher_weighted_summaries?.length"
          shadow="never"
          header="教师教学评价加权汇总（按参评人数加权计算，不含零参评教学班）"
          style="margin-top: 16px"
        >
          <el-table :data="evalSummaryData.teacher_weighted_summaries" border size="small">
            <el-table-column prop="teacher_id" label="教师编码" width="120" />
            <el-table-column prop="teacher_name" label="教师姓名" width="130" />
            <el-table-column prop="class_count" label="有效参评教学班数" width="150" />
            <el-table-column prop="total_eval_num" label="累计参评人数" width="130" />
            <el-table-column prop="weighted_avg_score" label="人数加权评教均分" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 4.1 评教提交弹窗 -->
    <el-dialog v-model="evalDialogVisible" title="提交课程教学评价（提交后不可修改）" width="500px">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 14px"
        title="每条选课记录只能提交一次，提交后不可修改或删除。"
      />
      <el-form label-width="95px">
        <el-form-item label="授课课程">
          <span>{{ evalTarget?.course_name }}（{{ evalTarget?.tc_id }} · {{ evalTarget?.teacher_name }}）</span>
        </el-form-item>
        <el-form-item label="评教总分">
          <el-input-number
            v-model="evalForm.eval_score"
            :min="0"
            :max="100"
            :step="1"
            :precision="0"
          />
          <span class="muted" style="margin-left: 10px">0~100 整数（0 与 100 均合法）</span>
        </el-form-item>
        <el-form-item label="意见建议">
          <el-input
            v-model="evalForm.eval_comment"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填，最多 200 字；明细仅教务处受控查阅，绝不向教师展示"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submittingEval" @click="confirmSubmitEval">确认提交评教</el-button>
      </template>
    </el-dialog>

    <!-- 4.6 教务处受控评教明细抽屉（仅教务处/管理员可见） -->
    <el-drawer v-model="detailDrawerVisible" title="教务处受控评教明细查阅（匿名化视图）" size="60%">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
        title="本视图仅限教务处与系统管理员受控查阅，教师及学院角色在服务端已严格拦截（HTTP 403）。"
      />
      <el-table :data="officeEvalDetails" border stripe v-loading="loadingEvalDetails">
        <el-table-column prop="tc_id" label="教学班号" width="115" />
        <el-table-column prop="course_name" label="课程名称" min-width="140" />
        <el-table-column prop="teacher_name" label="任课教师" width="95" />
        <el-table-column prop="eval_score" label="评教分" width="80" />
        <el-table-column prop="eval_comment" label="意见建议" min-width="200">
          <template #default="{ row }">{{ row.eval_comment || '（未填意见）' }}</template>
        </el-table-column>
        <el-table-column prop="eval_time" label="提交时间" width="165" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { statsApi } from '../../api/stats'
import { userState } from '../../stores/user'
import TranscriptPreview from './TranscriptPreview.vue'

const STUDY_TYPE_MAP = { 1: '初修', 2: '重修', 3: '辅修' }
const EVAL_SUMMARY_STATUS_MAP = {
  SUMMARIZED: '已汇总',
  ZERO_EVAL: '已汇总（零参评）',
  STALE: '有新评教待重算',
  NOT_SUMMARIZED: '尚未汇总',
  NOT_SUMMARIZED_EMPTY: '未汇总（暂无参评）'
}

const role = computed(() => userState.currentUser?.role)
const isStudent = computed(() => role.value === 'STUDENT')
const isStaff = computed(() => role.value && role.value !== 'STUDENT')
const canViewAcademicStats = computed(() =>
  ['COLLEGE_SECRETARY', 'ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR', 'GRADUATE_SCHOOL', 'SYS_ADMIN'].includes(role.value)
)
const canRecomputeAcademic = computed(() =>
  ['ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR', 'GRADUATE_SCHOOL', 'SYS_ADMIN'].includes(role.value)
)
const canPrintTranscript = computed(() =>
  ['STUDENT', 'ACADEMIC_OFFICE', 'ACADEMIC_DIRECTOR', 'GRADUATE_SCHOOL', 'SYS_ADMIN'].includes(role.value)
)

const activeTab = ref('eval')

// 4.1 评教
const evalData = ref(null)
const loadingEval = ref(false)
const evalDialogVisible = ref(false)
const evalTarget = ref(null)
const submittingEval = ref(false)
const evalForm = reactive({ eval_score: 95, eval_comment: '' })

// 4.2 成绩查询
const gradeQuery = reactive({ term: '', only_final: false })
const gradeTermOptions = ref(['2026-2027-1'])
const gradeResult = ref(null)
const loadingGrades = ref(false)
const gradeGateBlocked = ref(false)
const gradeGateMessage = ref('')

// 4.3 学业统计
const academicQuery = reactive({ term: '2026-2027-1', major_id: '', keyword: '' })
const academicData = ref(null)
const loadingAcademic = ref(false)
const recomputingAcademic = ref(false)

// 4.4 成绩单打印
const transcriptStudents = ref([])
const transcriptForm = reactive({ student_id: '', start_term: '', end_term: '', template: 'AUTO' })
const transcriptData = ref(null)
const loadingTranscript = ref(false)
const transcriptGateBlocked = ref(false)
const transcriptGateMessage = ref('')

// 4.5 统计报表
const reportOptions = ref(null)
const reportQuery = reactive({
  report_type: 'DISTRIBUTION',
  term: '2026-2027-1',
  tc_id: '',
  exam_type: 1,
  major_id: '120102',
  grade: 2024,
  start_term: '2026-2027-1',
  end_term: '2026-2027-1'
})
const honorPair = ref('120102|2024')
const reportData = ref(null)
const loadingReport = ref(false)

// 4.6 评教汇总
const evalSummaryQuery = reactive({ term: '2026-2027-1' })
const evalSummaryData = ref(null)
const loadingEvalSummary = ref(false)
const recomputingEvalSummary = ref(false)
const detailDrawerVisible = ref(false)
const officeEvalDetails = ref([])
const loadingEvalDetails = ref(false)

function evalSummaryTagType(status) {
  return ({
    SUMMARIZED: 'success',
    ZERO_EVAL: 'info',
    STALE: 'warning',
    NOT_SUMMARIZED: 'danger',
    NOT_SUMMARIZED_EMPTY: 'info'
  })[status] || 'info'
}

// ==================== 4.1 操作 ====================
async function loadPendingEvals() {
  if (!isStudent.value) return
  loadingEval.value = true
  try {
    const res = await statsApi.getPendingEvaluations()
    evalData.value = res.data
  } finally {
    loadingEval.value = false
  }
}

function openEvalDialog(row) {
  evalTarget.value = row
  evalForm.eval_score = 95
  evalForm.eval_comment = ''
  evalDialogVisible.value = true
}

async function confirmSubmitEval() {
  if (!evalTarget.value) return
  submittingEval.value = true
  try {
    const res = await statsApi.submitEvaluation({
      sc_id: evalTarget.value.sc_id,
      eval_score: evalForm.eval_score,
      eval_comment: evalForm.eval_comment
    })
    ElMessage.success(res.message || '评教提交成功')
    evalDialogVisible.value = false
    await loadPendingEvals()
    if (gradeGateBlocked.value && evalData.value?.all_completed) {
      loadMyGrades()
    }
  } finally {
    submittingEval.value = false
  }
}

// ==================== 4.2 操作 ====================
async function loadMyGrades() {
  if (!isStudent.value) return
  loadingGrades.value = true
  gradeGateBlocked.value = false
  try {
    const res = await statsApi.getMyGrades({
      term: gradeQuery.term || undefined,
      only_final: gradeQuery.only_final
    })
    gradeResult.value = res.data
    if (res.data?.available_terms?.length) {
      gradeTermOptions.value = res.data.available_terms
    }
  } catch (err) {
    const code = err?.response?.data?.code
    if (code === 'EVAL_REQUIRED') {
      gradeResult.value = null
      gradeGateBlocked.value = true
      gradeGateMessage.value = err?.response?.data?.message || '请先完成当前学期评教'
      await loadPendingEvals()
    }
  } finally {
    loadingGrades.value = false
  }
}

// ==================== 4.3 操作 ====================
async function loadAcademicStats() {
  if (!canViewAcademicStats.value) return
  loadingAcademic.value = true
  try {
    const res = await statsApi.listAcademicStats({
      term: academicQuery.term || undefined,
      major_id: academicQuery.major_id || undefined,
      keyword: academicQuery.keyword || undefined
    })
    academicData.value = res.data
  } finally {
    loadingAcademic.value = false
  }
}

async function handleRecomputeAcademic(studentId) {
  recomputingAcademic.value = true
  try {
    const res = await statsApi.recomputeAcademicStats(studentId ? { student_id: studentId } : {})
    ElMessage.success(`重算完成：更新 ${res.data.recomputed_students} 名学生、${res.data.updated_rank_groups} 个专业年级排名组`)
    await loadAcademicStats()
  } finally {
    recomputingAcademic.value = false
  }
}

// ==================== 4.4 操作 ====================
async function loadTranscriptStudents() {
  if (isStudent.value || !canPrintTranscript.value) return
  const res = await statsApi.listTranscriptStudents()
  transcriptStudents.value = res.data || []
  if (!transcriptForm.student_id && transcriptStudents.value.length) {
    const ex = transcriptStudents.value.find((s) => s.is_incoming_exchange)
    transcriptForm.student_id = ex ? ex.student_id : transcriptStudents.value[0].student_id
  }
}

async function loadTranscriptPreview() {
  loadingTranscript.value = true
  transcriptGateBlocked.value = false
  try {
    const payload = {
      start_term: transcriptForm.start_term || undefined,
      end_term: transcriptForm.end_term || undefined,
      template: transcriptForm.template
    }
    if (!isStudent.value) {
      payload.student_id = transcriptForm.student_id
    }
    const res = await statsApi.previewTranscript(payload)
    transcriptData.value = res.data
  } catch (err) {
    const code = err?.response?.data?.code
    if (code === 'EVAL_REQUIRED') {
      transcriptData.value = null
      transcriptGateBlocked.value = true
      transcriptGateMessage.value = err?.response?.data?.message || '当前学期尚有未评教课程，禁止预览或打印'
    }
  } finally {
    loadingTranscript.value = false
  }
}

function handlePrint() {
  window.print()
}

// ==================== 4.5 操作 ====================
async function loadReportOptions() {
  if (!isStaff.value) return
  const res = await statsApi.getReportOptions()
  reportOptions.value = res.data
  if (res.data?.current_term) {
    reportQuery.term = res.data.current_term
    reportQuery.start_term = res.data.current_term
    reportQuery.end_term = res.data.current_term
  }
  if (res.data?.major_grades?.length) {
    const first = res.data.major_grades[0]
    honorPair.value = `${first.major_id}|${first.grade}`
    reportQuery.major_id = first.major_id
    reportQuery.grade = first.grade
  }
}

function applyHonorPair(val) {
  if (!val) return
  const [m, g] = val.split('|')
  reportQuery.major_id = m
  reportQuery.grade = Number(g)
}

async function loadGradeReport() {
  if (!isStaff.value) return
  loadingReport.value = true
  try {
    const params = { report_type: reportQuery.report_type }
    if (reportQuery.report_type === 'HONOR_RANK') {
      params.major_id = reportQuery.major_id
      params.grade = reportQuery.grade
      params.start_term = reportQuery.start_term
      params.end_term = reportQuery.end_term
    } else {
      params.term = reportQuery.term || undefined
      params.tc_id = reportQuery.tc_id || undefined
      if (reportQuery.report_type === 'DISTRIBUTION') {
        params.exam_type = reportQuery.exam_type
      }
    }
    const res = await statsApi.getGradeReport(params)
    reportData.value = res.data
  } finally {
    loadingReport.value = false
  }
}

// ==================== 4.6 操作 ====================
async function loadEvalSummaries() {
  if (!isStaff.value) return
  loadingEvalSummary.value = true
  try {
    const res = await statsApi.getEvaluationSummaries({
      term: evalSummaryQuery.term || undefined
    })
    evalSummaryData.value = res.data
  } finally {
    loadingEvalSummary.value = false
  }
}

async function handleRecomputeEvalSummary() {
  recomputingEvalSummary.value = true
  try {
    const res = await statsApi.recomputeEvaluationSummaries({
      term: evalSummaryQuery.term || undefined
    })
    ElMessage.success(`汇总完成：处理 ${res.data.processed_classes} 个教学班（其中零参评 ${res.data.zero_eval_classes} 个）`)
    await loadEvalSummaries()
  } finally {
    recomputingEvalSummary.value = false
  }
}

async function openOfficeEvalDetails() {
  detailDrawerVisible.value = true
  loadingEvalDetails.value = true
  try {
    const res = await statsApi.getEvaluationDetails({
      term: evalSummaryQuery.term || undefined
    })
    officeEvalDetails.value = res.data || []
  } finally {
    loadingEvalDetails.value = false
  }
}

let initializedRole = null
async function initByRole(curRole) {
  if (!curRole || curRole === initializedRole) return
  initializedRole = curRole
  if (isStudent.value) {
    activeTab.value = 'eval'
    await loadPendingEvals()
    await loadMyGrades()
  } else if (canViewAcademicStats.value) {
    activeTab.value = 'academic'
    await Promise.all([
      loadAcademicStats(),
      loadTranscriptStudents(),
      loadReportOptions().then(() => loadGradeReport()),
      loadEvalSummaries()
    ])
  } else if (isStaff.value) {
    activeTab.value = 'reports'
    await loadReportOptions()
    await Promise.all([loadGradeReport(), loadEvalSummaries()])
  }
}

watch(role, (val) => {
  initByRole(val)
}, { immediate: true })
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.kpi-card {
  border-radius: 12px;
}
.kpi-label {
  font-size: 12.5px;
  color: var(--app-text-secondary);
  margin-bottom: 6px;
}
.kpi-val {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary);
}
.kpi-sub {
  font-size: 12px;
  color: var(--app-text-tertiary);
  margin-top: 4px;
}
.text-primary { color: var(--brand-primary); }
.text-success { color: #389e0d; }
.text-danger { color: #cf1322; }
.muted { color: var(--app-text-secondary); font-size: 12.5px; }
.sub-heading { font-weight: 600; margin-bottom: 8px; color: var(--app-text-primary); }
.gate-block-card { border-color: rgba(250, 173, 20, 0.45) !important; background: rgba(250, 173, 20, 0.08) !important; }
.reason-list { margin: 0; padding-left: 20px; color: #cf1322; line-height: 1.8; }
.print-area { margin-top: 12px; }

@media print {
  .no-print,
  :deep(.el-tabs__header) {
    display: none !important;
  }
  :deep(.el-tabs--border-card) {
    border: none !important;
    box-shadow: none !important;
  }
  :deep(.el-tabs__content) {
    padding: 0 !important;
  }
}
</style>
