<template>
  <div v-if="data" class="transcript-sheet" :class="{ 'bilingual-sheet': isBilingual }">
    <!-- 来校交换生中英文正式成绩单版式（对齐 4.4 打印版式） -->
    <template v-if="isBilingual">
      <div class="sheet-header">
        <div class="cn-title">北京科技大学来校交流学生正式成绩单</div>
        <div class="en-title">OFFICIAL ACADEMIC TRANSCRIPT FOR INCOMING EXCHANGE STUDENTS (USTB)</div>
        <div class="meta-bar">
          <span>成绩单编号 Transcript No.: <b>{{ data.transcript_no }}</b></span>
          <span>出具日期 Issue Date: <b>{{ data.issue_date }}</b></span>
          <span>范围 Range: {{ data.term_range_label }}</span>
        </div>
      </div>

      <table class="info-grid">
        <tbody>
          <tr>
            <th>姓名 Name</th>
            <td>{{ data.student?.student_name }}</td>
            <th>本校临时学号 Student ID</th>
            <td>{{ data.student?.student_id }}</td>
          </tr>
          <tr>
            <th>培养层次 Level</th>
            <td>本科交换生 Undergraduate</td>
            <th>交流学期 Term</th>
            <td>{{ data.student?.term_ref || data.term_range_label }}</td>
          </tr>
          <tr>
            <th>派出院校 Home Institution</th>
            <td>{{ data.student?.sender_school_name || '—' }}</td>
            <th>学生类别 Category</th>
            <td>来校交换生 Incoming Exchange</td>
          </tr>
        </tbody>
      </table>

      <table class="course-table">
        <thead>
          <tr>
            <th style="width: 90px">课程号<br /><small>Course ID</small></th>
            <th>课程名称<br /><small>Course Title</small></th>
            <th style="width: 82px">性质<br /><small>Type</small></th>
            <th style="width: 56px">学分<br /><small>Credit</small></th>
            <th style="width: 82px">考核成绩<br /><small>Score/Grade</small></th>
            <th style="width: 68px">本校绩点<br /><small>GP</small></th>
            <th style="width: 96px">备注<br /><small>Remark</small></th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!data.items || data.items.length === 0">
            <td colspan="7" class="empty-cell">所选学期范围内暂无已发布课程成绩 / No published grades in selected range.</td>
          </tr>
          <tr v-for="row in data.items" :key="row.score_id">
            <td class="center">{{ row.course_id }}</td>
            <td>{{ row.course_name }}</td>
            <td class="center">{{ row.course_type_label }}</td>
            <td class="center">{{ row.credit }}</td>
            <td class="center"><b>{{ row.display_score }}</b></td>
            <td class="center">{{ row.grade_point ?? '—' }}</td>
            <td class="center">{{ formatRemark(row) }}</td>
          </tr>
        </tbody>
      </table>

      <div class="summary-bar">
        <b>交流期间修读汇总 Summary：</b>
        修读课程 <b>{{ data.range_summary?.course_count ?? 0 }}</b> 门 ｜
        总修读学分 <b>{{ data.range_summary?.total_credit ?? '0.0' }}</b> ｜
        已获学分 <b>{{ data.range_summary?.earned_credit ?? '0.0' }}</b> ｜
        交流期平均学分绩点（GPA）<b>{{ data.range_summary?.gpa ?? '—' }}</b> ｜
        加权均分 <b>{{ data.range_summary?.avg_score ?? '—' }}</b>
      </div>

      <div class="scale-title">
        本校评分与绩点换算对照说明 / USTB Grading Scale &amp; GPA Conversion Reference（校发〔2021〕46号附表3）
      </div>
      <table class="scale-table">
        <tbody>
          <tr>
            <th>百分制 Score</th>
            <td>90–100</td><td>85–89</td><td>80–84</td><td>75–79</td>
            <td>70–74</td><td>65–69</td><td>60–64</td><td>&lt;60</td>
          </tr>
          <tr>
            <th>五级/两级制</th>
            <td>优</td><td>—</td><td>良 / 合格</td><td>—</td>
            <td>中</td><td>—</td><td>及格</td><td>不及格/不合格</td>
          </tr>
          <tr>
            <th>对应绩点 GP</th>
            <td>4.00</td><td>3.70</td><td>3.40</td><td>3.00</td>
            <td>2.40</td><td>2.00</td><td>1.00</td><td>0.00</td>
          </tr>
        </tbody>
      </table>

      <div class="notes-box">
        <b>出具说明 Notes：</b>{{ data.footer_notice }}
      </div>

      <div class="sign-row">
        <div class="sign-box">
          <div>教务处审核经办人 Prepared by:</div>
          <div class="sign-line">签名：__________________</div>
          <div>日期：{{ data.issue_date }}</div>
        </div>
        <div class="sign-box">
          <div>教务处复核负责人 Verified by:</div>
          <div class="sign-line">签名：__________________</div>
          <div>日期：{{ data.issue_date }}</div>
        </div>
        <div class="sign-box seal-box">
          <div>北京科技大学教务处</div>
          <div>Academic Affairs Office, USTB</div>
          <div class="seal-hint">（公章 / Official Seal）</div>
        </div>
      </div>
    </template>

    <!-- 本校学生标准历次成绩单版式（保留正考、补考、重修、不及格与原等级） -->
    <template v-else>
      <div class="sheet-header">
        <div class="cn-title">北京科技大学学生历次成绩明细单</div>
        <div class="meta-bar">
          <span>明细单号：<b>{{ data.transcript_no }}</b></span>
          <span>学期范围：<b>{{ data.term_range_label }}</b></span>
          <span>生成时间：{{ data.generated_at }}</span>
        </div>
      </div>

      <table class="info-grid">
        <tbody>
          <tr>
            <th>姓名</th>
            <td>{{ data.student?.student_name }}</td>
            <th>学号</th>
            <td>{{ data.student?.student_id }}</td>
          </tr>
          <tr>
            <th>培养层次 / 年级</th>
            <td>{{ data.student?.degree_level || '—' }} / {{ data.student?.grade || '—' }}级</td>
            <th>学院 / 班级</th>
            <td>{{ data.student?.college_name || data.student?.college_id || '—' }} · {{ data.student?.class_name || '—' }}</td>
          </tr>
        </tbody>
      </table>

      <table class="course-table">
        <thead>
          <tr>
            <th style="width: 102px">学期</th>
            <th style="width: 82px">课程号</th>
            <th>课程名称</th>
            <th style="width: 52px">学分</th>
            <th style="width: 68px">修读</th>
            <th style="width: 72px">考试</th>
            <th style="width: 72px">成绩</th>
            <th style="width: 56px">绩点</th>
            <th style="width: 80px">标记/情况</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!data.items || data.items.length === 0">
            <td colspan="9" class="empty-cell">所选学期范围内暂无已发布成绩记录。</td>
          </tr>
          <tr v-for="row in data.items" :key="row.score_id">
            <td class="center">{{ row.term }}</td>
            <td class="center">{{ row.course_id }}</td>
            <td>
              {{ row.course_name }}
              <span v-if="row.record_source === 'EXCHANGE'" class="inline-note">（外校认定：{{ row.host_school }}）</span>
            </td>
            <td class="center">{{ row.credit }}</td>
            <td class="center">{{ STUDY_TYPE_MAP[row.study_type] || '初修' }}</td>
            <td class="center">{{ row.exam_type === 2 ? '补考' : '正常考试' }}</td>
            <td class="center"><b>{{ row.display_score }}</b></td>
            <td class="center">{{ row.grade_point ?? '—' }}</td>
            <td class="center">{{ formatStandardFlag(row) }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="data.cumulative_summary" class="summary-bar">
        <b>本阶段累计学业摘要（按最高最终成绩计算，排除辅修）：</b>
        已获学分 <b>{{ data.cumulative_summary.earned_credit }}</b> ｜
        不及格学分 <b>{{ data.cumulative_summary.failed_credit }}</b> ｜
        平均学分绩点 GPA <b>{{ data.cumulative_summary.gpa ?? '—' }}</b> ｜
        加权平均分 <b>{{ data.cumulative_summary.avg_score ?? '—' }}</b> ｜
        通用专业排名 <b>{{ data.cumulative_summary.major_rank ? `${data.cumulative_summary.major_rank} / ${data.cumulative_summary.major_rank_total}` : '—' }}</b>
      </div>

      <div class="notes-box">
        <b>说明：</b>{{ data.footer_notice }}
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Object, default: null }
})

