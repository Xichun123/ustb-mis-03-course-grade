-- 03组选课及成绩管理：v6基线升级到本轮v7设计稿（朱鳞太，v1）
-- 依据：数据字典_03组共享表_朱鳞太_v7_设计稿.xlsx；本轮设计建议，未替代已共享v6。
-- 目标：MySQL 8.0.16+；先执行“建表SQL_本组12表基线_朱鳞太_v1.sql”。
-- 用途：本组新建开发库、业务表尚无数据时执行一次；不是已有真实业务数据的升级方案。
-- 如库内已有审批数据，必须先核实旧记录如何分离两级意见与时间；不得编造历史时间补值。
-- 本文件只提供脚本，未连接或修改现有数据库；DDL不能依赖事务整体回滚。
-- 整体仍为12张表，增加9字段（select_round +3、score_correction +4、score_audit +2）；没有外部表时不建立新的外部工号外键。

ALTER TABLE select_round
  DROP CHECK ck_select_round_credit,
  DROP CHECK ck_select_round_status,
  DROP CHECK ck_select_round_notice,
  DROP CHECK ck_select_round_pubtime,
  MODIFY round_status TINYINT NOT NULL DEFAULT 0
    COMMENT '0草稿 1待审批 2已发布 3进行中 4已结束 5已审批待发布（v7设计稿）',
  ADD approver_id CHAR(10) NULL COMMENT '最近一次方案审批人工号，引用t_employee.emp_id' AFTER publish_time,
  ADD approve_time DATETIME NULL COMMENT '最近一次方案审批时间，重新提交时清空' AFTER approver_id,
  ADD approve_opinion VARCHAR(200) NULL COMMENT '最近一次方案审批意见，退回时必填' AFTER approve_time,
  ADD KEY idx_select_round_approver (approver_id),
  ADD CONSTRAINT ck_select_round_credit CHECK (max_credit > 0),
  ADD CONSTRAINT ck_select_round_status CHECK (round_status IN (0,1,2,3,4,5)),
  ADD CONSTRAINT ck_select_round_notice CHECK (
    round_status NOT IN (2,3,4) OR (notice_content IS NOT NULL AND LENGTH(TRIM(notice_content)) > 0)
  ),
  ADD CONSTRAINT ck_select_round_pubtime CHECK (
    round_status NOT IN (2,3,4) OR publish_time IS NOT NULL
  ),
  ADD CONSTRAINT ck_select_round_approved CHECK (
    round_status NOT IN (5,2,3,4) OR (approver_id IS NOT NULL AND approve_time IS NOT NULL)
  ),
  ADD CONSTRAINT ck_select_round_approval_pair CHECK (
       (approver_id IS NULL AND approve_time IS NULL AND approve_opinion IS NULL)
    OR (approver_id IS NOT NULL AND CHAR_LENGTH(TRIM(approver_id))>0 AND approve_time IS NOT NULL)
  ),
  ADD CONSTRAINT ck_select_round_pending_clear CHECK (
    round_status<>1 OR (approver_id IS NULL AND approve_time IS NULL AND approve_opinion IS NULL)
  );

-- 不能再写 round_status<2 代表未发布：状态5同样未发布。
-- 审批通过1→5，发布5→2；发布时若已到开始时间，立即在同事务推进为3。
-- 上述CHECK不判断“退回”动作，退回意见必填和草稿重提清空旧审批信息由应用校验。

ALTER TABLE score_correction
  DROP CHECK ck_score_correction_opinion,
  MODIFY old_score DECIMAL(4,1) NULL COMMENT '百分制旧值；等级制为空',
  MODIFY new_score DECIMAL(4,1) NULL COMMENT '百分制新值；等级制为空',
  ADD old_level VARCHAR(6) NULL COMMENT '五级或两级制原等级快照' AFTER new_score,
  ADD new_level VARCHAR(6) NULL COMMENT '五级或两级制更正后等级' AFTER old_level,
  ADD college_audit_opinion VARCHAR(200) NULL COMMENT '学院审核意见，学院驳回必填' AFTER college_auditor_id,
  ADD college_audit_time DATETIME NULL COMMENT '学院审核完成时间' AFTER college_audit_opinion,
  MODIFY audit_opinion VARCHAR(200) NULL COMMENT '教务处审批意见，学校驳回必填',
  ADD CONSTRAINT ck_score_correction_value_pair CHECK (
       (old_score IS NOT NULL AND new_score IS NOT NULL AND old_level IS NULL AND new_level IS NULL)
    OR (old_score IS NULL AND new_score IS NULL AND old_level IS NOT NULL AND new_level IS NOT NULL)
  ),
  ADD CONSTRAINT ck_score_correction_old_level CHECK (
    old_level IS NULL OR old_level IN ('优','良','中','及格','不及格','合格','不合格')
  ),
  ADD CONSTRAINT ck_score_correction_new_level CHECK (
    new_level IS NULL OR new_level IN ('优','良','中','及格','不及格','合格','不合格')
  ),
  ADD CONSTRAINT ck_score_correction_college_time CHECK (
    apply_status=0 OR college_audit_time IS NOT NULL
  ),
  ADD CONSTRAINT ck_score_correction_opinion CHECK (
    apply_status<>3 OR
    (auditor_id IS NULL AND college_audit_opinion IS NOT NULL AND LENGTH(TRIM(college_audit_opinion))>0)
    OR
    (auditor_id IS NOT NULL AND audit_time IS NOT NULL AND audit_opinion IS NOT NULL AND LENGTH(TRIM(audit_opinion))>0)
  );

