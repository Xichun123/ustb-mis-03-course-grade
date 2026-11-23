-- 03组选课及成绩管理：v9结构只读核对（2026-11-23）
-- 本脚本未执行过。先在选定的隔离空开发库运行基线、v6至v7增量、v7至v8增量和v8至v9增量，再运行本文件。
-- 全部语句仅SELECT；不建库、不改表、不插入数据。先USE自己的隔离开发库。
-- 必须检查每一组结果，不能把“0行差异”单独当成全部验收成功。

SELECT DATABASE() AS current_database, @@version AS mysql_version, @@sql_mode AS sql_mode;

-- 期望：本组14表、134字段；只统计本组，不把外部桩表计入。
SELECT COUNT(DISTINCT TABLE_NAME) AS own_tables_expected_14, COUNT(*) AS own_columns_expected_134
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('select_round', 'round_class', 'select_limit', 'sc', 'select_log', 'lesson_class', 'score', 'score_audit', 'score_correction', 'stu_gpa', 'stu_term_gpa', 'teach_eval', 'tc_eval_result', 'exchange_score_convert');

-- 字段差异：期望0行。按DATA_TYPE比较整数，避免旧整数显示宽度影响判断。
WITH expected AS (
SELECT 'select_round' AS table_name, 'round_id' AS column_name, 1 AS ordinal_position, 'int' AS data_type, NULL AS char_len, NULL AS num_precision, NULL AS num_scale, 'NO' AS is_nullable, 1 AS is_auto, NULL AS expected_default
UNION ALL
SELECT 'select_round', 'term', 2, 'char', 11, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'round_name', 3, 'varchar', 30, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'round_type', 4, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'target_level', 5, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'select_round', 'start_time', 6, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'end_time', 7, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'max_credit', 8, 'decimal', NULL, 4, 1, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'select_mode', 9, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_round', 'allow_retake', 10, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'select_round', 'retake_ignore_conflict', 11, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'select_round', 'round_status', 12, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'select_round', 'notice_content', 13, 'text', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'select_round', 'publish_time', 14, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'select_round', 'approver_id', 15, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'select_round', 'approve_time', 16, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'select_round', 'approve_opinion', 17, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'round_class', 'round_id', 1, 'int', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'round_class', 'tc_id', 2, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_limit', 'limit_id', 1, 'int', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'select_limit', 'tc_id', 2, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_limit', 'limit_type', 3, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_limit', 'limit_value', 4, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'sc_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'sc', 'student_id', 2, 'char', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'tc_id', 3, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'round_id', 4, 'int', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'study_type', 5, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'sc', 'is_exempt_attend', 6, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'sc', 'stu_level', 7, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'stat_stage', 8, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'sc', 'priority', 9, 'tinyint', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'sc', 'select_status', 10, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'select_time', 11, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'sc', 'drop_time', 12, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'select_log', 'log_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'select_log', 'student_id', 2, 'char', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'tc_id', 3, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'round_id', 4, 'int', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'action_type', 5, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'operator_id', 6, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'action_time', 7, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'select_log', 'remark', 8, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'lesson_class', 'tc_id', 1, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'lesson_class', 'lesson_no', 2, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score', 'score_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'score', 'sc_id', 2, 'bigint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score', 'exam_type', 3, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'score', 'exam_flag', 4, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'score', 'score_type', 5, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'score', 'usual_ratio', 6, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score', 'usual_score', 7, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'exam_score', 8, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'total_score', 9, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'score_level', 10, 'varchar', 6, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'grade_point', 11, 'decimal', NULL, 3, 2, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'is_final', 12, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'score', 'score_status', 13, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'score', 'input_teacher_id', 14, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'submit_time', 15, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score', 'publish_time', 16, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_audit', 'audit_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'score_audit', 'lesson_no', 2, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_audit', 'submit_time', 3, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_audit', 'verify_code', 4, 'char', 6, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_audit', 'auditor_id', 5, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_audit', 'audit_result', 6, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'score_audit', 'audit_opinion', 7, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_audit', 'audit_time', 8, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'apply_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'score_correction', 'score_id', 2, 'bigint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_correction', 'apply_teacher_id', 3, 'char', 10, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_correction', 'old_score', 4, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'new_score', 5, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'old_level', 6, 'varchar', 6, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'new_level', 7, 'varchar', 6, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'apply_reason', 8, 'varchar', 200, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_correction', 'apply_time', 9, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'score_correction', 'apply_status', 10, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'score_correction', 'college_auditor_id', 11, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'college_audit_opinion', 12, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'college_audit_time', 13, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'auditor_id', 14, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'audit_opinion', 15, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'score_correction', 'audit_time', 16, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'student_id', 1, 'char', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'earned_credit', 2, 'decimal', NULL, 5, 1, 'NO', 0, '0'
UNION ALL
SELECT 'stu_gpa', 'failed_credit', 3, 'decimal', NULL, 5, 1, 'NO', 0, '0'
UNION ALL
SELECT 'stu_gpa', 'gpa', 4, 'decimal', NULL, 3, 2, 'YES', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'avg_score', 5, 'decimal', NULL, 5, 2, 'YES', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'major_rank', 6, 'int', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'major_rank_total', 7, 'int', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'stu_gpa', 'update_time', 8, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'stu_term_gpa', 'student_id', 1, 'char', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'stu_term_gpa', 'term', 2, 'char', 11, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'stu_term_gpa', 'term_credit', 3, 'decimal', NULL, 4, 1, 'NO', 0, '0'
UNION ALL
SELECT 'stu_term_gpa', 'term_failed_credit', 4, 'decimal', NULL, 4, 1, 'NO', 0, '0'
UNION ALL
SELECT 'stu_term_gpa', 'update_time', 5, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'teach_eval', 'sc_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'teach_eval', 'eval_score', 2, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'teach_eval', 'eval_comment', 3, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'teach_eval', 'eval_time', 4, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'tc_eval_result', 'tc_id', 1, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'tc_eval_result', 'eval_num', 2, 'int', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'tc_eval_result', 'avg_eval_score', 3, 'decimal', NULL, 5, 2, 'YES', 0, NULL
UNION ALL
SELECT 'tc_eval_result', 'calc_time', 4, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'convert_id', 1, 'bigint', NULL, NULL, NULL, 'NO', 1, NULL
UNION ALL
SELECT 'exchange_score_convert', 'student_id', 2, 'char', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'exchange_apply_id', 3, 'varchar', 40, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'term', 4, 'char', 11, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'stat_stage', 5, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'exchange_score_convert', 'host_school', 6, 'varchar', 150, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'ext_course_code', 7, 'varchar', 30, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'ext_course_name', 8, 'varchar', 100, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'ext_credit', 9, 'decimal', NULL, 4, 1, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'ext_score_raw', 10, 'varchar', 20, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'ext_scale_type', 11, 'tinyint', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'course_id', 12, 'char', 8, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'score_type', 13, 'tinyint', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'exchange_score_convert', 'converted_score', 14, 'decimal', NULL, 4, 1, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'converted_level', 15, 'varchar', 6, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'grade_point', 16, 'decimal', NULL, 3, 2, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'is_final', 17, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'exchange_score_convert', 'proof_no', 18, 'varchar', 50, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'proof_file_id', 19, 'varchar', 64, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'version_no', 20, 'int', NULL, NULL, NULL, 'NO', 0, '1'
UNION ALL
SELECT 'exchange_score_convert', 'audit_status', 21, 'tinyint', NULL, NULL, NULL, 'NO', 0, '0'
UNION ALL
SELECT 'exchange_score_convert', 'apply_time', 22, 'datetime', NULL, NULL, NULL, 'NO', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'college_auditor_id', 23, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'college_audit_opinion', 24, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'college_audit_time', 25, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'school_auditor_id', 26, 'char', 10, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'school_audit_opinion', 27, 'varchar', 200, NULL, NULL, 'YES', 0, NULL
UNION ALL
SELECT 'exchange_score_convert', 'school_audit_time', 28, 'datetime', NULL, NULL, NULL, 'YES', 0, NULL
)
SELECT e.table_name, e.column_name, c.COLUMN_TYPE AS actual_type,
       c.IS_NULLABLE AS actual_nullable, c.COLUMN_DEFAULT AS actual_default, c.EXTRA AS actual_extra
