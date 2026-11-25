-- 业务②课程展示案例，由 SelectionServiceTest 在独立 H2 内存库载入。
INSERT INTO select_round (
    round_id, term, round_name, round_type, target_level, start_time, end_time,
    max_credit, select_mode, round_status, allow_retake, retake_ignore_conflict, notice_content
) VALUES (910, '2026-2027-1', '课程展示示例轮次', 3, 3, '2026-11-20 09:00:00', '2026-12-10 17:00:00',
          30, 1, 3, 1, 1, '用于验证课程分类和教学班面向对象。');

INSERT INTO course (course_id, course_name, course_type, credit, course_level, open_college) VALUES
    ('DEMO0001', '信息系统基础', '12', 3.0, 1, '0001'),
    ('DEMO0002', '数据可视化实践', '14', 2.0, 1, '0002'),
    ('DEMO0003', '生活中的科学实验', '13', 2.0, 1, '0002'),
    ('DEMO0004', '学术英语交流', '13', 2.0, 1, '0001'),
    ('DEMO0005', '音乐鉴赏', '13', 2.0, 1, '0001'),
    ('DEMO0006', '城市与人文', '13', 2.0, 1, '0001'),
    ('DEMO0007', '创新项目与创业基础', '13', 2.0, 1, '0001'),
    ('DEMO0008', '信息系统实训', '16', 2.0, 1, '0001');

INSERT INTO teaching_class (tc_id, term, course_id, teacher_id, class_ids, capacity, tc_type, tc_status, actual_num) VALUES
    ('DEMO-REQUIRED', '2026-2027-1', 'DEMO0001', 'T1', '信管241，信管242', 60, '1', '4', 0),
    ('DEMO-PROF', '2026-2027-1', 'DEMO0002', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-SCI', '2026-2027-1', 'DEMO0003', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-LANG-01', '2026-2027-1', 'DEMO0004', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-LANG-02', '2026-2027-1', 'DEMO0004', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-ART', '2026-2027-1', 'DEMO0005', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-HUM', '2026-2027-1', 'DEMO0006', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-BIZ', '2026-2027-1', 'DEMO0007', 'T1', NULL, 30, '2', '4', 0),
    ('DEMO-PRACTICE', '2026-2027-1', 'DEMO0008', 'T1', NULL, 30, '2', '4', 0);

INSERT INTO round_class (round_id, tc_id) VALUES
    (910, 'DEMO-REQUIRED'), (910, 'DEMO-PROF'), (910, 'DEMO-SCI'),
    (910, 'DEMO-LANG-01'), (910, 'DEMO-LANG-02'), (910, 'DEMO-ART'),
    (910, 'DEMO-HUM'), (910, 'DEMO-BIZ'), (910, 'DEMO-PRACTICE');

INSERT INTO schedule_result (tc_id, week_range, week_day, start_slot, end_slot, room_id)
    SELECT tc_id, '1-16', 1, 1, 2, '示例教室101' FROM round_class WHERE round_id = 910;

INSERT INTO select_limit (tc_id, limit_type, limit_value) VALUES
    ('DEMO-REQUIRED', 1, '2024'), ('DEMO-REQUIRED', 2, '120102'),
    ('DEMO-PROF', 2, '080901'),
    ('DEMO-LANG-01', 1, '2024'), ('DEMO-LANG-01', 1, '2025'),
    ('DEMO-LANG-01', 2, '120102'), ('DEMO-LANG-01', 3, '0001'),
    ('DEMO-LANG-01', 4, '本科'),
    ('DEMO-LANG-02', 1, '2026'), ('DEMO-LANG-02', 2, '080901');
