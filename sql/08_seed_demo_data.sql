-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 演示与联调初始化测试数据（v9 全兼容）
-- 文件：02_演示与测试初始化数据.sql
-- 说明：
--   - 所有数据严格满足 v9 全部 82 条 CHECK 约束与外键约束；
--   - 覆盖场景：本科生/研究生/来校交换生、合班大课堂（lesson_class）、
--     初修/重修免听/辅修、选课轮次各状态（草稿/待审/已审/进行中/已结束）、
--     成绩各状态（录入中/待核对含6位随机码/已审核/已发布/退回）、
--     成绩更正申请（待学院/待教务处/已通过）、校外交流成绩换算、评教与学业统计。
--   - 仅限开发与演示使用，不含任何真实个人敏感信息。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 外部桩表基础数据（教职工、学生、课程、教学班、排课、研究生方案、交换生）
-- ---------------------------------------------------------------------
INSERT INTO t_employee (emp_id, emp_name, org_id) VALUES
  ('T000010001', '王明远', 'ORG001'),  -- 经管学院任课教师（对应教学班 teacher_id='T0010001'）
  ('T000010002', '李晓静', 'ORG001'),  -- 经管学院任课教师
  ('T000020001', '陈志强', 'ORG002'),  -- 计算机学院任课教师
  ('E000010001', '周敏（经管教学秘书）', 'ORG001'),
  ('E000020001', '赵刚（计通教学秘书）', 'ORG002'),
  ('A000000001', '林建华（教务处经办）', 'ORG900'),
  ('A000000002', '吴海峰（教务处处长）', 'ORG900'),
  ('G000000001', '孙梅（研究生院审核）', 'ORG901'),
  ('S000000001', '系统管理员',           'ORG999')
ON DUPLICATE KEY UPDATE emp_name = VALUES(emp_name);

INSERT INTO student (student_id, student_name, grade, degree_level, class_name, major_id, college_id, final_enroll) VALUES
  ('U202440001', '张晨曦', 2024, '本科',   '信管241',   '120102', '0001', '在籍'),
  ('U202440002', '林思远', 2024, '本科',   '信管241',   '120102', '0001', '在籍'),
  ('U202440003', '陈雨桐', 2024, '本科',   '信管242',   '120102', '0001', '在籍'),
  ('U202340004', '刘浩然', 2023, '本科',   '信管231',   '120102', '0001', '在籍'),  -- 重修生场景
  ('U202440005', '赵子涵', 2024, '本科',   '计科241',   '080901', '0002', '在籍'),  -- 辅修/跨学院场景
  ('M202610001', '许嘉豪', 2026, '研究生', '管科硕261', '120100', '0001', '在籍'),  -- 研究生场景
  ('M202610002', '宋婉清', 2026, '研究生', '管科硕261', '120100', '0001', '在籍')
ON DUPLICATE KEY UPDATE student_name = VALUES(student_name);

INSERT INTO course (course_id, course_name, course_type, credit, assess_type, open_college, course_level) VALUES
  ('CS201001', '管理信息系统',     '12', 3.0, '1', '0001', 1),
  ('CS201002', '数据库原理与应用', '12', 3.5, '1', '0001', 1),
  ('CS202003', 'Java企业级开发',   '14', 2.5, '1', '0002', 1),
  ('GE101001', '大学英语（三）',   '11', 2.0, '1', '0001', 1),
  ('PR301001', '信息系统开发实践', '16', 2.0, '2', '0001', 1),  -- 五级制考查课
  ('GR501001', '高级管理科学研究方法', '12', 3.0, '1', '0001', 2), -- 研究生课
  ('GR502002', '数据挖掘与商务智能',   '14', 2.0, '1', '0001', 2)
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name);

INSERT INTO program_course (major_id, grade, course_id) VALUES
  ('120102', '2024', 'CS201001'),
  ('120102', '2024', 'CS201002'),
  ('120102', '2024', 'PR301001'),
  ('080901', '2024', 'CS202003')
ON DUPLICATE KEY UPDATE course_id = VALUES(course_id);

