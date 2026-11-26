-- =====================================================================
-- 第 03 组（选课及成绩管理）向第 01 组（学籍管理）提供的跨组只读视图
-- 用途：零改动物理表结构，直接基于全班统一数据字典物理表提供标准口径的
--       1) 学生学业统计摘要（总已获学分、加权平均分、GPA、专业排名）
--       2) 学生分类已修学分（按课程性质 11-16，严格及格才计学分、同课择高、排除辅修、含外校认定）
--       3) 学生各科已发布成绩明细（合并校内成绩与已通过审批的外校交流认定成绩）
-- 兼容：MySQL 8.0+（含 ONLY_FULL_GROUP_BY）
-- =====================================================================

-- 1. 学生累计学业统计摘要（供第 1 组毕业初审、转专业综合测评、学籍档案使用）
--    说明：保留全部在册学生；无有效成绩记录时 earned_credit/failed_credit 为 0.0，
--          gpa / avg_score / major_rank 为 NULL（严禁将“无成绩”与“0 分”混淆）。
CREATE OR REPLACE VIEW v_sel_stu_academic_summary AS
SELECT
  st.student_id,
  p.person_name AS student_name,
  st.major_id,
  m.major_name,
  st.grade,
  st.student_type,
  CASE st.student_type
    WHEN '01' THEN '本科'
    WHEN '02' THEN '研究生'
    WHEN '03' THEN '研究生'
    WHEN '04' THEN '交流生'
    ELSE st.student_type
  END AS degree_level,
  CASE
    WHEN st.student_type IN ('02', '03') THEN 2
    ELSE 1
  END AS stat_stage,
  m.min_credit AS graduation_min_credit,
  COALESCE(g.earned_credit, 0.0) AS earned_credit,
  COALESCE(g.failed_credit, 0.0) AS failed_credit,
  g.gpa,
  g.avg_score,
  g.major_rank,
  g.major_rank_total,
  g.updated_at
FROM std_student st
LEFT JOIN sys_person p ON p.person_id = st.person_id
LEFT JOIN aca_major m ON m.major_id = st.major_id
LEFT JOIN sel_stu_gpa g ON g.student_id = st.student_id;

-- 2. 学生各科已发布成绩明细（自动合并校内已发布成绩与已通过生效的外校交流认定成绩）
--    说明：
--      - record_source: 'SCHOOL' 校内成绩 | 'EXCHANGE' 外校交流认定成绩
--      - study_type: 1 初修 | 2 重修 | 3 辅修（含双学位，不计入主修学分与 GPA）
--      - is_minor: 1 辅修/双学位 | 0 主修
--      - is_passed: 1 及格（获得学分）| 0 不及格（未获得学分）
--      - is_final: 1 该门课本阶段最终/最高成绩 | 0 非最终历次成绩
CREATE OR REPLACE VIEW v_sel_stu_course_grades AS
SELECT
  'SCHOOL' AS record_source,
  CAST(s.score_id AS CHAR) AS score_id,
  sc.student_id,
  tc.term,
  tc.tc_id,
  c.course_id,
  c.course_name,
  c.course_type,
  CASE TRIM(c.course_type)
    WHEN '11' THEN '校级必修'
    WHEN '12' THEN '院级必修'
    WHEN '13' THEN '专业必修'
    WHEN '14' THEN '专业选修'
    WHEN '15' THEN '通识选修'
    WHEN '16' THEN '实践环节'
    ELSE '其他'
  END AS course_type_label,
  CASE
    WHEN TRIM(c.course_type) IN ('11', '12', '13') THEN 'REQUIRED'
    WHEN TRIM(c.course_type) IN ('14', '15') THEN 'ELECTIVE'
    ELSE 'PRACTICE'
  END AS category_group,
  c.credit,
  sc.study_type,
  CASE WHEN sc.study_type = 3 THEN 1 ELSE 0 END AS is_minor,
  sc.stat_stage,
  s.exam_type,
  s.exam_flag,
  s.score_type,
  s.usual_score,
  s.exam_score,
  s.total_score,
  s.score_level,
  s.grade_point,
  CASE
    WHEN s.grade_point IS NOT NULL AND s.grade_point > 0 THEN 1
    WHEN s.score_type = 1 AND s.total_score IS NOT NULL AND s.total_score >= 60 THEN 1
    WHEN s.score_type = 2 AND s.score_level IN ('优', '良', '中', '及格') THEN 1
    WHEN s.score_type = 3 AND s.score_level = '合格' THEN 1
    ELSE 0
  END AS is_passed,
  s.is_final,
  s.published_at AS publish_time,
  NULL AS host_school,
  NULL AS ext_course_name
