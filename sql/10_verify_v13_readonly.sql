-- =====================================================================
-- 03 组 · 选课及成绩管理 —— DBA 统一数据字典 v1.3 结构只读核对脚本
-- 文件：10_verify_v13_readonly.sql
-- 说明：仅查询 information_schema，不修改任何结构或数据。
-- 期望结果：
--   1. 表数 = 14，总字段数 = 134
--   2. 差异字段检查返回 0 行（表名、列名、类型、长度/精度、可空、自增、默认值全部一致）
--   3. CHECK 约束总数 = 82，ENFORCED='YES' = 82
--   4. 本组内部外键数 = 6
-- =====================================================================

-- 1. 核对 14 张本组表及总字段数（期望：14 表 / 134 列）
SELECT COUNT(DISTINCT TABLE_NAME) AS tables_expected_14,
       COUNT(*) AS cols_expected_134
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'sel_select_round','sel_round_class','sel_select_limit','sel_lesson_class',
    'sel_sc','sel_select_log','sel_score','sel_score_audit','sel_score_correction',
    'sel_exchange_score_convert','sel_stu_gpa','sel_stu_term_gpa',
    'sel_teach_eval','sel_tc_eval_result'
  );

-- 2. 逐列精确核对 134 个字段定义（期望：返回 0 行差异）
WITH expected (table_name, column_name, data_type, char_len, num_precision, num_scale, is_nullable, is_auto, expected_default) AS (
  -- sel_select_round (17)
  SELECT 'sel_select_round','round_id','int',NULL,10,0,'NO',1,NULL UNION ALL
  SELECT 'sel_select_round','term','char',11,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','round_name','varchar',30,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','round_type','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','target_level','int',NULL,10,0,'NO',0,'1' UNION ALL
  SELECT 'sel_select_round','start_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','end_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','max_credit','decimal',NULL,5,1,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','select_mode','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_select_round','allow_retake','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_select_round','retake_ignore_conflict','int',NULL,10,0,'NO',0,'0' UNION ALL
  SELECT 'sel_select_round','round_status','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_select_round','notice_content','text',65535,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_select_round','published_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_select_round','approver_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_select_round','approved_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_select_round','approve_opinion','varchar',200,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_round_class (2)
  SELECT 'sel_round_class','round_id','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_round_class','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_select_limit (4)
  SELECT 'sel_select_limit','limit_id','int',NULL,10,0,'NO',1,NULL UNION ALL
  SELECT 'sel_select_limit','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_limit','limit_type','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_select_limit','limit_value','varchar',20,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_lesson_class (2)
  SELECT 'sel_lesson_class','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_lesson_class','lesson_no','varchar',20,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_sc (12)
  SELECT 'sel_sc','sc_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_sc','student_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','round_id','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','study_type','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_sc','is_exempt_attend','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_sc','stu_level','varchar',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','stat_stage','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_sc','priority','int',NULL,10,0,'YES',0,NULL UNION ALL
  SELECT 'sel_sc','select_status','tinyint',NULL,3,0,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','select_time','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_sc','drop_time','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_select_log (8)
  SELECT 'sel_select_log','log_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_select_log','student_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','round_id','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','action_type','tinyint',NULL,3,0,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','operator_id','varchar',40,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','operated_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_select_log','remark','varchar',500,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_score (16)
  SELECT 'sel_score','score_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_score','sc_id','bigint',NULL,19,0,'NO',0,NULL UNION ALL
  SELECT 'sel_score','exam_type','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_score','exam_flag','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_score','score_type','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_score','usual_ratio','decimal',NULL,5,2,'NO',0,NULL UNION ALL
  SELECT 'sel_score','usual_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score','exam_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score','total_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score','score_level','varchar',6,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score','grade_point','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score','is_final','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_score','score_status','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_score','input_teacher_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score','submitted_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score','published_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_score_audit (8)
  SELECT 'sel_score_audit','audit_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_score_audit','lesson_no','varchar',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_audit','submitted_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_audit','verify_code','char',6,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_audit','auditor_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_audit','audit_result','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_score_audit','audit_opinion','varchar',500,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_audit','reviewed_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_score_correction (16)
  SELECT 'sel_score_correction','apply_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_score_correction','score_id','bigint',NULL,19,0,'NO',0,NULL UNION ALL
  SELECT 'sel_score_correction','apply_teacher_id','char',10,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_correction','old_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','new_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','old_level','varchar',20,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','new_level','varchar',20,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','apply_reason','varchar',500,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_correction','submitted_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_score_correction','apply_status','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_score_correction','college_auditor_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','college_audit_opinion','varchar',200,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','college_audit_time','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','auditor_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','audit_opinion','varchar',500,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_score_correction','reviewed_at','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_exchange_score_convert (28)
  SELECT 'sel_exchange_score_convert','convert_id','bigint',NULL,19,0,'NO',1,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','student_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','exchange_apply_id','varchar',50,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','term','char',11,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','stat_stage','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_exchange_score_convert','host_school','varchar',200,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','ext_course_code','varchar',30,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','ext_course_name','varchar',100,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','ext_credit','decimal',NULL,5,1,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','ext_score_raw','varchar',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','ext_scale_type','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','course_id','char',10,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','score_type','tinyint',NULL,3,0,'NO',0,'1' UNION ALL
  SELECT 'sel_exchange_score_convert','converted_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','converted_level','varchar',20,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','grade_point','decimal',NULL,5,2,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','is_final','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_exchange_score_convert','proof_no','varchar',50,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','proof_file_id','varchar',64,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','version_no','int',NULL,10,0,'NO',0,'1' UNION ALL
  SELECT 'sel_exchange_score_convert','audit_status','tinyint',NULL,3,0,'NO',0,'0' UNION ALL
  SELECT 'sel_exchange_score_convert','submitted_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','college_auditor_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','college_audit_opinion','varchar',200,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','college_audit_time','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','school_auditor_id','char',10,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','school_audit_opinion','varchar',200,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_exchange_score_convert','school_audit_time','datetime',NULL,NULL,NULL,'YES',0,NULL UNION ALL
  -- sel_stu_gpa (8)
  SELECT 'sel_stu_gpa','student_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_stu_gpa','earned_credit','decimal',NULL,5,1,'NO',0,'0.0' UNION ALL
  SELECT 'sel_stu_gpa','failed_credit','decimal',NULL,5,1,'NO',0,'0.0' UNION ALL
  SELECT 'sel_stu_gpa','gpa','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_stu_gpa','avg_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_stu_gpa','major_rank','int',NULL,10,0,'YES',0,NULL UNION ALL
  SELECT 'sel_stu_gpa','major_rank_total','int',NULL,10,0,'YES',0,NULL UNION ALL
  SELECT 'sel_stu_gpa','updated_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_stu_term_gpa (5)
  SELECT 'sel_stu_term_gpa','student_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_stu_term_gpa','term','char',11,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_stu_term_gpa','term_credit','decimal',NULL,5,1,'NO',0,'0.0' UNION ALL
  SELECT 'sel_stu_term_gpa','term_failed_credit','decimal',NULL,5,1,'NO',0,'0.0' UNION ALL
  SELECT 'sel_stu_term_gpa','updated_at','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_teach_eval (4)
  SELECT 'sel_teach_eval','sc_id','bigint',NULL,19,0,'NO',0,NULL UNION ALL
  SELECT 'sel_teach_eval','eval_score','decimal',NULL,5,2,'NO',0,NULL UNION ALL
  SELECT 'sel_teach_eval','eval_comment','varchar',200,NULL,NULL,'YES',0,NULL UNION ALL
  SELECT 'sel_teach_eval','eval_time','datetime',NULL,NULL,NULL,'NO',0,NULL UNION ALL
  -- sel_tc_eval_result (4)
  SELECT 'sel_tc_eval_result','tc_id','char',20,NULL,NULL,'NO',0,NULL UNION ALL
  SELECT 'sel_tc_eval_result','eval_num','int',NULL,10,0,'NO',0,NULL UNION ALL
  SELECT 'sel_tc_eval_result','avg_eval_score','decimal',NULL,5,2,'YES',0,NULL UNION ALL
  SELECT 'sel_tc_eval_result','calc_time','datetime',NULL,NULL,NULL,'NO',0,NULL
)
SELECT e.table_name, e.column_name,
       e.data_type AS exp_type, c.COLUMN_TYPE AS actual_type,
       e.is_nullable AS exp_null, c.IS_NULLABLE AS actual_null,
       e.expected_default AS exp_def, c.COLUMN_DEFAULT AS actual_def
