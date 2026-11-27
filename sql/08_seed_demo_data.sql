-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 演示与联调初始化测试数据（DBA 统一字典 v1.3 全兼容）
-- 文件：02_演示与测试初始化数据.sql
-- 说明：
--   - 本组 14 张表均使用 DBA 统一字典 v1.3 表名（sel_*）与统一时间字段名；
--   - 所有数据严格满足 v1.3 全部 82 条 CHECK 约束与外键约束；
--   - 覆盖场景：本科生/研究生/来校交换生、合班大课堂（lesson_class）、
--     初修/重修免听/辅修、选课轮次各状态（草稿/待审/已审/进行中/已结束）、
--     成绩各状态（录入中/待核对含6位随机码/已审核/已发布/退回）、
--     成绩更正申请（待学院/待教务处/已通过）、校外交流成绩换算、评教与学业统计。
--   - 仅限开发与演示使用，不含任何真实个人敏感信息。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 上游公共/外部表基础数据（完全按 DBA 统一字典 v1.3 正式表名与字段）
-- ---------------------------------------------------------------------
INSERT INTO org_organization (org_id, org_code, org_name, org_short, org_type, college_id, org_status) VALUES
  ('ORG001', 'COL0001', '经济管理学院',       '经管学院', '01', '0001', '01'),
  ('ORG002', 'COL0002', '计算机与通信工程学院', '计通学院', '01', '0002', '01'),
  ('ORG900', 'DEP900',  '教务处',             '教务处',   '02', NULL,   '01'),
  ('ORG901', 'DEP901',  '研究生院',           '研究生院', '02', NULL,   '01'),
  ('ORG999', 'DEP999',  '信息化建设与管理办公室', '信息办', '02', NULL,   '01')
ON DUPLICATE KEY UPDATE org_name = VALUES(org_name);

INSERT INTO aca_term (term, academic_year, term_no, start_date, end_date, term_status) VALUES
  ('2025-2026-1', '2025-2026', 1, '2025-09-01', '2026-01-20', '02'),
  ('2025-2026-2', '2025-2026', 2, '2026-02-25', '2026-07-10', '02'),
  ('2026-2027-1', '2026-2027', 1, '2026-09-01', '2027-01-20', '01'),
  ('2026-2027-2', '2026-2027', 2, '2027-02-25', '2027-07-10', '00')
ON DUPLICATE KEY UPDATE term_status = VALUES(term_status);

INSERT INTO aca_major (major_id, major_name, org_id, college_id, duration, degree_name, min_credit, major_status) VALUES
  ('120102', '信息管理与信息系统', 'ORG001', '0001', 4, '管理学学士', 160.0, '01'),
  ('080901', '计算机科学与技术',   'ORG002', '0002', 4, '工学学士',   165.0, '01'),
  ('120100', '管理科学与工程',     'ORG001', '0001', 3, '管理学硕士',  34.0, '01')
ON DUPLICATE KEY UPDATE major_name = VALUES(major_name);

INSERT INTO sys_person (person_id, person_no, person_name, person_type, gender) VALUES
  ('P_T000010001', 'T000010001', '王明远',             '02', '1'),
  ('P_T000010002', 'T000010002', '李晓静',             '02', '2'),
  ('P_T000020001', 'T000020001', '陈志强',             '02', '1'),
  ('P_E000010001', 'E000010001', '周敏（经管教学秘书）', '02', '2'),
  ('P_E000020001', 'E000020001', '赵刚（计通教学秘书）', '02', '1'),
  ('P_A000000001', 'A000000001', '林建华（教务处经办）', '02', '1'),
  ('P_A000000002', 'A000000002', '吴海峰（教务处处长）', '02', '1'),
  ('P_G000000001', 'G000000001', '孙梅（研究生院审核）', '02', '2'),
  ('P_S000000001', 'S000000001', '系统管理员',          '02', '9'),
  ('P_U202440001', 'U202440001', '张晨曦',             '01', '2'),
  ('P_U202440002', 'U202440002', '林思远',             '01', '1'),
  ('P_U202440003', 'U202440003', '陈雨桐',             '01', '2'),
  ('P_U202340004', 'U202340004', '刘浩然',             '01', '1'),
  ('P_U202440005', 'U202440005', '赵子涵',             '01', '1'),
  ('P_M202610001', 'M202610001', '许嘉豪',             '01', '1'),
  ('P_M202610002', 'M202610002', '宋婉清',             '01', '2'),
  ('P_EX20260001', 'EX20260001', 'Alex Mueller',       '03', '1'),
  ('P_IS20260001', 'IS20260001', 'Sofia Lindqvist',    '01', '2')
ON DUPLICATE KEY UPDATE person_name = VALUES(person_name);

INSERT INTO hr_employee (emp_id, person_id, emp_name, id_card, org_id, post_type, title_code, edu_level, hire_date, emp_status, salary) VALUES
  ('T000010001', 'P_T000010001', '王明远',             '110101198001010001', 'ORG001', 'TEACHER', 'PROF',       '博士', '2015-09-01', '01', 12000.00),
  ('T000010002', 'P_T000010002', '李晓静',             '110101198201010002', 'ORG001', 'TEACHER', 'ASSOC_PROF', '博士', '2016-09-01', '01', 11500.00),
  ('T000020001', 'P_T000020001', '陈志强',             '110101197901010003', 'ORG002', 'TEACHER', 'PROF',       '博士', '2014-09-01', '01', 12500.00),
  ('E000010001', 'P_E000010001', '周敏（经管教学秘书）', '110101198801010004', 'ORG001', 'ADMIN',   'LECTURER',   '硕士', '2018-09-01', '01',  9000.00),
  ('E000020001', 'P_E000020001', '赵刚（计通教学秘书）', '110101198701010005', 'ORG002', 'ADMIN',   'LECTURER',   '硕士', '2018-09-01', '01',  9000.00),
  ('A000000001', 'P_A000000001', '林建华（教务处经办）', '110101198501010006', 'ORG900', 'ADMIN',   'LECTURER',   '硕士', '2017-09-01', '01',  9500.00),
  ('A000000002', 'P_A000000002', '吴海峰（教务处处长）', '110101197501010007', 'ORG900', 'ADMIN',   'PROF',       '博士', '2010-09-01', '01', 15000.00),
  ('G000000001', 'P_G000000001', '孙梅（研究生院审核）', '110101198301010008', 'ORG901', 'ADMIN',   'ASSOC_PROF', '博士', '2016-09-01', '01', 10000.00),
  ('S000000001', 'P_S000000001', '系统管理员',          '110101199001010009', 'ORG999', 'ADMIN',   'ENGINEER',   '硕士', '2020-09-01', '01',  9800.00)
ON DUPLICATE KEY UPDATE emp_name = VALUES(emp_name), title_code = VALUES(title_code), edu_level = VALUES(edu_level);

