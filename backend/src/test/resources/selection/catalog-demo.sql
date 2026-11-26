-- 业务②课程展示案例，由 SelectionServiceTest 在独立 H2 内存库载入。
INSERT INTO sel_select_round (
    round_id, term, round_name, round_type, target_level, start_at, end_at,
    max_credit, select_mode, round_status, allow_retake, retake_ignore_conflict, notice_content
) VALUES (910, '2026-2027-1', '课程展示示例轮次', 3, 3, '2026-11-20 09:00:00', '2026-12-10 17:00:00',
          30, 1, 3, 1, 1, '用于验证课程分类和教学班面向对象。');

INSERT INTO aca_course (course_id, course_name, course_type, credit, org_id) VALUES
    ('DEMO0001', '大学英语', '11', 2.0, 'ORG001'),
    ('DEMO0002', '信息系统基础', '12', 3.0, 'ORG001'),
    ('DEMO0003', '数据结构与算法', '13', 3.5, 'ORG001'),
    ('DEMO0004', '数据可视化实践', '14', 2.0, 'ORG002'),
    ('DEMO0005', '学术英语交流', '15', 2.0, 'ORG001'),
    ('DEMO0006', '信息系统实训', '16', 2.0, 'ORG001');

INSERT INTO aca_teaching_class (tc_id, term, course_id, emp_id, class_ids, capacity, tc_type, tc_status, actual_num, selected_count) VALUES
    ('DEMO-UNI-REQ', '2026-2027-1', 'DEMO0001', 'E1', '信管241，信管242', 60, '1', '4', 0, 0),
    ('DEMO-COL-REQ', '2026-2027-1', 'DEMO0002', 'E1', NULL, 60, '1', '4', 0, 0),
    ('DEMO-MAJ-REQ', '2026-2027-1', 'DEMO0003', 'E1', NULL, 60, '1', '4', 0, 0),
    ('DEMO-PROF', '2026-2027-1', 'DEMO0004', 'E1', NULL, 30, '2', '4', 0, 0),
    ('DEMO-LANG-01', '2026-2027-1', 'DEMO0005', 'E1', NULL, 30, '2', '4', 0, 0),
    ('DEMO-LANG-02', '2026-2027-1', 'DEMO0005', 'E1', NULL, 30, '2', '4', 0, 0),
    ('DEMO-PRACTICE', '2026-2027-1', 'DEMO0006', 'E1', NULL, 30, '2', '4', 0, 0);

INSERT INTO sel_round_class (round_id, tc_id) VALUES
    (910, 'DEMO-UNI-REQ'), (910, 'DEMO-COL-REQ'), (910, 'DEMO-MAJ-REQ'),
    (910, 'DEMO-PROF'), (910, 'DEMO-LANG-01'), (910, 'DEMO-LANG-02'),
    (910, 'DEMO-PRACTICE');

INSERT INTO aca_schedule_result (sched_id, tc_id, week_range, week_day, start_slot, end_slot, room_id, sched_status) VALUES
    ('DS001', 'DEMO-UNI-REQ', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS002', 'DEMO-COL-REQ', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS003', 'DEMO-MAJ-REQ', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS004', 'DEMO-PROF', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS005', 'DEMO-LANG-01', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS006', 'DEMO-LANG-02', '1-16', '1', 1, 2, '示例教室101', '1'),
    ('DS007', 'DEMO-PRACTICE', '1-16', '1', 1, 2, '示例教室101', '1');

INSERT INTO sel_select_limit (tc_id, limit_type, limit_value) VALUES
    ('DEMO-UNI-REQ', 1, '2024'), ('DEMO-UNI-REQ', 2, '120102'),
    ('DEMO-PROF', 2, '080901'),
    ('DEMO-LANG-01', 1, '2024'), ('DEMO-LANG-01', 1, '2025'),
    ('DEMO-LANG-01', 2, '120102'), ('DEMO-LANG-01', 3, '0001'),
    ('DEMO-LANG-01', 4, '本科'),
    ('DEMO-LANG-02', 1, '2026'), ('DEMO-LANG-02', 2, '080901');