FROM expected e
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME=e.table_name AND c.COLUMN_NAME=e.column_name
WHERE c.COLUMN_NAME IS NULL
   OR LOWER(c.DATA_TYPE)<>e.data_type
   OR c.ORDINAL_POSITION<>e.ordinal_position
   OR (e.char_len IS NOT NULL AND NOT(c.CHARACTER_MAXIMUM_LENGTH <=> e.char_len))
   OR (e.num_precision IS NOT NULL AND NOT(c.NUMERIC_PRECISION <=> e.num_precision))
   OR (e.num_scale IS NOT NULL AND NOT(c.NUMERIC_SCALE <=> e.num_scale))
   OR c.IS_NULLABLE<>e.is_nullable
   OR (LOCATE('auto_increment',c.EXTRA)>0)<>e.is_auto
   OR NOT(c.COLUMN_DEFAULT <=> e.expected_default)
ORDER BY e.table_name,e.ordinal_position;

-- CHECK数量期望82，ENFORCED应均为YES；这只检查元数据，不能代替实际违规数据测试。
SELECT COUNT(*) AS checks_expected_82,
       SUM(CASE WHEN ENFORCED='YES' THEN 1 ELSE 0 END) AS enforced_expected_82
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_TYPE='CHECK'
  AND TABLE_NAME IN ('select_round', 'round_class', 'select_limit', 'sc', 'select_log', 'lesson_class', 'score', 'score_audit', 'score_correction', 'stu_gpa', 'stu_term_gpa', 'teach_eval', 'tc_eval_result', 'exchange_score_convert');

-- 外键：只执行基线时6；加集成外键、v7审批人外键、v8/v9外部外键后26。
SELECT TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
  AND TABLE_NAME IN ('select_round', 'round_class', 'select_limit', 'sc', 'select_log', 'lesson_class', 'score', 'score_audit', 'score_correction', 'stu_gpa', 'stu_term_gpa', 'teach_eval', 'tc_eval_result', 'exchange_score_convert')
ORDER BY TABLE_NAME,CONSTRAINT_NAME;