INSERT INTO aca_class (class_id, class_name, grade, major_id, org_id) VALUES
  ('CLS_XG241', '信管241',   '2024', '120102', 'ORG001'),
  ('CLS_XG242', '信管242',   '2024', '120102', 'ORG001'),
  ('CLS_XG231', '信管231',   '2023', '120102', 'ORG001'),
  ('CLS_JK241', '计科241',   '2024', '080901', 'ORG002'),
  ('CLS_JK261', '计科留261', '2026', '080901', 'ORG002'),
  ('CLS_GK261', '管科硕261', '2026', '120100', 'ORG001')
ON DUPLICATE KEY UPDATE class_name = VALUES(class_name);

INSERT INTO std_student (student_id, person_id, class_id, major_id, org_id, grade, enrollment_year, student_type, student_status) VALUES
  ('U202440001', 'P_U202440001', 'CLS_XG241', '120102', 'ORG001', '2024', 2024, '01', '01'),
  ('U202440002', 'P_U202440002', 'CLS_XG241', '120102', 'ORG001', '2024', 2024, '01', '01'),
  ('U202440003', 'P_U202440003', 'CLS_XG242', '120102', 'ORG001', '2024', 2024, '01', '01'),
  ('U202340004', 'P_U202340004', 'CLS_XG231', '120102', 'ORG001', '2023', 2023, '01', '01'),
  ('U202440005', 'P_U202440005', 'CLS_JK241', '080901', 'ORG002', '2024', 2024, '01', '01'),
  ('M202610001', 'P_M202610001', 'CLS_GK261', '120100', 'ORG001', '2026', 2026, '02', '01'),
  ('M202610002', 'P_M202610002', 'CLS_GK261', '120100', 'ORG001', '2026', 2026, '02', '01'),
  ('IS20260001', 'P_IS20260001', 'CLS_JK261', '080901', 'ORG002', '2026', 2026, '01', '01')
ON DUPLICATE KEY UPDATE student_status = VALUES(student_status);

INSERT INTO aca_course (course_id, course_code, course_name, course_type, credit, total_hours, org_id, course_status) VALUES
  ('CS201001', 'CS201001', '管理信息系统',         '12', 3.0, 48.00, 'ORG001', '01'),
  ('CS201002', 'CS201002', '数据库原理与应用',     '12', 3.5, 56.00, 'ORG001', '01'),
  ('CS202003', 'CS202003', 'Java企业级开发',       '14', 2.5, 40.00, 'ORG002', '01'),
  ('GE101001', 'GE101001', '大学英语（三）',       '11', 2.0, 32.00, 'ORG001', '01'),
  ('PR301001', 'PR301001', '信息系统开发实践',     '16', 2.0, 32.00, 'ORG001', '01'),
  ('GR501001', 'GR501001', '高级管理科学研究方法', '12', 3.0, 48.00, 'ORG001', '01'),
  ('GR502002', 'GR502002', '数据挖掘与商务智能',   '14', 2.0, 32.00, 'ORG001', '01')
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name);

INSERT INTO aca_course_id (course_id, course_name, course_type, credit, total_hours, assess_type, open_college, course_status) VALUES
  ('CS201001', '管理信息系统',         '12', 3.0, 48.00, '1', '0001', '1'),
  ('CS201002', '数据库原理与应用',     '12', 3.5, 56.00, '1', '0001', '1'),
  ('CS202003', 'Java企业级开发',       '14', 2.5, 40.00, '1', '0002', '1'),
  ('GE101001', '大学英语（三）',       '11', 2.0, 32.00, '1', '0001', '1'),
  ('PR301001', '信息系统开发实践',     '16', 2.0, 32.00, '2', '0001', '1'),
  ('GR501001', '高级管理科学研究方法', '12', 3.0, 48.00, '1', '0001', '1'),
  ('GR502002', '数据挖掘与商务智能',   '14', 2.0, 32.00, '1', '0001', '1')
ON DUPLICATE KEY UPDATE assess_type = VALUES(assess_type);

INSERT INTO aca_program_course (major_id, grade, program_type, course_id, course_type, credit, hours, open_college, open_semester, cross_status) VALUES
  ('120102', '2024', '1', 'CS201001', '12', 3.0, 48.00, '0001', 5, '0'),
  ('120102', '2024', '1', 'CS201002', '12', 3.5, 56.00, '0001', 5, '0'),
  ('120102', '2024', '1', 'PR301001', '16', 2.0, 32.00, '0001', 5, '0'),
  ('080901', '2024', '1', 'CS202003', '14', 2.5, 40.00, '0002', 5, '0')
ON DUPLICATE KEY UPDATE credit = VALUES(credit);

INSERT INTO aca_teaching_class (tc_id, term, course_id, emp_id, class_name, class_ids, capacity, selected_count, actual_num, hours, student_num, tc_type, teach_type, tc_status, week_count) VALUES
  ('TC20261001', '2026-2027-1', 'CS201001', 'T000010001', '管理信息系统-01班', '信管241',         60, 3, 3, 48.00, 30, '1', '1', '4', 16),
  ('TC20261002', '2026-2027-1', 'CS201001', 'T000010001', '管理信息系统-02班', '信管242',         60, 2, 2, 48.00, 30, '1', '1', '4', 16),
  ('TC20261003', '2026-2027-1', 'CS201002', 'T000010002', '数据库原理-01班',   '信管241,信管242', 80, 2, 2, 56.00, 60, '1', '1', '4', 16),
  ('TC20261004', '2026-2027-1', 'CS202003', 'T000020001', 'Java企业级-01班',   '全校选修',        50, 1, 1, 40.00, 50, '2', '1', '4', 16),
  ('TC20261005', '2026-2027-1', 'PR301001', 'T000010001', '开发实践-01班',     '信管241',         40, 2, 2, 32.00, 30, '1', '2', '4',  8),
  ('TC20261006', '2026-2027-1', 'GR501001', 'T000010002', '管理研究方法-01班', '管科硕261',       30, 2, 2, 48.00, 20, '1', '1', '4', 16),
  ('TC20261007', '2026-2027-1', 'GR502002', 'T000020001', '数据挖掘-01班',     '管科硕261',       30, 0, 0, 32.00, 20, '2', '1', '4', 16),
  ('TC20261008', '2026-2027-1', 'GE101001', 'T000010002', '大学英语-01班',     '信管241',         45, 0, 0, 32.00, 30, '1', '1', '2', 16)
ON DUPLICATE KEY UPDATE capacity = VALUES(capacity);