FROM expected e
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = e.table_name AND c.COLUMN_NAME = e.column_name
WHERE c.COLUMN_NAME IS NULL
   OR LOWER(c.DATA_TYPE) <> e.data_type
   OR (e.char_len IS NOT NULL AND NOT (c.CHARACTER_MAXIMUM_LENGTH <=> e.char_len))
   OR (e.num_precision IS NOT NULL AND NOT (c.NUMERIC_PRECISION <=> e.num_precision))
   OR (e.num_scale IS NOT NULL AND NOT (c.NUMERIC_SCALE <=> e.num_scale))
   OR c.IS_NULLABLE <> e.is_nullable
   OR (LOCATE('auto_increment', c.EXTRA) > 0) <> e.is_auto
   OR NOT (c.COLUMN_DEFAULT <=> e.expected_default)
ORDER BY e.table_name, e.column_name;

-- 3. 核对 CHECK 约束数量与生效状态（期望：82 / 82）
SELECT COUNT(*) AS checks_expected_82,
       SUM(CASE WHEN ENFORCED = 'YES' THEN 1 ELSE 0 END) AS enforced_expected_82
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_TYPE = 'CHECK'
  AND TABLE_NAME IN (
    'sel_select_round','sel_round_class','sel_select_limit','sel_lesson_class',
    'sel_sc','sel_select_log','sel_score','sel_score_audit','sel_score_correction',
    'sel_exchange_score_convert','sel_stu_gpa','sel_stu_term_gpa',
    'sel_teach_eval','sel_tc_eval_result'
  );

-- 4. 核对本组内部外键（期望：6 条）
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND REFERENCED_TABLE_NAME IS NOT NULL
  AND TABLE_NAME IN (
    'sel_select_round','sel_round_class','sel_select_limit','sel_lesson_class',
    'sel_sc','sel_select_log','sel_score','sel_score_audit','sel_score_correction',
    'sel_exchange_score_convert','sel_stu_gpa','sel_stu_term_gpa',
    'sel_teach_eval','sel_tc_eval_result'
  )
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