FROM sel_score s
JOIN sel_sc sc ON s.sc_id = sc.sc_id
JOIN aca_teaching_class tc ON sc.tc_id = tc.tc_id
JOIN aca_course c ON tc.course_id = c.course_id
WHERE s.score_status = 3
UNION ALL
SELECT
  'EXCHANGE' AS record_source,
  CONCAT('EX-', CAST(e.convert_id AS CHAR)) AS score_id,
  e.student_id,
  e.term,
  'EXCHANGE' AS tc_id,
  c.course_id,
  c.course_name,
  c.course_type,
  CASE TRIM(c.course_type)
    WHEN '11' THEN '校级必修'
    WHEN '12' THEN '院级必修'
    WHEN '13' THEN '专业必修'
    WHEN '14' THEN '专业选修'
    WHEN '15' THEN '通识选修'
    WHEN '16' THEN '实践环节'
    ELSE '其他'
  END AS course_type_label,
  CASE
    WHEN TRIM(c.course_type) IN ('11', '12', '13') THEN 'REQUIRED'
    WHEN TRIM(c.course_type) IN ('14', '15') THEN 'ELECTIVE'
    ELSE 'PRACTICE'
  END AS category_group,
  c.credit,
  1 AS study_type,
  0 AS is_minor,
  e.stat_stage,
  1 AS exam_type,
  0 AS exam_flag,
  e.score_type,
  NULL AS usual_score,
  NULL AS exam_score,
  e.converted_score AS total_score,
  e.converted_level AS score_level,
  e.grade_point,
  CASE
    WHEN e.grade_point IS NOT NULL AND e.grade_point > 0 THEN 1
    WHEN e.score_type = 1 AND e.converted_score IS NOT NULL AND e.converted_score >= 60 THEN 1
    WHEN e.score_type = 2 AND e.converted_level IN ('优', '良', '中', '及格') THEN 1
    WHEN e.score_type = 3 AND e.converted_level = '合格' THEN 1
    ELSE 0
  END AS is_passed,
  e.is_final,
  e.school_audit_time AS publish_time,
  e.host_school,
  e.ext_course_name
FROM sel_exchange_score_convert e
JOIN aca_course c ON e.course_id = c.course_id
WHERE e.audit_status = 2;

-- 3. 学生按课程性质分类的已修学分汇总（供第 1 组毕业学分核对与分类统计使用）
--    核心规则（与本组学业统计及学业完成情况完全同源）：
--      1) 仅计入学生当前培养阶段（stat_stage）的主修成绩（自动排除辅修 is_minor = 1）；
--      2) 合并校内已发布成绩与已通过审批的外校交流认定成绩；
--      3) 同一门课程多次考核（补考/重修）按绩点最高、折算分最高、最近一次仅取 1 条，绝不重复计学分；
--      4) 仅及格（is_passed = 1）计入 earned_credit，最终仍不及格计入 failed_credit。
CREATE OR REPLACE VIEW v_sel_stu_credit_by_category AS
WITH major_candidates AS (
  SELECT
    st.student_id,
    CASE WHEN st.student_type IN ('02', '03') THEN 2 ELSE 1 END AS stat_stage,
    g.course_id,
    COALESCE(NULLIF(TRIM(pc.course_type), ''), TRIM(g.course_type)) AS course_type,
    g.credit,
    g.is_passed,
    COALESCE(g.grade_point, 0.00) AS sort_gp,
    CASE
      WHEN g.score_type = 1 THEN COALESCE(g.total_score, 0.00)
      WHEN g.score_type = 2 THEN
        CASE g.score_level
          WHEN '优' THEN 90.00
          WHEN '良' THEN 80.00
          WHEN '中' THEN 70.00
          WHEN '及格' THEN 60.00
          ELSE 50.00
        END
      WHEN g.score_type = 3 THEN
        CASE g.score_level
          WHEN '合格' THEN 85.00
          ELSE 50.00
        END
      ELSE 0.00
    END AS sort_equiv,
    g.term,
    g.score_id
  FROM std_student st
  JOIN v_sel_stu_course_grades g
    ON g.student_id = st.student_id
   AND g.is_minor = 0
   AND g.stat_stage = CASE WHEN st.student_type IN ('02', '03') THEN 2 ELSE 1 END
  LEFT JOIN aca_program_course pc
    ON pc.major_id = st.major_id
   AND pc.grade = st.grade
   AND pc.program_type = '1'
   AND pc.course_id = g.course_id
),
ranked AS (
  SELECT
    student_id,
    stat_stage,
    course_id,
    course_type,
    credit,
    is_passed,
    ROW_NUMBER() OVER (
      PARTITION BY student_id, course_id
      ORDER BY sort_gp DESC, sort_equiv DESC, term DESC, score_id DESC
    ) AS rn
  FROM major_candidates
)
SELECT
  student_id,
  stat_stage,
  course_type,
  CASE course_type
    WHEN '11' THEN '校级必修'
    WHEN '12' THEN '院级必修'
    WHEN '13' THEN '专业必修'
    WHEN '14' THEN '专业选修'
    WHEN '15' THEN '通识选修'
    WHEN '16' THEN '实践环节'
    ELSE '其他'
  END AS course_type_label,
  CASE
    WHEN course_type IN ('11', '12', '13') THEN 'REQUIRED'
    WHEN course_type IN ('14', '15') THEN 'ELECTIVE'
    ELSE 'PRACTICE'
  END AS category_group,
  ROUND(SUM(CASE WHEN is_passed = 1 THEN credit ELSE 0 END), 1) AS earned_credit,
  ROUND(SUM(CASE WHEN is_passed = 0 THEN credit ELSE 0 END), 1) AS failed_credit,
  SUM(CASE WHEN is_passed = 1 THEN 1 ELSE 0 END) AS passed_course_count,
  SUM(CASE WHEN is_passed = 0 THEN 1 ELSE 0 END) AS failed_course_count
FROM ranked
WHERE rn = 1
GROUP BY student_id, stat_stage, course_type;
