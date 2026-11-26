<template>
  <section class="selection-page" v-loading="busy">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="notice" />
    <el-alert v-if="!isStudent && !isStaff" title="当前账号无权访问该模块。" type="info" :closable="false" />
    <template v-else>
      <div class="filters">
        <div class="filters-left">
          <label>学年学期</label>
          <el-select v-model="term" style="width:170px" @change="changeTerm"><el-option v-for="t in context.terms" :key="t" :label="t" :value="t" /></el-select>
          <template v-if="isStudent || active==='screen'">
            <label>选课轮次</label>
            <el-select v-model="roundId" style="width:340px" placeholder="选择已发布轮次" @change="changeRound">
              <el-option v-for="r in termRounds" :key="r.round_id" :value="r.round_id" :label="r.round_name + (r.window_open ? ' · 开放中' : ' · 非办理时段')" />
            </el-select>
          </template>
        </div>
        <el-button @click="perform(refresh)">刷新</el-button>
      </div>
      <div v-if="isStudent" class="summary">
        <span>本学期已选中 <strong>{{ summary.courses ?? '—' }}</strong> 门</span>
        <span>有效学分 <strong>{{ summary.credits ?? '—' }}</strong></span>
      </div>
      <el-tabs v-model="active" type="border-card" @tab-change="() => perform(refresh)">
        <template v-if="isStudent">
          <el-tab-pane label="课程查询与选课" name="courses">
            <el-alert v-if="selectedRound" :title="selectedRound.round_name" :description="`${formatTime(selectedRound.start_time)} 至 ${formatTime(selectedRound.end_time)}；${selectedRound.notice_content || '请按本轮规则办理'}`" :type="selectedRound.window_open?'info':'warning'" :closable="false" class="notice" />
            <div class="toolbar">
              <el-input v-model="keyword" placeholder="输入课程代码或名称" clearable style="width:280px" @keyup.enter="searchCourses" />
              <el-select v-model="courseType" aria-label="课程类型" placeholder="全部课程类型" clearable style="width:180px" @change="searchCourses">
                <el-option v-for="type in context.course_types" :key="type.value" :value="type.value" :label="type.label" />
              </el-select>
              <el-button type="primary" @click="searchCourses">查询</el-button>
              <el-button @click="resetCourses">重置</el-button>
            </div>
            <el-table :data="courses" row-key="course_id" border empty-text="当前条件下没有开放课程">
              <el-table-column type="expand"><template #default="{row}">
                <div class="class-detail"><el-table :data="row.classes" size="small" border>
                  <el-table-column prop="tc_id" label="教学班号" min-width="150" />
                  <el-table-column label="面向对象" min-width="230">
                    <template #default="{ row: tc }"><SelectionAudience :audience="tc.audience" detailed /></template>
                  </el-table-column>
                  <el-table-column label="上课安排" min-width="200"><template #default="{row:tc}"><div v-for="(s,i) in tc.schedule" :key="i">{{ slotText(s) }}</div><span v-if="!tc.schedule.length" class="muted">排课待确认</span></template></el-table-column>
                  <el-table-column label="已选 / 容量" width="110"><template #default="{row:tc}">{{tc.actual_num}} / {{tc.capacity}}</template></el-table-column>
                  <el-table-column label="选课状态" width="115"><template #default="{row:tc}"><el-tag :type="statusType(tc.select_status)">{{statusText(tc.select_status)}}</el-tag></template></el-table-column>
                  <el-table-column v-if="selectedRound?.select_mode===2" label="各档志愿人数" min-width="150"><template #default="{row:tc}">{{ tc.preferences.map(p => `${p.priority ?? '未分档'}志愿 ${p.applicants}人`).join('；') || '暂无申请' }}</template></el-table-column>
                  <el-table-column label="办理" width="130"><template #default="{row:tc}"><el-button size="small" type="primary" :disabled="!canApply(tc)" @click="openApply(tc)">{{ selectedRound?.select_mode===2?'登记志愿':'选课' }}</el-button></template></el-table-column>
                </el-table></div>
              </template></el-table-column>
              <el-table-column prop="course_id" label="课程代码" width="125" />
              <el-table-column prop="course_name" label="课程名称" min-width="170" />
              <el-table-column prop="course_type_label" label="课程类型" width="115" />
              <el-table-column label="面向对象" min-width="230">
                <template #default="{ row }">
                  <span v-if="row.audience === null" class="muted">各教学班要求不同，展开查看</span>
                  <SelectionAudience v-else :audience="row.audience" />
                </template>
              </el-table-column>
              <el-table-column prop="credit" label="学分" width="75" />
              <el-table-column label="教学班" width="85"><template #default="{row}">{{row.classes.length}} 个</template></el-table-column>
            </el-table>
            <el-pagination v-model:current-page="coursePage" :page-size="10" :total="courseTotal" layout="total, prev, pager, next" @current-change="() => perform(loadCourses)" />
          </el-tab-pane>
          <el-tab-pane label="已选" name="results">
            <div class="toolbar"><el-input v-model="resultKeyword" placeholder="课程名称或教学班号" clearable style="width:260px" /><el-select v-model="statusFilter" clearable placeholder="全部状态" style="width:150px"><el-option v-for="(s,i) in statuses" :key="i" :label="s" :value="i" /></el-select><el-button @click="active='timetable';perform(loadTimetable)">查看课表</el-button></div>
            <el-table :data="filteredResults" border empty-text="没有匹配的选课记录">
              <el-table-column type="expand"><template #default="{row}"><div class="class-detail"><div v-for="(s,i) in row.schedule" :key="i">{{slotText(s)}}</div><span v-if="!row.schedule.length">排课资料待确认</span><div v-if="row.no_open_reason" class="muted" style="margin-top:6px">停开说明：{{ row.no_open_reason }}</div></div></template></el-table-column>
              <el-table-column prop="course_name" label="课程名称" min-width="200" /><el-table-column prop="tc_id" label="教学班" min-width="150" /><el-table-column prop="credit" label="学分" width="80" />
              <el-table-column label="修读类别" width="95"><template #default="{row}">{{studyText(row.study_type)}}</template></el-table-column>
              <el-table-column label="状态" width="115"><template #default="{row}"><el-tag :type="resultStatusType(row)">{{resultStatusText(row)}}</el-tag></template></el-table-column>
              <el-table-column label="免听" width="85"><template #default="{row}">{{row.is_exempt_attend===1?'冲突免听':'正常'}}</template></el-table-column>
              <el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="danger" :disabled="!row.withdraw_round_id" @click="withdraw(row)">退选</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="课表" name="timetable">
            <div class="toolbar timetable-toolbar">
              <el-radio-group v-model="timetableMode" @change="onTimetableModeChange">
                <el-radio-button value="week">周课表</el-radio-button>
                <el-radio-button value="full">总课表</el-radio-button>
              </el-radio-group>
              <template v-if="timetableMode === 'week'">
                <el-button :disabled="week <= 1" @click="week--; perform(loadTimetable)">上一周</el-button>
                <el-select v-model="week" style="width: 120px" @change="() => perform(loadTimetable)">
                  <el-option v-for="w in WEEK_OPTIONS" :key="w" :value="w" :label="`第${w}周`" />
                </el-select>
                <el-button :disabled="week >= 16" @click="week++; perform(loadTimetable)">下一周</el-button>
              </template>
            </div>
            <el-alert v-if="timetableMode === 'week' && timetable.missing_schedule?.length" :title="`以下有效课程排课缺失：${timetable.missing_schedule.join('、')}`" type="warning" class="notice" :closable="false" />
            <div class="ustb-schedule-wrap">
              <div class="ustb-schedule-grid">
                <div class="schedule-header-cell">节次</div>
                <div v-for="day in WEEKDAYS" :key="day" class="schedule-header-cell">{{ day }}</div>
                <template v-for="(period, periodIdx) in PERIOD_TIMES" :key="period.label">
                  <div class="schedule-period-cell">
                    <div class="period-label">{{ period.label }}</div>
                    <div class="period-time">{{ period.time }}</div>
                  </div>
                  <template v-for="(_, dayIdx) in WEEKDAYS" :key="`${periodIdx}-${dayIdx}`">
                    <div
                      v-if="!scheduleGrid[periodIdx]?.[dayIdx]?.length"
                      class="schedule-empty-cell"
                    />
                    <div v-else class="schedule-cell">
                      <el-tooltip
                        v-for="(b, idx) in scheduleGrid[periodIdx][dayIdx]"
                        :key="`${b.tc_id}-${b.week_range}-${idx}`"
                        :content="`${b.course_name} · ${b.week_range}周 · ${b.room_id}`"
                        placement="top"
                      >
                        <button
                          type="button"
                          class="schedule-course-card"
                          :style="{ background: courseColorMap[b.course_name] || COURSE_COLORS[0] }"
                          @click="showSchedule(b)"
                        >
                          <div class="course-card-name">{{ b.course_name }}</div>
                          <div class="course-card-weeks">{{ b.week_range }}周</div>
                          <div class="course-card-room">{{ b.room_id }}</div>
                          <span v-if="b.is_exempt_attend === 1" class="course-card-badge">免听</span>
                        </button>
                      </el-tooltip>
                    </div>
                  </template>
                </template>
              </div>
            </div>
          </el-tab-pane>
        </template>
        <el-tab-pane v-if="isStaff" label="上课名单与调整" name="roster">
          <div class="toolbar"><el-select v-model="tcId" filterable placeholder="选择教学班" style="width:400px" @change="() => perform(loadRoster)"><el-option v-for="c in teachingClasses" :key="c.tc_id" :value="c.tc_id" :label="`${c.tc_id} · ${c.course_name}${c.tc_status !== '4' ? '（已停开）' : ''}`" /></el-select><el-input v-model="rosterKeyword" placeholder="学号或姓名" clearable style="width:200px" @keyup.enter="perform(loadRoster)" /><el-button @click="perform(loadRoster)">查询</el-button><el-button @click="printRoster" :disabled="!roster.teaching_class">打印名单</el-button><el-button type="primary" @click="openAdjustment()" :disabled="!tcId || isCurrentRosterStopped">补加学生</el-button></div>
          <el-alert v-if="isCurrentRosterStopped" title="该教学班已停开：无需录入成绩，且不计入教师教学工作量。" :description="roster.teaching_class?.no_open_reason ? `停开原因：${roster.teaching_class.no_open_reason}` : ''" type="warning" show-icon :closable="false" class="notice" />
          <div v-if="roster.teaching_class" class="roster-summary">{{roster.teaching_class.course_name}} · {{tcId}} · 有效人数 {{roster.effective_count}} / 容量 {{roster.teaching_class.capacity}}<p>{{roster.teaching_class.schedule.map(slotText).join('；')}}</p></div>
          <el-table :data="roster.items || []" border empty-text="当前条件下没有有效选课学生"><el-table-column type="index" label="序号" width="65" /><el-table-column prop="student_id" label="学号" min-width="150" /><el-table-column prop="student_name" label="姓名" min-width="120" /><el-table-column prop="class_name" label="行政班" min-width="150" /><el-table-column label="选课类别" width="110"><template #default="{row}">{{studyText(row.study_type)}}</template></el-table-column><el-table-column label="免听标记" width="110"><template #default="{row}">{{row.is_exempt_attend===1?'冲突免听':'正常跟班'}}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="danger" :disabled="isCurrentRosterStopped" @click="openAdjustment(row)">移出名单</el-button></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane v-if="isOffice" label="截止筛选" name="screen"><el-alert title="按志愿优先级依次录取，同档超额随机筛选。" type="info" :closable="false" class="notice" /><p v-if="selectedRound">{{selectedRound.round_name}} · 截止 {{formatTime(selectedRound.end_time)}}</p><el-button type="primary" :disabled="!canScreen" @click="screen">执行本轮截止筛选</el-button><p v-if="screenResult">{{screenResult}}</p></el-tab-pane>
        <el-tab-pane v-if="isOffice" label="停开处理" name="stop">
          <el-alert :title="stop.reason || `当前最低开班人数为 ${stop.min_open_students || 6} 人；选课人数不足的教学班确认停开后将清退学生选课、关闭成绩录入通道，且不计入教师教学工作量。`" type="warning" show-icon :closable="false" class="notice" />
          <div class="toolbar">
            <el-tag type="warning">人数不足（低于 {{ stop.min_open_students || 6 }} 人）：{{ stop.under_min_count || 0 }} 个</el-tag>
            <el-tag type="info">已停开：{{ stop.stopped_count || 0 }} 个</el-tag>
            <el-button @click="() => perform(refresh)">刷新列表</el-button>
          </div>
          <el-table :data="stop.items || []" border empty-text="当前学期暂无人数不足或已停开的教学班">
            <el-table-column prop="tc_id" label="教学班" width="135" />
            <el-table-column prop="course_name" label="课程名称" min-width="150" />
            <el-table-column prop="teacher_name" label="任课教师" width="100" />
            <el-table-column label="学时" width="75"><template #default="{ row }">{{ row.hours ?? '—' }}</template></el-table-column>
            <el-table-column label="有效人数 / 最低标准" width="150">
              <template #default="{ row }">
                <el-tag :type="row.stop_category === 'UNDER_MIN' ? 'danger' : 'info'" size="small">
                  {{ row.active_count ?? 0 }} / {{ row.min_open_students || 6 }} 人
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="affected_count" label="受影响学生" width="100" />
            <el-table-column label="状态" width="165">
              <template #default="{ row }">
                <el-tag :type="stopCategoryTagType(row.stop_category)" size="small">{{ row.status_label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="停开原因与工作量说明" min-width="230">
              <template #default="{ row }">
                <div v-if="row.no_open_reason">{{ row.no_open_reason }}</div>
                <div class="muted">{{ row.workload_note }}</div>
                <div v-if="row.block_reason" class="stop-block-note">{{ row.block_reason }}</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="125" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.can_stop" type="danger" link :loading="busy" @click="openStopDialog(row)">
                  {{ row.stop_category === 'STOPPED_PENDING_CLEANUP' ? '清退并完结' : '确认停开' }}
                </el-button>
                <span v-else class="muted">{{ row.stop_category === 'STOPPED' ? '不计入工作量' : '受保护' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-dialog v-model="applyVisible" title="选课申请" width="620px" :close-on-click-modal="false">
      <template v-if="target">
        <p><strong>{{ target.course_name }}</strong> · {{ target.tc_id }} · {{ target.credit }} 学分</p>
        <el-descriptions :column="1" border size="small" class="notice">
          <el-descriptions-item label="课程类型">{{ target.course_type_label }}</el-descriptions-item>
          <el-descriptions-item label="面向对象"><SelectionAudience :audience="target.audience" detailed /></el-descriptions-item>
        </el-descriptions>
        <p v-for="(s, i) in target.schedule" :key="i">{{ slotText(s) }}</p>
        <el-form label-width="90px">
          <el-form-item label="选课类别">
            <el-select v-model="application.studyType" @change="checkResult = ''">
              <el-option label="初修" :value="1" />
              <el-option label="重修" :value="2" />
              <el-option v-if="target.tc_type === '3'" label="辅修" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedRound?.select_mode === 2" label="志愿档位">
            <el-select v-model="application.priority" @change="checkResult = ''">
              <el-option v-for="n in 3" :key="n" :value="n" :label="`第${n}志愿`" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert v-if="checkResult" :title="checkResult" type="info" :closable="false" />
      </template>
      <template #footer><el-button @click="applyVisible=false">取消</el-button><el-button :loading="busy" @click="precheck">先校验</el-button><el-button type="primary" :loading="busy" @click="enroll">确认提交</el-button></template>
    </el-dialog>
    <el-dialog v-model="adjustVisible" :title="adjustment.operation==='add'?'补加学生':'移出名单'" width="520px" :close-on-click-modal="false"><el-form label-width="90px"><el-form-item label="学号"><el-input v-model="adjustment.studentId" maxlength="20" :disabled="adjustment.operation==='remove'" /></el-form-item><el-form-item v-if="adjustment.operation==='add'" label="修读类别"><el-select v-model="adjustment.studyType"><el-option v-for="(s,i) in ['初修','重修','辅修']" :key="s" :label="s" :value="i+1" /></el-select></el-form-item><el-form-item label="调整原因" required><el-input v-model="adjustment.reason" type="textarea" maxlength="200" show-word-limit :rows="3" /></el-form-item></el-form><template #footer><el-button @click="adjustVisible=false">取消</el-button><el-button type="primary" :loading="busy" @click="submitAdjustment">确认调整</el-button></template></el-dialog>
    <el-dialog v-model="stopDialogVisible" title="确认停开教学班" width="540px" :close-on-click-modal="false">
      <template v-if="stopTarget">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          class="notice"
          :title="`确认停开 ${stopTarget.course_name}（${stopTarget.tc_id}）？`"
          :description="`当前有效人数 ${stopTarget.active_count ?? 0} 人（低于最低开班人数 ${stopTarget.min_open_students || 6} 人），将清退 ${stopTarget.affected_count ?? 0} 条选课记录；停开后任课教师无需录入成绩，且该课程不计入教师教学工作量。`"
        />
        <el-form label-width="90px">
          <el-form-item label="停开原因">
            <el-input v-model="stopReason" type="textarea" maxlength="100" show-word-limit :rows="3" placeholder="请输入停开原因（留空则默认按人数不足停开）" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="stopDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="busy" @click="confirmStopClass">确认停开</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="scheduleVisible" title="课程详情" width="480px">
      <el-descriptions v-if="selectedBlock" :column="1" border size="small">
        <el-descriptions-item label="课程名称">{{ selectedBlock.course_name }}</el-descriptions-item>
        <el-descriptions-item label="教学班号">{{ selectedBlock.tc_id }}</el-descriptions-item>
        <el-descriptions-item label="上课周次">
          <el-tag size="small">{{ selectedBlock.week_range }}周</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上课地点">{{ selectedBlock.room_id }}</el-descriptions-item>
        <el-descriptions-item label="节次">
          第 {{ selectedBlock.start_slot }}—{{ selectedBlock.end_slot }} 节
        </el-descriptions-item>
        <el-descriptions-item label="听课方式">
          {{ selectedBlock.is_exempt_attend === 1 ? '冲突免听' : '正常跟班' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="scheduleDetail.length > 1" label="全部排课">
          <div v-for="(s, i) in scheduleDetail" :key="i">{{ slotText(s) }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="scheduleVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <div v-if="printData" class="print-sheet"><h2>上课名单</h2><p>学期：{{term}}　课程：{{printData.teaching_class.course_name}}　教学班：{{tcId}}</p><p>学分：{{printData.teaching_class.credit}}　容量：{{printData.teaching_class.capacity}}　有效人数：{{printData.effective_count}}</p><p>{{printData.teaching_class.schedule.map(slotText).join('；')}}</p><table><thead><tr><th>序号</th><th>学号</th><th>姓名</th><th>行政班</th><th>类别</th><th>免听</th></tr></thead><tbody><tr v-for="(s,i) in printData.items" :key="s.sc_id"><td>{{i+1}}</td><td>{{s.student_id}}</td><td>{{s.student_name}}</td><td>{{s.class_name}}</td><td>{{studyText(s.study_type)}}</td><td>{{s.is_exempt_attend===1?'是':'否'}}</td></tr></tbody></table><p>数据时间：{{new Date().toLocaleString()}}　任课教师签字：____________</p></div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectionApi as api } from '../../api/selection'
import { userState } from '../../stores/user'
import { themeState } from '../../stores/theme'
import SelectionAudience from './SelectionAudience.vue'

const PERIOD_TIMES = [
  { label: '1-2节', time: '08:00-09:35' },
  { label: '3-4节', time: '09:55-11:30' },
  { label: '5-6节', time: '13:30-15:05' },
  { label: '7-8节', time: '15:20-16:55' },
  { label: '9-10节', time: '17:10-18:45' },
  { label: '11-12节', time: '19:30-21:05' },
]
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五']
const WEEK_OPTIONS = Array.from({ length: 16 }, (_, i) => i + 1)
const COURSE_COLORS = [
  '#e6f7ff', '#fff7e6', '#f6ffed', '#fff1f0', '#f9f0ff',
  '#e6fffb', '#fcffe6', '#fff0f6', '#f0f5ff', '#fffbe6',
]
const COURSE_COLORS_DARK = [
  '#13293d', '#3d2e13', '#1e3d13', '#3d1a1a', '#2d1a3d',
  '#133d3d', '#333d13', '#3d1330', '#1a2440', '#3d3613',
]

const context = ref({ rounds: [], terms: [], user: {} }), busy = ref(false), error = ref(''), active = ref('courses')
const term = ref('2026-2027-1'), roundId = ref(null), keyword = ref(''), coursePage = ref(1), courses = ref([]), courseTotal = ref(0), summary = ref({})
const courseType = ref('')
const resultRows = ref([]), resultKeyword = ref(''), statusFilter = ref(null), week = ref(1), timetableMode = ref('week'), timetable = ref({ blocks: [] })
const teachingClasses = ref([]), tcId = ref(''), rosterKeyword = ref(''), roster = ref({}), stop = ref({}), screenResult = ref('')
const applyVisible = ref(false), target = ref(null), application = ref({}), checkResult = ref(''), adjustVisible = ref(false), adjustment = ref({}), printData = ref(null)
const stopDialogVisible = ref(false), stopTarget = ref(null), stopReason = ref('')
const scheduleVisible = ref(false), scheduleDetail = ref([]), selectedBlock = ref(null)
const statuses = ['待筛选','已选中','未选中','已退选'], days = ['一','二','三','四','五','六','日']
const isStudent = computed(() => context.value.user.role === 'STUDENT')
const isOffice = computed(() => context.value.user.role === 'ACADEMIC_OFFICE')
const isStaff = computed(() => isOffice.value || context.value.user.role === 'TEACHER')
const isCurrentRosterStopped = computed(() => Boolean(roster.value.teaching_class && roster.value.teaching_class.tc_status !== '4'))
const termRounds = computed(() => context.value.rounds.filter(r => r.term === term.value))
const selectedRound = computed(() => termRounds.value.find(r => r.round_id === roundId.value))
const canScreen = computed(() => selectedRound.value?.select_mode === 2 && Date.parse(selectedRound.value.end_time) <= Date.parse(context.value.server_time))
const filteredResults = computed(() => resultRows.value.filter(r => (statusFilter.value == null || r.select_status === statusFilter.value) && `${r.course_name} ${r.tc_id}`.toLowerCase().includes(resultKeyword.value.toLowerCase())))
const statusText = s => s == null ? '未选课' : (statuses[s] || '状态待确认')
const statusType = s => ({0:'warning',1:'success',2:'info',3:'info'}[s] || 'info')
const resultStatusText = row => (row.select_status === 3 && ['2', '5'].includes(String(row.tc_status || ''))) ? '课程停开' : statusText(row.select_status)
const resultStatusType = row => (row.select_status === 3 && ['2', '5'].includes(String(row.tc_status || ''))) ? 'danger' : statusType(row.select_status)
const stopCategoryTagType = cat => ({ UNDER_MIN: 'warning', STOPPED_PENDING_CLEANUP: 'danger', STOPPED: 'info' }[cat] || 'info')
const studyText = s => ({1:'初修',2:'重修',3:'辅修'}[s] || '待确认')
const formatTime = t => String(t || '').replace('T',' ')
const slotText = s => `${s.week_range}周 · 周${days[Number(s.week_day)-1] || '?'} · ${s.start_slot}—${s.end_slot}节 · ${s.room_id}`
const displayBlocks = computed(() => {
  if (timetableMode.value === 'week') {
    return timetable.value.blocks || []
  }
  const blocks = []
  for (const row of resultRows.value) {
    if (row.select_status !== 1) continue
    for (const slot of (row.schedule || [])) {
      blocks.push({
        ...slot,
        course_name: row.course_name,
        tc_id: row.tc_id,
        is_exempt_attend: row.is_exempt_attend,
      })
    }
  }
  return blocks
})
const courseColorMap = computed(() => {
  const palette = themeState.resolved === 'dark' ? COURSE_COLORS_DARK : COURSE_COLORS
  const map = {}
  const names = []
  for (const b of displayBlocks.value) {
    if (b.course_name && !names.includes(b.course_name)) names.push(b.course_name)
  }
  names.forEach((name, idx) => {
    map[name] = palette[idx % palette.length]
  })
  return map
})
const scheduleGrid = computed(() => {
  const grid = Array.from({ length: PERIOD_TIMES.length }, () =>
    Array.from({ length: WEEKDAYS.length }, () => [])
  )
  for (const b of displayBlocks.value) {
    const startIdx = Math.floor((Number(b.start_slot) - 1) / 2)
    const endIdx = Math.floor((Number(b.end_slot) - 1) / 2)
    const dayIdx = Number(b.week_day) - 1
    if (dayIdx < 0 || dayIdx >= WEEKDAYS.length) continue
    for (let p = startIdx; p <= endIdx; p += 1) {
      if (p >= 0 && p < PERIOD_TIMES.length) {
        grid[p][dayIdx].push(b)
      }
    }
  }
  return grid
})
const canApply = tc => selectedRound.value?.window_open && ![0,1].includes(tc.select_status) && (selectedRound.value.select_mode === 2 || tc.actual_num < tc.capacity)
async function perform(task) { if(busy.value) return; busy.value=true; error.value=''; try { await task() } catch(e) { if(e !== 'cancel' && e !== 'close') error.value=e?.response?.data?.message || e?.message || '操作失败，请刷新核实' } finally { busy.value=false } }
async function loadCourses() {
  if (!roundId.value) {
    courses.value = []
    courseTotal.value = 0
    return
  }
  const r = await api.courses({
    roundId: roundId.value,
    keyword: keyword.value,
    courseType: courseType.value || undefined,
    page: coursePage.value,
    pageSize: 10
  })
  courses.value = r.data.items
  courseTotal.value = r.data.total
  summary.value = r.data.summary
}
async function loadResults(){const r=await api.results(term.value);resultRows.value=r.data.items;summary.value=r.data.summary}
async function loadTimetable(){
  if (timetableMode.value === 'full') {
    await loadResults()
    return
  }
  const r = await api.timetable(term.value, week.value)
  timetable.value = r.data
  summary.value = r.data.summary
}
function onTimetableModeChange() {
  perform(loadTimetable)
}
async function loadRoster(){ if(!tcId.value){roster.value={};return} roster.value=(await api.roster(tcId.value,rosterKeyword.value)).data }
async function loadClasses(){teachingClasses.value=(await api.teachingClasses(term.value)).data;if(!teachingClasses.value.some(c => c.tc_id===tcId.value))tcId.value=teachingClasses.value[0]?.tc_id || '';await loadRoster()}
async function refresh(){if(active.value==='courses')await loadCourses();if(active.value==='results')await loadResults();if(active.value==='timetable')await loadTimetable();if(active.value==='roster')await loadClasses();if(active.value==='stop')stop.value=(await api.stopPreview(term.value)).data}
function chooseRound(){roundId.value=(termRounds.value.find(r=>r.window_open)||termRounds.value[0])?.round_id || null}
function changeTerm(){chooseRound();coursePage.value=1;summary.value={};perform(refresh)}
function changeRound(){coursePage.value=1;perform(refresh)}
function searchCourses(){coursePage.value=1;perform(loadCourses)}
function resetCourses(){keyword.value='';courseType.value='';searchCourses()}
function openApply(tc){target.value=tc;application.value={roundId:roundId.value,tcId:tc.tc_id,studyType:1,priority:selectedRound.value.select_mode===2?1:null,expectedVersion:tc.version || null};checkResult.value='';applyVisible.value=true}
function precheck(){perform(async()=>{try{checkResult.value=(await api.check(application.value)).data.message}catch(e){checkResult.value=e?.response?.data?.message || '校验未完成，请稍后重试';throw e}})}
function enroll(){perform(async()=>{try{const r=await api.enroll(application.value);ElMessage.success(r.data.message);applyVisible.value=false;await loadCourses()}catch(e){if(!e.response){await loadResults();await loadCourses();applyVisible.value=false;error.value='提交未获得明确回执，已重新读取选课记录，请核对结果后再办理。';active.value='results';return}throw e}})}
function withdraw(row){perform(async()=>{await ElMessageBox.confirm(`确认退选 ${row.course_name}（${row.tc_id}）？`,'退选确认',{type:'warning'});try{const r=await api.withdraw(row.sc_id,{roundId:row.withdraw_round_id,expectedVersion:row.version,confirmed:true});ElMessage.success(r.data.message)}catch(e){if(!e.response){error.value='退选结果待核实，正在重新读取选课记录。'}else throw e}await loadResults()})}
function showSchedule(block){selectedBlock.value=block;const found=resultRows.value.find(r=>r.tc_id===block.tc_id);perform(async()=>{if(!found)await loadResults();scheduleDetail.value=resultRows.value.find(r=>r.tc_id===block.tc_id)?.schedule || [];scheduleVisible.value=true})}
function openAdjustment(row){adjustment.value={studentId:row?.student_id || '',operation:row?'remove':'add',studyType:1,reason:''};adjustVisible.value=true}
function submitAdjustment(){perform(async()=>{if(!adjustment.value.reason?.trim())throw new Error('请填写调整原因');try{const r=await api.adjust(tcId.value,adjustment.value);ElMessage.success(r.data.message);adjustVisible.value=false}catch(e){if(!e.response){adjustVisible.value=false;error.value='调整结果待核实，已刷新名单，请核对后再办理。'}else throw e}await loadRoster()})}
function screen(){perform(async()=>{await ElMessageBox.confirm('确认执行本轮截止筛选？将按实际余量和志愿重新校验所有待筛选申请。','截止筛选',{type:'warning'});const r=await api.screen(roundId.value);screenResult.value=`${r.data.message}；本次录取 ${r.data.accepted} 人，未选中 ${r.data.rejected} 人。`})}
function openStopDialog(row){stopTarget.value=row;stopReason.value=row.no_open_reason || `选课人数不足${row.min_open_students || 6}人停开`;stopDialogVisible.value=true}
function confirmStopClass(){if(!stopTarget.value)return;perform(async()=>{const r=await api.stopClasses({tcIds:[stopTarget.value.tc_id],reason:stopReason.value});ElMessage.success(r.message || r.data?.message || '已完成停开处理');stopDialogVisible.value=false;stopTarget.value=null;await refresh()})}
function printRoster(){perform(async()=>{printData.value=(await api.roster(tcId.value,'')).data;await nextTick();window.print()})}
async function bootContext() {
  context.value = (await api.context()).data
  term.value = context.value.rounds.find(r => r.window_open)?.term || context.value.terms[0] || term.value
  chooseRound()
  active.value = isStudent.value ? 'courses' : 'roster'
  if (isStudent.value || isStaff.value) await refresh()
}
watch(() => userState.currentKey, () => perform(bootContext))
onMounted(() => perform(bootContext))
</script>

<style scoped>
.selection-page{max-width:1600px;margin:0 auto}
.heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}
.heading h2{font-size:20px;font-weight:700;margin:0 0 4px;color:var(--app-text-primary)}
.heading p{margin:0;color:var(--app-text-secondary);font-size:13px}
.filters,.toolbar{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}
.filters{justify-content:space-between;padding:14px 18px;background:var(--app-card-bg);border:1px solid var(--app-border);border-radius:14px;box-shadow:var(--app-shadow-sm)}
.filters-left{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.filters label{font-size:13px;color:var(--app-text-secondary)}
.summary{display:flex;align-items:baseline;gap:28px;padding:15px 20px;background:var(--app-menu-active-bg);border:1px solid var(--app-border);border-radius:12px;margin-bottom:18px;color:var(--app-text-primary);font-size:14px;line-height:1.4}
.summary > span{display:inline-flex;align-items:baseline;gap:4px}
.summary strong{font-size:20px;line-height:1;color:var(--brand-primary);margin:0 2px}
.notice{margin-bottom:16px}
.class-detail{padding:16px 20px;background:var(--app-card-muted-bg)}
.muted{font-size:12px;color:var(--app-text-secondary);line-height:1.7}
.el-pagination{margin-top:16px;justify-content:flex-end}
.timetable-toolbar{margin-bottom:14px}
.ustb-schedule-wrap{overflow-x:auto;border:1px solid var(--app-grid-line);border-radius:10px;background:var(--app-card-bg)}
.ustb-schedule-grid{display:grid;grid-template-columns:72px repeat(5,minmax(120px,1fr));gap:1px;background:var(--app-grid-line)}
.schedule-header-cell{padding:10px 8px;text-align:center;font-weight:700;font-size:13px;color:var(--app-text-primary);background:var(--app-grid-bg)}
.schedule-period-cell{padding:8px 6px;background:var(--app-grid-bg);display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;min-height:84px}
.period-label{font-weight:700;font-size:12px;color:var(--app-text-primary)}
.period-time{font-size:10px;color:var(--app-text-secondary);margin-top:3px;line-height:1.2}
.schedule-empty-cell{min-height:84px;background:var(--app-grid-bg)}
.schedule-cell{min-height:84px;padding:3px;background:var(--app-card-bg);display:flex;flex-direction:column;gap:3px}
.schedule-course-card{flex:1;width:100%;min-height:76px;padding:6px 5px;border:1px solid var(--app-grid-line);border-radius:6px;cursor:pointer;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:3px;color:var(--app-text-primary);transition:transform 0.15s ease,box-shadow 0.15s ease}
.schedule-course-card:hover{transform:translateY(-1px);box-shadow:var(--app-shadow-sm)}
.course-card-name{font-size:11.5px;font-weight:700;line-height:1.25;color:var(--app-text-primary);display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.course-card-weeks{font-size:10px;font-weight:600;color:var(--brand-primary);line-height:1.2}
.course-card-room{font-size:10px;color:var(--app-text-secondary);line-height:1.2}
.course-card-badge{font-size:9.5px;padding:1px 5px;border-radius:4px;background:rgba(217,119,6,0.14);color:#b45309;font-weight:600}
:global(html[data-theme='dark']) .course-card-badge{background:rgba(245,158,11,0.22);color:#fbbf24}
.roster-summary{padding:14px 16px;background:var(--app-card-muted-bg);border:1px solid var(--app-border);border-radius:10px;margin-bottom:16px;color:var(--app-text-primary);font-size:14px}
.roster-summary p{font-size:12px;color:var(--app-text-secondary);margin-bottom:0}
.stop-block-note{font-size:12px;color:#c45656;margin-top:2px}
.print-sheet{display:none}
@media(max-width:1000px){.summary{gap:15px;flex-wrap:wrap}}
@media print {
  .selection-page > :not(.print-sheet) { display: none !important; }
  .print-sheet { display: block !important; width: 100%; color: #000; background: #fff; font-size: 11pt; }
  .print-sheet h2 { text-align: center; }
  .print-sheet table { width: 100%; border-collapse: collapse; }
  .print-sheet th, .print-sheet td { border: 1px solid #777; padding: 7px; text-align: left; }
  .print-sheet thead { display: table-header-group; }
  .print-sheet tr { break-inside: avoid; }
}
</style>