-- 行内约束只验证分数/等级两组互斥和基本枚举。
-- 与score.score_type的严格对应、同一成绩只能有一个待审申请、学院/学校角色范围、
-- 原值未变化、成绩及统计同事务生效，均必须由后端在锁定关联记录后再校验。
-- 外部t_employee表就绪且字符集/排序规则核定后，可由DBA另加approver_id外键；本稿不自动执行。

-- 2026-11-22 业务③接入成绩提交批次与学院核对随机码（verify_code）
-- 粒度为“一个教学班 + 一次提交”一条记录，不放在 score 表以避免按学生人数重复存储。
ALTER TABLE score_audit
  DROP CHECK ck_score_audit_result,
  DROP CHECK ck_score_audit_opinion,
  ADD submit_time DATETIME NOT NULL COMMENT '本次提交时间，与 score.submit_time 对应，标识本次提交批次' AFTER tc_id,
  ADD verify_code CHAR(6) NOT NULL COMMENT '6位核对随机码（大写字母+数字，排除0/O/1/I），提交时由系统自动生成；退回重提生成新码、旧码作废' AFTER submit_time,
  MODIFY auditor_id CHAR(10) NULL COMMENT '审核人工号，关联 t_employee.emp_id（开课学院工作人员）；待核对时为空',
  MODIFY audit_result TINYINT NOT NULL DEFAULT 0 COMMENT '审核结果：0待核对 1通过 2退回（v7接入随机码）',
  MODIFY audit_time DATETIME NULL COMMENT '审核时间；待核对时为空',
  ADD UNIQUE KEY uk_score_audit_tc_submit (tc_id, submit_time),
  ADD CONSTRAINT ck_score_audit_result CHECK (audit_result IN (0, 1, 2)),
  ADD CONSTRAINT ck_score_audit_code CHECK (REGEXP_LIKE(verify_code, '^[2-9A-HJ-NP-Z]{6}$', 'c')),
  ADD CONSTRAINT ck_score_audit_pending CHECK (
    audit_result <> 0 OR (auditor_id IS NULL AND audit_time IS NULL AND audit_opinion IS NULL)
  ),
  ADD CONSTRAINT ck_score_audit_done CHECK (
    audit_result = 0 OR (auditor_id IS NOT NULL AND CHAR_LENGTH(TRIM(auditor_id)) > 0 AND audit_time IS NOT NULL)
  ),
  ADD CONSTRAINT ck_score_audit_opinion CHECK (
    audit_result <> 2 OR (audit_opinion IS NOT NULL AND LENGTH(TRIM(audit_opinion)) > 0)
  );

-- 业务规则说明（应用层与事务保证）：
-- 1. 教师在 3.2 确认提交时同事务锁定该班 score（score_status IN (0,4) → 1，写统一 submit_time）
--    并插入 1 条 score_audit（audit_result=0，生成6位 verify_code）。
-- 2. 打印纸质原始成绩登记表时取本班最新待核对批次的 verify_code 印于页眉/签字栏旁。
-- 3. 学院在 3.3 核对时须输入纸质单随机码，后端核验与本班最新 audit_result=0 记录的 verify_code 一致后才允许置为 1（通过）；
--    若退回（置为 2，必填意见），教师修改重提时会插入新 submit_time 批次并生成新随机码，旧码自动作废。
-- 4. 教务处在 3.4 发布时核验本班当前 MAX(score.submit_time) 对应的 score_audit.audit_result = 1。

-- 2026-11-22 统一业务④零参评均分可空性与 is_final 归属阶段口径
ALTER TABLE tc_eval_result
  DROP CHECK ck_tc_eval_result_avg,
  MODIFY avg_eval_score DECIMAL(5,2) NULL COMMENT '评教平均分 0~100；参评人数为0时为空（v7统一口径）',
  ADD CONSTRAINT ck_tc_eval_result_avg CHECK (
    (eval_num = 0 AND avg_eval_score IS NULL)
    OR (eval_num > 0 AND avg_eval_score BETWEEN 0 AND 100)
  );

ALTER TABLE score
  MODIFY is_final TINYINT NOT NULL DEFAULT 0 COMMENT '是否本阶段最终成绩：同一学生、同一归属阶段(sc.stat_stage)、同一课程已发布成绩中最高的一条为1';