INSERT INTO aca_schedule_result (sched_id, tc_id, week_range, week_day, start_slot, end_slot, room_id, lock_flag, sched_status) VALUES
  ('SCH20261000001', 'TC20261001', '1-16', '1', 1, 2, '经管楼301',    1, '1'),
  ('SCH20261000002', 'TC20261002', '1-16', '1', 1, 2, '经管楼301',    1, '1'),
  ('SCH20261000003', 'TC20261003', '1-16', '2', 3, 4, '经管楼205',    1, '1'),
  ('SCH20261000004', 'TC20261004', '1-16', '1', 1, 2, '逸夫楼402',    1, '1'),
  ('SCH20261000005', 'TC20261005', '9-16', '3', 5, 6, '信息实验楼B201', 1, '1'),
  ('SCH20261000006', 'TC20261006', '1-16', '4', 1, 3, '经管楼502',    1, '1'),
  ('SCH20261000007', 'TC20261007', '1-16', '5', 3, 4, '逸夫楼308',    1, '1')
ON DUPLICATE KEY UPDATE room_id = VALUES(room_id);

INSERT INTO grad_direction (direction_id, direction_name) VALUES
  ('DIR01', '信息管理与大数据分析'),
  ('DIR02', '智能供应链与运营管理')
ON DUPLICATE KEY UPDATE direction_name = VALUES(direction_name);

INSERT INTO grad_scheme_assignment (assignment_id, student_id, scheme_id, direction_id, method, valid_from, valid_to) VALUES
  ('ASN_M202610001_SCH_MS_2026', 'M202610001', 'SCH_MS_2026', 'DIR01', 'DEFAULT', '2026-09-01', NULL),
  ('ASN_M202610002_SCH_MS_2026', 'M202610002', 'SCH_MS_2026', 'DIR02', 'DEFAULT', '2026-09-01', NULL)
ON DUPLICATE KEY UPDATE direction_id = VALUES(direction_id);

INSERT INTO grad_scheme_course (scheme_course_id, scheme_id, course_id, direction_id, group_code, course_category, suggest_term) VALUES
  ('GSC_SCH_MS_2026_GR501001', 'SCH_MS_2026', 'GR501001', NULL,    'CORE', 1, 1),
  ('GSC_SCH_MS_2026_GR502002', 'SCH_MS_2026', 'GR502002', 'DIR01', 'DIR',  2, 1)
ON DUPLICATE KEY UPDATE course_category = VALUES(course_category);

-- 第 15 组交换生统一表数据（支撑 v_exchange_student 与 v_exchange_credit_ready 视图）
INSERT INTO exg_application (apply_id, person_id, student_name, id_card_no, home_school, home_student_no, apply_college_name, apply_major_name, apply_term, apply_status) VALUES
  ('EXAPP20260001', 'P_EX20260001', 'Alex Mueller', 'DE2026000000000001', 'RWTH Aachen University', 'RWTH2026001', '经济管理学院', '信息管理与信息系统', '2026-2027-1', '01')
ON DUPLICATE KEY UPDATE home_school = VALUES(home_school);

INSERT INTO exg_admission (admission_id, apply_id, person_id, student_name, college_name, major_name, term, interview_score, approver_name, admission_time, admission_status) VALUES
  ('EXADM20260001', 'EXAPP20260001', 'P_EX20260001', 'Alex Mueller', '经济管理学院', '信息管理与信息系统', '2026-2027-1', 92.00, '教务处', '2026-07-01', '01')
ON DUPLICATE KEY UPDATE admission_status = VALUES(admission_status);

INSERT INTO exg_temp_student (temp_student_id, admission_id, person_id, student_name, id_card_no, org_id, college_name, major_name, start_date, end_date, student_status, creator_name) VALUES
  ('EX20260001', 'EXADM20260001', 'P_EX20260001', 'Alex Mueller', 'DE2026000000000001', 'ORG001', '经济管理学院', '信息管理与信息系统', '2026-09-01', '2027-01-31', '01', '国际处')
ON DUPLICATE KEY UPDATE student_status = VALUES(student_status);

INSERT INTO exg_vst_departure (departure_id, apply_id, student_id, host_country, partner_school, departure_date, planned_return_date, actual_return_date, return_status) VALUES
  ('DEP20260001', 'SQ20260001', 'U202440001', '新加坡', '新加坡国立大学 (NUS)', '2026-09-01', '2026-11-15', '2026-11-14', '02'),
  ('DEP20260002', 'SQ20260002', 'U202440002', '德国',   '慕尼黑工业大学 (TUM)', '2026-09-01', '2027-01-15', NULL,         '01')
ON DUPLICATE KEY UPDATE partner_school = VALUES(partner_school);

INSERT INTO exg_vst_return_report (report_id, departure_id, student_id, actual_return_date, report_date, return_apply_file, report_note) VALUES
  ('REP20260001', 'DEP20260001', 'U202440001', '2026-11-14', '2026-11-15', 'return_sq20260001.pdf', '已提交外校官方成绩单'),
  ('REP20260002', 'DEP20260002', 'U202440002', '2027-01-15', '2027-01-16', 'return_sq20260002.pdf', '待返校核验')
ON DUPLICATE KEY UPDATE report_note = VALUES(report_note);

INSERT INTO exg_vst_return_filing (return_filing_id, report_id, student_id, actual_return_date, filing_date, filer, filing_status) VALUES
  ('FIL20260001', 'REP20260001', 'U202440001', '2026-11-14', '2026-11-16', '国际处', '01'),
  ('FIL20260002', 'REP20260002', 'U202440002', '2027-01-15', '2027-01-16', '国际处', '00')
ON DUPLICATE KEY UPDATE filing_status = VALUES(filing_status);

-- 1.2 全班跨组深化接入演示数据（第1/2/4/5/7/9/13/15组）
-- 第 1 组：新学期学籍注册、辅修/双学位资格、转专业记录
INSERT INTO std_registration (student_id, term, student_name, college_name, org_id, class_name, enrollment_status, payment_status, info_confirm, registration_status, registration_time, approver_id, abnormal_reason) VALUES
  ('U202440001', '2026-2027-1', '张晨曦', '经济管理学院',       'ORG001', '信管241',   '01', '01', '已确认', '01', '2026-08-30', 'E000010001', NULL),
  ('U202440002', '2026-2027-1', '林思远', '经济管理学院',       'ORG001', '信管241',   '01', '02', '已确认', '01', '2026-08-30', 'E000010001', NULL),
  ('U202440003', '2026-2027-1', '陈雨桐', '经济管理学院',       'ORG001', '信管242',   '01', '01', '待确认', '02', '2026-08-31', 'E000010001', '尚未完成本学期学籍报到注册，请先联系学院学籍办补办注册'),
  ('U202340004', '2026-2027-1', '刘浩然', '经济管理学院',       'ORG001', '信管231',   '01', '01', '已确认', '01', '2026-08-30', 'E000010001', NULL),
  ('U202440005', '2026-2027-1', '赵子涵', '计算机与通信工程学院', 'ORG002', '计科241',   '01', '01', '已确认', '01', '2026-08-30', 'E000020001', NULL),
  ('M202610001', '2026-2027-1', '许嘉豪', '经济管理学院',       'ORG001', '管科硕261', '01', '01', '已确认', '01', '2026-08-30', 'G000000001', NULL),
  ('M202610002', '2026-2027-1', '宋婉清', '经济管理学院',       'ORG001', '管科硕261', '01', '01', '已确认', '01', '2026-08-30', 'G000000001', NULL)