const STUDY_TYPE_MAP = { 1: '初修', 2: '重修', 3: '辅修' }
const EXAM_FLAG_MAP = { 0: '正常', 1: '缺考', 2: '缓考', 3: '违纪' }

const isBilingual = computed(() => props.data?.template === 'EXCHANGE_BILINGUAL')

function formatRemark(row) {
  if (row.score_type === 2) return '五级制'
  if (row.score_type === 3) return '两级制'
  if (row.exam_flag && row.exam_flag !== 0) return EXAM_FLAG_MAP[row.exam_flag] || '异常'
  return row.exam_type === 2 ? '补考' : '正常考试'
}

function formatStandardFlag(row) {
  const tags = []
  if (row.exam_flag && row.exam_flag !== 0) tags.push(EXAM_FLAG_MAP[row.exam_flag])
  if (row.study_type === 3) tags.push('辅修')
  if (row.is_final === 1) tags.push('最终')
  return tags.length ? tags.join(' / ') : '历次'
}
</script>

<style scoped>
.transcript-sheet {
  background: #ffffff;
  border: 1px solid #1e293b;
  padding: 24px 28px;
  max-width: 820px;
  margin: 0 auto;
  color: #0f172a;
  font-size: 13px;
  line-height: 1.5;
}
.sheet-header {
  text-align: center;
  margin-bottom: 14px;
}
.cn-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}
.en-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  margin-top: 2px;
}
.meta-bar {
  display: flex;
  justify-content: space-between;
  background: #f8fafc;
  border: 1px solid #334155;
  padding: 6px 12px;
  margin-top: 10px;
  font-size: 12px;
}
.info-grid,
.course-table,
.scale-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
}
.info-grid th,
.info-grid td,
.course-table th,
.course-table td,
.scale-table th,
.scale-table td {
  border: 1px solid #334155;
  padding: 6px 8px;
}
.info-grid th,
.course-table th,
.scale-table th {
  background: #f1f5f9;
  font-weight: 600;
  text-align: center;
}
.course-table tr {
  break-inside: avoid;
}
.scale-table td {
  text-align: center;
  font-size: 12px;
}
.scale-title {
  background: #e2e8f0;
  border: 1px solid #334155;
  border-bottom: none;
  padding: 5px 10px;
  font-weight: 600;
  font-size: 12px;
}
.center {
  text-align: center;
}
.empty-cell {
  text-align: center;
  color: #64748b;
  padding: 20px !important;
}
.inline-note {
  font-size: 12px;
  color: #475569;
}
.summary-bar {
  background: #f8fafc;
  border: 1px solid #334155;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 12.5px;
}
.notes-box {
  border: 1px solid #334155;
  padding: 8px 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: #334155;
}
.sign-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
}
.sign-box {
  border: 1px solid #334155;
  padding: 10px 12px;
  min-height: 92px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.sign-box + .sign-box {
  border-left: none;
}
.seal-box {
  text-align: center;
  justify-content: center;
  background: #f8fafc;
  border-style: dashed;
}
.seal-hint {
  margin-top: 10px;
  color: #64748b;
}
</style>