INSERT INTO teaching_class (tc_id, term, course_id, teacher_id, class_ids, capacity, tc_type, tc_status, actual_num) VALUES
  ('TC20261001', '2026-2027-1', 'CS201001', 'T0010001', '信管241',      60, '1', '4', 3),
  ('TC20261002', '2026-2027-1', 'CS201001', 'T0010001', '信管242',      60, '1', '4', 2), -- 与 TC20261001 合班上课
  ('TC20261003', '2026-2027-1', 'CS201002', 'T0010002', '信管241,信管242', 80, '1', '4', 2),
  ('TC20261004', '2026-2027-1', 'CS202003', 'T0020001', '全校选修',     50, '2', '4', 1),
  ('TC20261005', '2026-2027-1', 'PR301001', 'T0010001', '信管241',      40, '1', '4', 2),
  ('TC20261006', '2026-2027-1', 'GR501001', 'T0010002', '管科硕261',    30, '1', '4', 2),
  ('TC20261007', '2026-2027-1', 'GR502002', 'T0020001', '管科硕261',    30, '2', '4', 0),
  ('TC20261008', '2026-2027-1', 'GE101001', 'T0010002', '信管241',      45, '1', '2', 0)  -- 状态2不开课（校验不可加入轮次）
ON DUPLICATE KEY UPDATE capacity = VALUES(capacity);

INSERT INTO schedule_result (schedule_id, tc_id, week_range, week_day, start_slot, end_slot, room_id) VALUES
  (1, 'TC20261001', '1-16', 1, 1, 2, '经管楼301'),
  (2, 'TC20261002', '1-16', 1, 1, 2, '经管楼301'), -- 同时间同地点同教师，合班 KT202610001
  (3, 'TC20261003', '1-16', 2, 3, 4, '经管楼205'),
  (4, 'TC20261004', '1-16', 1, 1, 2, '逸夫楼402'), -- 与 TC20261001 撞课（用于测冲突 & 重修免听）
  (5, 'TC20261005', '9-16', 3, 5, 6, '信息实验楼B201'),
  (6, 'TC20261006', '1-16', 4, 1, 3, '经管楼502'),
  (7, 'TC20261007', '1-16', 5, 3, 4, '逸夫楼308')
ON DUPLICATE KEY UPDATE room_id = VALUES(room_id);

INSERT INTO grad_direction (direction_id, direction_name) VALUES
  ('DIR01', '信息管理与大数据分析'),
  ('DIR02', '智能供应链与运营管理')
ON DUPLICATE KEY UPDATE direction_name = VALUES(direction_name);

INSERT INTO scheme_assignment (student_id, scheme_id, direction_id, effective_from, effective_to) VALUES
  ('M202610001', 'SCH_MS_2026', 'DIR01', '2026-09-01', NULL),
  ('M202610002', 'SCH_MS_2026', 'DIR02', '2026-09-01', NULL);

INSERT INTO scheme_course (scheme_id, course_id, direction_id, course_category, suggest_term) VALUES
  ('SCH_MS_2026', 'GR501001', NULL,    1, 1), -- 年度必修
  ('SCH_MS_2026', 'GR502002', 'DIR01', 2, 1); -- 方向必修

-- 第 15 组交换生桩数据
INSERT INTO stub_ex_credit_ready (application_no, student_id, term_ref, partner_school_snapshot, ready_flag) VALUES
  ('SQ20260001', 'U202440001', '2026-2027-1', '新加坡国立大学 (NUS)', 1),
  ('SQ20260002', 'U202440002', '2026-2027-1', '慕尼黑工业大学 (TUM)', 0)
ON DUPLICATE KEY UPDATE ready_flag = VALUES(ready_flag);

INSERT INTO stub_ex_incoming_student (temp_student_no, student_name, sender_school_name, term_ref, college_ref, major_ref, start_on, end_on, status_code) VALUES
  ('EX20260001', 'Alex Mueller', 'RWTH Aachen University', '2026-2027-1', '0001', '120102', '2026-09-01', '2027-01-31', 'ACTIVE')
ON DUPLICATE KEY UPDATE status_code = VALUES(status_code);

