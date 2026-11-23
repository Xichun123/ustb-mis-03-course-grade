-- =====================================================================
-- 03组选课及成绩管理：v7设计稿升级到v8设计稿（朱鳞太，v2 冻结版，2026-11-23）
-- 依据：数据字典_03组共享表_朱鳞太_v8_设计稿.xlsx（暂未写回在线共享表）
-- 升级内容：
--   1. 重修生选课方案支持（不新增轮次类型）：
--      - select_round 增加 allow_retake、retake_ignore_conflict（+2列，+3 CHECK）
--      - sc 增加 is_exempt_attend 冲突免听标记（+1列，+2 CHECK）
--   2. 本校学生外校交流成绩换算认定（全部换算为本校成绩与绩点，统一计入本校GPA与已获学分）：
--      - 新增第 13 张表 exchange_score_convert（28列，17 CHECK；已与第15组冻结接口口径：
--        补齐 ext_course_code、proof_file_id、version_no；对齐 exchange_apply_id VARCHAR(40)、
--        host_school VARCHAR(150)、ext_credit DECIMAL(4,1)）
-- 目标：MySQL 8.0.16+；先执行基线脚本与“建表SQL_v6至v7设计稿_朱鳞太_v1.sql”。
-- 升级后合计：13张表、132字段、82条CHECK约束（原v7为12表101字段60条CHECK）。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、选课轮次表 select_round（15 → 17 字段）：不新增轮次类型，增加重修开放与免听开关
-- ---------------------------------------------------------------------
ALTER TABLE select_round
  ADD allow_retake TINYINT NOT NULL DEFAULT 0
    COMMENT '是否开放重修生选课：0否 1是（正选/补退选可设为1，重修生豁免限年级与面向班级限制）'
    AFTER select_mode,
  ADD retake_ignore_conflict TINYINT NOT NULL DEFAULT 0
    COMMENT '重修生是否允许时间冲突免听：0否 1是（仅 allow_retake=1 时可设为1）'
    AFTER allow_retake,
  ADD CONSTRAINT ck_select_round_allow_retake
    CHECK (allow_retake IN (0, 1)),
  ADD CONSTRAINT ck_select_round_retake_conflict
    CHECK (retake_ignore_conflict IN (0, 1)),
  ADD CONSTRAINT ck_select_round_retake_pair
    CHECK (retake_ignore_conflict = 0 OR allow_retake = 1);

-- ---------------------------------------------------------------------
-- 二、选课记录表 sc（11 → 12 字段）：增加重修冲突免听标记
-- ---------------------------------------------------------------------
ALTER TABLE sc
  ADD is_exempt_attend TINYINT NOT NULL DEFAULT 0
    COMMENT '是否冲突免听：0正常跟班 1冲突免听（仅重修生在允许冲突放行时置1，上课名单可见）'
    AFTER study_type,
  ADD CONSTRAINT ck_sc_exempt_attend
    CHECK (is_exempt_attend IN (0, 1)),
  ADD CONSTRAINT ck_sc_exempt_retake
    CHECK (is_exempt_attend = 0 OR study_type = 2);

