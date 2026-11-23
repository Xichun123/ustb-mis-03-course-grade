package com.ustb.coursegrade.module.correction;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * 【业务⑤ 成绩更正申请（模块 5.1–5.4）· 负责人：朱鳞太】
 * 完整支持百分制与五级/两级制成绩两级审批更正、原值乐观并发核验（409 SCORE_CHANGED）、
 * 校发〔2021〕46号附表3绩点重算、is_final 重标与学生更正结果通知。
 */
@Service
public class CorrectionService {

    private final JdbcTemplate jdbc;

    public CorrectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 5.1 教师检索本人已发布（score_status=3）的可更正成绩 */
    public List<Map<String, Object>> listCandidates(String term, String keyword) {
        CurrentUser user = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);

        StringBuilder sql = new StringBuilder("""
                SELECT CAST(s.score_id AS CHAR) AS score_id,
                       CAST(s.sc_id AS CHAR) AS sc_id,
                       sc.student_id, st.student_name, tc.term, tc.tc_id,
                       lc.lesson_no, c.course_id, c.course_name, c.credit,
                       s.exam_type, s.score_type, s.total_score, s.score_level,
                       s.grade_point, s.publish_time,
                       (SELECT COUNT(*) FROM score_correction cr
                        WHERE cr.score_id = s.score_id AND cr.apply_status IN (0, 1)) AS pending_count
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN student st ON sc.student_id = st.student_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                WHERE s.score_status = 3
                """);
        List<Object> params = new ArrayList<>();
        if (user.role() == RoleEnum.TEACHER) {
            sql.append(" AND (s.input_teacher_id = ? OR tc.teacher_id = ?) ");
            params.add(user.empId());
            params.add(user.teacherCode8());
        }
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (sc.student_id LIKE ? OR st.student_name LIKE ? OR c.course_name LIKE ? OR lc.lesson_no LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY s.publish_time DESC, s.score_id DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** 5.1 教师提交成绩更正申请（状态 0：待学院审核） */
    @Transactional
    public Map<String, Object> submitCorrection(Map<String, Object> body) {
        CurrentUser user = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        long scoreId = parseLong(body.get("score_id"), "score_id");
        String reason = body.get("apply_reason") == null ? "" : String.valueOf(body.get("apply_reason")).trim();
        if (reason.isEmpty() || reason.length() > 200) {
            throw BizException.unprocessable("INVALID_REASON", "更正原因必填且不超过 200 字符");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.score_id, s.score_type, s.total_score, s.score_level, s.score_status,
                       s.input_teacher_id, tc.teacher_id
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                WHERE s.score_id = ? FOR UPDATE
                """, scoreId);
        if (rows.isEmpty()) {
            throw BizException.notFound("成绩记录不存在");
        }
        Map<String, Object> scoreRow = rows.get(0);
        if (user.role() == RoleEnum.TEACHER
                && !Objects.equals(user.empId(), scoreRow.get("input_teacher_id"))
                && !Objects.equals(user.teacherCode8(), scoreRow.get("teacher_id"))) {
            throw BizException.forbidden("仅任课教师本人可对本课堂已发布成绩发起更正申请");
        }
        if (((Number) scoreRow.get("score_status")).intValue() != 3) {
            throw BizException.conflict("SCORE_NOT_PUBLISHED", "仅已发布（3）状态的成绩走更正申请流程");
        }

        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM score_correction WHERE score_id = ? AND apply_status IN (0, 1)",
                Integer.class, scoreId);
        if (pending != null && pending > 0) {
            throw BizException.conflict("DUPLICATE_PENDING", "该成绩已存在待审核的更正申请，请勿重复提交");
        }

        int scoreType = ((Number) scoreRow.get("score_type")).intValue();
        BigDecimal oldScore = null;
        BigDecimal newScore = null;
        String oldLevel = null;
        String newLevel = null;

        if (scoreType == 1) {
            oldScore = scoreRow.get("total_score") == null ? null : new BigDecimal(String.valueOf(scoreRow.get("total_score")));
            if (oldScore == null) {
                throw BizException.unprocessable("MISSING_OLD_SCORE", "原百分制总评成绩为空，无法申请更正");
            }
            if (body.get("expected_score") != null) {
                BigDecimal expected = new BigDecimal(String.valueOf(body.get("expected_score")));
                if (expected.compareTo(oldScore) != 0) {
                    throw BizException.conflict("SCORE_CHANGED", "成绩已变化，请刷新后重新申请");
                }
            }
            if (body.get("new_score") == null) {
                throw BizException.unprocessable("MISSING_NEW_SCORE", "百分制课程必须填写更正后分数");
            }
            newScore = new BigDecimal(String.valueOf(body.get("new_score")));
            if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(new BigDecimal("100")) > 0) {
                throw BizException.unprocessable("INVALID_SCORE_RANGE", "更正后分数必须在 0~100 之间");
            }
            if (newScore.compareTo(oldScore) == 0) {
                throw BizException.unprocessable("NO_CHANGE", "更正后成绩与原成绩相同，无需提交");
            }
        } else {
            oldLevel = scoreRow.get("score_level") == null ? null : String.valueOf(scoreRow.get("score_level"));
            if (oldLevel == null) {
                throw BizException.unprocessable("MISSING_OLD_LEVEL", "原等级成绩为空，无法申请更正");
            }
            if (body.get("expected_level") != null
                    && !Objects.equals(String.valueOf(body.get("expected_level")).trim(), oldLevel)) {
                throw BizException.conflict("SCORE_CHANGED", "成绩已变化，请刷新后重新申请");
            }
            newLevel = body.get("new_level") == null ? "" : String.valueOf(body.get("new_level")).trim();
            validateLevel(scoreType, newLevel);
            if (Objects.equals(oldLevel, newLevel)) {
                throw BizException.unprocessable("NO_CHANGE", "更正后等级与原等级相同，无需提交");
            }
        }

        final BigDecimal fOldScore = oldScore;
        final BigDecimal fNewScore = newScore;
        final String fOldLevel = oldLevel;
        final String fNewLevel = newLevel;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO score_correction
                      (score_id, apply_teacher_id, old_score, new_score, old_level, new_level,
                       apply_reason, apply_time, apply_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, scoreId);
            ps.setString(2, user.empId());
            if (fOldScore != null) ps.setBigDecimal(3, fOldScore); else ps.setNull(3, java.sql.Types.DECIMAL);
            if (fNewScore != null) ps.setBigDecimal(4, fNewScore); else ps.setNull(4, java.sql.Types.DECIMAL);
            ps.setString(5, fOldLevel);
            ps.setString(6, fNewLevel);
            ps.setString(7, reason);
            return ps;
        }, keyHolder);

        long applyId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return Map.of("apply_id", String.valueOf(applyId), "apply_status", 0);
    }

    /** 5.1/5.2/5.3 按角色范围检索更正申请列表 */
    public List<Map<String, Object>> listCorrections(Integer applyStatus, String term) {
        CurrentUser user = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        StringBuilder sql = new StringBuilder("""
                SELECT CAST(cr.apply_id AS CHAR) AS apply_id,
                       CAST(cr.score_id AS CHAR) AS score_id,
                       sc.student_id, st.student_name, tc.term, tc.tc_id, lc.lesson_no,
                       c.course_id, c.course_name, c.open_college, s.score_type,
                       cr.apply_teacher_id, emp.emp_name AS teacher_name,
                       cr.old_score, cr.new_score, cr.old_level, cr.new_level,
                       cr.apply_reason, cr.apply_time, cr.apply_status,
                       cr.college_auditor_id, cr.college_audit_opinion, cr.college_audit_time,
                       cr.auditor_id, cr.audit_opinion, cr.audit_time
                FROM score_correction cr
                JOIN score s ON cr.score_id = s.score_id
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN student st ON sc.student_id = st.student_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN t_employee emp ON cr.apply_teacher_id = emp.emp_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (user.role() == RoleEnum.TEACHER) {
            sql.append(" AND cr.apply_teacher_id = ? ");
            params.add(user.empId());
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            sql.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        }
        if (applyStatus != null) {
            sql.append(" AND cr.apply_status = ? ");
            params.add(applyStatus);
        }
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        sql.append(" ORDER BY cr.apply_id DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** 5.2 学院审核（0 -> 1 通过 / 0 -> 3 驳回，驳回意见必填） */
    @Transactional
    public Map<String, Object> collegeDecision(long applyId, int decision, String opinion) {
        CurrentUser user = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        if (decision != 1 && decision != 2) {
            throw BizException.unprocessable("INVALID_DECISION", "决定必须为 1（通过）或 2（驳回）");
        }
        String op = opinion == null ? "" : opinion.trim();
        if (decision == 2 && op.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "学院驳回时必须填写审核意见");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cr.apply_id, cr.apply_status, c.open_college
                FROM score_correction cr
                JOIN score s ON cr.score_id = s.score_id
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE cr.apply_id = ? FOR UPDATE
                """, applyId);
        if (rows.isEmpty()) {
            throw BizException.notFound("更正申请不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (user.role() == RoleEnum.COLLEGE_SECRETARY
                && !Objects.equals(user.collegeId(), String.valueOf(row.get("open_college")))) {
            throw BizException.forbidden("无权审核其他学院的成绩更正申请");
        }
        if (((Number) row.get("apply_status")).intValue() != 0) {
            throw BizException.conflict("INVALID_STATUS", "当前申请已处理，不可重复学院审核");
        }

        int nextStatus = (decision == 1) ? 1 : 3;
        jdbc.update("""
                UPDATE score_correction
                SET apply_status = ?, college_auditor_id = ?, college_audit_opinion = ?, college_audit_time = NOW()
                WHERE apply_id = ? AND apply_status = 0
                """, nextStatus, user.empId(), op.isEmpty() ? "学院核对通过" : op, applyId);
        return Map.of("apply_id", String.valueOf(applyId), "apply_status", nextStatus);
    }

    /** 5.3 教务处审批 + 5.4 同事务改分、重算绩点与 is_final */
    @Transactional
    public Map<String, Object> officeDecision(long applyId, int decision, String opinion) {
        CurrentUser user = UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);
        if (decision != 1 && decision != 2) {
            throw BizException.unprocessable("INVALID_DECISION", "决定必须为 1（通过）或 2（驳回）");
        }
        String op = opinion == null ? "" : opinion.trim();
        if (decision == 2 && op.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "教务处驳回时必须填写审批意见");
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cr.*, s.score_type, s.total_score AS cur_score, s.score_level AS cur_level,
                       sc.student_id, sc.stat_stage, tc.course_id, tc.term
                FROM score_correction cr
                JOIN score s ON cr.score_id = s.score_id
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                WHERE cr.apply_id = ? FOR UPDATE
                """, applyId);
        if (rows.isEmpty()) {
            throw BizException.notFound("更正申请不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("apply_status")).intValue() != 1) {
            throw BizException.conflict("INVALID_STATUS", "仅待教务处审批（1）状态的申请可执行终审");
        }

        if (decision == 2) {
            jdbc.update("""
                    UPDATE score_correction
                    SET apply_status = 3, auditor_id = ?, audit_opinion = ?, audit_time = NOW()
                    WHERE apply_id = ? AND apply_status = 1
                    """, user.empId(), op, applyId);
            return Map.of("apply_id", String.valueOf(applyId), "apply_status", 3);
        }

        // 5.4 同事务生效：重核原值 -> 更新成绩与绩点 -> 重标 is_final
        long scoreId = ((Number) row.get("score_id")).longValue();
        int scoreType = ((Number) row.get("score_type")).intValue();
        BigDecimal newGp;
        if (scoreType == 1) {
            BigDecimal oldVal = new BigDecimal(String.valueOf(row.get("old_score")));
            BigDecimal curVal = new BigDecimal(String.valueOf(row.get("cur_score")));
            if (oldVal.compareTo(curVal) != 0) {
                throw BizException.conflict("SCORE_CHANGED", "成绩当前值已与申请原值不一致，拒绝覆盖");
            }
            BigDecimal newVal = new BigDecimal(String.valueOf(row.get("new_score")));
            newGp = calcPercentGradePoint(newVal);
            jdbc.update("UPDATE score SET total_score = ?, grade_point = ? WHERE score_id = ?",
                    newVal, newGp, scoreId);
        } else {
            String oldLv = String.valueOf(row.get("old_level"));
            String curLv = String.valueOf(row.get("cur_level"));
            if (!Objects.equals(oldLv, curLv)) {
                throw BizException.conflict("SCORE_CHANGED", "成绩等级已变化，拒绝覆盖");
            }
            String newLv = String.valueOf(row.get("new_level"));
            newGp = calcLevelGradePoint(newLv);
            jdbc.update("UPDATE score SET score_level = ?, grade_point = ? WHERE score_id = ?",
                    newLv, newGp, scoreId);
        }

        String studentId = String.valueOf(row.get("student_id"));
        int statStage = ((Number) row.get("stat_stage")).intValue();
        String courseId = String.valueOf(row.get("course_id"));
        String term = String.valueOf(row.get("term"));

        recomputeIsFinalForCourse(studentId, statStage, courseId);
        recomputeAcademicStatsSnapshot(studentId, statStage, term);

        jdbc.update("""
                UPDATE score_correction
                SET apply_status = 2, auditor_id = ?, audit_opinion = ?, audit_time = NOW()
                WHERE apply_id = ? AND apply_status = 1
                """, user.empId(), op.isEmpty() ? "同意更正并生效" : op, applyId);

        return Map.of("apply_id", String.valueOf(applyId), "apply_status", 2, "new_grade_point", newGp.toPlainString());
    }

    /** 5.4 学生查看本人已通过的成绩更正结果（隐藏审批工号与他生记录） */
    public List<Map<String, Object>> listMyCorrectionResults(String term) {
        CurrentUser user = UserContext.requireRole(RoleEnum.STUDENT);
        StringBuilder sql = new StringBuilder("""
                SELECT CAST(cr.apply_id AS CHAR) AS apply_id,
                       tc.term, c.course_id, c.course_name, s.score_type,
                       cr.old_score, cr.new_score, cr.old_level, cr.new_level,
                       s.grade_point AS new_grade_point, cr.apply_reason, cr.audit_time
                FROM score_correction cr
                JOIN score s ON cr.score_id = s.score_id
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE sc.student_id = ? AND cr.apply_status = 2
                """);
        List<Object> params = new ArrayList<>();
        params.add(user.studentId());
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        sql.append(" ORDER BY cr.audit_time DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** 按校发〔2021〕46号附表3换算百分制绩点 */
    public static BigDecimal calcPercentGradePoint(BigDecimal score) {
        double v = score.doubleValue();
        if (v >= 90.0) return new BigDecimal("4.00");
        if (v >= 85.0) return new BigDecimal("3.70");
        if (v >= 82.0) return new BigDecimal("3.40");
        if (v >= 78.0) return new BigDecimal("3.00");
        if (v >= 75.0) return new BigDecimal("2.40");
        if (v >= 72.0) return new BigDecimal("2.00");
        if (v >= 60.0) return new BigDecimal("1.00");
        return new BigDecimal("0.00");
    }

    /** 按校发〔2021〕46号附表3换算等级制绩点 */
    public static BigDecimal calcLevelGradePoint(String level) {
        return switch (level) {
            case "优" -> new BigDecimal("4.00");
            case "良" -> new BigDecimal("3.40");
            case "中" -> new BigDecimal("2.40");
            case "及格", "合格" -> new BigDecimal("1.00");
            default -> new BigDecimal("0.00");
        };
    }

    private void recomputeIsFinalForCourse(String studentId, int statStage, String courseId) {
        List<Map<String, Object>> published = jdbc.queryForList("""
                SELECT s.score_id, s.total_score, s.grade_point, s.publish_time
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                WHERE sc.student_id = ? AND sc.stat_stage = ? AND tc.course_id = ? AND s.score_status = 3
                ORDER BY COALESCE(s.grade_point, 0) DESC, COALESCE(s.total_score, 0) DESC, s.publish_time DESC, s.score_id DESC
                """, studentId, statStage, courseId);
        if (published.isEmpty()) {
            return;
        }
        long bestId = ((Number) published.get(0).get("score_id")).longValue();
        for (Map<String, Object> r : published) {
            long id = ((Number) r.get("score_id")).longValue();
            jdbc.update("UPDATE score SET is_final = ? WHERE score_id = ?", (id == bestId ? 1 : 0), id);
        }
    }

    /**
     * 5.4 同事务刷新 stu_gpa、stu_term_gpa 与专业年级排名（与 4.3 统计算法一致：
     * 严格排除 sc.study_type=3 辅修成绩，并联合 exchange_score_convert 生效记录）。
     */
    private void recomputeAcademicStatsSnapshot(String studentId, int statStage, String term) {
        List<Map<String, Object>> finalScores = jdbc.queryForList("""
                SELECT c.credit, s.score_type, s.total_score, s.score_level, s.grade_point
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE sc.student_id = ? AND sc.stat_stage = ? AND sc.study_type <> 3
                  AND s.score_status = 3 AND s.is_final = 1
                UNION ALL
                SELECT c.credit, e.score_type, e.converted_score AS total_score,
                       e.converted_level AS score_level, e.grade_point
                FROM exchange_score_convert e
                JOIN course c ON e.course_id = c.course_id
                WHERE e.student_id = ? AND e.stat_stage = ?
                  AND e.audit_status = 2 AND e.is_final = 1
                """, studentId, statStage, studentId, statStage);

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal failed = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal gpWeighted = BigDecimal.ZERO;
        BigDecimal scoreWeighted = BigDecimal.ZERO;

        for (Map<String, Object> r : finalScores) {
            BigDecimal credit = new BigDecimal(String.valueOf(r.get("credit")));
            BigDecimal gp = r.get("grade_point") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(r.get("grade_point")));
            BigDecimal numericScore = resolveNumericScore(r.get("score_type"), r.get("total_score"), r.get("score_level"));
            if (gp.compareTo(BigDecimal.ZERO) > 0) {
                earned = earned.add(credit);
            } else {
                failed = failed.add(credit);
            }
            totalCredit = totalCredit.add(credit);
            gpWeighted = gpWeighted.add(gp.multiply(credit));
            scoreWeighted = scoreWeighted.add(numericScore.multiply(credit));
        }

        BigDecimal gpa = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? gpWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;
        BigDecimal avgScore = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? scoreWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;

        jdbc.update("""
                INSERT INTO stu_gpa (student_id, earned_credit, failed_credit, gpa, avg_score, update_time)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                  earned_credit = VALUES(earned_credit),
                  failed_credit = VALUES(failed_credit),
                  gpa = VALUES(gpa),
                  avg_score = VALUES(avg_score),
                  update_time = NOW()
                """, studentId, earned, failed, gpa, avgScore);

        // 同步刷新本学期学业统计 stu_term_gpa（同样排除辅修）
        List<Map<String, Object>> termRows = jdbc.queryForList("""
                SELECT tc.course_id, MAX(c.credit) AS credit, MAX(COALESCE(s.grade_point, 0)) AS best_gp
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE sc.student_id = ? AND sc.stat_stage = ? AND sc.study_type <> 3
                  AND tc.term = ? AND s.score_status = 3
                GROUP BY tc.course_id
                """, studentId, statStage, term);
        BigDecimal termEarned = BigDecimal.ZERO;
        BigDecimal termFailed = BigDecimal.ZERO;
        for (Map<String, Object> tr : termRows) {
            BigDecimal credit = new BigDecimal(String.valueOf(tr.get("credit")));
            BigDecimal bestGp = new BigDecimal(String.valueOf(tr.get("best_gp")));
            if (bestGp.compareTo(BigDecimal.ZERO) > 0) {
                termEarned = termEarned.add(credit);
            } else {
                termFailed = termFailed.add(credit);
            }
        }
        jdbc.update("""
                INSERT INTO stu_term_gpa (student_id, term, term_credit, term_failed_credit, update_time)
                VALUES (?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                  term_credit = VALUES(term_credit),
                  term_failed_credit = VALUES(term_failed_credit),
                  update_time = NOW()
                """, studentId, term, termEarned, termFailed);

        // 同步刷新同专业同年级排名（同分并列跳号）
        List<Map<String, Object>> stuMeta = jdbc.queryForList(
                "SELECT major_id, grade FROM student WHERE student_id = ?", studentId);
        if (!stuMeta.isEmpty() && stuMeta.get(0).get("major_id") != null && stuMeta.get(0).get("grade") != null) {
            String majorId = String.valueOf(stuMeta.get(0).get("major_id"));
            Object grade = stuMeta.get(0).get("grade");
            List<Map<String, Object>> peers = jdbc.queryForList("""
                    SELECT g.student_id, g.avg_score
                    FROM stu_gpa g
                    JOIN student st ON g.student_id = st.student_id
                    WHERE st.major_id = ? AND st.grade = ? AND g.avg_score IS NOT NULL
                    ORDER BY g.avg_score DESC, g.student_id ASC
                    """, majorId, grade);
            int totalPeers = peers.size();
            BigDecimal prevScore = null;
            int currentRank = 0;
            for (int i = 0; i < peers.size(); i++) {
                BigDecimal cur = new BigDecimal(String.valueOf(peers.get(i).get("avg_score")));
                if (prevScore == null || cur.compareTo(prevScore) != 0) {
                    currentRank = i + 1;
                    prevScore = cur;
                }
                jdbc.update("UPDATE stu_gpa SET major_rank = ?, major_rank_total = ? WHERE student_id = ?",
                        currentRank, totalPeers, peers.get(i).get("student_id"));
            }
        }
    }

    private BigDecimal resolveNumericScore(Object scoreTypeObj, Object totalScoreObj, Object levelObj) {
        int st = ((Number) scoreTypeObj).intValue();
        if (st == 1 && totalScoreObj != null) {
            return new BigDecimal(String.valueOf(totalScoreObj));
        }
        String lv = levelObj == null ? "" : String.valueOf(levelObj);
        return switch (lv) {
            case "优" -> new BigDecimal("95.0");
            case "良" -> new BigDecimal("85.0");
            case "中" -> new BigDecimal("75.0");
            case "及格", "合格" -> new BigDecimal("65.0");
            default -> new BigDecimal("50.0");
        };
    }

    private void validateLevel(int scoreType, String level) {
        Set<String> valid = (scoreType == 2)
                ? Set.of("优", "良", "中", "及格", "不及格")
                : Set.of("合格", "不合格");
        if (!valid.contains(level)) {
            throw BizException.unprocessable("INVALID_LEVEL", "等级取值不合法，允许值: " + valid);
        }
    }

    private long parseLong(Object raw, String key) {
        if (raw == null) {
            throw BizException.unprocessable("MISSING_FIELD", "缺少必填字段: " + key);
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw BizException.unprocessable("INVALID_ID", key + " 格式非法");
        }
    }
}
