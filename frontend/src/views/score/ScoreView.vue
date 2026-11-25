<template>
  <div>
    <el-alert v-if="role && !isTeacher && !isCollege && !isOffice && !isAdmin" title="当前账号无权访问成绩录入与审核模块。" type="info" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-tabs v-else v-model="activeTab" type="border-card">
      <!-- 3.1 教师：我的课堂（本学期在前、往届在后） -->
      <el-tab-pane v-if="isTeacher || isAdmin" label="课堂与成绩录入" name="entry">
        <div class="toolbar">
          <el-select v-model="classQuery.term" placeholder="全部学期" clearable style="width: 170px">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="classQuery.keyword" placeholder="课堂号 / 课程名" style="width: 220px" clearable />
          <el-button type="primary" @click="loadClasses">查询</el-button>
        </div>
        <el-table :data="classList" border stripe v-loading="loadingClasses" @row-click="openClass">
          <el-table-column label="课堂号" width="185">
            <template #default="{ row }">
              <span>{{ row.lesson_no }}</span>
              <el-tag v-if="row.correction_pending_count > 0" type="danger" size="small" style="margin-left: 6px">
                更正待重录 {{ row.correction_pending_count }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="course_name" label="课程" min-width="150" />
          <el-table-column prop="class_ids" label="面向班级" min-width="140" />
          <el-table-column prop="student_count" label="选课人数" width="85" />
          <el-table-column prop="submitted_count" label="待核对" width="75" />
          <el-table-column prop="published_count" label="已发布" width="75" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="openClass(row)">
                {{ row.correction_pending_count > 0 ? '更正重录' : '录入' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="currentClass">
          <el-divider content-position="left">
            {{ currentClass.lesson_no }} {{ currentClass.course_name }}（{{ currentClass.term }}）
          </el-divider>
          <el-alert
            v-if="roster.has_active_correction"
            type="warning"
            show-icon
            :closable="false"
            style="margin-bottom: 12px"
            title="本课堂存在学院已批准的成绩更正记录：系统仅解锁对应学生的原成绩（其余已发布学生保持锁定防误改）。修改后请点击「总评计算并提交」生成新 6 位核对码，打印新版纸质成绩登记表交学院核对，待教务处重新发布后生效。"
          />
          <div class="toolbar">
            <el-tag type="warning">平时占比下限 {{ roster.ratio_min }}%</el-tag>
            <template v-if="roster.default_score_type === 1">
              <span class="ratio-label">课堂统一占比：平时</span>
              <el-input-number v-model="classRatio" :min="roster.ratio_min" :max="100" size="small" :disabled="readOnly" />
              <span class="ratio-label">% ＋ 考试/期末 <b>{{ 100 - classRatio }}%</b>（按课堂填一次即可）</span>
              <el-button size="small" :disabled="readOnly" @click="applyRatio">应用到全课堂</el-button>
            </template>
            <el-tag v-else type="info">本课堂为等级制课程，无需设置平时占比</el-tag>
            <el-button type="primary" :disabled="readOnly" @click="saveAll">保存成绩</el-button>
            <el-button type="success" :disabled="readOnly" @click="submitAll">总评计算并提交</el-button>
            <el-button @click="showSheet">纸质成绩登记表 / 核对码</el-button>
          </div>
          <div class="toolbar">
            <el-button type="primary" plain :loading="templateLoading" @click="downloadTemplate">
              下载本课堂 Excel 模板 (.xlsx)
            </el-button>
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :show-file-list="false"
              accept=".xlsx,.csv"
              :on-change="onImportFile"
            >
              <el-button :disabled="readOnly">选择 Excel 成绩表</el-button>
            </el-upload>
            <span class="muted" v-if="importFile">{{ importFile.name }}</span>
            <el-checkbox v-model="importDryRun" :disabled="readOnly">仅预览</el-checkbox>
            <el-button type="warning" :disabled="readOnly || !importFile || importLoading" :loading="importLoading" @click="doImport">
              导入成绩
            </el-button>
            <span class="muted">先下载模板填好平时成绩与考试成绩后上传（支持 .xlsx / .csv，占比按课堂统一设置，表内无需填写占比）</span>
          </div>
          <el-table :data="roster.students" border stripe max-height="440">
            <el-table-column prop="student_id" label="学号" width="135" />
            <el-table-column label="姓名" width="135">
              <template #default="{ row }">
                <div>{{ row.student_name }}</div>
                <el-tag v-if="row.correction_apply_id" type="danger" size="small" effect="dark" style="margin-top: 2px">
                  更正重录
                </el-tag>
                <div v-if="row.correction_apply_id" class="corr-note">
                  原 {{ row.score_type === 1 ? row.correction_old_score : row.correction_old_level }} → 拟 {{ row.score_type === 1 ? row.correction_target_score : row.correction_target_level }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="class_name" label="班级" width="120" />
            <el-table-column label="修读" width="75">
              <template #default="{ row }">{{ { 1: '初修', 2: '重修', 3: '辅修' }[row.study_type] || '初修' }}</template>
            </el-table-column>
            <el-table-column label="平时/考试占比" width="115">
              <template #default="{ row }">
                <template v-if="row.score_type === 1">
                  <span v-if="isLockedRow(row)">{{ row.usual_ratio ?? '—' }}<template v-if="row.usual_ratio != null">% / {{ 100 - row.usual_ratio }}%</template></span>
                  <span v-else>{{ classRatio }}% / {{ 100 - classRatio }}%</span>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="平时成绩" width="120">
              <template #default="{ row }">
                <el-input-number v-if="row.score_type === 1" v-model="row.usual_score" :min="0" :max="100" :precision="1" :disabled="readOnly || isLockedRow(row)" size="small" />
                <span v-else class="muted">等级制</span>
              </template>
            </el-table-column>
            <el-table-column label="考试/等级成绩" width="130">
              <template #default="{ row }">
                <el-input-number v-if="row.score_type === 1" v-model="row.exam_score" :min="0" :max="100" :precision="1" :disabled="readOnly || isLockedRow(row)" size="small" />
                <el-select v-else-if="row.score_type === 2" v-model="row.score_level" :disabled="readOnly || isLockedRow(row)" size="small" placeholder="选等级">
                  <el-option v-for="lv in ['优', '良', '中', '及格', '不及格']" :key="lv" :value="lv" :label="lv" />
                </el-select>
                <el-select v-else v-model="row.score_level" :disabled="readOnly || isLockedRow(row)" size="small" placeholder="选等级">
                  <el-option v-for="lv in ['合格', '不合格']" :key="lv" :value="lv" :label="lv" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="考试情况" width="110">
              <template #default="{ row }">
                <el-select v-model="row.exam_flag" :disabled="readOnly || isLockedRow(row)" size="small">
                  <el-option :value="0" label="正常" />
                  <el-option :value="1" label="缺考" />
                  <el-option :value="2" label="缓考" />
                  <el-option :value="3" label="违纪" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="total_score" label="总评" width="80" />
            <el-table-column prop="grade_point" label="绩点" width="70" />
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.score_status)">{{ statusText(row.score_status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- 3.3 学院核对 -->
      <el-tab-pane v-if="isCollege || isAdmin" label="学院核对" name="audit">
        <div class="toolbar">
          <el-select v-model="auditTerm" placeholder="全部学期" clearable style="width: 170px">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-button type="primary" @click="loadAudit">刷新待核对</el-button>
        </div>
        <el-table :data="auditList" border stripe v-loading="loadingAudit">
          <el-table-column label="课堂号" width="185">
            <template #default="{ row }">
              <span>{{ row.lesson_no }}</span>
              <el-tag v-if="row.correction_count > 0" type="danger" size="small" style="margin-left: 6px">
                含更正 {{ row.correction_count }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="course_name" label="课程" min-width="150" />
          <el-table-column label="开课学院" min-width="145">
            <template #default="{ row }">{{ getCollegeName(row.open_college) }}</template>
          </el-table-column>
          <el-table-column prop="wait_count" label="待核对人数" width="105" />
          <el-table-column label="提交时间" min-width="160">
            <template #default="{ row }">{{ String(row.submit_time || '—').replace('T', ' ') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" link @click="openAudit(row)">核对</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 3.4 发布 -->
      <el-tab-pane v-if="isOffice || isAdmin" label="成绩发布" name="publish">
        <div class="toolbar">
          <el-select v-model="publishTerm" placeholder="全部学期" clearable style="width: 170px">
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
          <el-button type="primary" @click="loadPublish">刷新待发布</el-button>
        </div>
        <el-table :data="publishList" border stripe v-loading="loadingPublish">
          <el-table-column label="课堂号" width="185">
            <template #default="{ row }">
              <span>{{ row.lesson_no }}</span>
              <el-tag v-if="row.correction_count > 0" type="danger" size="small" style="margin-left: 6px">
                含更正 {{ row.correction_count }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="term" label="学期" width="120" />
          <el-table-column prop="course_name" label="课程" min-width="160" />
          <el-table-column prop="ready_count" label="可发布人数" width="105" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="success" link @click="doPublish(row)">发布</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 3.5 校外交流认定 -->
      <el-tab-pane v-if="isOffice || isCollege || isAdmin" label="校外交流成绩认定" name="exchange">
        <div class="toolbar">
          <el-input v-model="exchangeQuery.student_id" placeholder="学号" style="width: 160px" clearable />
          <el-input v-model="exchangeQuery.keyword" placeholder="课程名 / 外校" style="width: 200px" clearable />
          <el-button type="primary" @click="loadExchange">查询</el-button>
          <el-button type="success" @click="openExchange">+ 登记认定</el-button>
        </div>
        <el-table :data="exchangeList" border stripe>
          <el-table-column prop="student_id" label="学号" width="130" />
          <el-table-column prop="host_school" label="外校" min-width="150" />
          <el-table-column prop="ext_course_name" label="外校课程" min-width="150" />
          <el-table-column prop="ext_credit" label="学分" width="70" />
          <el-table-column prop="ext_score_raw" label="原始成绩" width="90" />
          <el-table-column prop="course_id" label="本校课程号" width="110" />
          <el-table-column prop="converted_score" label="换算成绩" width="90" />
          <el-table-column prop="converted_level" label="等级" width="80" />
          <el-table-column prop="grade_point" label="绩点" width="70" />
          <el-table-column prop="proof_no" label="证明编号" min-width="140" />
          <el-table-column prop="audit_status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="['warning', 'primary', 'success', 'danger'][row.audit_status] || 'info'">
                {{ ['待学院审核', '待教务处审批', '已通过生效', '已驳回'][row.audit_status] || row.audit_status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230">
            <template #default="{ row }">
              <template v-if="row.audit_status === 0 && (isCollege || isAdmin)">
                <el-button type="primary" link @click="decideExchange(row, 'college', 1)">学院通过</el-button>
                <el-button type="danger" link @click="decideExchange(row, 'college', 2)">驳回</el-button>
              </template>
              <template v-else-if="row.audit_status === 1 && (isOffice || isAdmin)">
                <el-button type="success" link @click="decideExchange(row, 'school', 1)">教务审批</el-button>
                <el-button type="danger" link @click="decideExchange(row, 'school', 2)">驳回</el-button>
              </template>
              <el-button
                v-if="(isOffice || isAdmin) && (row.audit_status === 0 || row.audit_status === 1)"
                type="warning"
                link
                @click="recognizeExchange(row)"
              >教务直接认定</el-button>
              <span v-if="row.audit_status >= 2" class="muted" style="font-size: 12px">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 成绩登记表（纸质提交版）与 6 位核对码 -->
    <el-dialog v-model="codeVisible" title="成绩登记表（纸质提交版）" width="780px">
      <div class="paper-sheet">
        <div class="paper-header">
          <div>
            <div class="paper-title">课程成绩登记表</div>
            <div class="paper-sub">
              课堂号：<b>{{ sheetData.lesson_no || currentClass?.lesson_no }}</b>
              ｜ 课程：<b>{{ sheetData.course_name || currentClass?.course_name }}</b>
              ｜ 学期：{{ sheetData.term || currentClass?.term }}
            </div>
            <div class="paper-sub" v-if="(sheetData.default_score_type || 1) === 1">
              记分方式：百分制 ｜ 课堂统一占比：平时 {{ sheetData.usual_ratio ?? classRatio }}% ＋ 考试 {{ 100 - (sheetData.usual_ratio ?? classRatio) }}%
            </div>
            <div class="paper-sub" v-else>
              记分方式：等级制 ｜ 学生人数：{{ sheetData.students.length }}
            </div>
          </div>
          <div class="code-box">
            <div class="code-box-label">6 位核对码</div>
            <div class="code-box-val">{{ verifyCode || '尚未提交' }}</div>
          </div>
        </div>
        <el-alert
          v-if="isCorrectionResubmit || sheetData.has_active_correction"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
          title="本批次包含成绩更正重报记录：请打印此新版纸质成绩登记表，由任课教师签字后交学院教务秘书核对，再经教务处重新发布生效。"
        />
        <el-table :data="sheetData.students" border size="small" max-height="360">
          <el-table-column prop="student_id" label="学号" width="130" />
          <el-table-column prop="student_name" label="姓名" width="95" />
          <el-table-column prop="class_name" label="班级" width="110" />
          <el-table-column label="平时成绩" width="85">
            <template #default="{ row }">{{ row.score_type === 1 ? (row.usual_score ?? '—') : '—' }}</template>
          </el-table-column>
          <el-table-column label="考试成绩" width="85">
            <template #default="{ row }">{{ row.score_type === 1 ? (row.exam_score ?? '—') : '—' }}</template>
          </el-table-column>
          <el-table-column label="总评/等级" width="90">
            <template #default="{ row }">{{ row.score_type === 1 ? (row.total_score ?? '—') : (row.score_level || '—') }}</template>
          </el-table-column>
          <el-table-column prop="grade_point" label="绩点" width="65" />
          <el-table-column label="状态/备注" min-width="105">
            <template #default="{ row }">
              <span>{{ statusText(row.score_status) }}</span>
              <el-tag v-if="row.correction_apply_id" type="danger" size="small" style="margin-left: 4px">更正</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="paper-sign">
          <span>任课教师签字：________________</span>
          <span>学院核对签字：________________</span>
          <span>上交日期：____年__月__日</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="codeVisible = false">关闭</el-button>
        <el-button type="primary" @click="printSheet">打印纸质成绩登记表</el-button>
      </template>
    </el-dialog>

    <!-- 学院核对 -->
    <el-dialog v-model="auditVisible" title="学院核对" width="460px">
      <el-form label-width="90px">
        <el-form-item label="课堂号"><el-input v-model="auditForm.lesson_no" disabled /></el-form-item>
        <el-form-item label="核对码">
          <el-input v-model="auditForm.verify_code" maxlength="6" placeholder="输入纸质成绩单上的 6 位核对码" />
          <div class="muted" style="font-size: 12px; line-height: 1.5; margin-top: 6px">
            系统自动校验 6 位核对码，核验一致即通过；如纸质单存在问题可退回教师修改
          </div>
        </el-form-item>
        <el-form-item v-if="auditRejectMode" label="退回原因">
          <el-input
            v-model="auditForm.opinion"
            type="textarea"
            :rows="3"
            placeholder="必填，说明退回原因（如纸质单缺签字、成绩异常等），将退回任课教师修改"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <template v-if="!auditRejectMode">
          <el-button @click="auditVisible = false">取消</el-button>
          <el-button type="danger" plain @click="auditRejectMode = true">退回教师修改</el-button>
          <el-button type="primary" @click="doAudit(1)">核验并通过</el-button>
        </template>
        <template v-else>
          <el-button @click="auditRejectMode = false">返回核验</el-button>
          <el-button type="danger" @click="doAudit(2)">确认退回</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 校外认定 -->
    <el-dialog v-model="exchangeVisible" title="校外交流成绩换算与认定" width="520px">
      <el-form label-width="110px">
        <el-form-item label="学号"><el-input v-model="exchangeForm.student_id" /></el-form-item>
        <el-form-item label="学期">
          <el-select
            v-model="exchangeForm.term"
            placeholder="选择学期"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="t in TERM_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="外校"><el-input v-model="exchangeForm.host_school" /></el-form-item>
        <el-form-item label="外校课程名"><el-input v-model="exchangeForm.ext_course_name" /></el-form-item>
        <el-form-item label="外校学分"><el-input-number v-model="exchangeForm.ext_credit" :min="0.5" :precision="1" controls-position="right" style="width: 100%" /></el-form-item>
        <el-form-item label="原始成绩"><el-input v-model="exchangeForm.ext_score_raw" placeholder="如 92 / A-" /></el-form-item>
        <el-form-item label="成绩单编号"><el-input v-model="exchangeForm.proof_no" /></el-form-item>
        <el-form-item label="本校对应课程">
          <el-select
            v-model="exchangeForm.course_id"
            filterable
            remote
            reserve-keyword
            allow-create
            default-first-option
            clearable
            :remote-method="searchCourseCandidates"
            :loading="loadingCandidates"
            placeholder="输入本校课程名或课程号检索"
            style="width: 100%"
          >
            <el-option
              v-for="c in courseCandidates"
              :key="c.course_id"
              :value="c.course_id"
              :label="`${c.course_id} ${c.course_name}（${c.course_type_name} · ${c.credit}学分）`"
            >
              <div style="display: flex; justify-content: space-between; align-items: center; gap: 8px">
                <span>{{ c.course_id }} {{ c.course_name }}</span>
                <el-tag size="small" :type="c.required ? 'warning' : 'info'">{{ c.course_type_name }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="按必修认定">
          <el-checkbox v-model="exchangeForm.ext_required">外校必修学分直接对应本校必修课（本校课程须为必修）</el-checkbox>
        </el-form-item>
        <el-form-item label="记分方式">
          <el-select v-model="exchangeForm.score_type" style="width: 100%" @change="exchangeForm.converted_level = ''">
            <el-option :value="1" label="百分制" />
            <el-option :value="2" label="五级制" />
            <el-option :value="3" label="两级制" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="exchangeForm.score_type === 1" label="换算成绩">
          <el-input-number v-model="exchangeForm.converted_score" :min="0" :max="100" :precision="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="换算等级">
          <el-select v-model="exchangeForm.converted_level" style="width: 100%" placeholder="选择换算等级">
            <el-option
              v-for="lv in (exchangeForm.score_type === 2 ? ['优', '良', '中', '及格', '不及格'] : ['合格', '不合格'])"
              :key="lv"
              :label="lv"
              :value="lv"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exchangeVisible = false">取消</el-button>
        <el-button type="primary" @click="doExchange">登记认定</el-button>
      </template>
    </el-dialog>
    <!-- Excel 导入结果 -->
    <el-dialog v-model="importVisible" title="Excel 导入结果" width="640px">
      <el-alert :title="importResult.message || '导入完成'" :type="importResult.failed ? 'warning' : 'success'" show-icon :closable="false" />
      <el-table :data="importResult.errors || []" border size="small" style="margin-top: 12px" max-height="320">
        <el-table-column prop="row" label="行号" width="70" />
        <el-table-column prop="student_id" label="学号" width="140" />
        <el-table-column prop="message" label="说明" min-width="300" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="importVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scoreApi } from '../../api/score'
import { userState } from '../../stores/user'
import { TERM_OPTIONS } from '../../constants/terms'
import { getCollegeName } from '../../constants/dicts'

const route = useRoute()
const role = computed(() => userState.currentUser?.role)
const isTeacher = computed(() => role.value === 'TEACHER')
const isCollege = computed(() => role.value === 'COLLEGE_SECRETARY')
const isOffice = computed(() => role.value === 'ACADEMIC_OFFICE')
const isAdmin = computed(() => role.value === 'SYS_ADMIN')

const activeTab = ref(isTeacher.value ? 'entry' : isCollege.value ? 'audit' : isOffice.value ? 'publish' : 'entry')
const classQuery = reactive({ term: '', keyword: '' })
const classList = ref([])
const loadingClasses = ref(false)
const currentClass = ref(null)
const roster = reactive({ students: [], ratio_min: 30, verify_code: null, default_score_type: 1, has_active_correction: false })
const classRatio = ref(30)
const readOnly = computed(() => !isTeacher.value && !isAdmin.value)
const templateLoading = ref(false)

async function loadClasses() {
  loadingClasses.value = true
  try {
    const res = await scoreApi.myClasses({ term: classQuery.term || undefined, keyword: classQuery.keyword || undefined })
    classList.value = res.data || []
  } finally {
    loadingClasses.value = false
  }
}

const isLockedRow = (row) => row && [1, 2, 3].includes(row.score_status)
const uploadRef = ref(null)
const importFile = ref(null)
const importDryRun = ref(false)
const importLoading = ref(false)
const importVisible = ref(false)
const importResult = reactive({ message: '', failed: 0, errors: [] })

async function openClass(row, options = {}) {
  const switched = currentClass.value?.lesson_no !== row.lesson_no
  currentClass.value = row
  if (switched || !options.keepFile) {
    importFile.value = null
    importDryRun.value = false
    uploadRef.value?.clearFiles?.()
  }
  const res = await scoreApi.getRoster(row.lesson_no)
  const defaultScoreType = res.data.default_score_type || 1
  roster.students = res.data.students || []
  roster.ratio_min = res.data.ratio_min
  roster.verify_code = res.data.verify_code
  roster.default_score_type = defaultScoreType
  roster.has_active_correction = Boolean(res.data.has_active_correction)
  classRatio.value = options.overrideRatio ?? res.data.usual_ratio ?? res.data.ratio_min ?? 30
  roster.students.forEach((s) => {
    if (s.usual_ratio == null) s.usual_ratio = classRatio.value
    if (s.exam_flag == null) s.exam_flag = 0
    if (s.score_type == null) s.score_type = defaultScoreType
  })
}

async function downloadTemplate() {
  if (!currentClass.value) return
  templateLoading.value = true
  try {
    const blob = await scoreApi.downloadTemplate(currentClass.value.lesson_no)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `成绩录入模板_${currentClass.value.lesson_no}.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('Excel 模板已下载，请填写平时成绩与考试成绩后上传')
  } finally {
    templateLoading.value = false
  }
}

async function applyRatio() {
  const targetRatio = classRatio.value
  const res = await scoreApi.setClassRatio(currentClass.value.lesson_no, targetRatio)
  ElMessage.success(res.message || `课堂平时占比已设为 ${targetRatio}%`)
  if (res.data?.persisted) {
    await openClass(currentClass.value, { keepFile: true, overrideRatio: targetRatio })
  } else {
    roster.students.forEach((s) => {
      if (!isLockedRow(s)) s.usual_ratio = targetRatio
    })
  }
}

function onImportFile(uploadFile) {
  importFile.value = uploadFile.raw
}

async function doImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  importLoading.value = true
  try {
    const res = await scoreApi.importGrades(
      currentClass.value.lesson_no, importFile.value, classRatio.value, importDryRun.value)
    Object.assign(importResult, { message: '', failed: 0, errors: [] }, res.data)
    importVisible.value = true
    if (!importDryRun.value) {
      await openClass(currentClass.value)
    }
  } finally {
    importLoading.value = false
  }
}

async function saveAll() {
  const editable = roster.students.filter((s) => !isLockedRow(s))
  if (!editable.length) {
    ElMessage.info('当前课堂所有学生成绩均已提交或发布，无需保存')
    return
  }
  const items = editable.map((s) => ({
    sc_id: s.sc_id,
    usual_ratio: classRatio.value,
    usual_score: s.usual_score,
    exam_score: s.exam_score,
    exam_flag: s.exam_flag,
    score_type: s.score_type || 1,
    score_level: s.score_level
  }))
  await scoreApi.saveGrades(currentClass.value.lesson_no, items, classRatio.value)
  ElMessage.success('成绩已保存')
  await openClass(currentClass.value, { keepFile: true })
}

const codeVisible = ref(false)
const verifyCode = ref('')
const isCorrectionResubmit = ref(false)
const sheetData = reactive({
  lesson_no: '',
  course_name: '',
  term: '',
  usual_ratio: null,
  default_score_type: 1,
  has_active_correction: false,
  students: []
})

function fillSheetData(data) {
  sheetData.lesson_no = data?.lesson_no || currentClass.value?.lesson_no || ''
  sheetData.course_name = data?.course_name || currentClass.value?.course_name || ''
  sheetData.term = data?.term || currentClass.value?.term || ''
  sheetData.usual_ratio = data?.usual_ratio ?? classRatio.value
  sheetData.default_score_type = data?.default_score_type || 1
  sheetData.has_active_correction = Boolean(data?.has_active_correction)
  sheetData.students = data?.students || []
}

async function submitAll() {
  const prompt = roster.has_active_correction
    ? '提交后更正成绩将重新锁定并生成新的 6 位核对码，请打印新版纸质成绩登记表交学院核对，确认提交？'
    : '提交后成绩锁定，学院将按核对码核对，确认提交？'
  await ElMessageBox.confirm(prompt, '提示', { type: 'warning' })
  const res = await scoreApi.submit(currentClass.value.lesson_no)
  verifyCode.value = res.data.verify_code
  isCorrectionResubmit.value = Boolean(res.data.is_correction_resubmit)
  await loadClasses()
  await openClass(currentClass.value)
  const sheetRes = await scoreApi.sheet(currentClass.value.lesson_no)
  fillSheetData(sheetRes.data)
  verifyCode.value = sheetRes.data.verify_code || res.data.verify_code
  codeVisible.value = true
}

async function showSheet() {
  const res = await scoreApi.sheet(currentClass.value.lesson_no)
  fillSheetData(res.data)
  verifyCode.value = res.data.verify_code || ''
  isCorrectionResubmit.value = Boolean(res.data.has_active_correction)
  codeVisible.value = true
}

function printSheet() {
  window.print()
}

const statusText = (s) => ({ 0: '录入中', 1: '已提交', 2: '学院已核对', 3: '已发布', 4: '退回修改' }[s] ?? '未录入')
const statusType = (s) => ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[s] ?? 'info')

// 3.3
const auditTerm = ref('')
const auditList = ref([])
const loadingAudit = ref(false)
const auditVisible = ref(false)
const auditRejectMode = ref(false)
const auditForm = reactive({ lesson_no: '', verify_code: '', opinion: '' })

async function loadAudit() {
  loadingAudit.value = true
  try {
    const res = await scoreApi.auditPending({ term: auditTerm.value || undefined })
    auditList.value = res.data || []
  } finally {
    loadingAudit.value = false
  }
}

function openAudit(row) {
  auditForm.lesson_no = row.lesson_no
  auditForm.verify_code = ''
  auditForm.opinion = ''
  auditRejectMode.value = false
  auditVisible.value = true
}

async function doAudit(result) {
  const code = auditForm.verify_code.trim()
  if (!code) {
    ElMessage.warning('请输入 6 位核对码')
    return
  }
  const opinion = auditForm.opinion.trim()
  if (result === 2 && !opinion) {
    ElMessage.warning('退回时请填写退回原因')
    return
  }
  await scoreApi.audit(auditForm.lesson_no, {
    verify_code: code,
    result,
    opinion: result === 2 ? opinion : ''
  })
  ElMessage.success(result === 1 ? '核对码校验通过，等待教务处发布' : '已退回任课教师修改')
  auditVisible.value = false
  await loadAudit()
}

// 3.4
const publishTerm = ref('')
const publishList = ref([])
const loadingPublish = ref(false)

async function loadPublish() {
  loadingPublish.value = true
  try {
    const res = await scoreApi.publishPending({ term: publishTerm.value || undefined })
    publishList.value = res.data || []
  } finally {
    loadingPublish.value = false
  }
}

async function doPublish(row) {
  const tip = row.correction_count > 0
    ? `确认发布课堂 ${row.lesson_no} 的成绩？其中包含 ${row.correction_count} 条成绩更正重报记录，发布后将同步生效并通知学生。`
    : `确认发布课堂 ${row.lesson_no} 的成绩？发布后学生可查。`
  await ElMessageBox.confirm(tip, '发布确认', { type: 'warning' })
  const res = await scoreApi.publish(row.lesson_no)
  const fin = res.data?.finalized_corrections || 0
  ElMessage.success(fin > 0 ? `成绩已发布，同步完结 ${fin} 条成绩更正并生效` : '成绩已发布')
  await loadPublish()
}

// 3.5
const exchangeQuery = reactive({ student_id: '', keyword: '' })
const exchangeList = ref([])
const exchangeVisible = ref(false)
const exchangeForm = reactive({
  student_id: '', term: '2026-2027-1', host_school: '', ext_course_name: '',
  ext_credit: 2, ext_score_raw: '', proof_no: '', course_id: '', score_type: 1, converted_score: 90, converted_level: '', ext_required: false
})

async function loadExchange() {
  const res = await scoreApi.listExchange({
    student_id: exchangeQuery.student_id || undefined,
    keyword: exchangeQuery.keyword || undefined
  })
  exchangeList.value = res.data || []
}

const courseCandidates = ref([])
const loadingCandidates = ref(false)

async function searchCourseCandidates(keyword = '') {
  loadingCandidates.value = true
  try {
    const res = await scoreApi.exchangeCandidates({ keyword: keyword || undefined })
    courseCandidates.value = res.data || []
  } finally {
    loadingCandidates.value = false
  }
}

function openExchange() {
  exchangeVisible.value = true
  searchCourseCandidates('')
}

async function doExchange() {
  await scoreApi.createExchange({ ...exchangeForm })
  ElMessage.success('已登记')
  exchangeVisible.value = false
  await loadExchange()
}

async function decideExchange(row, stage, decision) {
  let opinion = ''
  if (decision === 2) {
    const promptRes = await ElMessageBox.prompt('请填写驳回意见', '驳回确认', {
      inputPlaceholder: '必填，说明退回原因',
      inputValidator: (v) => (v && v.trim() ? true : '驳回意见不能为空')
    })
    opinion = promptRes.value
  } else {
    await ElMessageBox.confirm(`确认通过 ${row.student_id}《${row.ext_course_name}》的校外成绩认定？`, '审批确认', { type: 'info' })
  }
  if (stage === 'college') {
    await scoreApi.collegeAuditExchange(row.convert_id, { decision, opinion })
  } else {
    await scoreApi.schoolApproveExchange(row.convert_id, { decision, opinion })
  }
  ElMessage.success(decision === 1 ? '审批通过' : '已驳回')
  await loadExchange()
}

async function recognizeExchange(row) {
  await ElMessageBox.confirm(
    `教务处直接认定 ${row.student_id}《${row.ext_course_name}》→ 本校课程 ${row.course_id}？认定后立即计入成绩与 GPA。`,
    '教务处认定', { type: 'warning' })
  await scoreApi.recognizeExchange(row.convert_id, { opinion: '教务处直接认定' })
  ElMessage.success('已认定生效')
  await loadExchange()
}

async function initByRole() {
  currentClass.value = null
  roster.students = []
  roster.has_active_correction = false
  classList.value = []
  auditList.value = []
  publishList.value = []
  exchangeList.value = []
  if (!role.value) return
  const qTab = typeof route.query.tab === 'string' ? route.query.tab : ''
  const qLesson = typeof route.query.lesson_no === 'string' ? route.query.lesson_no : ''
  if (isTeacher.value) {
    activeTab.value = 'entry'
    await loadClasses()
    if (qLesson) {
      const target = classList.value.find((c) => c.lesson_no === qLesson)
      if (target) await openClass(target)
    }
  } else if (isCollege.value) {
    activeTab.value = ['audit', 'exchange'].includes(qTab) ? qTab : 'audit'
    await loadAudit()
    loadExchange()
  } else if (isOffice.value) {
    activeTab.value = ['publish', 'exchange'].includes(qTab) ? qTab : 'publish'
    await loadPublish()
    loadExchange()
  } else if (isAdmin.value) {
    activeTab.value = ['entry', 'audit', 'publish', 'exchange'].includes(qTab) ? qTab : 'entry'
    await loadClasses()
    if (qLesson) {
      const target = classList.value.find((c) => c.lesson_no === qLesson)
      if (target) await openClass(target)
    }
    loadAudit()
    loadPublish()
    loadExchange()
  }
}

watch(() => userState.currentUser?.userId, () => {
  initByRole()
})

watch(() => route.query.lesson_no, async (lessonNo) => {
  if (!lessonNo || (!isTeacher.value && !isAdmin.value)) return
  if (!classList.value.length) await loadClasses()
  const target = classList.value.find((c) => c.lesson_no === lessonNo)
  if (target) {
    activeTab.value = 'entry'
    await openClass(target)
  }
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
.sheet-line {
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.muted {
  color: var(--app-text-secondary);
  font-size: 12.5px;
}
.ratio-label {
  font-size: 13px;
  color: var(--app-text-secondary);
}
.corr-note {
  font-size: 11.5px;
  color: #c45656;
  margin-top: 2px;
}
.paper-sheet {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 16px;
  background: #fff;
}
.paper-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
}
.paper-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
}
.paper-sub {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.code-box {
  border: 2px solid #409eff;
  border-radius: 6px;
  padding: 8px 16px;
  text-align: center;
  min-width: 145px;
  background: #ecf5ff;
}
.code-box-label {
  font-size: 12px;
  color: #409eff;
}
.code-box-val {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 3px;
  color: #1d39c4;
  margin-top: 2px;
}
.paper-sign {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px dashed #dcdfe6;
  font-size: 13px;
  color: #303133;
}
@media print {
  .paper-sheet {
    border: none;
    padding: 0;
  }
}
</style>
