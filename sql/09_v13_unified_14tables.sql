-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 本组 14 张表统一版建表脚本（DBA 统一字典 v1.3）
-- 文件：09_v13_unified_14tables.sql
-- 依据：系统数据字典统一版v1.3.xlsx（工作表 G03_选课及成绩管理_数据库字典）
-- 目标：MySQL 8.0.16 及以上，InnoDB，utf8mb4
--
-- 说明：
--   1. 本脚本一键建立本组全部 14 张正式业务表（sel_* 前缀）、134 个字段、
--      6 条本组内部外键与 82 条 ENFORCED CHECK 约束，全部表名、字段名、
--      数据类型与长度均与全班统一数据字典 v1.3 严格对齐。
--   2. 14 处统一时间字段名：
--      - sel_select_round: start_at / end_at / approved_at / published_at
--      - sel_select_log: operated_at
--      - sel_score: submitted_at / published_at
--      - sel_score_audit: submitted_at / reviewed_at
--      - sel_score_correction: submitted_at / reviewed_at
--      - sel_stu_gpa / sel_stu_term_gpa: updated_at
--      - sel_exchange_score_convert: submitted_at
--   3. 不依赖任何外部表，可直接交 DBA 合库执行；跨组物理外键由 DBA 合库时按
--      03_主外键关系矩阵 统一追加。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 业务 ① 选课准备与发布（3 表：sel_select_round, sel_round_class, sel_select_limit）
-- ---------------------------------------------------------------------

