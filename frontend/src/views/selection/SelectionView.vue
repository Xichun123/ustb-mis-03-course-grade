<template>
  <section class="selection-page" v-loading="busy">
    <div class="heading">
      <div><h2>学生选课</h2><p>课程、申请与上课安排</p></div>
      <el-button @click="perform(refresh)">刷新当前页面</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="notice" />
    <el-alert v-if="!isStudent && !isStaff" title="请切换至学生、任课教师或教务处经办账号使用本模块。" type="info" :closable="false" />
    <template v-else>
      <div class="filters">
        <label>学年学期</label>
        <el-select v-model="term" style="width:170px" @change="changeTerm"><el-option v-for="t in context.terms" :key="t" :label="t" :value="t" /></el-select>
        <template v-if="isStudent || active==='screen'">
          <label>选课轮次</label>
          <el-select v-model="roundId" style="width:340px" placeholder="选择已发布轮次" @change="changeRound">
            <el-option v-for="r in termRounds" :key="r.round_id" :value="r.round_id" :label="r.round_name + (r.window_open ? ' · 开放中' : ' · 非办理时段')" />
          </el-select>
        </template>
      </div>
      <div v-if="isStudent" class="summary">
        <span>本学期已选中 <strong>{{ summary.courses ?? '—' }}</strong> 门</span>
        <span>有效学分 <strong>{{ summary.credits ?? '—' }}</strong></span>
        <span v-if="selectedRound">{{ selectedRound.select_mode===2 ? '超额后筛选' : '先到先得' }} · 本轮学分上限 {{ selectedRound.max_credit }}</span>
      </div>
      <el-tabs v-model="active" @tab-change="() => perform(refresh)">
        <template v-if="isStudent">
          <el-tab-pane label="课程查询与选课" name="courses">
            <el-alert v-if="selectedRound" :title="selectedRound.round_name" :description="`${formatTime(selectedRound.start_time)} 至 ${formatTime(selectedRound.end_time)}；${selectedRound.notice_content || '请按本轮规则办理'}`" :type="selectedRound.window_open?'info':'warning'" :closable="false" class="notice" />
            <div class="toolbar"><el-input v-model="keyword" placeholder="输入课程代码或名称" clearable style="width:300px" @keyup.enter="searchCourses" /><el-button type="primary" @click="searchCourses">查询</el-button><el-button @click="resetCourses">重置</el-button></div>
            <el-table :data="courses" row-key="course_id" border empty-text="当前条件下没有开放课程" @expand-change="() => {}">
              <el-table-column type="expand"><template #default="{row}">
                <div class="class-detail"><el-table :data="row.classes" size="small" border>
                  <el-table-column prop="tc_id" label="教学班号" min-width="150" />
                  <el-table-column label="上课安排" min-width="260"><template #default="{row:tc}"><div v-for="(s,i) in tc.schedule" :key="i">{{ slotText(s) }}</div><span v-if="!tc.schedule.length" class="muted">排课待确认</span></template></el-table-column>
                  <el-table-column label="已选 / 容量" width="110"><template #default="{row:tc}">{{tc.actual_num}} / {{tc.capacity}}</template></el-table-column>
                  <el-table-column label="本人状态" width="115"><template #default="{row:tc}"><el-tag :type="statusType(tc.select_status)">{{statusText(tc.select_status)}}</el-tag></template></el-table-column>
                  <el-table-column v-if="selectedRound?.select_mode===2" label="各档志愿人数" min-width="150"><template #default="{row:tc}">{{ tc.preferences.map(p => `${p.priority ?? '未分档'}志愿 ${p.applicants}人`).join('；') || '暂无申请' }}</template></el-table-column>
                  <el-table-column label="办理" width="130"><template #default="{row:tc}"><el-button size="small" type="primary" :disabled="!canApply(tc)" @click="openApply(tc)">{{ selectedRound?.select_mode===2?'登记志愿':'选课' }}</el-button></template></el-table-column>
                </el-table></div>
              </template></el-table-column>
              <el-table-column prop="course_id" label="课程代码" width="140" /><el-table-column prop="course_name" label="课程名称" min-width="240" />
              <el-table-column prop="credit" label="学分" width="90" /><el-table-column label="教学班" width="100"><template #default="{row}">{{row.classes.length}} 个</template></el-table-column>
              <el-table-column label="提示" min-width="180"><template #default>展开左侧箭头查看教学班并办理</template></el-table-column>
            </el-table>
            <el-pagination v-model:current-page="coursePage" :page-size="10" :total="courseTotal" layout="total, prev, pager, next" @current-change="() => perform(loadCourses)" />
          </el-tab-pane>
          <el-tab-pane label="本人选课结果" name="results">
            <div class="toolbar"><el-input v-model="resultKeyword" placeholder="课程名称或教学班号" clearable style="width:260px" /><el-select v-model="statusFilter" clearable placeholder="全部状态" style="width:150px"><el-option v-for="(s,i) in statuses" :key="i" :label="s" :value="i" /></el-select><el-button @click="active='timetable';perform(loadTimetable)">查看按周课表</el-button></div>
            <el-table :data="filteredResults" border empty-text="没有匹配的本人选课记录">
              <el-table-column type="expand"><template #default="{row}"><div class="class-detail"><div v-for="(s,i) in row.schedule" :key="i">{{slotText(s)}}</div><span v-if="!row.schedule.length">排课资料待确认</span></div></template></el-table-column>
              <el-table-column prop="course_name" label="课程名称" min-width="200" /><el-table-column prop="tc_id" label="教学班" min-width="150" /><el-table-column prop="credit" label="学分" width="80" />
              <el-table-column label="修读类别" width="95"><template #default="{row}">{{studyText(row.study_type)}}</template></el-table-column>
              <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.select_status)">{{statusText(row.select_status)}}</el-tag></template></el-table-column>
              <el-table-column label="免听" width="85"><template #default="{row}">{{row.is_exempt_attend===1?'冲突免听':'正常'}}</template></el-table-column>
              <el-table-column label="操作" width="110"><template #default="{row}"><el-button link type="danger" :disabled="!row.withdraw_round_id" @click="withdraw(row)">退选</el-button></template></el-table-column>
            </el-table>
            <p class="muted">退选需有本学期开放的补退选轮次，且未进入成绩或评教等后续业务；历史记录继续保留。</p>
          </el-tab-pane>
          <el-tab-pane label="本人按周课表" name="timetable">
            <div class="toolbar"><el-button :disabled="week<=1" @click="week--;perform(loadTimetable)">上一周</el-button><span>第</span><el-input-number v-model="week" :min="1" :max="60" @change="() => perform(loadTimetable)" /><span>教学周</span><el-button :disabled="week>=60" @click="week++;perform(loadTimetable)">下一周</el-button></div>
            <el-alert v-if="timetable.missing_schedule?.length" :title="`以下有效课程排课缺失：${timetable.missing_schedule.join('、')}`" type="warning" class="notice" :closable="false" />
            <div class="week-grid"><div v-for="(day,i) in days" :key="day" class="day-column"><div class="day-title">周{{day}}</div><button v-for="(b,j) in dayBlocks(i+1)" :key="j" class="course-block" @click="showSchedule(b)"><strong>{{b.course_name}}</strong><span>第 {{b.start_slot}}—{{b.end_slot}} 节 · {{b.room_id}}</span><small>{{b.tc_id}}{{b.is_exempt_attend===1?' · 冲突免听':''}}</small></button><p v-if="!dayBlocks(i+1).length" class="empty-day">无安排</p></div></div>
            <el-button class="back" @click="active='results';perform(loadResults)">返回本人选课结果</el-button>
          </el-tab-pane>
        </template>
        <el-tab-pane v-if="isStaff" label="上课名单与调整" name="roster">
          <div class="toolbar"><el-select v-model="tcId" filterable placeholder="选择教学班" style="width:380px" @change="() => perform(loadRoster)"><el-option v-for="c in teachingClasses" :key="c.tc_id" :value="c.tc_id" :label="`${c.tc_id} · ${c.course_name}`" /></el-select><el-input v-model="rosterKeyword" placeholder="学号或姓名" clearable style="width:200px" @keyup.enter="perform(loadRoster)" /><el-button @click="perform(loadRoster)">查询</el-button><el-button @click="printRoster" :disabled="!roster.teaching_class">打印名单</el-button><el-button type="primary" @click="openAdjustment()" :disabled="!tcId">补加学生</el-button></div>
          <div v-if="roster.teaching_class" class="roster-summary">{{roster.teaching_class.course_name}} · {{tcId}} · 有效人数 {{roster.effective_count}} / 容量 {{roster.teaching_class.capacity}}<p>{{roster.teaching_class.schedule.map(slotText).join('；')}}</p></div>
          <el-table :data="roster.items || []" border empty-text="当前条件下没有有效选课学生"><el-table-column type="index" label="序号" width="65" /><el-table-column prop="student_id" label="学号" min-width="150" /><el-table-column prop="student_name" label="姓名" min-width="120" /><el-table-column prop="class_name" label="行政班" min-width="150" /><el-table-column label="选课类别" width="110"><template #default="{row}">{{studyText(row.study_type)}}</template></el-table-column><el-table-column label="免听标记" width="110"><template #default="{row}">{{row.is_exempt_attend===1?'冲突免听':'正常跟班'}}</template></el-table-column><el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="danger" @click="openAdjustment(row)">移出名单</el-button></template></el-table-column></el-table>
          <p class="muted">教师仅能维护本人授课班，教务处可维护全校；每次调整必须填写原因并留痕。</p>
        </el-tab-pane>
        <el-tab-pane v-if="isOffice" label="截止筛选" name="screen"><el-alert title="按志愿优先级筛选，同档超额随机；整轮失败整体回滚。" type="info" :closable="false" class="notice" /><p v-if="selectedRound">{{selectedRound.round_name}} · 截止 {{formatTime(selectedRound.end_time)}}</p><el-button type="primary" :disabled="!canScreen" @click="screen">执行本轮截止筛选</el-button><p v-if="screenResult">{{screenResult}}</p></el-tab-pane>
        <el-tab-pane v-if="isOffice" label="停开预览" name="stop"><el-alert title="当前展示停开及待停开教学班受影响选课学生名单，供教务处核对与后续通知。" type="warning" :closable="false" class="notice" /><el-table :data="stop.items || []" border><el-table-column prop="tc_id" label="教学班" /><el-table-column prop="course_name" label="课程名称" /><el-table-column prop="tc_status" label="教学班状态" /><el-table-column prop="affected_count" label="受影响申请数" /></el-table></el-tab-pane>
      </el-tabs>
    </template>
    <el-dialog v-model="applyVisible" title="选课申请" width="620px" :close-on-click-modal="false">
      <template v-if="target"><p><strong>{{target.course_name}}</strong> · {{target.tc_id}} · {{target.credit}} 学分</p><p v-for="(s,i) in target.schedule" :key="i">{{slotText(s)}}</p><el-form label-width="90px"><el-form-item label="选课类别"><el-select v-model="application.studyType" @change="checkResult=''"><el-option label="初修" :value="1" /><el-option label="重修" :value="2" /><el-option v-if="target.tc_type==='3'" label="辅修" :value="3" /></el-select></el-form-item><el-form-item v-if="selectedRound?.select_mode===2" label="志愿档位"><el-select v-model="application.priority" @change="checkResult=''"><el-option v-for="n in 3" :key="n" :value="n" :label="`第${n}志愿`" /></el-select></el-form-item></el-form><el-alert v-if="checkResult" :title="checkResult" type="info" :closable="false" /></template>
      <template #footer><el-button @click="applyVisible=false">取消</el-button><el-button :loading="busy" @click="precheck">先校验</el-button><el-button type="primary" :loading="busy" @click="enroll">确认提交</el-button></template>
    </el-dialog>
    <el-dialog v-model="adjustVisible" :title="adjustment.operation==='add'?'补加学生':'移出名单'" width="520px" :close-on-click-modal="false"><el-form label-width="90px"><el-form-item label="学号"><el-input v-model="adjustment.studentId" maxlength="20" :disabled="adjustment.operation==='remove'" /></el-form-item><el-form-item v-if="adjustment.operation==='add'" label="修读类别"><el-select v-model="adjustment.studyType"><el-option v-for="(s,i) in ['初修','重修','辅修']" :key="s" :label="s" :value="i+1" /></el-select></el-form-item><el-form-item label="调整原因" required><el-input v-model="adjustment.reason" type="textarea" maxlength="200" show-word-limit :rows="3" /></el-form-item></el-form><p class="muted">教务调整不检查办理时间、容量、冲突和学分；已有后续业务的记录仍需先核实保护。</p><template #footer><el-button @click="adjustVisible=false">取消</el-button><el-button type="primary" :loading="busy" @click="submitAdjustment">确认调整</el-button></template></el-dialog>
    <el-dialog v-model="scheduleVisible" title="教学班完整排课" width="550px"><p v-for="(s,i) in scheduleDetail" :key="i">{{slotText(s)}}</p><template #footer><el-button @click="scheduleVisible=false;active='results';perform(loadResults)">查看本人选课记录</el-button></template></el-dialog>
    <div v-if="printData" class="print-sheet"><h2>上课名单</h2><p>学期：{{term}}　课程：{{printData.teaching_class.course_name}}　教学班：{{tcId}}</p><p>学分：{{printData.teaching_class.credit}}　容量：{{printData.teaching_class.capacity}}　有效人数：{{printData.effective_count}}</p><p>{{printData.teaching_class.schedule.map(slotText).join('；')}}</p><table><thead><tr><th>序号</th><th>学号</th><th>姓名</th><th>行政班</th><th>类别</th><th>免听</th></tr></thead><tbody><tr v-for="(s,i) in printData.items" :key="s.sc_id"><td>{{i+1}}</td><td>{{s.student_id}}</td><td>{{s.student_name}}</td><td>{{s.class_name}}</td><td>{{studyText(s.study_type)}}</td><td>{{s.is_exempt_attend===1?'是':'否'}}</td></tr></tbody></table><p>数据时间：{{new Date().toLocaleString()}}　任课教师签字：____________</p></div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectionApi as api } from '../../api/selection'