ON DUPLICATE KEY UPDATE registration_status = VALUES(registration_status), abnormal_reason = VALUES(abnormal_reason);

INSERT INTO std_minor_degree (apply_id, person_id, student_name, apply_category, target_major, grade_qualification, score_qualification, minor_review_result, minor_review_opinion, college_result, college_opinion, final_result, final_opinion, apply_status, submitted_at) VALUES
  ('MD20260001', 'P_U202440005', '赵子涵', '辅修', '信息管理与信息系统', '符合二年级要求', 86.50, '通过', '成绩与学分条件达标', '通过', '同意接收辅修修读', '通过', '准予修读辅修专业', '03', '2026-05-12 10:00:00')
ON DUPLICATE KEY UPDATE apply_status = VALUES(apply_status), final_result = VALUES(final_result);

INSERT INTO std_major_transfer (student_id, application_term, current_major_id, target_major_id, weighted_average_score, current_major_rank, current_major_rank_total, target_major_quota, target_major_applicant_count, application_status, admission_result, submitted_at, decision_date, effective_term, student_record_updated) VALUES
  ('U202340004', '2024-2025-2', '080901', '120102', 84.60, 12, 68, 15, 22, '03', '已录取', '2025-05-15 09:00:00', '2025-06-18', '2025-2026-1', 1)
ON DUPLICATE KEY UPDATE admission_result = VALUES(admission_result);

-- 第 2 组：培养方案总纲、通识计划、调停课记录、排课冲突、双学位录取、转专业学分转换
INSERT INTO aca_major_program (major_id, grade, program_type, version, total_credit, credit_rule, goal_desc, plan_status, dual_quota, published_at, review_opinion) VALUES
  ('120102', '2024', '1', '2024版', 160.0, '11:2.0;12:6.5;16:2.0', '培养具备现代管理理论基础与信息系统分析、设计及实施能力的复合型人才', '2', 20, '2024-06-20', '校教学委员会审议通过'),
  ('080901', '2024', '1', '2024版', 165.0, '14:2.5', '培养具备扎实计算机系统基础与工程开发能力的高素质专门人才', '2', 25, '2024-06-20', '校教学委员会审议通过')
ON DUPLICATE KEY UPDATE credit_rule = VALUES(credit_rule), goal_desc = VALUES(goal_desc);

INSERT INTO aca_general_plan (school_year, course_id, plan_category, suggest_hours, undertake_college, emp_id, confirm_status, plan_status) VALUES
  ('2026-2027', 'GE101001', '外语能力与跨文化沟通', 32, '0001', 'T000010002', '1', '2'),
  ('2026-2027', 'CS202003', '科学思维与信息技术',   40, '0002', 'T000020001', '1', '2')
ON DUPLICATE KEY UPDATE plan_category = VALUES(plan_category);

INSERT INTO aca_adjust_record (adjust_id, sched_id, adjust_type, old_value, new_value, reason, approve_status, notify_status, restore_flag, operated_at, valid_from, approver, approve_opinion) VALUES
  ('ADJ202610001', 'SCH20261000003', '临时调时间', '1-16周 周二 3-4节 经管楼205', '第10周调至周五 5-6节 经管楼205', '第10周周二学院承办学术论坛，经教务处审批顺延至周五补课', '1', '1', 0, '2026-11-10', '2026-11-12', 'A000000001', '同意调课，请及时通知班级学生')
ON DUPLICATE KEY UPDATE new_value = VALUES(new_value), approve_status = VALUES(approve_status);

INSERT INTO aca_schedule_conflict (conflict_id, tc_id, other_tc_id, conflict_type, conflict_desc, suggest_plan, found_time, handle_status) VALUES
  ('CNF202610001', 'TC20261007', 'TC20261004', '场地衔接预警', '逸夫楼308周五3-4节与前序实验课转场时间较紧', '建议提前确认逸夫楼308多媒体设备与课间预留', '2026-11-15', '0')
ON DUPLICATE KEY UPDATE conflict_desc = VALUES(conflict_desc), handle_status = VALUES(handle_status);

INSERT INTO aca_dual_enroll (enroll_id, student_id, major_id, enroll_year, apply_date, audit_status, enroll_date, finish_status, remark) VALUES
  ('DUAL20260001', 'U202440005', '120102', '2025-2026', '2025-05-10', '1', '2025-06-15', '1', '经教务处核准修读信息管理与信息系统辅修/双学位')
ON DUPLICATE KEY UPDATE audit_status = VALUES(audit_status);

INSERT INTO aca_credit_apply (apply_id, student_id, from_major, to_major, apply_credit, apply_date, audit_status, college_opinion, approver, audit_date, audit_opinion) VALUES
  ('CRA20250001', 'U202340004', '080901', '120102', 6.0, '2025-09-05', '2', '原专业计算机课程与本专业培养方案匹配度高', 'A000000001', '2025-09-10', '同意认定转换 6.0 学分')
ON DUPLICATE KEY UPDATE audit_status = VALUES(audit_status);

INSERT INTO aca_credit_item (apply_id, seq_no, source_course_id, source_credit, target_course_id, target_credit, transfer_type, item_status) VALUES
  ('CRA20250001', 1, 'CS202003', 2.5, 'CS202003', 2.5, '01', '1'),
  ('CRA20250001', 2, 'CS201002', 3.5, 'CS201002', 3.5, '01', '1')
ON DUPLICATE KEY UPDATE item_status = VALUES(item_status);

-- 第 4 组：教职工请假与因公出国提醒（设置在陈志强，不影响王明远主流程）
INSERT INTO hr_leave (leave_id, emp_id, leave_type, start_at, end_at, days, reason, approve_level, leave_status) VALUES
  ('LV2026110001', 'T000020001', '公差', '2026-12-01', '2026-12-04', 4.00, '赴上海参加全国高校计算机教育教学研讨会', '学院审批', '02')
ON DUPLICATE KEY UPDATE leave_status = VALUES(leave_status);

INSERT INTO hr_abroad (abroad_id, emp_id, abroad_type, country, start_date, end_date, approve_doc, apply_status) VALUES
  ('ABR2026110001', 'T000020001', '短期学术会议', '日本', '2026-12-18', '2026-12-23', '北科外批[2026]118号', '02')
ON DUPLICATE KEY UPDATE apply_status = VALUES(apply_status);