-- 1. 选课轮次表（17 字段，14 CHECK）
CREATE TABLE sel_select_round (
  round_id               INT           NOT NULL AUTO_INCREMENT COMMENT '轮次编号，系统自动生成',
  term                   CHAR(11)      NOT NULL COMMENT '学年学期，如 2026-2027-1，引用 aca_term.term',
  round_name             VARCHAR(30)   NOT NULL COMMENT '轮次名称，如 第一轮正选',
  round_type             INT           NOT NULL COMMENT '轮次类型：1预选 2正选 3补退选',
  target_level           INT           NOT NULL DEFAULT 1 COMMENT '适用学生：1本科生 2研究生 3全部',
  start_at               DATETIME      NOT NULL COMMENT '开始时间（原 start_time）',
  end_at                 DATETIME      NOT NULL COMMENT '结束时间，晚于 start_at（原 end_time）',
  max_credit             DECIMAL(5,1)  NOT NULL COMMENT '每名学生本轮最多可选的学分，如 30.0',
  select_mode            INT           NOT NULL COMMENT '选课方式：1先到先得 2超额后按规则筛选',
  allow_retake           TINYINT       NOT NULL DEFAULT 0 COMMENT '是否开放重修生选课：0否 1是',
  retake_ignore_conflict INT           NOT NULL DEFAULT 0 COMMENT '重修生是否允许时间冲突免听：0否 1是（仅 allow_retake=1 时可设为1）',
  round_status           TINYINT       NOT NULL DEFAULT 0 COMMENT '轮次状态：0草稿 1待审批 2已发布 3进行中 4已结束 5已审批待发布',
  notice_content         TEXT          NULL COMMENT '选课通知内容，发布时必填',
  published_at           DATETIME      NULL COMMENT '发布时间，审批通过并发布通知时写入（原 publish_time）',
  approver_id            CHAR(10)      NULL COMMENT '最近一次方案审批人工号，引用 hr_employee.emp_id',
  approved_at            DATETIME      NULL COMMENT '最近一次方案审批时间，重新提交时清空（原 approve_time）',
  approve_opinion        VARCHAR(200)  NULL COMMENT '最近一次方案审批意见，退回时必填',
  PRIMARY KEY (round_id),
  KEY idx_sel_select_round_approver (approver_id),
  CONSTRAINT ck_sel_select_round_type            CHECK (round_type IN (1, 2, 3)),
  CONSTRAINT ck_sel_select_round_level           CHECK (target_level IN (1, 2, 3)),
  CONSTRAINT ck_sel_select_round_time            CHECK (end_at > start_at),
  CONSTRAINT ck_sel_select_round_credit          CHECK (max_credit > 0),
  CONSTRAINT ck_sel_select_round_mode            CHECK (select_mode IN (1, 2)),
  CONSTRAINT ck_sel_select_round_allow_retake    CHECK (allow_retake IN (0, 1)),
  CONSTRAINT ck_sel_select_round_retake_conflict CHECK (retake_ignore_conflict IN (0, 1)),
  CONSTRAINT ck_sel_select_round_retake_pair     CHECK (retake_ignore_conflict = 0 OR allow_retake = 1),
  CONSTRAINT ck_sel_select_round_status          CHECK (round_status IN (0, 1, 2, 3, 4, 5)),
  CONSTRAINT ck_sel_select_round_notice          CHECK (round_status NOT IN (2, 3, 4) OR (notice_content IS NOT NULL AND LENGTH(TRIM(notice_content)) > 0)),
  CONSTRAINT ck_sel_select_round_pubtime         CHECK (round_status NOT IN (2, 3, 4) OR published_at IS NOT NULL),
  CONSTRAINT ck_sel_select_round_approved        CHECK (round_status NOT IN (5, 2, 3, 4) OR (approver_id IS NOT NULL AND approved_at IS NOT NULL)),
  CONSTRAINT ck_sel_select_round_approval_pair   CHECK (
        (approver_id IS NULL AND approved_at IS NULL AND approve_opinion IS NULL)
     OR (approver_id IS NOT NULL AND CHAR_LENGTH(TRIM(approver_id)) > 0 AND approved_at IS NOT NULL)
  ),
  CONSTRAINT ck_sel_select_round_pending_clear   CHECK (round_status <> 1 OR (approver_id IS NULL AND approved_at IS NULL AND approve_opinion IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课轮次表（业务①，DBA统一版v1.3）';

-- 2. 轮次开放教学班表（2 字段，0 CHECK，1 内部外键）
CREATE TABLE sel_round_class (
  round_id INT      NOT NULL COMMENT '轮次编号，关联 sel_select_round.round_id',
  tc_id    CHAR(20) NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id',
  PRIMARY KEY (round_id, tc_id),
  KEY idx_sel_round_class_tc (tc_id),
  CONSTRAINT fk_sel_round_class_round FOREIGN KEY (round_id) REFERENCES sel_select_round (round_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '轮次开放教学班表（业务①，DBA统一版v1.3）';

-- 3. 选课限制条件表（4 字段，1 CHECK）
CREATE TABLE sel_select_limit (
  limit_id    INT         NOT NULL AUTO_INCREMENT COMMENT '限制编号，系统自动生成',
  tc_id       CHAR(20)    NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id',
  limit_type  INT         NOT NULL COMMENT '限制类型：1限年级 2限专业 3限学院 4限培养层次',
  limit_value VARCHAR(20) NOT NULL COMMENT '限制值：年级如2024；专业填major_id；学院填college_id；培养层次填degree_level取值',
  PRIMARY KEY (limit_id),
  UNIQUE KEY uk_sel_select_limit_cond (tc_id, limit_type, limit_value),
  CONSTRAINT ck_sel_select_limit_type CHECK (limit_type IN (1, 2, 3, 4))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课限制条件表（业务①，DBA统一版v1.3）';

-- ---------------------------------------------------------------------
-- 业务 ② 学生选课（2 表：sel_sc, sel_select_log）+ 课堂组成表（sel_lesson_class）
-- ---------------------------------------------------------------------

-- 4. 课堂组成表（2 字段，0 CHECK）
CREATE TABLE sel_lesson_class (
  tc_id     CHAR(20)    NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id；一个教学班只属于一个课堂',
  lesson_no VARCHAR(20) NOT NULL COMMENT '课堂号，如 KT202610001；同一课堂号下的教学班须同学期、同课程、同任课教师',
  PRIMARY KEY (tc_id),
  KEY idx_sel_lesson_class_no (lesson_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课堂组成表（业务③，DBA统一版v1.3）';

-- 5. 选课记录表（12 字段，7 CHECK，1 内部外键）
CREATE TABLE sel_sc (
  sc_id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '选课记录号，系统自动生成',
  student_id       CHAR(20)    NOT NULL COMMENT '学号，关联 std_student.student_id',
  tc_id            CHAR(20)    NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id',
  round_id         INT         NOT NULL COMMENT '选课轮次，关联 sel_select_round.round_id',
  study_type       TINYINT     NOT NULL DEFAULT 1 COMMENT '修读类型：1初修 2重修 3辅修（含双学位，不计入主修统计）',
  is_exempt_attend TINYINT     NOT NULL DEFAULT 0 COMMENT '是否冲突免听：0正常跟班 1冲突免听（仅重修生可置1）',
  stu_level        VARCHAR(20) NOT NULL COMMENT '选课时培养层次快照',
  stat_stage       TINYINT     NOT NULL DEFAULT 1 COMMENT '成绩归属阶段：1本科阶段 2研究生阶段',
  priority         INT         NULL COMMENT '志愿优先级：1第一志愿 2第二志愿 3第三志愿，仅超额筛选轮次填写',
  select_status    TINYINT     NOT NULL COMMENT '选课状态：0待筛选 1已选中 2未选中 3已退选',
  select_time      DATETIME    NOT NULL COMMENT '选课时间',
  drop_time        DATETIME    NULL COMMENT '退选时间，未退选时为空',
  PRIMARY KEY (sc_id),
  UNIQUE KEY uk_sel_sc_student_tc (student_id, tc_id),
  KEY idx_sel_sc_tc (tc_id),
  CONSTRAINT fk_sel_sc_round      FOREIGN KEY (round_id) REFERENCES sel_select_round (round_id),
  CONSTRAINT ck_sel_sc_study_type     CHECK (study_type IN (1, 2, 3)),
  CONSTRAINT ck_sel_sc_exempt_attend  CHECK (is_exempt_attend IN (0, 1)),
  CONSTRAINT ck_sel_sc_exempt_retake  CHECK (is_exempt_attend = 0 OR study_type = 2),
  CONSTRAINT ck_sel_sc_stat_stage     CHECK (stat_stage IN (1, 2)),
  CONSTRAINT ck_sel_sc_priority       CHECK (priority IS NULL OR priority IN (1, 2, 3)),
  CONSTRAINT ck_sel_sc_status         CHECK (select_status IN (0, 1, 2, 3)),
  CONSTRAINT ck_sel_sc_drop_time      CHECK (select_status = 3 OR drop_time IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课记录表（业务②，DBA统一版v1.3）；不物理删除';

-- 6. 选课日志表（8 字段，2 CHECK，1 内部外键）
CREATE TABLE sel_select_log (
  log_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志编号，系统自动生成；只追加',
  student_id  CHAR(20)     NOT NULL COMMENT '学号，关联 std_student.student_id',
  tc_id       CHAR(20)     NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id',
  round_id    INT          NOT NULL COMMENT '选课轮次，关联 sel_select_round.round_id',
  action_type TINYINT      NOT NULL COMMENT '操作类型：1选课 2退课 3筛选未选中 4教务处/教师名单调整',
  operator_id VARCHAR(40)  NOT NULL COMMENT '操作人：学生填学号，教务/教师调整填工号',
  operated_at DATETIME     NOT NULL COMMENT '操作时间（原 action_time）',
  remark      VARCHAR(500) NULL COMMENT '备注，名单调整时必填调整原因',
  PRIMARY KEY (log_id),
  KEY idx_sel_select_log_student (student_id),
  KEY idx_sel_select_log_tc (tc_id),
  CONSTRAINT fk_sel_select_log_round FOREIGN KEY (round_id) REFERENCES sel_select_round (round_id),
  CONSTRAINT ck_sel_select_log_action    CHECK (action_type IN (1, 2, 3, 4)),
  CONSTRAINT ck_sel_select_log_remark    CHECK (action_type <> 4 OR (remark IS NOT NULL AND remark <> ''))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课日志表（业务②，DBA统一版v1.3）；只追加';

-- ---------------------------------------------------------------------
-- 业务 ③ 成绩录入与审核（3 表：sel_score, sel_score_audit, sel_exchange_score_convert）
-- ---------------------------------------------------------------------

-- 7. 成绩表（16 字段，14 CHECK，1 内部外键）
CREATE TABLE sel_score (
  score_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '成绩记录号，系统自动生成',
  sc_id            BIGINT       NOT NULL COMMENT '选课记录号，关联 sel_sc.sc_id',
  exam_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '考试类别：1正常考试 2补考',
  exam_flag        TINYINT      NOT NULL DEFAULT 0 COMMENT '考试情况：0正常 1缺考 2缓考 3违纪',
  score_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '记分方式：1百分制 2五级制 3两级制',
  usual_ratio      DECIMAL(5,2) NOT NULL COMMENT '平时成绩占比 0~100，如 30 表示 30%；同一课堂号统一设定',
  usual_score      DECIMAL(5,2) NULL COMMENT '平时成绩 0~100',
  exam_score       DECIMAL(5,2) NULL COMMENT '期末成绩 0~100，空表示未考',
  total_score      DECIMAL(5,2) NULL COMMENT '总评成绩 0~100，百分制按占比自动计算',
  score_level      VARCHAR(6)   NULL COMMENT '等级成绩：五级制 优/良/中/及格/不及格；两级制 合格/不合格',
  grade_point      DECIMAL(5,2) NULL COMMENT '课程绩点，按校发〔2021〕46号附表3换算',
  is_final         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否本阶段最终成绩：同生同阶段同课程已发布/外校认定成绩中最高一条置1',
  score_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '成绩状态：0录入中 1已提交 2学院已审核 3已发布 4退回修改',
  input_teacher_id CHAR(10)     NULL COMMENT '录入教师工号，关联 hr_employee.emp_id',
  submitted_at     DATETIME     NULL COMMENT '提交时间（原 submit_time）',
  published_at     DATETIME     NULL COMMENT '发布时间（原 publish_time）',
  PRIMARY KEY (score_id),
  UNIQUE KEY uk_sel_score_sc_exam (sc_id, exam_type),
  KEY idx_sel_score_teacher (input_teacher_id),
  CONSTRAINT fk_sel_score_sc     FOREIGN KEY (sc_id) REFERENCES sel_sc (sc_id),
  CONSTRAINT ck_sel_score_exam_type  CHECK (exam_type IN (1, 2)),
  CONSTRAINT ck_sel_score_exam_flag  CHECK (exam_flag IN (0, 1, 2, 3)),
  CONSTRAINT ck_sel_score_score_type CHECK (score_type IN (1, 2, 3)),
  CONSTRAINT ck_sel_score_ratio      CHECK (usual_ratio BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_usual      CHECK (usual_score IS NULL OR usual_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_exam       CHECK (exam_score IS NULL OR exam_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_total      CHECK (total_score IS NULL OR total_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_level      CHECK (
        (score_type = 1 AND score_level IS NULL)
     OR (score_type = 2 AND (score_level IS NULL OR score_level IN ('优', '良', '中', '及格', '不及格')))
     OR (score_type = 3 AND (score_level IS NULL OR score_level IN ('合格', '不合格')))
  ),
  CONSTRAINT ck_sel_score_gp         CHECK (grade_point IS NULL OR grade_point IN (4.00, 3.70, 3.40, 3.00, 2.40, 2.00, 1.00, 0.00)),
  CONSTRAINT ck_sel_score_is_final   CHECK (is_final IN (0, 1)),
  CONSTRAINT ck_sel_score_status     CHECK (score_status IN (0, 1, 2, 3, 4)),
  CONSTRAINT ck_sel_score_final_pub  CHECK (is_final = 0 OR score_status = 3),
  CONSTRAINT ck_sel_score_submit     CHECK (score_status = 0 OR submitted_at IS NOT NULL),
  CONSTRAINT ck_sel_score_publish    CHECK (score_status <> 3 OR published_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩表（业务③，DBA统一版v1.3）；不删除';

-- 8. 成绩审核记录表（8 字段，5 CHECK）
CREATE TABLE sel_score_audit (
  audit_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录号，系统自动生成',
  lesson_no     VARCHAR(20)  NOT NULL COMMENT '课堂号，按课堂整体提交与核对，关联 sel_lesson_class.lesson_no',
  submitted_at  DATETIME     NOT NULL COMMENT '本次提交时间，与 sel_score.submitted_at 对应（原 submit_time）',
  verify_code   CHAR(6)      NOT NULL COMMENT '6位核对随机码（大写字母+数字，排除0/O/1/I）',
  auditor_id    CHAR(10)     NULL COMMENT '审核人工号，关联 hr_employee.emp_id；待核对时为空',
  audit_result  TINYINT      NOT NULL DEFAULT 0 COMMENT '审核结果：0待核对 1通过 2退回',
  audit_opinion VARCHAR(500) NULL COMMENT '审核意见，退回时必填',
  reviewed_at   DATETIME     NULL COMMENT '审核时间；待核对时为空（原 audit_time）',
  PRIMARY KEY (audit_id),
  UNIQUE KEY uk_sel_score_audit_lesson_submit (lesson_no, submitted_at),
  KEY idx_sel_score_audit_lesson_time (lesson_no, reviewed_at),
  KEY idx_sel_score_audit_auditor (auditor_id),
  CONSTRAINT ck_sel_score_audit_result  CHECK (audit_result IN (0, 1, 2)),
  CONSTRAINT ck_sel_score_audit_code    CHECK (REGEXP_LIKE(verify_code, '^[2-9A-HJ-NP-Z]{6}$', 'c')),
  CONSTRAINT ck_sel_score_audit_pending CHECK (audit_result <> 0 OR (auditor_id IS NULL AND reviewed_at IS NULL AND audit_opinion IS NULL)),
  CONSTRAINT ck_sel_score_audit_done    CHECK (audit_result = 0 OR (auditor_id IS NOT NULL AND CHAR_LENGTH(TRIM(auditor_id)) > 0 AND reviewed_at IS NOT NULL)),
  CONSTRAINT ck_sel_score_audit_opinion CHECK (audit_result <> 2 OR (audit_opinion IS NOT NULL AND LENGTH(TRIM(audit_opinion)) > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩审核记录表（业务③，DBA统一版v1.3）；只追加';

-- 9. 校外交流成绩换算认定表（28 字段，17 CHECK）
CREATE TABLE sel_exchange_score_convert (
  convert_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '校外成绩认定记录号，系统自动生成',
  student_id            CHAR(20)     NOT NULL COMMENT '本校学号，关联 std_student.student_id',
  exchange_apply_id     VARCHAR(50)  NULL     COMMENT '外出交流申请业务号，对齐第15组 application_no',
  term                  CHAR(11)     NOT NULL COMMENT '交流归属学年学期，如 2026-2027-1，关联 aca_term.term',
  stat_stage            TINYINT      NOT NULL DEFAULT 1 COMMENT '成绩归属阶段：1本科阶段 2研究生阶段',
  host_school           VARCHAR(200) NOT NULL COMMENT '交流院校名称',
  ext_course_code       VARCHAR(30)  NULL     COMMENT '外校课程代码（无代码可空）',
  ext_course_name       VARCHAR(100) NOT NULL COMMENT '外校课程名称',
  ext_credit            DECIMAL(5,1) NOT NULL COMMENT '外校原始学分，须大于0',
  ext_score_raw         VARCHAR(20)  NOT NULL COMMENT '外校原始成绩，如 92、A-、3.7/4.0',
  ext_scale_type        INT          NOT NULL COMMENT '外校记分制：1百分制 2字母等级制 3四分/五分绩点制 4其他',
  course_id             CHAR(10)     NOT NULL COMMENT '认定对应的本校课程号，关联 aca_course.course_id',
  score_type            TINYINT      NOT NULL DEFAULT 1 COMMENT '换算后本校记分方式：1百分制 2五级制 3两级制',
  converted_score       DECIMAL(5,2) NULL     COMMENT '换算后本校百分制成绩 0~100；等级制时为空',
  converted_level       VARCHAR(20)  NULL     COMMENT '换算后本校等级成绩：五级制 优/良/中/及格/不及格；两级制 合格/不合格',
  grade_point           DECIMAL(5,2) NOT NULL COMMENT '换算后本校绩点，按校发〔2021〕46号附表3换算',
  is_final              TINYINT      NOT NULL DEFAULT 0 COMMENT '是否本阶段最终成绩：与 sel_score.is_final 联合唯一置1',
  proof_no              VARCHAR(50)  NOT NULL COMMENT '外校官方成绩单编号或核验证明编号',
  proof_file_id         VARCHAR(64)  NULL     COMMENT '外校成绩单原件或认定表PDF文件标识',
  version_no            INT          NOT NULL DEFAULT 1 COMMENT '归档快照版本号，初始为1',
  audit_status          TINYINT      NOT NULL DEFAULT 0 COMMENT '认定状态：0待学院审核 1待教务处审批 2已通过生效 3已驳回',
  submitted_at          DATETIME     NOT NULL COMMENT '申请提交时间（原 apply_time）',
  college_auditor_id    CHAR(10)     NULL     COMMENT '学院审核人工号，关联 hr_employee.emp_id',
  college_audit_opinion VARCHAR(200) NULL     COMMENT '学院审核意见，学院驳回时必填',
  college_audit_time    DATETIME     NULL     COMMENT '学院审核完成时间',
  school_auditor_id     CHAR(10)     NULL     COMMENT '教务处审批人工号，关联 hr_employee.emp_id',
  school_audit_opinion  VARCHAR(200) NULL     COMMENT '教务处审批意见，教务处驳回时必填',
  school_audit_time     DATETIME     NULL     COMMENT '教务处审批完成（生效）时间',
  PRIMARY KEY (convert_id),
  KEY idx_esc_student_stage_course (student_id, stat_stage, course_id),
  KEY idx_esc_student_term (student_id, term),
  KEY idx_esc_status (audit_status),
  KEY idx_esc_exchange_apply (exchange_apply_id),
  KEY idx_esc_course (course_id),
  KEY idx_esc_college_auditor (college_auditor_id),
  KEY idx_esc_school_auditor (school_auditor_id),
  CONSTRAINT ck_sel_esc_stat_stage       CHECK (stat_stage IN (1, 2)),
  CONSTRAINT ck_sel_esc_ext_credit       CHECK (ext_credit > 0),
  CONSTRAINT ck_sel_esc_ext_scale        CHECK (ext_scale_type IN (1, 2, 3, 4)),
  CONSTRAINT ck_sel_esc_score_type       CHECK (score_type IN (1, 2, 3)),
  CONSTRAINT ck_sel_esc_conv_score       CHECK (converted_score IS NULL OR converted_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_esc_value_pair       CHECK (
        (score_type = 1 AND converted_score IS NOT NULL AND converted_level IS NULL)
     OR (score_type = 2 AND converted_score IS NULL AND converted_level IN ('优', '良', '中', '及格', '不及格'))
     OR (score_type = 3 AND converted_score IS NULL AND converted_level IN ('合格', '不合格'))
  ),
  CONSTRAINT ck_sel_esc_gp               CHECK (grade_point IN (4.00, 3.70, 3.40, 3.00, 2.40, 2.00, 1.00, 0.00)),
  CONSTRAINT ck_sel_esc_is_final         CHECK (is_final IN (0, 1)),
  CONSTRAINT ck_sel_esc_final_active     CHECK (is_final = 0 OR audit_status = 2),
  CONSTRAINT ck_sel_esc_version_no       CHECK (version_no >= 1),
  CONSTRAINT ck_sel_esc_status           CHECK (audit_status IN (0, 1, 2, 3)),
  CONSTRAINT ck_sel_esc_proof_nonempty   CHECK (LENGTH(TRIM(proof_no)) > 0),
  CONSTRAINT ck_sel_esc_college_done     CHECK (audit_status = 0 OR (college_auditor_id IS NOT NULL AND college_audit_time IS NOT NULL)),
  CONSTRAINT ck_sel_esc_pending_clear    CHECK (audit_status <> 0 OR (college_auditor_id IS NULL AND college_audit_time IS NULL AND school_auditor_id IS NULL AND school_audit_time IS NULL)),
  CONSTRAINT ck_sel_esc_school_pass      CHECK (audit_status <> 2 OR (school_auditor_id IS NOT NULL AND school_audit_time IS NOT NULL)),
  CONSTRAINT ck_sel_esc_school_wait      CHECK (audit_status <> 1 OR (school_auditor_id IS NULL AND school_audit_time IS NULL)),
  CONSTRAINT ck_sel_esc_reject_opinion   CHECK (
        audit_status <> 3
     OR (school_auditor_id IS NULL AND college_audit_opinion IS NOT NULL AND LENGTH(TRIM(college_audit_opinion)) > 0)
     OR (school_auditor_id IS NOT NULL AND school_audit_time IS NOT NULL AND school_audit_opinion IS NOT NULL AND LENGTH(TRIM(school_audit_opinion)) > 0)
  )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校外交流成绩换算认定表（业务③ 3.5，DBA统一版v1.3）';

-- ---------------------------------------------------------------------
-- 业务 ⑤ 成绩更正申请（1 表：sel_score_correction）
-- ---------------------------------------------------------------------

-- 10. 成绩更正申请表（16 字段，10 CHECK，1 内部外键）
CREATE TABLE sel_score_correction (
  apply_id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请编号，系统自动生成',
  score_id              BIGINT       NOT NULL COMMENT '成绩记录号，关联 sel_score.score_id',
  apply_teacher_id      CHAR(10)     NOT NULL COMMENT '申请教师工号，关联 hr_employee.emp_id',
  old_score             DECIMAL(5,2) NULL COMMENT '原总评成绩 0~100；等级制时为空',
  new_score             DECIMAL(5,2) NULL COMMENT '更正后成绩 0~100；等级制时为空',
  old_level             VARCHAR(20)  NULL COMMENT '五级或两级制原等级快照',
  new_level             VARCHAR(20)  NULL COMMENT '五级或两级制更正后等级',
  apply_reason          VARCHAR(500) NOT NULL COMMENT '更正原因',
  submitted_at          DATETIME     NOT NULL COMMENT '申请时间（原 apply_time）',
  apply_status          TINYINT      NOT NULL DEFAULT 0 COMMENT '申请状态：0待学院审核 1待学校审批 2已通过 3已驳回',
  college_auditor_id    CHAR(10)     NULL COMMENT '学院审核人工号，关联 hr_employee.emp_id',
  college_audit_opinion VARCHAR(200) NULL COMMENT '学院审核意见，学院驳回必填',
  college_audit_time    DATETIME     NULL COMMENT '学院审核完成时间',
  auditor_id            CHAR(10)     NULL COMMENT '学校审批人工号，关联 hr_employee.emp_id',
  audit_opinion         VARCHAR(500) NULL COMMENT '审批意见，驳回必填',
  reviewed_at           DATETIME     NULL COMMENT '审批时间（原 audit_time）',
  PRIMARY KEY (apply_id),
  KEY idx_sel_score_correction_score (score_id),
  KEY idx_sel_score_correction_teacher (apply_teacher_id),
  KEY idx_sel_score_correction_college (college_auditor_id),
  KEY idx_sel_score_correction_auditor (auditor_id),
  CONSTRAINT fk_sel_score_correction_score   FOREIGN KEY (score_id) REFERENCES sel_score (score_id),
  CONSTRAINT ck_sel_score_correction_old         CHECK (old_score IS NULL OR old_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_correction_new         CHECK (new_score IS NULL OR new_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_score_correction_value_pair  CHECK (
        (old_score IS NOT NULL AND new_score IS NOT NULL AND old_level IS NULL AND new_level IS NULL)
     OR (old_score IS NULL AND new_score IS NULL AND old_level IS NOT NULL AND new_level IS NOT NULL)
  ),
  CONSTRAINT ck_sel_score_correction_old_level   CHECK (old_level IS NULL OR old_level IN ('优', '良', '中', '及格', '不及格', '合格', '不合格')),
  CONSTRAINT ck_sel_score_correction_new_level   CHECK (new_level IS NULL OR new_level IN ('优', '良', '中', '及格', '不及格', '合格', '不合格')),
  CONSTRAINT ck_sel_score_correction_status      CHECK (apply_status IN (0, 1, 2, 3)),
  CONSTRAINT ck_sel_score_correction_college     CHECK (apply_status = 0 OR college_auditor_id IS NOT NULL),
  CONSTRAINT ck_sel_score_correction_college_time CHECK (apply_status = 0 OR college_audit_time IS NOT NULL),
  CONSTRAINT ck_sel_score_correction_school      CHECK (apply_status <> 2 OR (auditor_id IS NOT NULL AND reviewed_at IS NOT NULL)),
  CONSTRAINT ck_sel_score_correction_opinion     CHECK (
        apply_status <> 3
     OR (auditor_id IS NULL AND college_audit_opinion IS NOT NULL AND LENGTH(TRIM(college_audit_opinion)) > 0)
     OR (auditor_id IS NOT NULL AND reviewed_at IS NOT NULL AND audit_opinion IS NOT NULL AND LENGTH(TRIM(audit_opinion)) > 0)
  )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩更正申请表（业务⑤，DBA统一版v1.3）；不删除';

-- ---------------------------------------------------------------------
-- 业务 ④ 成绩查询与统计（4 表：sel_stu_gpa, sel_stu_term_gpa, sel_teach_eval, sel_tc_eval_result）
-- ---------------------------------------------------------------------

-- 11. 学生学业统计表（8 字段，7 CHECK）
CREATE TABLE sel_stu_gpa (
  student_id       CHAR(20)     NOT NULL COMMENT '学号，关联 std_student.student_id',
  earned_credit    DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '已获学分：本阶段 is_final=1 且及格的课程学分之和',
  failed_credit    DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '不及格学分：本阶段 is_final=1 且不及格的课程学分之和',
  gpa              DECIMAL(5,2) NULL COMMENT '平均学分绩点；Σ学分为 0 时留空',
  avg_score        DECIMAL(5,2) NULL COMMENT '加权平均分',
  major_rank       INT          NULL COMMENT '通用专业排名：同专业同年级按 avg_score 从高到低',
  major_rank_total INT          NULL COMMENT '参加排名的同专业、同年级学生人数',
  updated_at       DATETIME     NOT NULL COMMENT '更新时间（原 update_time）',
  PRIMARY KEY (student_id),
  CONSTRAINT ck_sel_stu_gpa_earned  CHECK (earned_credit >= 0),
  CONSTRAINT ck_sel_stu_gpa_failed  CHECK (failed_credit >= 0),
  CONSTRAINT ck_sel_stu_gpa_gpa     CHECK (gpa IS NULL OR gpa BETWEEN 0 AND 4),
  CONSTRAINT ck_sel_stu_gpa_avg     CHECK (avg_score IS NULL OR avg_score BETWEEN 0 AND 100),
  CONSTRAINT ck_sel_stu_gpa_rank    CHECK (major_rank IS NULL OR major_rank >= 1),
  CONSTRAINT ck_sel_stu_gpa_total   CHECK (major_rank_total IS NULL OR major_rank_total >= 1),
  CONSTRAINT ck_sel_stu_gpa_rank_le CHECK (major_rank IS NULL OR major_rank_total IS NULL OR major_rank <= major_rank_total)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生学业统计表（业务④，DBA统一版v1.3）；派生快照';

-- 12. 学生学期学业统计表（5 字段，2 CHECK）
CREATE TABLE sel_stu_term_gpa (
  student_id         CHAR(20)     NOT NULL COMMENT '学号，关联 std_student.student_id',
  term               CHAR(11)     NOT NULL COMMENT '学年学期，如 2026-2027-1，关联 aca_term.term',
  term_credit        DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '本学期已获学分，只计本阶段记录',
  term_failed_credit DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '本学期最终仍不及格的课程学分之和',
  updated_at         DATETIME     NOT NULL COMMENT '更新时间（原 update_time）',
  PRIMARY KEY (student_id, term),
  CONSTRAINT ck_sel_stu_term_gpa_credit CHECK (term_credit >= 0),
  CONSTRAINT ck_sel_stu_term_gpa_failed CHECK (term_failed_credit >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生学期学业统计表（业务④，DBA统一版v1.3）；派生快照';

-- 13. 学生评教记录表（4 字段，1 CHECK，1 内部外键）
CREATE TABLE sel_teach_eval (
  sc_id        BIGINT       NOT NULL COMMENT '选课记录号，关联 sel_sc.sc_id',
  eval_score   DECIMAL(5,2) NOT NULL COMMENT '评教分数 0~100（代码层限制为整数）',
  eval_comment VARCHAR(200) NULL COMMENT '意见建议，明细仅教务处可查',
  eval_time    DATETIME     NOT NULL COMMENT '评教时间',
  PRIMARY KEY (sc_id),
  CONSTRAINT fk_sel_teach_eval_sc  FOREIGN KEY (sc_id) REFERENCES sel_sc (sc_id),
  CONSTRAINT ck_sel_teach_eval_score   CHECK (eval_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生评教记录表（业务④，DBA统一版v1.3）；提交后不可改';

-- 14. 教学班评教结果表（4 字段，2 CHECK）
CREATE TABLE sel_tc_eval_result (
  tc_id          CHAR(20)     NOT NULL COMMENT '教学班号，关联 aca_teaching_class.tc_id',
  eval_num       INT          NOT NULL COMMENT '参评人数',
  avg_eval_score DECIMAL(5,2) NULL COMMENT '评教平均分 0~100；参评人数为0时为空',
  calc_time      DATETIME     NOT NULL COMMENT '统计时间',
  PRIMARY KEY (tc_id),
  CONSTRAINT ck_sel_tc_eval_result_num CHECK (eval_num >= 0),
  CONSTRAINT ck_sel_tc_eval_result_avg CHECK (
        (eval_num = 0 AND avg_eval_score IS NULL)
     OR (eval_num > 0 AND avg_eval_score BETWEEN 0 AND 100)
  )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教学班评教结果表（业务④，DBA统一版v1.3）；派生快照';

-- 结束：14 表、134 字段、6 内部外键、82 CHECK 约束（DBA 统一数据字典 v1.3）。