-- ---------------------------------------------------------------------
-- 2. 业务① 选课准备与发布（select_round、round_class、select_limit）
-- ---------------------------------------------------------------------
INSERT INTO select_round (
  round_id, term, round_name, round_type, target_level, start_time, end_time,
  max_credit, select_mode, allow_retake, retake_ignore_conflict, round_status,
  notice_content, publish_time, approver_id, approve_time, approve_opinion
) VALUES
  (1, '2026-2027-1', '2026秋第一轮正选（本科）', 2, 1, '2026-09-01 09:00:00', '2026-09-05 17:00:00',
   30.0, 2, 1, 1, 4,
   '本轮面向全体本科生开放正选，并开放重修生选课与冲突免听申请，请在规定时间内完成志愿提交。',
   '2026-08-28 10:00:00', 'A000000002', '2026-08-27 16:30:00', '方案完整，同意发布'),
  (2, '2026-2027-1', '2026秋补退选（本研打通）', 3, 3, '2026-11-20 09:00:00', '2026-12-10 17:00:00',
   32.0, 1, 1, 1, 3,
   '本轮为补退选阶段，先到先得，支持高年级本科生跨选部分研究生课程，重修生支持冲突免听。',
   '2026-11-19 14:00:00', 'A000000002', '2026-11-19 11:20:00', '准予发布'),
  (3, '2026-2027-1', '2026秋研究生增选轮次',     3, 2, '2026-11-26 09:00:00', '2026-12-05 17:00:00',
   20.0, 1, 0, 0, 1,
   '面向2026级研究生开放方向选修课增选，请结合导师确定研究方向后选课。',
   NULL, NULL, NULL, NULL),
  (4, '2026-2027-2', '2027春第一轮预选草案',     1, 1, '2026-12-20 09:00:00', '2026-12-25 17:00:00',
   28.0, 2, 0, 0, 0,
   '2026-2027学年第二学期预选草案，待学院补齐限选条件后提交审批。',
   NULL, NULL, NULL, NULL);

INSERT INTO round_class (round_id, tc_id) VALUES
  (1, 'TC20261001'), (1, 'TC20261002'), (1, 'TC20261003'), (1, 'TC20261004'), (1, 'TC20261005'),
  (2, 'TC20261001'), (2, 'TC20261002'), (2, 'TC20261003'), (2, 'TC20261004'), (2, 'TC20261006'), (2, 'TC20261007'),
  (3, 'TC20261006'), (3, 'TC20261007'),
  (4, 'TC20261001');

INSERT INTO select_limit (limit_id, tc_id, limit_type, limit_value) VALUES
  (1, 'TC20261004', 1, '2024'),
  (2, 'TC20261004', 4, '本科'),
  (3, 'TC20261007', 4, '研究生'),
  (4, 'TC20261007', 3, '0001');

-- ---------------------------------------------------------------------
-- 3. 业务② 学生选课（sc、select_log）+ v9 合班课堂（lesson_class）
-- ---------------------------------------------------------------------
INSERT INTO lesson_class (tc_id, lesson_no) VALUES
  ('TC20261001', 'KT202610001'),
  ('TC20261002', 'KT202610001'), -- 两班合并为同一大课堂 KT202610001
  ('TC20261003', 'KT202610002'),
  ('TC20261004', 'KT202610003'),
  ('TC20261005', 'KT202610004'),
  ('TC20261006', 'KT202610005'),
  ('TC20261007', 'KT202610006');

