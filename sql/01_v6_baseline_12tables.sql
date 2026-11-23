-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 本组 12 张表基线建表脚本（v1）
-- 文件：建表SQL_本组12表基线_朱鳞太_v1.sql
-- 依据：数据字典_03组共享表_朱鳞太_v6.xlsx，工作表“03组_选课及成绩管理”第 3–129 行
--       （与《03选课及成绩管理_系统分析报告.docx》表 2-4～表 2-15 一致）
-- 目标：MySQL 8.0.16 及以上（8.0.16 起 CHECK 约束才真正生效），InnoDB，utf8mb4
-- 状态：只做过静态检查，没有在任何 MySQL 实例上执行过
--
-- 使用说明
-- 1. 不依赖任何外部表，在一个空库里执行即可建完 12 张表；只含本组表之间的外键。
-- 2. 引用第 1、2、4 组的列（student_id、tc_id、*_teacher_id、auditor_id 等）
--    本脚本只建列和索引，不建外键；外键见
--    “建表SQL_组间集成外键_可选_朱鳞太_v1.sql”，那份要等外部表就位后再执行。
-- 3. 不写 DROP TABLE、不写 IF NOT EXISTS：在已有同名表的库里执行会直接报错，
--    不会覆盖已有数据，也不会悄悄跳过旧定义。
-- 4. 整数类型不写显示宽度（v6 “长度”列里 int 11、tinyint 1、bigint 20 只是显示
--    宽度，不代表取值范围，MySQL 8.0.17 起已弃用），取值范围用 CHECK 表达。
-- 5. v6 中“非空”写 NOT NULL，“可空”或未写写 NULL；默认值照抄 v6。
-- 6. CHECK 只用本行列、常量和确定性运算，不含子查询、跨表引用、存储函数、
--    NOW() 之类的非确定性函数。跨行、跨表规则由应用或事务保证，清单见
--    “数据库设计_关系模式与规范化_朱鳞太_v1.md”第 6 节。
-- 7. 外键动作全部用默认的 RESTRICT（不级联删除）：v6“数据权限”表规定
--    sc、score、score_audit、score_correction 等都不删除。
-- 8. 字符集只指定 utf8mb4；排序规则用服务器默认值。组间集成外键要求字符列
--    两边字符集、排序规则一致，DBA 合库时统一。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 业务 ① 选课准备与发布
-- ---------------------------------------------------------------------