-- ---------------------------------------------------------------------
-- 三、新增第 13 张表：校外交流成绩换算认定表 exchange_score_convert（业务③ 3.5，28字段）
-- 说明：
--   - 本校学生赴外校交流返校后，经第15组只读视图 v_exchange_credit_ready 校验 ready_flag=1
--     （已返校且返校备案已备案）后，凭合作院校官方成绩单申请换算并认定为本校课程成绩；
--   - 经开课/学生所在学院审核 + 教务处审批生效（audit_status=2）后，全部换算为本校成绩
--     与绩点（按校发〔2021〕46号附表3），与校内 score 联合参与 is_final 标记及
--     stu_gpa / stu_term_gpa 计算（统一计入本校 GPA、加权平均分与已获学分）。
--   - 外键引用第1、2、4组字段见可选脚本“建表SQL_v8外部外键_可选_朱鳞太_v1.sql”；
--     exchange_apply_id 存第15组业务号 v_exchange_credit_ready.application_no（如 SQ20260001），
--     不建跨库物理外键，按 convert_id + version_no + proof_no 向第15组提供归档快照（含 grade_point）。
-- ---------------------------------------------------------------------
CREATE TABLE exchange_score_convert (
  convert_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '校外成绩认定记录号，系统自动生成',
  student_id            CHAR(20)     NOT NULL COMMENT '本校学号，关联 student.student_id（第1组）',
  exchange_apply_id     VARCHAR(40)  NULL     COMMENT '外出交流申请业务号，对齐第15组 v_exchange_credit_ready.application_no（如 SQ20260001）及第1组 exchange_application_id',
  term                  CHAR(11)     NOT NULL COMMENT '交流归属学年学期，如 2026-2027-1，对齐 v_exchange_credit_ready.term_ref',
  stat_stage            TINYINT      NOT NULL DEFAULT 1 COMMENT '成绩归属阶段：1本科阶段 2研究生阶段',
  host_school           VARCHAR(150) NOT NULL COMMENT '外校（合作交流院校）名称，对齐 v_exchange_credit_ready.partner_school_snapshot 与第1组 host_institution',
  ext_course_code       VARCHAR(30)  NULL     COMMENT '外校课程代码（原成绩单有代码时填写，无代码可空）',
  ext_course_name       VARCHAR(100) NOT NULL COMMENT '外校课程名称（原成绩单所载名称）',
  ext_credit            DECIMAL(4,1) NOT NULL COMMENT '外校原始学分，须大于0（对齐第15组 raw_credits DECIMAL(4,1)）',
  ext_score_raw         VARCHAR(20)  NOT NULL COMMENT '外校原始成绩，如 92、A-、3.7/4.0',
  ext_scale_type        TINYINT      NOT NULL COMMENT '外校记分制：1百分制 2字母等级制 3四分/五分绩点制 4其他',
  course_id             CHAR(8)      NOT NULL COMMENT '认定对应的本校课程号，关联 course.course_id（第2组）',
  score_type            TINYINT      NOT NULL DEFAULT 1 COMMENT '换算后本校记分方式：1百分制 2五级制 3两级制',
  converted_score       DECIMAL(4,1) NULL     COMMENT '换算后本校百分制成绩 0~100；等级制时为空',
  converted_level       VARCHAR(6)   NULL     COMMENT '换算后本校等级成绩：五级制 优/良/中/及格/不及格；两级制 合格/不合格；百分制时为空',
  grade_point           DECIMAL(3,2) NOT NULL COMMENT '换算后本校绩点，按校发〔2021〕46号附表3换算，统一计入本校GPA并随归档快照回传第15组',
  is_final              TINYINT      NOT NULL DEFAULT 0 COMMENT '是否本阶段最终成绩：与 score.is_final 联合在同生同阶段同课程内唯一置1，仅生效记录可置1',
  proof_no              VARCHAR(50)  NOT NULL COMMENT '外校官方成绩单编号或核验证明编号（关联第15组 ex_sv_host_transcript.proof_no）',
  proof_file_id         VARCHAR(64)  NULL     COMMENT '外校成绩单原件或认定表PDF文件标识，统一存第15组文件表 file_id 字符串',
  version_no            INT          NOT NULL DEFAULT 1 COMMENT '归档快照版本号，初始为1，每次复核重算或重新生效递增',
  audit_status          TINYINT      NOT NULL DEFAULT 0 COMMENT '认定状态：0待学院审核 1待教务处审批 2已通过生效 3已驳回',
  apply_time            DATETIME     NOT NULL COMMENT '申请提交时间',
  college_auditor_id    CHAR(10)     NULL     COMMENT '学院审核人工号，关联 t_employee.emp_id（第4组）',
  college_audit_opinion VARCHAR(200) NULL     COMMENT '学院审核意见，学院驳回时必填',
  college_audit_time    DATETIME     NULL     COMMENT '学院审核完成时间',
  school_auditor_id     CHAR(10)     NULL     COMMENT '教务处审批人工号，关联 t_employee.emp_id（第4组）',
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
  CONSTRAINT ck_esc_stat_stage       CHECK (stat_stage IN (1, 2)),
  CONSTRAINT ck_esc_ext_credit       CHECK (ext_credit > 0),
  CONSTRAINT ck_esc_ext_scale        CHECK (ext_scale_type IN (1, 2, 3, 4)),
  CONSTRAINT ck_esc_score_type       CHECK (score_type IN (1, 2, 3)),
  CONSTRAINT ck_esc_conv_score       CHECK (converted_score IS NULL OR converted_score BETWEEN 0 AND 100),
  CONSTRAINT ck_esc_value_pair       CHECK (
        (score_type = 1 AND converted_score IS NOT NULL AND converted_level IS NULL)
     OR (score_type = 2 AND converted_score IS NULL AND converted_level IN ('优', '良', '中', '及格', '不及格'))
     OR (score_type = 3 AND converted_score IS NULL AND converted_level IN ('合格', '不合格'))
  ),
  CONSTRAINT ck_esc_gp               CHECK (grade_point IN (4.00, 3.70, 3.40, 3.00, 2.40, 2.00, 1.00, 0.00)),
  CONSTRAINT ck_esc_is_final         CHECK (is_final IN (0, 1)),
  CONSTRAINT ck_esc_final_active     CHECK (is_final = 0 OR audit_status = 2),
  CONSTRAINT ck_esc_version_no       CHECK (version_no >= 1),
  CONSTRAINT ck_esc_status           CHECK (audit_status IN (0, 1, 2, 3)),
  CONSTRAINT ck_esc_proof_nonempty   CHECK (LENGTH(TRIM(proof_no)) > 0),
  CONSTRAINT ck_esc_college_done     CHECK (audit_status = 0 OR (college_auditor_id IS NOT NULL AND college_audit_time IS NOT NULL)),
  CONSTRAINT ck_esc_pending_clear    CHECK (audit_status <> 0 OR (college_auditor_id IS NULL AND college_audit_time IS NULL AND school_auditor_id IS NULL AND school_audit_time IS NULL)),
  CONSTRAINT ck_esc_school_pass      CHECK (audit_status <> 2 OR (school_auditor_id IS NOT NULL AND school_audit_time IS NOT NULL)),
  CONSTRAINT ck_esc_school_wait      CHECK (audit_status <> 1 OR (school_auditor_id IS NULL AND school_audit_time IS NULL)),
  CONSTRAINT ck_esc_reject_opinion   CHECK (
        audit_status <> 3
     OR (school_auditor_id IS NULL AND college_audit_opinion IS NOT NULL AND LENGTH(TRIM(college_audit_opinion)) > 0)
     OR (school_auditor_id IS NOT NULL AND school_audit_time IS NOT NULL AND school_audit_opinion IS NOT NULL AND LENGTH(TRIM(school_audit_opinion)) > 0)
  )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '校外交流成绩换算认定表（业务③ 3.5，D22）；不物理删除，审批通过后计入本校成绩与GPA';

-- 业务规则说明（应用层与事务保证）：
-- 1. 教务处审批通过（audit_status: 1→2）时，必须在同一事务内：
--    (a) 按统一锁序锁定学生所在排名组、score 与 exchange_score_convert；
--    (b) 联合 score（score_status=3）与 exchange_score_convert（audit_status=2）重标同生、同阶段、同课程的 is_final；
--    (c) 同事务调用 4.3 RecomputeAcademicStats 重算 stu_gpa、stu_term_gpa 与专业排名；
--    (d) 生成认定生效结果（按 convert_id + version_no + proof_no，含 grade_point、school_audit_time 与 proof_file_id）供第15组返校材料归档读取。
-- 2. 同一学生、同一归属阶段、同一外校课程证明不得重复生效认定，由应用层在提交与审批锁内校验；复核重算生效时 version_no 递增。