-- 第 5 组：财务收费项目与学生缴费/欠费台账（林思远欠费 1200 元用于演示欠费提醒与开关拦截）
INSERT INTO fin_charge_item (charge_item_id, charge_item_name, charge_type, standard_amount, charge_item_status) VALUES
  ('FEE_TUIT', '本科学费',   '01', 5000.00, '01'),
  ('FEE_DORM', '住宿费',     '02', 1200.00, '01'),
  ('FEE_GRAD', '研究生学费', '01', 8000.00, '01')
ON DUPLICATE KEY UPDATE charge_item_name = VALUES(charge_item_name);

INSERT INTO fin_charge (charge_no, student_id, charge_item, charge_period, receivable_amount, received_amount, arrears_amount, pay_method, invoice_no, income_no, audit_status) VALUES
  ('CHG20260001', 'U202440001', 'FEE_TUIT', '2026-2027-1', 5000.00, 5000.00,    0.00, '线上缴费',     'INV20260001', 'INC20260001', '01'),
  ('CHG20260002', 'U202440002', 'FEE_TUIT', '2026-2027-1', 5000.00, 3800.00, 1200.00, '绿色通道缓缴', NULL,          NULL,          '01'),
  ('CHG20260003', 'U202440002', 'FEE_DORM', '2026-2027-1', 1200.00, 1200.00,    0.00, '线上缴费',     'INV20260003', 'INC20260003', '01'),
  ('CHG20260004', 'U202340004', 'FEE_TUIT', '2026-2027-1', 5000.00, 5000.00,    0.00, '线上缴费',     'INV20260004', 'INC20260004', '01'),
  ('CHG20260005', 'M202610001', 'FEE_GRAD', '2026-2027-1', 8000.00, 8000.00,    0.00, '线上缴费',     'INV20260005', 'INC20260005', '01')
ON DUPLICATE KEY UPDATE arrears_amount = VALUES(arrears_amount), received_amount = VALUES(received_amount);

-- 第 7 组：教学楼栋与教室场地资产（逸夫楼402容量45 < TC20261004容量50，演示自动超容预警）
INSERT INTO ast_building (building_id, building_name, building_type, campus_id, floors, org_id, address, building_status) VALUES
  ('BLD_JG', '经济管理楼', '01', 'CAMP01', 6, 'ORG001', '学院路校区教学区东侧',   '1'),
  ('BLD_YF', '逸夫教学楼', '01', 'CAMP01', 8, 'ORG900', '学院路校区中心广场北侧', '1'),
  ('BLD_SY', '信息实验楼', '02', 'CAMP01', 5, 'ORG002', '学院路校区实验区西侧',   '1')
ON DUPLICATE KEY UPDATE building_name = VALUES(building_name);

INSERT INTO aca_room (room_id, room_code, room_name, building_id, floor, room_number, room_type, room_subtype, lab_category, capacity, has_projector, computer_num, blackboard_type, equip_desc, org_id, room_status) VALUES
  ('RM_JG301',   'JG-301',  '经管楼301',    'BLD_JG', 3, '301',  '01', '0101', NULL,   120, '1',  0, '02', '高清投影仪、双屏互动白板、无线扩音', 'ORG001', '01'),
  ('RM_JG205',   'JG-205',  '经管楼205',    'BLD_JG', 2, '205',  '01', '0101', NULL,    90, '1',  0, '02', '多媒体讲台、双投影仪、录播设备',     'ORG001', '01'),
  ('RM_YF402',   'YF-402',  '逸夫楼402',    'BLD_YF', 4, '402',  '01', '0102', NULL,    45, '1',  0, '01', '智慧黑板、激光投影仪',               'ORG900', '01'),
  ('RM_LABB201', 'SY-B201', '信息实验楼B201','BLD_SY', 2, 'B201', '02', '0201', 'CS01',  48, '1', 48, '02', '48台高性能实验工作站、教学实验网',   'ORG002', '01'),
  ('RM_JG502',   'JG-502',  '经管楼502',    'BLD_JG', 5, '502',  '01', '0103', NULL,    40, '1',  0, '02', '研讨型多媒体教室、互动白板',         'ORG001', '01'),
  ('RM_YF308',   'YF-308',  '逸夫楼308',    'BLD_YF', 3, '308',  '01', '0102', NULL,    40, '1',  0, '01', '标准多媒体投影教室',                 'ORG900', '01')
ON DUPLICATE KEY UPDATE capacity = VALUES(capacity), equip_desc = VALUES(equip_desc);

-- 第 9 组：实验室台账、临时封闭提醒与课程教学预约（关联 TC20261005 信息系统开发实践）
INSERT INTO lab_laboratory (lab_id, lab_name, room_id, org_id, manager_emp_id, capacity, open_time, close_time, lab_status, description, building, floor, room_number, location, lab_type, manager_id) VALUES
  ('LAB_B201', '信息系统与数据库综合教学实验室', 'RM_LABB201', 'ORG002', 'T000020001', 48, '08:00', '21:30', '01', '承接本科信息系统开发实践、数据库实验与课程设计上机教学', '信息实验楼', 2, 'B201', '信息实验楼B座二层201室', 'teaching', 1001)
ON DUPLICATE KEY UPDATE lab_name = VALUES(lab_name), open_time = VALUES(open_time), close_time = VALUES(close_time);

INSERT INTO lab_closure (closure_id, lab_id, start_at, end_at, reason, closure_status, created_by) VALUES
  (1, 'LAB_B201', '2026-12-06 08:00', '2026-12-06 18:00', '实验台服务器例行固件升级与安全巡检（周日维护，不影响周三正常上机）', '01', '1001')
ON DUPLICATE KEY UPDATE reason = VALUES(reason), closure_status = VALUES(closure_status);

INSERT INTO lab_reservation (reservation_id, lab_id, equipment_id, user_id, date, start_at, end_at, purpose, participants, reservation_status, source_type, course_id, course_name, course_teacher, week_no, active_slot_lock) VALUES
  (1, 'LAB_B201', NULL, 1001, '2026-11-25', '14:00', '15:40', '信息系统开发实践第13周上机实验', 30, '01', 'teaching_schedule', 'PR301001', '信息系统开发实践', '王明远', 13, 1)
ON DUPLICATE KEY UPDATE purpose = VALUES(purpose);

-- 第 13 组：研究生培养方案总表、分类学分要求、个人培养计划、导师指导关系
INSERT INTO grad_training_scheme (scheme_id, major_id, grade, level, college_id, version_no, min_credit, max_credit, publish_status, application_id) VALUES
  ('SCH_MS_2026', '120100', '2026', '硕士', '0001', '2026版', 34.00, 42.00, 'PUBLISHED', 'APP_SCH_2026')
ON DUPLICATE KEY UPDATE min_credit = VALUES(min_credit), max_credit = VALUES(max_credit);

INSERT INTO grad_scheme_requirement (scheme_line_id, scheme_id, group_code, min_credit, max_credit, min_count) VALUES
  ('REQ_MS_CORE', 'SCH_MS_2026', 'CORE', 14.00, 20.00, 4),
  ('REQ_MS_DIR',  'SCH_MS_2026', 'DIR',  10.00, 16.00, 3),
  ('REQ_MS_PRAC', 'SCH_MS_2026', 'PRAC',  4.00,  6.00, 1)