INSERT INTO sc (sc_id, student_id, tc_id, round_id, study_type, is_exempt_attend, stu_level, stat_stage, priority, select_status, select_time, drop_time) VALUES
  (1,  'U202440001', 'TC20261001', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:05:00', NULL), -- 初修
  (2,  'U202440002', 'TC20261001', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:08:00', NULL), -- 初修
  (3,  'U202440003', 'TC20261002', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:12:00', NULL), -- 同合班课堂另一行政班
  (4,  'U202340004', 'TC20261002', 1, 2, 1, '本科',   1, 1, 1, '2026-09-01 10:00:00', NULL), -- 重修 + 冲突免听
  (5,  'U202440005', 'TC20261001', 2, 3, 0, '本科',   1, NULL, 1, '2026-11-20 09:30:00', NULL), -- 辅修（v9新值3）
  (6,  'U202440001', 'TC20261003', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:06:00', NULL),
  (7,  'U202440002', 'TC20261003', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:09:00', NULL),
  (8,  'U202440001', 'TC20261005', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:15:00', NULL),
  (9,  'EX20260001', 'TC20261004', 2, 1, 0, '本科',   1, NULL, 1, '2026-11-20 10:10:00', NULL), -- 来校交换生
  (10, 'M202610001', 'TC20261006', 2, 1, 0, '研究生', 2, NULL, 1, '2026-11-20 10:20:00', NULL), -- 研究生
  (11, 'M202610002', 'TC20261006', 2, 1, 0, '研究生', 2, NULL, 1, '2026-11-20 10:25:00', NULL);

INSERT INTO select_log (log_id, student_id, tc_id, round_id, action_type, operator_id, action_time, remark) VALUES
  (1, 'U202440001', 'TC20261001', 1, 1, 'U202440001', '2026-09-01 09:05:00', NULL),
  (2, 'U202340004', 'TC20261002', 1, 1, 'U202340004', '2026-09-01 10:00:00', '重修冲突免听选课'),
  (3, 'U202440005', 'TC20261001', 2, 4, 'A000000001', '2026-11-20 09:30:00', '辅修双学位补加进入合班课堂');

-- ---------------------------------------------------------------------
-- 4. 业务③ 成绩录入与审核（score、score_audit、exchange_score_convert）
-- ---------------------------------------------------------------------
-- 课堂 KT202610001（含 sc_id 1..5）：已发布（score_status=3）用于演示查分、评教、成绩更正
INSERT INTO score (
  score_id, sc_id, exam_type, exam_flag, score_type, usual_ratio,
  usual_score, exam_score, total_score, score_level, grade_point,
  is_final, score_status, input_teacher_id, submit_time, publish_time
) VALUES
  (1, 1, 1, 0, 1, 30, 92.0, 88.0, 89.2, NULL,   3.70, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (2, 2, 1, 0, 1, 30, 85.0, 79.0, 80.8, NULL,   3.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (3, 3, 1, 0, 1, 30, 95.0, 93.0, 93.6, NULL,   4.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (4, 4, 1, 0, 1, 30, 72.0, 68.0, 69.2, NULL,   2.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (5, 5, 1, 0, 1, 30, 90.0, 86.0, 87.2, NULL,   3.70, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  -- 课堂 KT202610002（含 sc_id 6,7）：已提交待学院核对（score_status=1，配随机码 K7M9P2）
  (6, 6, 1, 0, 1, 40, 88.0, 84.0, 85.6, NULL,   3.70, 0, 1, 'T000010002', '2026-11-22 15:00:00', NULL),
  (7, 7, 1, 0, 1, 40, 76.0, 70.0, 72.4, NULL,   2.40, 0, 1, 'T000010002', '2026-11-22 15:00:00', NULL),
  -- 课堂 KT202610004（含 sc_id 8）：五级制已发布（演示等级制更正）
  (8, 8, 1, 0, 2, 40, NULL, NULL, NULL, '良',   3.40, 1, 3, 'T000010001', '2026-11-18 11:00:00', '2026-11-19 09:30:00'),
  -- 课堂 KT202610003（来校交换生 sc_id 9）：已发布
  (9, 9, 1, 0, 1, 30, 91.0, 89.0, 89.6, NULL,   3.70, 1, 3, 'T000020001', '2026-11-20 14:00:00', '2026-11-21 10:00:00');

INSERT INTO score_audit (audit_id, lesson_no, submit_time, verify_code, auditor_id, audit_result, audit_opinion, audit_time) VALUES
  (1, 'KT202610001', '2026-11-18 10:00:00', 'A3H8W5', 'E000010001', 1, '核对纸质成绩单无误', '2026-11-18 16:00:00'),
  (2, 'KT202610002', '2026-11-22 15:00:00', 'K7M9P2', NULL,         0, NULL,                 NULL),
  (3, 'KT202610004', '2026-11-18 11:00:00', 'M4N6Q9', 'E000010001', 1, '核对通过',           '2026-11-18 16:30:00'),
  (4, 'KT202610003', '2026-11-20 14:00:00', 'R5T8Y2', 'E000020001', 1, '核对通过',           '2026-11-20 17:00:00');

-- 校外交流成绩换算认定（v8 第 13 表）：一条待教务处审批示例
INSERT INTO exchange_score_convert (
  convert_id, student_id, exchange_apply_id, term, stat_stage, host_school,
  ext_course_code, ext_course_name, ext_credit, ext_score_raw, ext_scale_type,
  course_id, score_type, converted_score, converted_level, grade_point, is_final,
  proof_no, proof_file_id, version_no, audit_status, apply_time,
  college_auditor_id, college_audit_opinion, college_audit_time,
  school_auditor_id, school_audit_opinion, school_audit_time
) VALUES
  (1, 'U202440001', 'SQ20260001', '2026-2027-1', 1, '新加坡国立大学 (NUS)',
   'IS3103', 'Information Systems Leadership', 4.0, 'A-', 2,
   'CS202003', 1, 90.0, NULL, 4.00, 0,
   'NUS-TR-2026-8821', 'FILE_EX_8821', 1, 1, '2026-11-21 10:00:00',
   'E000010001', '课程大纲匹配度达90%，建议按90分认定', '2026-11-22 09:30:00',
   NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- 5. 业务⑤ 成绩更正申请（score_correction）
-- ---------------------------------------------------------------------
INSERT INTO score_correction (
  apply_id, score_id, apply_teacher_id, old_score, new_score, old_level, new_level,
  apply_reason, apply_time, apply_status,
  college_auditor_id, college_audit_opinion, college_audit_time,
  auditor_id, audit_opinion, audit_time
) VALUES
  -- 待学院审核（status=0）
  (1, 2, 'T000010001', 80.8, 84.8, NULL, NULL,
   '复核实验报告得分时漏加第4次实验附加分，经核查原始卷面应为84.8分',
   '2026-11-21 14:20:00', 0, NULL, NULL, NULL, NULL, NULL, NULL),
  -- 学院已通过、待教务处审批（status=1，等级制示例）
  (2, 8, 'T000010001', NULL, NULL, '良', '优',
   '答辩评分汇总表误录，该生小组综合答辩为92分应评为优',
   '2026-11-20 11:00:00', 1, 'E000010001', '已核对答辩原始记录，情况属实', '2026-11-21 09:00:00', NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- 6. 业务④ 评教与学业统计快照（teach_eval、tc_eval_result、stu_gpa、stu_term_gpa）
-- ---------------------------------------------------------------------
INSERT INTO teach_eval (sc_id, eval_score, eval_comment, eval_time) VALUES
  (1, 95, '课程案例贴合实际，讲解非常清晰', '2026-11-19 10:30:00'),
  (3, 92, '课堂互动很好',                   '2026-11-19 11:00:00'),
  (8, 96, '实践指导非常细致',               '2026-11-19 10:35:00');
-- 注意：U202440002（sc_id=2）故意暂未评教，用于演示“先评教后查分”拦截！

INSERT INTO tc_eval_result (tc_id, eval_num, avg_eval_score, calc_time) VALUES
  ('TC20261001', 1, 95.00, '2026-11-20 08:00:00'),
  ('TC20261002', 1, 92.00, '2026-11-20 08:00:00'),
  ('TC20261005', 1, 96.00, '2026-11-20 08:00:00'),
  ('TC20261003', 0, NULL,  '2026-11-20 08:00:00'); -- 零参评均分为 NULL（v7 CHECK 验证）

INSERT INTO stu_gpa (student_id, earned_credit, failed_credit, gpa, avg_score, major_rank, major_rank_total, update_time) VALUES
  ('U202440001', 5.0, 0.0, 3.58, 87.52, 2, 3, '2026-11-19 09:35:00'),
  ('U202440002', 3.0, 0.0, 3.00, 80.80, 3, 3, '2026-11-19 09:35:00'),
  ('U202440003', 3.0, 0.0, 4.00, 93.60, 1, 3, '2026-11-19 09:35:00'),
  ('U202340004', 3.0, 0.0, 2.00, 69.20, 1, 1, '2026-11-19 09:35:00');

INSERT INTO stu_term_gpa (student_id, term, term_credit, term_failed_credit, update_time) VALUES
  ('U202440001', '2026-2027-1', 5.0, 0.0, '2026-11-19 09:35:00'),
  ('U202440002', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00'),
  ('U202440003', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00'),
  ('U202340004', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00');

-- ---------------------------------------------------------------------
-- 7. 业务⑥ 系统参数与编码初始值
-- ---------------------------------------------------------------------
INSERT INTO sys_code_dict (dict_group, dict_code, dict_name, remark) VALUES
  ('system_param', 'current_term',     '2026-2027-1', '当前默认学年学期'),
  ('system_param', 'usual_ratio_min',  '30',          '平时成绩最低占比（%），3.1 录入保存时校验一次'),
  ('college_map',  '0001',             '经济管理学院', '对应第4组 org_id=ORG001'),
  ('college_map',  '0002',             '计算机与通信工程学院', '对应第4组 org_id=ORG002'),
  ('teacher_map',  'T0010001',         'T000010001',  '第2组8位teacher_id -> 第4组10位emp_id映射'),
  ('teacher_map',  'T0010002',         'T000010002',  '第2组8位teacher_id -> 第4组10位emp_id映射'),
  ('teacher_map',  'T0020001',         'T000020001',  '第2组8位teacher_id -> 第4组10位emp_id映射')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name);

INSERT INTO sys_backup_log (backup_id, backup_type, file_name, file_size_kb, operator_id, backup_time, status, remark) VALUES
  (1, 1, 'course_grade_mgmt_v9_init_20261123.sql', 128, 'S000000001', '2026-11-23 23:00:00', 1, 'v9 初始化部署基线快照')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);