-- 1. 选课轮次表（v6 第 3–16 行）
CREATE TABLE select_round (
  round_id       INT           NOT NULL AUTO_INCREMENT COMMENT '轮次编号，系统自动生成',
  term           CHAR(11)      NOT NULL COMMENT '学年学期，如 2026-2027-1，写法与 teaching_class.term 一致',
  round_name     VARCHAR(30)   NOT NULL COMMENT '轮次名称，如 第一轮正选',
  round_type     TINYINT       NOT NULL COMMENT '轮次类型：1预选 2正选 3补退选',
  target_level   TINYINT       NOT NULL DEFAULT 1 COMMENT '适用学生：1本科生 2研究生 3全部',
  start_time     DATETIME      NOT NULL COMMENT '开始时间',
  end_time       DATETIME      NOT NULL COMMENT '结束时间，晚于 start_time',
  max_credit     DECIMAL(4,1)  NOT NULL COMMENT '每名学生本轮最多可选的学分，如 30.0',
  select_mode    TINYINT       NOT NULL COMMENT '选课方式：1先到先得 2超额后按规则筛选',
  round_status   TINYINT       NOT NULL DEFAULT 0 COMMENT '轮次状态：0草稿 1待审批 2已发布 3进行中 4已结束',
  notice_content TEXT          NULL COMMENT '选课通知内容，发布时必填',
  publish_time   DATETIME      NULL COMMENT '发布时间，审批通过、发布选课通知时写入',
  PRIMARY KEY (round_id),
  CONSTRAINT ck_select_round_type    CHECK (round_type IN (1, 2, 3)),
  CONSTRAINT ck_select_round_level   CHECK (target_level IN (1, 2, 3)),
  CONSTRAINT ck_select_round_time    CHECK (end_time > start_time),
  CONSTRAINT ck_select_round_credit  CHECK (max_credit >= 0),
  CONSTRAINT ck_select_round_mode    CHECK (select_mode IN (1, 2)),
  CONSTRAINT ck_select_round_status  CHECK (round_status IN (0, 1, 2, 3, 4)),
  -- 状态 2/3/4 都在发布之后：通知内容发布时必填，发布时间发布时写入
  CONSTRAINT ck_select_round_notice  CHECK (round_status < 2 OR notice_content IS NOT NULL),
  CONSTRAINT ck_select_round_pubtime CHECK (round_status < 2 OR publish_time IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课轮次表（业务①，D3）';

-- 2. 轮次开放教学班表（v6 第 18–21 行）：选课轮次与教学班的 M:N 联系
CREATE TABLE round_class (
  round_id INT         NOT NULL COMMENT '轮次编号，关联 select_round.round_id',
  tc_id    VARCHAR(20) NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组，集成外键见可选脚本）',
  PRIMARY KEY (round_id, tc_id),
  KEY idx_round_class_tc (tc_id),
  CONSTRAINT fk_round_class_round FOREIGN KEY (round_id) REFERENCES select_round (round_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '轮次开放教学班表（业务①，D3）';

-- 3. 选课限制条件表（v6 第 23–28 行）
CREATE TABLE select_limit (
  limit_id    INT         NOT NULL AUTO_INCREMENT COMMENT '限制编号，系统自动生成',
  tc_id       VARCHAR(20) NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组）',
  limit_type  TINYINT     NOT NULL COMMENT '限制类型：1限年级 2限专业 3限学院 4限培养层次',
  limit_value VARCHAR(20) NOT NULL COMMENT '限制值：年级如 2024；专业填 major_id；学院填 college_id；培养层次填 degree_level 取值',
  PRIMARY KEY (limit_id),
  -- 同一教学班、同一类型、同一取值重复登记没有意义：作为自然候选键
  UNIQUE KEY uk_select_limit_cond (tc_id, limit_type, limit_value),
  CONSTRAINT ck_select_limit_type CHECK (limit_type IN (1, 2, 3, 4))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课限制条件表（业务①，D2）';

-- ---------------------------------------------------------------------
-- 业务 ② 学生选课
-- ---------------------------------------------------------------------

-- 4. 选课记录表（v6 第 30–42 行）
CREATE TABLE sc (
  sc_id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '选课记录号，系统自动生成',
  student_id    CHAR(20)    NOT NULL COMMENT '学号，关联 student.student_id（第1组）',
  tc_id         VARCHAR(20) NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组）',
  round_id      INT         NOT NULL COMMENT '选课轮次，关联 select_round.round_id',
  study_type    TINYINT     NOT NULL DEFAULT 1 COMMENT '修读类型：1初修 2重修',
  stu_level     VARCHAR(20) NOT NULL COMMENT '选课时培养层次，选课时从 student.degree_level 复制',
  stat_stage    TINYINT     NOT NULL DEFAULT 1 COMMENT '成绩归属阶段：1本科阶段 2研究生阶段',
  priority      TINYINT     NULL COMMENT '志愿优先级：1第一志愿 2第二志愿 3第三志愿，仅超额筛选轮次填写',
  select_status TINYINT     NOT NULL COMMENT '选课状态：0待筛选 1已选中 2未选中 3已退选',
  select_time   DATETIME    NOT NULL COMMENT '选课时间',
  drop_time     DATETIME    NULL COMMENT '退选时间，未退选时为空',
  PRIMARY KEY (sc_id),
  UNIQUE KEY uk_sc_student_tc (student_id, tc_id),
  KEY idx_sc_tc (tc_id),
  CONSTRAINT fk_sc_round FOREIGN KEY (round_id) REFERENCES select_round (round_id),
  CONSTRAINT ck_sc_study_type CHECK (study_type IN (1, 2)),
  CONSTRAINT ck_sc_stat_stage CHECK (stat_stage IN (1, 2)),
  CONSTRAINT ck_sc_priority   CHECK (priority IS NULL OR priority IN (1, 2, 3)),
  CONSTRAINT ck_sc_status     CHECK (select_status IN (0, 1, 2, 3)),
  -- v6：退选时间“未退选时为空”
  CONSTRAINT ck_sc_drop_time  CHECK (select_status = 3 OR drop_time IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课记录表（业务②，D11）；不物理删除，退选只改状态';

-- 5. 选课日志表（v6 第 44–53 行）
CREATE TABLE select_log (
  log_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志编号，系统自动生成；只追加，不修改、不删除',
  student_id  CHAR(20)     NOT NULL COMMENT '学号，关联 student.student_id（第1组）',
  tc_id       VARCHAR(20)  NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组）',
  round_id    INT          NOT NULL COMMENT '选课轮次，关联 select_round.round_id',
  action_type TINYINT      NOT NULL COMMENT '操作类型：1选课 2退课 3筛选未选中 4教务处调整',
  operator_id VARCHAR(20)  NOT NULL COMMENT '操作人：学生本人填学号，教务处调整填工号（多态，不建外键）',
  action_time DATETIME     NOT NULL COMMENT '操作时间',
  remark      VARCHAR(200) NULL COMMENT '备注，教务处调整时必填调整原因',
  PRIMARY KEY (log_id),
  KEY idx_select_log_student (student_id),
  KEY idx_select_log_tc (tc_id),
  CONSTRAINT fk_select_log_round FOREIGN KEY (round_id) REFERENCES select_round (round_id),
  CONSTRAINT ck_select_log_action CHECK (action_type IN (1, 2, 3, 4)),
  CONSTRAINT ck_select_log_remark CHECK (action_type <> 4 OR (remark IS NOT NULL AND remark <> ''))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课日志表（业务②，D12）；只追加';

-- ---------------------------------------------------------------------
-- 业务 ③ 成绩录入与审核
-- ---------------------------------------------------------------------

-- 6. 成绩表（v6 第 55–72 行）
CREATE TABLE score (
  score_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '成绩记录号，系统自动生成',
  sc_id            BIGINT       NOT NULL COMMENT '选课记录号，关联 sc.sc_id',
  exam_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '考试类别：1正常考试 2补考（重修看 sc.study_type）',
  exam_flag        TINYINT      NOT NULL DEFAULT 0 COMMENT '考试情况：0正常 1缺考 2缓考 3违纪',
  score_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '记分方式：1百分制 2五级制 3两级制',
  usual_ratio      TINYINT      NOT NULL COMMENT '平时成绩占比 0~100，如 30 表示 30%',
  usual_score      DECIMAL(4,1) NULL COMMENT '平时成绩 0~100',
  exam_score       DECIMAL(4,1) NULL COMMENT '期末成绩 0~100，空表示未考',
  total_score      DECIMAL(4,1) NULL COMMENT '总评成绩 0~100，百分制课程按占比自动计算',
  score_level      VARCHAR(6)   NULL COMMENT '等级成绩：五级制 优/良/中/及格/不及格；两级制 合格/不合格',
  grade_point      DECIMAL(3,2) NULL COMMENT '课程绩点，按校发〔2021〕46号附表3换算',
  is_final         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否最终成绩：同一学生同一门课已发布成绩中最高的一条为 1',
  score_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '成绩状态：0录入中 1已提交 2学院已审核 3已发布 4退回修改',
  input_teacher_id CHAR(10)     NULL COMMENT '录入教师工号，关联 t_employee.emp_id（第4组）；v6 未写非空/可空，暂按可空',
  submit_time      DATETIME     NULL COMMENT '提交时间',
  publish_time     DATETIME     NULL COMMENT '发布时间',
  PRIMARY KEY (score_id),
  UNIQUE KEY uk_score_sc_exam (sc_id, exam_type),
  KEY idx_score_teacher (input_teacher_id),
  CONSTRAINT fk_score_sc FOREIGN KEY (sc_id) REFERENCES sc (sc_id),
  CONSTRAINT ck_score_exam_type  CHECK (exam_type IN (1, 2)),
  CONSTRAINT ck_score_exam_flag  CHECK (exam_flag IN (0, 1, 2, 3)),
  CONSTRAINT ck_score_score_type CHECK (score_type IN (1, 2, 3)),
  CONSTRAINT ck_score_ratio      CHECK (usual_ratio BETWEEN 0 AND 100),
  CONSTRAINT ck_score_usual      CHECK (usual_score IS NULL OR usual_score BETWEEN 0 AND 100),
  CONSTRAINT ck_score_exam       CHECK (exam_score IS NULL OR exam_score BETWEEN 0 AND 100),
  CONSTRAINT ck_score_total      CHECK (total_score IS NULL OR total_score BETWEEN 0 AND 100),
  -- 百分制不填等级；五级制、两级制的等级只能取各自的值（v6 第 61、66 行）
  CONSTRAINT ck_score_level      CHECK (
        (score_type = 1 AND score_level IS NULL)
     OR (score_type = 2 AND (score_level IS NULL OR score_level IN ('优', '良', '中', '及格', '不及格')))
     OR (score_type = 3 AND (score_level IS NULL OR score_level IN ('合格', '不合格')))
  ),
  -- 附表 3 只有这 8 个绩点值（v6 第 67 行）
  CONSTRAINT ck_score_gp         CHECK (grade_point IS NULL OR grade_point IN (4.00, 3.70, 3.40, 3.00, 2.40, 2.00, 1.00, 0.00)),
  CONSTRAINT ck_score_is_final   CHECK (is_final IN (0, 1)),
  CONSTRAINT ck_score_status     CHECK (score_status IN (0, 1, 2, 3, 4)),
  -- is_final 只在“已发布成绩”中标记（v6 第 68 行）
  CONSTRAINT ck_score_final_pub  CHECK (is_final = 0 OR score_status = 3),
  -- 提交时写 submit_time，状态 1/2/3/4 都发生在提交之后（分析报告表 2-31）
  CONSTRAINT ck_score_submit     CHECK (score_status = 0 OR submit_time IS NOT NULL),
  -- 发布时写 publish_time（分析报告表 2-29 P24）
  CONSTRAINT ck_score_publish    CHECK (score_status <> 3 OR publish_time IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩表（业务③，D20）；不删除，发布后只能走成绩更正';

-- 7. 成绩审核记录表（v6 第 74–81 行）
CREATE TABLE score_audit (
  audit_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录号，系统自动生成',
  tc_id         VARCHAR(20)  NOT NULL COMMENT '教学班号，按教学班整体审核，关联 teaching_class.tc_id（第2组）',
  auditor_id    CHAR(10)     NOT NULL COMMENT '审核人工号，关联 t_employee.emp_id（第4组），开课学院工作人员',
  audit_result  TINYINT      NOT NULL COMMENT '审核结果：1通过 2退回',
  audit_opinion VARCHAR(200) NULL COMMENT '审核意见，退回时必填',
  audit_time    DATETIME     NOT NULL COMMENT '审核时间',
  PRIMARY KEY (audit_id),
  KEY idx_score_audit_tc_time (tc_id, audit_time),
  KEY idx_score_audit_auditor (auditor_id),
  CONSTRAINT ck_score_audit_result  CHECK (audit_result IN (1, 2)),
  CONSTRAINT ck_score_audit_opinion CHECK (audit_result <> 2 OR (audit_opinion IS NOT NULL AND audit_opinion <> ''))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩审核记录表（业务③，D21）；只追加';

-- ---------------------------------------------------------------------
-- 业务 ⑤ 成绩更正申请（依赖 score，先于业务 ④ 的统计表创建无影响）
-- ---------------------------------------------------------------------

-- 8. 成绩更正申请表（v6 第 83–96 行）
CREATE TABLE score_correction (
  apply_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请编号，系统自动生成',
  score_id           BIGINT       NOT NULL COMMENT '成绩记录号，关联 score.score_id',
  apply_teacher_id   CHAR(10)     NOT NULL COMMENT '申请教师工号，关联 t_employee.emp_id（第4组）',
  old_score          DECIMAL(4,1) NOT NULL COMMENT '原总评成绩 0~100',
  new_score          DECIMAL(4,1) NOT NULL COMMENT '更正后成绩 0~100',
  apply_reason       VARCHAR(200) NOT NULL COMMENT '更正原因',
  apply_time         DATETIME     NOT NULL COMMENT '申请时间',
  apply_status       TINYINT      NOT NULL DEFAULT 0 COMMENT '申请状态：0待学院审核 1待学校审批 2已通过 3已驳回',
  college_auditor_id CHAR(10)     NULL COMMENT '学院审核人工号，关联 t_employee.emp_id（第4组）',
  auditor_id         CHAR(10)     NULL COMMENT '学校审批人工号（教务处），关联 t_employee.emp_id（第4组）',
  audit_opinion      VARCHAR(200) NULL COMMENT '审核意见，驳回时必填',
  audit_time         DATETIME     NULL COMMENT '审批时间，学校审批完成的时间',
  PRIMARY KEY (apply_id),
  KEY idx_score_correction_score (score_id),
  KEY idx_score_correction_teacher (apply_teacher_id),
  KEY idx_score_correction_college (college_auditor_id),
  KEY idx_score_correction_auditor (auditor_id),
  CONSTRAINT fk_score_correction_score FOREIGN KEY (score_id) REFERENCES score (score_id),
  CONSTRAINT ck_score_correction_old    CHECK (old_score BETWEEN 0 AND 100),
  CONSTRAINT ck_score_correction_new    CHECK (new_score BETWEEN 0 AND 100),
  CONSTRAINT ck_score_correction_status CHECK (apply_status IN (0, 1, 2, 3)),
  -- 驳回时必填意见（v6 第 95 行；分析报告表 2-37 P41、P42）
  CONSTRAINT ck_score_correction_opinion CHECK (apply_status <> 3 OR (audit_opinion IS NOT NULL AND audit_opinion <> '')),
  -- 状态 1/2/3 都要先经过学院审核（学院驳回也记 3）
  CONSTRAINT ck_score_correction_college CHECK (apply_status = 0 OR college_auditor_id IS NOT NULL),
  -- 已通过 = 教务处审批通过，审批人和审批时间必须有
  CONSTRAINT ck_score_correction_school  CHECK (apply_status <> 2 OR (auditor_id IS NOT NULL AND audit_time IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩更正申请表（业务⑤，D40）；不删除';

-- ---------------------------------------------------------------------
-- 业务 ④ 成绩查询与统计（stu_gpa、stu_term_gpa、tc_eval_result 是派生快照）
-- ---------------------------------------------------------------------

-- 9. 学生学业统计表（v6 第 98–107 行）：每名学生一行的派生快照
CREATE TABLE stu_gpa (
  student_id       CHAR(20)     NOT NULL COMMENT '学号，关联 student.student_id（第1组）',
  earned_credit    DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '已获学分：本阶段 is_final=1 且及格的课程学分之和',
  failed_credit    DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '不及格学分：本阶段 is_final=1 且不及格的课程学分之和',
  gpa              DECIMAL(3,2) NULL COMMENT '平均学分绩点；Σ学分为 0 时留空',
  avg_score        DECIMAL(5,2) NULL COMMENT '加权平均分（等级制按附表1、2折算）',
  major_rank       INT          NULL COMMENT '通用专业排名：同专业同年级按 avg_score 从高到低',
  major_rank_total INT          NULL COMMENT '参加排名的同专业、同年级学生人数',
  update_time      DATETIME     NOT NULL COMMENT '更新时间，成绩发布或更正后重新计算',
  PRIMARY KEY (student_id),
  CONSTRAINT ck_stu_gpa_earned CHECK (earned_credit >= 0),
  CONSTRAINT ck_stu_gpa_failed CHECK (failed_credit >= 0),
  -- 绩点取值 0~4.0，按学分加权的平均值也在 0~4.0 之间
  CONSTRAINT ck_stu_gpa_gpa    CHECK (gpa IS NULL OR gpa BETWEEN 0 AND 4),
  CONSTRAINT ck_stu_gpa_avg    CHECK (avg_score IS NULL OR avg_score BETWEEN 0 AND 100),
  CONSTRAINT ck_stu_gpa_rank   CHECK (major_rank IS NULL OR major_rank >= 1),
  CONSTRAINT ck_stu_gpa_total  CHECK (major_rank_total IS NULL OR major_rank_total >= 1),
  CONSTRAINT ck_stu_gpa_rank_le CHECK (major_rank IS NULL OR major_rank_total IS NULL OR major_rank <= major_rank_total)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生学业统计表（业务④，D32）；派生快照，供第1组读取';

-- 10. 学生学期学业统计表（v6 第 109–115 行）：每名学生每学期一行的派生快照
CREATE TABLE stu_term_gpa (
  student_id         CHAR(20)     NOT NULL COMMENT '学号，关联 student.student_id（第1组）',
  term               CHAR(11)     NOT NULL COMMENT '学年学期，如 2026-2027-1',
  term_credit        DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '本学期已获学分（补考通过算及格），只计本阶段记录',
  term_failed_credit DECIMAL(4,1) NOT NULL DEFAULT 0 COMMENT '本学期最终仍不及格的课程学分之和',
  update_time        DATETIME     NOT NULL COMMENT '更新时间，本学期成绩发布或更正后重新计算',
  PRIMARY KEY (student_id, term),
  CONSTRAINT ck_stu_term_gpa_credit CHECK (term_credit >= 0),
  CONSTRAINT ck_stu_term_gpa_failed CHECK (term_failed_credit >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生学期学业统计表（业务④，D32）；派生快照，供第1组读取';

-- 11. 学生评教记录表（v6 第 117–122 行）：每条选课记录最多评一次
CREATE TABLE teach_eval (
  sc_id        BIGINT       NOT NULL COMMENT '选课记录号，关联 sc.sc_id',
  eval_score   TINYINT      NOT NULL COMMENT '评教分数 0~100',
  eval_comment VARCHAR(200) NULL COMMENT '意见建议，明细只有教务处能看',
  eval_time    DATETIME     NOT NULL COMMENT '评教时间',
  PRIMARY KEY (sc_id),
  CONSTRAINT fk_teach_eval_sc FOREIGN KEY (sc_id) REFERENCES sc (sc_id),
  CONSTRAINT ck_teach_eval_score CHECK (eval_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生评教记录表（业务④，D30）；提交后不可改';

-- 12. 教学班评教结果表（v6 第 124–129 行）：每个教学班一行的派生快照
CREATE TABLE tc_eval_result (
  tc_id          VARCHAR(20)  NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组）',
  eval_num       INT          NOT NULL COMMENT '参评人数',
  avg_eval_score DECIMAL(5,2) NOT NULL COMMENT '评教平均分 0~100',
  calc_time      DATETIME     NOT NULL COMMENT '统计时间，评教结束后统计',
  PRIMARY KEY (tc_id),
  CONSTRAINT ck_tc_eval_result_num CHECK (eval_num >= 0),
  CONSTRAINT ck_tc_eval_result_avg CHECK (avg_eval_score BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教学班评教结果表（业务④，D35）；派生快照，供第4组读取';

-- 结束：12 张表。下一步（可选）：外部引用桩表 → 组间集成外键。