ON DUPLICATE KEY UPDATE min_credit = VALUES(min_credit);

INSERT INTO grad_training_plan (plan_id, student_id, scheme_id, version_no, plan_status, application_id) VALUES
  ('PLAN_M202610001', 'M202610001', 'SCH_MS_2026', 1, 'APPROVED', 'APP_PLAN_001'),
  ('PLAN_M202610002', 'M202610002', 'SCH_MS_2026', 1, 'APPROVED', 'APP_PLAN_002')
ON DUPLICATE KEY UPDATE plan_status = VALUES(plan_status);

INSERT INTO grad_plan_course (plan_line_id, plan_id, course_id, course_source, planned_term, credit_snapshot) VALUES
  ('PL_M1_C1', 'PLAN_M202610001', 'GR501001', 'SCHEME', '2026-2027-1', 3.00),
  ('PL_M1_C2', 'PLAN_M202610001', 'GR502002', 'SCHEME', '2026-2027-1', 2.00),
  ('PL_M2_C1', 'PLAN_M202610002', 'GR501001', 'SCHEME', '2026-2027-1', 3.00)
ON DUPLICATE KEY UPDATE planned_term = VALUES(planned_term);

INSERT INTO grad_student_mentor (student_mentor_id, student_id, mentor_user_id, is_primary, valid_from, valid_to) VALUES
  ('GSM_M202610001', 'M202610001', 'T000010001', 1, '2026-09-01', NULL),
  ('GSM_M202610002', 'M202610002', 'T000010002', 1, '2026-09-01', NULL)
ON DUPLICATE KEY UPDATE mentor_user_id = VALUES(mentor_user_id);

-- 第 15 组：国内访学返校链、外校原始成绩单、来华留学生、接收导师意见、教师因公出访状态
INSERT INTO exg_dv_filing (filing_id, student_id, host_school, visit_location, start_date, planned_return_date, filing_date, filing_status, visit_reason) VALUES
  ('DVFIL20260001', 'U202440003', '清华大学经济管理学院', '北京', '2026-09-01', '2026-11-10', '2026-08-25', '01', '校际本科生国内访学交流')
ON DUPLICATE KEY UPDATE host_school = VALUES(host_school);

INSERT INTO exg_dv_return_report (report_id, filing_id, student_id, actual_return_date, report_date, report_note) VALUES
  ('DVREP20260001', 'DVFIL20260001', 'U202440003', '2026-11-10', '2026-11-11', '已提交清华大学课程成绩单原件')
ON DUPLICATE KEY UPDATE report_note = VALUES(report_note);

INSERT INTO exg_dv_return_filing (return_filing_id, report_id, student_id, actual_return_date, filing_date, filer, filing_status) VALUES
  ('DVRFL20260001', 'DVREP20260001', 'U202440003', '2026-11-10', '2026-11-12', '教务处国内合作科', '01')
ON DUPLICATE KEY UPDATE filing_status = VALUES(filing_status);

INSERT INTO exg_vst_foreign_transcript (foreign_transcript_id, student_id, partner_school, study_term, foreign_course_id, foreign_course_name, foreign_credit, foreign_score, issue_date) VALUES
  ('FT20260001', 'U202440001', '新加坡国立大学 (NUS)', '2026-2027-1', 'IS3103', 'Information Systems Leadership', 4.0, 90.00, '2026-11-12'),
  ('FT20260002', 'U202440001', '新加坡国立大学 (NUS)', '2026-2027-1', 'BT3103', 'Application Systems Development', 4.0, 88.50, '2026-11-12')
ON DUPLICATE KEY UPDATE foreign_score = VALUES(foreign_score);

INSERT INTO exg_is_student (student_id, person_name, nationality, passport_no, college_name, org_id, major_name, major_id, class_name, degree_level, enroll_date, expected_graduation_date, scholarship_program, register_status, student_status) VALUES
  ('IS20260001', 'Sofia Lindqvist', '瑞典', 'SE2026998811', '计算机与通信工程学院', 'ORG002', '计算机科学与技术', '080901', '计科留261', '本科', '2026-09-01', '2030-06-30', '中国政府奖学金', '01', '01')
ON DUPLICATE KEY UPDATE nationality = VALUES(nationality), scholarship_program = VALUES(scholarship_program);

INSERT INTO exg_supervisor_opinion (opinion_id, apply_id, emp_id, emp_name, college_name, org_id, accept_opinion, course_plan, submitted_at) VALUES
  ('OPN20260001', 'EXAPP20260001', 'T000010001', '王明远', '经济管理学院', 'ORG001', '同意接收该交流生来院交流学习', '建议本学期选修《Java企业级开发》《管理信息系统》及实践环节课程，总学分控制在 8-12 学分', '2026-06-25 14:30:00')
ON DUPLICATE KEY UPDATE course_plan = VALUES(course_plan);

INSERT INTO exg_ob_teacher_status (status_id, emp_id, trip_id, current_status, effective_time, end_at, sync_status, sync_time, sync_error) VALUES
  ('OBS202611001', 'T000020001', 'TRIP20261218001', '02', '2026-12-18', '2026-12-23', '01', '2026-11-20', NULL)
ON DUPLICATE KEY UPDATE current_status = VALUES(current_status);