import { userState } from '../../stores/user'

const context = ref({ rounds: [], terms: [], user: {} }), busy = ref(false), error = ref(''), active = ref('courses')
const term = ref('2026-2027-1'), roundId = ref(null), keyword = ref(''), coursePage = ref(1), courses = ref([]), courseTotal = ref(0), summary = ref({})
const resultRows = ref([]), resultKeyword = ref(''), statusFilter = ref(null), week = ref(1), timetable = ref({ blocks: [] })
const teachingClasses = ref([]), tcId = ref(''), rosterKeyword = ref(''), roster = ref({}), stop = ref({}), screenResult = ref('')
const applyVisible = ref(false), target = ref(null), application = ref({}), checkResult = ref(''), adjustVisible = ref(false), adjustment = ref({}), printData = ref(null)
const scheduleVisible = ref(false), scheduleDetail = ref([])
const statuses = ['待筛选','已选中','未选中','已退选'], days = ['一','二','三','四','五','六','日']
const isStudent = computed(() => context.value.user.role === 'STUDENT')
const isOffice = computed(() => context.value.user.role === 'ACADEMIC_OFFICE')
const isStaff = computed(() => isOffice.value || context.value.user.role === 'TEACHER')
const termRounds = computed(() => context.value.rounds.filter(r => r.term === term.value))
const selectedRound = computed(() => termRounds.value.find(r => r.round_id === roundId.value))
const canScreen = computed(() => selectedRound.value?.select_mode === 2 && Date.parse(selectedRound.value.end_time) <= Date.parse(context.value.server_time))
const filteredResults = computed(() => resultRows.value.filter(r => (statusFilter.value == null || r.select_status === statusFilter.value) && `${r.course_name} ${r.tc_id}`.toLowerCase().includes(resultKeyword.value.toLowerCase())))
const statusText = s => s == null ? '未选课' : (statuses[s] || '状态待确认')
const statusType = s => ({0:'warning',1:'success',2:'info',3:'info'}[s] || 'info')
const studyText = s => ({1:'初修',2:'重修',3:'辅修'}[s] || '待确认')
const formatTime = t => String(t || '').replace('T',' ')
const slotText = s => `${s.week_range}周 · 周${days[Number(s.week_day)-1] || '?'} · ${s.start_slot}—${s.end_slot}节 · ${s.room_id}`
const dayBlocks = day => (timetable.value.blocks || []).filter(b => Number(b.week_day) === day).sort((a,b) => a.start_slot-b.start_slot)
const canApply = tc => selectedRound.value?.window_open && ![0,1].includes(tc.select_status) && (selectedRound.value.select_mode === 2 || tc.actual_num < tc.capacity)
async function perform(task) { if(busy.value) return; busy.value=true; error.value=''; try { await task() } catch(e) { if(e !== 'cancel' && e !== 'close') error.value=e?.response?.data?.message || e?.message || '操作失败，请刷新核实' } finally { busy.value=false } }
async function loadCourses(){ if(!roundId.value){courses.value=[];courseTotal.value=0;return} const r=await api.courses({roundId:roundId.value,keyword:keyword.value,page:coursePage.value,pageSize:10});courses.value=r.data.items;courseTotal.value=r.data.total;summary.value=r.data.summary }
async function loadResults(){const r=await api.results(term.value);resultRows.value=r.data.items;summary.value=r.data.summary}
async function loadTimetable(){const r=await api.timetable(term.value,week.value);timetable.value=r.data;summary.value=r.data.summary}
async function loadRoster(){ if(!tcId.value){roster.value={};return} roster.value=(await api.roster(tcId.value,rosterKeyword.value)).data }
async function loadClasses(){teachingClasses.value=(await api.teachingClasses(term.value)).data;if(!teachingClasses.value.some(c => c.tc_id===tcId.value))tcId.value=teachingClasses.value[0]?.tc_id || '';await loadRoster()}
async function refresh(){if(active.value==='courses')await loadCourses();if(active.value==='results')await loadResults();if(active.value==='timetable')await loadTimetable();if(active.value==='roster')await loadClasses();if(active.value==='stop')stop.value=(await api.stopPreview(term.value)).data}
function chooseRound(){roundId.value=(termRounds.value.find(r=>r.window_open)||termRounds.value[0])?.round_id || null}
function changeTerm(){chooseRound();coursePage.value=1;summary.value={};perform(refresh)}
function changeRound(){coursePage.value=1;perform(refresh)}
function searchCourses(){coursePage.value=1;perform(loadCourses)}
function resetCourses(){keyword.value='';searchCourses()}
function openApply(tc){target.value=tc;application.value={roundId:roundId.value,tcId:tc.tc_id,studyType:1,priority:selectedRound.value.select_mode===2?1:null,expectedVersion:tc.version || null};checkResult.value='';applyVisible.value=true}
function precheck(){perform(async()=>{try{checkResult.value=(await api.check(application.value)).data.message}catch(e){checkResult.value=e?.response?.data?.message || '校验未完成，请稍后重试';throw e}})}
function enroll(){perform(async()=>{try{const r=await api.enroll(application.value);ElMessage.success(r.data.message);applyVisible.value=false;await loadCourses()}catch(e){if(!e.response){await loadResults();await loadCourses();applyVisible.value=false;error.value='提交未获得明确回执，已重新读取本人记录，请核对结果后再办理。';active.value='results';return}throw e}})}
function withdraw(row){perform(async()=>{await ElMessageBox.confirm(`确认退选 ${row.course_name}（${row.tc_id}）？`,'退选确认',{type:'warning'});try{const r=await api.withdraw(row.sc_id,{roundId:row.withdraw_round_id,expectedVersion:row.version,confirmed:true});ElMessage.success(r.data.message)}catch(e){if(!e.response){error.value='退选结果待核实，正在重新读取本人记录。'}else throw e}await loadResults()})}
function showSchedule(block){const found=resultRows.value.find(r=>r.tc_id===block.tc_id);perform(async()=>{if(!found)await loadResults();scheduleDetail.value=resultRows.value.find(r=>r.tc_id===block.tc_id)?.schedule || [];scheduleVisible.value=true})}
function openAdjustment(row){adjustment.value={studentId:row?.student_id || '',operation:row?'remove':'add',studyType:1,reason:''};adjustVisible.value=true}
function submitAdjustment(){perform(async()=>{if(!adjustment.value.reason?.trim())throw new Error('请填写调整原因');try{const r=await api.adjust(tcId.value,adjustment.value);ElMessage.success(r.data.message);adjustVisible.value=false}catch(e){if(!e.response){adjustVisible.value=false;error.value='调整结果待核实，已刷新名单，请核对后再办理。'}else throw e}await loadRoster()})}
function screen(){perform(async()=>{await ElMessageBox.confirm('确认执行本轮截止筛选？将按实际余量和志愿重新校验所有待筛选申请。','截止筛选',{type:'warning'});const r=await api.screen(roundId.value);screenResult.value=`${r.data.message}；本次录取 ${r.data.accepted} 人，未选中 ${r.data.rejected} 人。`})}
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
.filters{padding:16px 18px;background:var(--app-card-bg);border:1px solid var(--app-border);border-radius:14px;box-shadow:var(--app-shadow-sm)}
.filters label{font-size:13px;color:var(--app-text-secondary)}
.summary{display:flex;gap:28px;padding:15px 20px;background:var(--app-menu-active-bg);border:1px solid var(--app-border);border-radius:12px;margin-bottom:18px;color:var(--app-text-primary);font-size:14px}
.summary strong{font-size:20px;color:var(--brand-primary);margin:0 5px}
.notice{margin-bottom:16px}
.class-detail{padding:16px 20px;background:var(--app-card-muted-bg)}
.muted{font-size:12px;color:var(--app-text-secondary);line-height:1.7}
.el-pagination{margin-top:16px;justify-content:flex-end}
.week-grid{display:grid;grid-template-columns:repeat(7,minmax(120px,1fr));border:1px solid var(--app-grid-line);border-radius:12px;overflow:auto;background:var(--app-card-bg)}
.day-column{min-height:280px;border-right:1px solid var(--app-grid-line);padding:0 8px 12px}
.day-column:last-child{border-right:0}
.day-title{text-align:center;font-weight:600;padding:12px;color:var(--app-text-primary);background:var(--app-grid-bg);border-bottom:1px solid var(--app-grid-line);margin:0 -8px 12px}
.course-block{display:flex;flex-direction:column;text-align:left;width:100%;gap:6px;margin-bottom:10px;padding:11px;border:1px solid var(--app-grid-line);border-left:4px solid var(--brand-primary);border-radius:8px;background:var(--app-menu-active-bg);color:var(--app-text-primary);cursor:pointer;transition:transform 0.2s ease,box-shadow 0.2s ease}
.course-block:hover{transform:translateY(-1px);box-shadow:var(--app-shadow-sm)}
.course-block strong{font-size:13px;color:var(--brand-primary)}
.course-block span,.course-block small{font-size:11px;line-height:1.5;color:var(--app-text-secondary)}
.empty-day{text-align:center;color:var(--app-text-tertiary);font-size:12px;margin-top:28px}
.back{margin-top:16px}
.roster-summary{padding:14px 16px;background:var(--app-card-muted-bg);border:1px solid var(--app-border);border-radius:10px;margin-bottom:16px;color:var(--app-text-primary);font-size:14px}
.roster-summary p{font-size:12px;color:var(--app-text-secondary);margin-bottom:0}
.print-sheet{display:none}
@media(max-width:1000px){.summary{gap:15px;flex-wrap:wrap}.week-grid{grid-template-columns:repeat(7,145px)}}
</style>
<style>
@media print{@page{size:A4 portrait;margin:15mm}body *{visibility:hidden!important}.print-sheet,.print-sheet *{visibility:visible!important}.print-sheet{display:block!important;position:absolute;left:0;top:0;width:100%;color:#000;background:#fff;font-size:11pt}.print-sheet h2{text-align:center}.print-sheet table{width:100%;border-collapse:collapse}.print-sheet th,.print-sheet td{border:1px solid #777;padding:7px;text-align:left}.print-sheet thead{display:table-header-group}.print-sheet tr{break-inside:avoid}.layout-main,.layout-root{overflow:visible!important;height:auto!important}}
</style>