-- ---------------------------------------------------------------------
-- 2. 业务① 选课准备与发布（sel_select_round、sel_round_class、sel_select_limit）
-- ---------------------------------------------------------------------
INSERT INTO sel_select_round (
  round_id, term, round_name, round_type, target_level, start_at, end_at,
  max_credit, select_mode, allow_retake, retake_ignore_conflict, round_status,
  notice_content, published_at, approver_id, approved_at, approve_opinion
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

INSERT INTO sel_round_class (round_id, tc_id) VALUES
  (1, 'TC20261001'), (1, 'TC20261002'), (1, 'TC20261003'), (1, 'TC20261004'), (1, 'TC20261005'),
  (2, 'TC20261001'), (2, 'TC20261002'), (2, 'TC20261003'), (2, 'TC20261004'), (2, 'TC20261006'), (2, 'TC20261007'),
  (3, 'TC20261006'), (3, 'TC20261007'),
  (4, 'TC20261001');

INSERT INTO sel_select_limit (limit_id, tc_id, limit_type, limit_value) VALUES
  (1, 'TC20261004', 1, '2024'),
  (2, 'TC20261004', 4, '本科'),
  (3, 'TC20261007', 4, '研究生'),
  (4, 'TC20261007', 3, '0001');

-- ---------------------------------------------------------------------
-- 3. 业务② 学生选课（sel_sc、sel_select_log）+ 合班课堂（sel_lesson_class）
-- ---------------------------------------------------------------------
INSERT INTO sel_lesson_class (tc_id, lesson_no) VALUES
  ('TC20261001', 'KT202610001'),
  ('TC20261002', 'KT202610001'), -- 两班合并为同一大课堂 KT202610001
  ('TC20261003', 'KT202610002'),
  ('TC20261004', 'KT202610003'),
  ('TC20261005', 'KT202610004'),
  ('TC20261006', 'KT202610005'),
  ('TC20261007', 'KT202610006');

INSERT INTO sel_sc (sc_id, student_id, tc_id, round_id, study_type, is_exempt_attend, stu_level, stat_stage, priority, select_status, select_time, drop_time) VALUES
  (1,  'U202440001', 'TC20261001', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:05:00', NULL), -- 初修
  (2,  'U202440002', 'TC20261001', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:08:00', NULL), -- 初修
  (3,  'U202440003', 'TC20261002', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:12:00', NULL), -- 同合班课堂另一行政班
  (4,  'U202340004', 'TC20261002', 1, 2, 1, '本科',   1, 1, 1, '2026-09-01 10:00:00', NULL), -- 重修 + 冲突免听
  (5,  'U202440005', 'TC20261001', 2, 3, 0, '本科',   1, NULL, 1, '2026-11-20 09:30:00', NULL), -- 辅修（3）
  (6,  'U202440001', 'TC20261003', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:06:00', NULL),
  (7,  'U202440002', 'TC20261003', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:09:00', NULL),
  (8,  'U202440001', 'TC20261005', 1, 1, 0, '本科',   1, 1, 1, '2026-09-01 09:15:00', NULL),
  (9,  'EX20260001', 'TC20261004', 2, 1, 0, '本科',   1, NULL, 1, '2026-11-20 10:10:00', NULL), -- 来校交换生
  (10, 'M202610001', 'TC20261006', 2, 1, 0, '研究生', 2, NULL, 1, '2026-11-20 10:20:00', NULL), -- 研究生
  (11, 'M202610002', 'TC20261006', 2, 1, 0, '研究生', 2, NULL, 1, '2026-11-20 10:25:00', NULL);

INSERT INTO sel_select_log (log_id, student_id, tc_id, round_id, action_type, operator_id, operated_at, remark) VALUES
  (1, 'U202440001', 'TC20261001', 1, 1, 'U202440001', '2026-09-01 09:05:00', NULL),
  (2, 'U202340004', 'TC20261002', 1, 1, 'U202340004', '2026-09-01 10:00:00', '重修冲突免听选课'),
  (3, 'U202440005', 'TC20261001', 2, 4, 'A000000001', '2026-11-20 09:30:00', '辅修双学位补加进入合班课堂');

-- ---------------------------------------------------------------------
-- 4. 业务③ 成绩录入与审核（sel_score、sel_score_audit、sel_exchange_score_convert）
-- ---------------------------------------------------------------------
INSERT INTO sel_score (
  score_id, sc_id, exam_type, exam_flag, score_type, usual_ratio,
  usual_score, exam_score, total_score, score_level, grade_point,
  is_final, score_status, input_teacher_id, submitted_at, published_at
) VALUES
  (1, 1, 1, 0, 1, 30.00, 92.00, 88.00, 89.20, NULL,   3.70, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (2, 2, 1, 0, 1, 30.00, 85.00, 79.00, 80.80, NULL,   3.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (3, 3, 1, 0, 1, 30.00, 95.00, 93.00, 93.60, NULL,   4.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (4, 4, 1, 0, 1, 30.00, 72.00, 68.00, 69.20, NULL,   2.00, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  (5, 5, 1, 0, 1, 30.00, 90.00, 86.00, 87.20, NULL,   3.70, 1, 3, 'T000010001', '2026-11-18 10:00:00', '2026-11-19 09:00:00'),
  -- 课堂 KT202610002（含 sc_id 6,7）：已提交待学院核对（score_status=1，配随机码 K7M9P2）
  (6, 6, 1, 0, 1, 40.00, 88.00, 84.00, 85.60, NULL,   3.70, 0, 1, 'T000010002', '2026-11-22 15:00:00', NULL),
  (7, 7, 1, 0, 1, 40.00, 76.00, 70.00, 72.40, NULL,   2.40, 0, 1, 'T000010002', '2026-11-22 15:00:00', NULL),
  -- 课堂 KT202610004（含 sc_id 8）：五级制已发布（演示等级制更正）
  (8, 8, 1, 0, 2, 40.00, NULL, NULL, NULL, '良',   3.40, 1, 3, 'T000010001', '2026-11-18 11:00:00', '2026-11-19 09:30:00'),
  -- 课堂 KT202610003（来校交换生 sc_id 9）：已发布
  (9, 9, 1, 0, 1, 30.00, 91.00, 89.00, 89.60, NULL,   3.70, 1, 3, 'T000020001', '2026-11-20 14:00:00', '2026-11-21 10:00:00');

INSERT INTO sel_score_audit (audit_id, lesson_no, submitted_at, verify_code, auditor_id, audit_result, audit_opinion, reviewed_at) VALUES
  (1, 'KT202610001', '2026-11-18 10:00:00', 'A3H8W5', 'E000010001', 1, '核对纸质成绩单无误', '2026-11-18 16:00:00'),
  (2, 'KT202610002', '2026-11-22 15:00:00', 'K7M9P2', NULL,         0, NULL,                 NULL),
  (3, 'KT202610004', '2026-11-18 11:00:00', 'M4N6Q9', 'E000010001', 1, '核对通过',           '2026-11-18 16:30:00'),
  (4, 'KT202610003', '2026-11-20 14:00:00', 'R5T8Y2', 'E000020001', 1, '核对通过',           '2026-11-20 17:00:00');

-- 校外交流成绩换算认定：一条待教务处审批示例
INSERT INTO sel_exchange_score_convert (
  convert_id, student_id, exchange_apply_id, term, stat_stage, host_school,
  ext_course_code, ext_course_name, ext_credit, ext_score_raw, ext_scale_type,
  course_id, score_type, converted_score, converted_level, grade_point, is_final,
  proof_no, proof_file_id, version_no, audit_status, submitted_at,
  college_auditor_id, college_audit_opinion, college_audit_time,
  school_auditor_id, school_audit_opinion, school_audit_time
) VALUES
  (1, 'U202440001', 'SQ20260001', '2026-2027-1', 1, '新加坡国立大学 (NUS)',
   'IS3103', 'Information Systems Leadership', 4.0, 'A-', 2,
   'CS202003', 1, 90.00, NULL, 4.00, 0,
   'NUS-TR-2026-8821', 'FILE_EX_8821', 1, 1, '2026-11-21 10:00:00',
   'E000010001', '课程大纲匹配度达90%，建议按90分认定', '2026-11-22 09:30:00',
   NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- 5. 业务⑤ 成绩更正申请（sel_score_correction）
-- ---------------------------------------------------------------------
INSERT INTO sel_score_correction (
  apply_id, score_id, apply_teacher_id, old_score, new_score, old_level, new_level,
  apply_reason, submitted_at, apply_status,
  college_auditor_id, college_audit_opinion, college_audit_time,
  auditor_id, audit_opinion, reviewed_at
) VALUES
  -- 待学院审核（status=0）
  (1, 2, 'T000010001', 80.80, 84.80, NULL, NULL,
   '复核实验报告得分时漏加第4次实验附加分，经核查原始卷面应为84.8分',
   '2026-11-21 14:20:00', 0, NULL, NULL, NULL, NULL, NULL, NULL),
  -- 待学院审批（status=0，等级制示例）
  (2, 8, 'T000010001', NULL, NULL, '良', '优',
   '答辩评分汇总表误录，该生小组综合答辩为92分应评为优',
   '2026-11-20 11:00:00', 0, NULL, NULL, NULL, NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- 6. 业务④ 评教与学业统计快照（sel_teach_eval、sel_tc_eval_result、sel_stu_gpa、sel_stu_term_gpa）
-- ---------------------------------------------------------------------
INSERT INTO sel_teach_eval (sc_id, eval_score, eval_comment, eval_time) VALUES
  (1, 95.00, '课程案例贴合实际，讲解非常清晰', '2026-11-19 10:30:00'),
  (3, 92.00, '课堂互动很好',                   '2026-11-19 11:00:00'),
  (8, 96.00, '实践指导非常细致',               '2026-11-19 10:35:00');
-- 注意：U202440002（sc_id=2）故意暂未评教，用于演示“先评教后查分”拦截！

INSERT INTO sel_tc_eval_result (tc_id, eval_num, avg_eval_score, calc_time) VALUES
  ('TC20261001', 1, 95.00, '2026-11-20 08:00:00'),
  ('TC20261002', 1, 92.00, '2026-11-20 08:00:00'),
  ('TC20261005', 1, 96.00, '2026-11-20 08:00:00'),
  ('TC20261003', 0, NULL,  '2026-11-20 08:00:00'); -- 零参评均分为 NULL（CHECK 验证）

INSERT INTO sel_stu_gpa (student_id, earned_credit, failed_credit, gpa, avg_score, major_rank, major_rank_total, updated_at) VALUES
  ('U202440001', 5.0, 0.0, 3.58, 87.52, 2, 3, '2026-11-19 09:35:00'),
  ('U202440002', 3.0, 0.0, 3.00, 80.80, 3, 3, '2026-11-19 09:35:00'),
  ('U202440003', 3.0, 0.0, 4.00, 93.60, 1, 3, '2026-11-19 09:35:00'),
  ('U202340004', 3.0, 0.0, 2.00, 69.20, 1, 1, '2026-11-19 09:35:00');

INSERT INTO sel_stu_term_gpa (student_id, term, term_credit, term_failed_credit, updated_at) VALUES
  ('U202440001', '2026-2027-1', 5.0, 0.0, '2026-11-19 09:35:00'),
  ('U202440002', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00'),
  ('U202440003', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00'),
  ('U202340004', '2026-2027-1', 3.0, 0.0, '2026-11-19 09:35:00');

-- ---------------------------------------------------------------------
-- 7. 业务⑥ DBA 统一字典项与备份日志初始值（al_dict_type / al_dict_item）
-- ---------------------------------------------------------------------
INSERT INTO al_dict_type (dict_type, type_name, is_system, dict_status, remark) VALUES
  ('system_param',   '选课与成绩系统运行参数', 1, '01', '当前学期、平时成绩最低占比等核心配置'),
  ('STUDENT_TYPE',   '学生类别',               1, '01', '01本科 02硕士 03博士 04交流生'),
  ('STUDENT_STATUS', '学籍状态',               1, '01', '01在籍'),
  ('COURSE_TYPE',            '课程性质',           1, '01', '11校级必修 12院级必修 13专业必修 14专业选修 15通识 16实践'),
  ('tc_special_requirement', '教学班选课特殊要求', 0, '01', '按教学班号记录四六级成绩等特殊选课要求说明'),
  ('HR_TITLE',               '教师职称字典',       1, '01', '与第4组师资管理职称编码对齐')
ON DUPLICATE KEY UPDATE type_name = VALUES(type_name);

INSERT INTO al_dict_item (dict_type, item_code, item_name, sort_no, version, effective_from, item_status) VALUES
  ('system_param',   'current_term',               '2026-2027-1', 1, '当前默认学年学期',            '2026-01-01', '01'),
  ('system_param',   'usual_ratio_min',            '30',          2, '平时成绩最低占比(%)',         '2026-01-01', '01'),
  ('system_param',   'min_open_students',          '6',           3, '最低开班人数(低于可停开)',     '2026-01-01', '01'),
  ('system_param',   'block_selection_on_arrears', '0',           4, '财务欠费是否拦截选课(0提醒1拦截)', '2026-01-01', '01'),
  ('STUDENT_TYPE',   '01',              '本科',        1, '2026',                    '2026-01-01', '01'),
  ('STUDENT_TYPE',   '02',              '硕士',        2, '2026',                    '2026-01-01', '01'),
  ('STUDENT_TYPE',   '03',              '博士',        3, '2026',                    '2026-01-01', '01'),
  ('STUDENT_TYPE',   '04',              '交流生',      4, '2026',                    '2026-01-01', '01'),
  ('STUDENT_STATUS', '01',              '在籍',        1, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '11',              '校级必修',    1, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '12',              '院级必修',    2, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '13',              '专业必修',    3, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '14',              '专业选修',    4, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '15',              '通识选修',    5, '2026',                    '2026-01-01', '01'),
  ('COURSE_TYPE',    '16',              '实践环节',    6, '2026',                    '2026-01-01', '01'),
  ('HR_TITLE',       'PROF',            '教授',        1, '2026',                    '2026-01-01', '01'),
  ('HR_TITLE',       'ASSOC_PROF',      '副教授',      2, '2026',                    '2026-01-01', '01'),
  ('HR_TITLE',       'LECTURER',        '讲师',        3, '2026',                    '2026-01-01', '01'),
  ('HR_TITLE',       'ASSISTANT',       '助教',        4, '2026',                    '2026-01-01', '01'),
  ('HR_TITLE',       'ENGINEER',        '工程师',      5, '2026',                    '2026-01-01', '01')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

INSERT INTO sys_backup_log (backup_id, backup_type, file_name, file_size_kb, operator_id, backup_time, status, remark) VALUES
  (1, 1, 'course_grade_mgmt_20261123.sql', 128, 'S000000001', '2026-11-23 23:00:00', 1, '系统初始化全量备份')
ON DUPLICATE KEY UPDATE remark = VALUES(remark);
