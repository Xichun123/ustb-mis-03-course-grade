package com.ustb.coursegrade.module.stats;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 【业务④ 成绩查询与统计（模块 4.1–4.6）· 负责人：黎家俊】
 * 完整覆盖：
 * 4.1 学生最简评教与服务端门禁（teach_eval，提交后不可改，0~100整数）
 * 4.2 先评教后查分拦截与历次成绩查询（score_status=3，联合外校认定，历史豁免隐藏当期摘要）
 * 4.3 学业统计重算（排除 sc.study_type=3 辅修，联合 exchange_score_convert，学期独立择优，同专业同年级通用排名）
 * 4.4 成绩单预览与打印（含标准历次成绩单与来校交换生中英文正式成绩单，重新校验门禁）
 * 4.5 成绩统计报表（成绩分布分制独立统计 / 当前最终不及格名单 / 首次考核评优专业排名与完整性阻断）
 * 4.6 评教结果汇总（tc_eval_result，零参评均分为 NULL，匿名汇总，第4组按人数加权视图，教务处受控明细）
 */
@Service
public class StatsService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern TERM_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d$");

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =========================================================================
    // 公共基础辅助方法
    // =========================================================================

    public String getCurrentTerm() {
        List<String> rows = jdbc.queryForList(
                "SELECT dict_name FROM sys_code_dict WHERE dict_group = 'system_param' AND dict_code = 'current_term' AND is_enabled = 1 LIMIT 1",
                String.class);
        if (!rows.isEmpty() && rows.get(0) != null && !rows.get(0).isBlank()) {
            return rows.get(0).trim();
        }
        return "2026-2027-1";
    }

    private void validateTermFormat(String term) {
        if (term == null || term.isBlank()) {
            return;
        }
        String t = term.trim();
        if (!TERM_PATTERN.matcher(t).matches()) {
            throw BizException.unprocessable("INVALID_TERM", "学期格式非法，应为 YYYY-YYYY-T（如 2026-2027-1）");
        }
        int y1 = Integer.parseInt(t.substring(0, 4));
        int y2 = Integer.parseInt(t.substring(5, 9));
        if (y2 - y1 != 1) {
            throw BizException.unprocessable("INVALID_TERM", "学期起止学年必须相差 1 年（如 2026-2027-1）");
        }
    }

    private String resolveTeacherCode8(CurrentUser user) {
        if (user.teacherCode8() != null && !user.teacherCode8().isBlank()) {
            return user.teacherCode8().trim();
        }
        if (user.empId() != null && !user.empId().isBlank()) {
            List<String> mapped = jdbc.queryForList(
                    "SELECT dict_code FROM sys_code_dict WHERE dict_group = 'teacher_map' AND dict_name = ? AND is_enabled = 1 LIMIT 1",
                    String.class, user.empId().trim());
            if (!mapped.isEmpty()) {
                return mapped.get(0).trim();
            }
        }
        throw BizException.forbidden("当前教师工号尚未建立与教学班 teacher_id 的有效映射，拒绝越权查询");
    }

    private Map<String, Object> loadStudentProfile(String studentId) {
        List<Map<String, Object>> stuRows = jdbc.queryForList("""
                SELECT s.student_id, s.student_name, s.grade, s.degree_level, s.class_name,
                       s.major_id, s.college_id, s.final_enroll,
                       cm.dict_name AS college_name
                FROM student s
                LEFT JOIN sys_code_dict cm ON cm.dict_group = 'college_map' AND cm.dict_code = s.college_id
                WHERE s.student_id = ?
                """, studentId);
        if (!stuRows.isEmpty()) {
            Map<String, Object> profile = new LinkedHashMap<>(stuRows.get(0));
            profile.put("is_incoming_exchange", false);
            profile.put("student_category", "本校" + profile.getOrDefault("degree_level", "学生"));
            return profile;
        }

        // 来校交换生视图 v_exchange_student（第 15 组冻结接口）
        List<Map<String, Object>> exRows = jdbc.queryForList("""
                SELECT e.temp_student_no AS student_id, e.student_name, e.sender_school_name,
                       e.term_ref, e.college_ref AS college_id, e.major_ref AS major_id,
                       e.start_on, e.end_on, e.status_code,
                       cm.dict_name AS college_name
                FROM v_exchange_student e
                LEFT JOIN sys_code_dict cm ON cm.dict_group = 'college_map' AND cm.dict_code = e.college_ref
                WHERE e.temp_student_no = ?
                """, studentId);
        if (!exRows.isEmpty()) {
            Map<String, Object> profile = new LinkedHashMap<>(exRows.get(0));
            profile.put("is_incoming_exchange", true);
            profile.put("degree_level", "本科");
            profile.put("class_name", "来校交换生");
            profile.put("student_category", "来校交换生");
            return profile;
        }

        throw BizException.notFound("未找到学号【" + studentId + "】对应的学生档案");
    }

    private int mapDegreeLevelToStage(String degreeLevel) {
        if ("本科".equals(degreeLevel)) {
            return 1;
        }
        if ("研究生".equals(degreeLevel)) {
            return 2;
        }
        throw BizException.unprocessable("UNKNOWN_DEGREE_LEVEL",
                "学生培养层次【" + degreeLevel + "】无法映射到已知归属阶段（仅支持本科/研究生）");
    }

    private boolean isPassed(int scoreType, BigDecimal totalScore, String scoreLevel, BigDecimal gradePoint) {
        if (gradePoint != null) {
            return gradePoint.compareTo(BigDecimal.ZERO) > 0;
        }
        if (scoreType == 1) {
            return totalScore != null && totalScore.compareTo(new BigDecimal("60.0")) >= 0;
        }
        if (scoreType == 2) {
            return scoreLevel != null && Set.of("优", "良", "中", "及格").contains(scoreLevel);
        }
        if (scoreType == 3) {
            return "合格".equals(scoreLevel);
        }
        return false;
    }

    /**
     * 与业务⑤ CorrectionService 及云库演示数据口径完全对齐的等值分换算：
     * 五级制：优95 / 良85 / 中75 / 及格65 / 不及格50
     * 两级制：合格85 / 不合格50
     */
    private BigDecimal resolveEquivalentScore(int scoreType, BigDecimal totalScore, String scoreLevel) {
        if (scoreType == 1) {
            if (totalScore == null) {
                throw BizException.unprocessable("MISSING_SCORE", "已发布百分制成绩缺少总评分数，拒绝按0分替代");
            }
            return totalScore;
        }
        if (scoreLevel == null || scoreLevel.isBlank()) {
            throw BizException.unprocessable("MISSING_LEVEL", "已发布等级制成绩缺少等级值，拒绝按0分替代");
        }
        return switch (scoreLevel.trim()) {
            case "优" -> new BigDecimal("95.0");
            case "良" -> new BigDecimal("85.0");
            case "中" -> new BigDecimal("75.0");
            case "及格" -> new BigDecimal("65.0");
            case "合格" -> new BigDecimal("85.0");
            case "不及格", "不合格" -> new BigDecimal("50.0");
            default -> throw BizException.unprocessable("UNKNOWN_LEVEL", "未知成绩等级: " + scoreLevel);
        };
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(String.valueOf(val));
    }

    private String courseTypeLabel(String code) {
        if (code == null) return "其他";
        return switch (code.trim()) {
            case "11" -> "校级必修";
            case "12" -> "专业必修";
            case "13" -> "通识选修";
            case "14" -> "专业选修";
            case "15" -> "公共选修";
            case "16" -> "实践环节";
            default -> "课程(" + code + ")";
        };
    }

    // =========================================================================
    // 4.1 学生评教与服务端门禁（P30）
    // =========================================================================

    /** 4.1 获取本人当前学期评教任务与完成状态（不返回任何成绩，不向学生回显已提交分数/意见） */
    public Map<String, Object> getMyEvaluationPending(String term) {
        CurrentUser user = UserContext.requireRole(RoleEnum.STUDENT);
        if (user.studentId() == null || user.studentId().isBlank()) {
            throw BizException.forbidden("当前登录账号缺少学生学号绑定");
        }
        String currentTerm = getCurrentTerm();
        if (term != null && !term.isBlank()) {
            validateTermFormat(term);
            if (!term.trim().equals(currentTerm)) {
                throw BizException.unprocessable("EVAL_TERM_ONLY_CURRENT",
                        "仅支持查询当前学期（" + currentTerm + "）的评教任务");
            }
        }
        return buildEvaluationGateInfo(user.studentId(), currentTerm);
    }

    /** 内部复用的 4.1 门禁检查分支（供 4.1 / 4.2 / 4.4 统一调用） */
    private Map<String, Object> buildEvaluationGateInfo(String studentId, String currentTerm) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT CAST(sc.sc_id AS CHAR) AS sc_id,
                       sc.tc_id, lc.lesson_no, tc.term,
                       c.course_id, c.course_name, c.credit, c.course_type,
                       sc.study_type, tc.teacher_id,
                       COALESCE(emp.emp_name, tc.teacher_id) AS teacher_name,
                       CASE WHEN te.sc_id IS NOT NULL THEN 1 ELSE 0 END AS evaluated,
                       te.eval_time
                FROM sc
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN sys_code_dict tm ON tm.dict_group = 'teacher_map' AND tm.dict_code = tc.teacher_id
                LEFT JOIN t_employee emp ON emp.emp_id = tm.dict_name
                LEFT JOIN teach_eval te ON sc.sc_id = te.sc_id
                WHERE sc.student_id = ? AND tc.term = ? AND sc.select_status = 1
                ORDER BY evaluated ASC, sc.sc_id ASC
                """, studentId, currentTerm);

        List<Map<String, Object>> pending = new ArrayList<>();
        int evaluatedCount = 0;
        for (Map<String, Object> row : rows) {
            boolean done = ((Number) row.get("evaluated")).intValue() == 1;
            row.put("evaluated", done);
            if (done) {
                evaluatedCount++;
            } else {
                pending.add(row);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current_term", currentTerm);
        result.put("total_count", rows.size());
        result.put("evaluated_count", evaluatedCount);
        result.put("pending_count", pending.size());
        result.put("all_completed", pending.isEmpty());
        result.put("items", rows);
        result.put("pending_items", pending);
        return result;
    }

    /** 4.1 学生提交评教（仅新增，不可修改；重复提交幂等返回已提交状态，不覆盖原记录） */
    @Transactional
    public Map<String, Object> submitEvaluation(Map<String, Object> body) {
        CurrentUser user = UserContext.requireRole(RoleEnum.STUDENT);
        String studentId = user.studentId();
        if (studentId == null || studentId.isBlank()) {
            throw BizException.forbidden("当前登录账号缺少学号绑定");
        }

        long scId = parseStrictLong(body.get("sc_id"), "sc_id");
        int evalScore = parseStrictEvalScore(body.get("eval_score"));
        String comment = body.get("eval_comment") == null ? null : String.valueOf(body.get("eval_comment")).trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > 200) {
            throw BizException.unprocessable("COMMENT_TOO_LONG", "意见建议不能超过 200 字符",
                    Map.of("eval_comment", "长度不能超过 200 字符"));
        }

        String currentTerm = getCurrentTerm();

        // 锁定目标 sc 行并重读归属、状态与学期
        List<Map<String, Object>> scRows = jdbc.queryForList("""
                SELECT sc.sc_id, sc.student_id, sc.tc_id, sc.select_status, tc.term
                FROM sc
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                WHERE sc.sc_id = ? FOR UPDATE
                """, scId);
        if (scRows.isEmpty()) {
            throw BizException.notFound("未找到对应的选课记录");
        }
        Map<String, Object> scRow = scRows.get(0);
        if (!studentId.equals(String.valueOf(scRow.get("student_id")).trim())) {
            throw BizException.forbidden("仅可对本人的选课记录提交评教");
        }
        if (((Number) scRow.get("select_status")).intValue() != 1) {
            throw BizException.unprocessable("INVALID_SC_STATUS", "仅已选中（有效选课）的课程允许提交评教");
        }
        if (!currentTerm.equals(String.valueOf(scRow.get("term")).trim())) {
            throw BizException.unprocessable("NOT_CURRENT_TERM", "仅支持对当前学期（" + currentTerm + "）的课程提交评教");
        }

        // 已提交则幂等返回（不覆盖、不泄露原分数和原意见）
        Integer existed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM teach_eval WHERE sc_id = ?", Integer.class, scId);
        if (existed != null && existed > 0) {
            Map<String, Object> gate = buildEvaluationGateInfo(studentId, currentTerm);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sc_id", String.valueOf(scId));
            resp.put("already_submitted", true);
            resp.put("status", "ALREADY_SUBMITTED");
            resp.put("pending_count", gate.get("pending_count"));
            resp.put("all_completed", gate.get("all_completed"));
            return resp;
        }

        try {
            jdbc.update("""
                    INSERT INTO teach_eval (sc_id, eval_score, eval_comment, eval_time)
                    VALUES (?, ?, ?, NOW())
                    """, scId, evalScore, comment);
        } catch (DuplicateKeyException dup) {
            Map<String, Object> gate = buildEvaluationGateInfo(studentId, currentTerm);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("sc_id", String.valueOf(scId));
            resp.put("already_submitted", true);
            resp.put("status", "ALREADY_SUBMITTED");
            resp.put("pending_count", gate.get("pending_count"));
            resp.put("all_completed", gate.get("all_completed"));
            return resp;
        }

        Map<String, Object> gate = buildEvaluationGateInfo(studentId, currentTerm);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sc_id", String.valueOf(scId));
        resp.put("already_submitted", false);
        resp.put("status", "SUBMITTED");
        resp.put("pending_count", gate.get("pending_count"));
        resp.put("all_completed", gate.get("all_completed"));
        return resp;
    }

    // =========================================================================
    // 4.2 本人成绩查询（P31，先评教后查分）
    // =========================================================================

    public Map<String, Object> queryMyGrades(String term, Boolean onlyFinal, int page, int pageSize) {
        CurrentUser user = UserContext.requireRole(RoleEnum.STUDENT);
        String studentId = user.studentId();
        if (studentId == null || studentId.isBlank()) {
            throw BizException.forbidden("当前登录账号缺少学号绑定");
        }
        validateTermFormat(term);

        String currentTerm = getCurrentTerm();
        String trimmedTerm = (term == null || term.isBlank()) ? null : term.trim();
        boolean includesCurrentTerm = (trimmedTerm == null || trimmedTerm.equals(currentTerm));

        // 复用 4.1 服务端门禁
        Map<String, Object> gate = buildEvaluationGateInfo(studentId, currentTerm);
        boolean currentTermCompleted = Boolean.TRUE.equals(gate.get("all_completed"));
        if (includesCurrentTerm && !currentTermCompleted) {
            throw new BizException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "EVAL_REQUIRED",
                    "当前学期（" + currentTerm + "）尚有 " + gate.get("pending_count") + " 门课程未完成评教，请先完成评教后再查询成绩"
            );
        }

        Map<String, Object> profile = loadStudentProfile(studentId);
        List<Map<String, Object>> allRecords = fetchPublishedGradeRecords(studentId, trimmedTerm, null, null);

        // 可选视图筛选（仅看最终成绩）；默认展示全部历次（含补考/重修/不及格/辅修/外校认定）
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> rec : allRecords) {
            if (Boolean.TRUE.equals(onlyFinal) && ((Number) rec.get("is_final")).intValue() != 1) {
                continue;
            }
            filtered.add(rec);
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        int total = filtered.size();
        int fromIdx = Math.min((safePage - 1) * safeSize, total);
        int toIdx = Math.min(fromIdx + safeSize, total);
        List<Map<String, Object>> pagedItems = filtered.subList(fromIdx, toIdx);

        // 读取 stu_gpa 与 stu_term_gpa；若仅历史查询但当期未评完，隐藏可能含当期成绩的累计摘要
        Map<String, Object> summary = null;
        String summaryStatus;
        if (!currentTermCompleted) {
            summaryStatus = "HIDDEN_BY_EVAL_GATE";
        } else {
            List<Map<String, Object>> gpaRows = jdbc.queryForList("""
                    SELECT student_id, earned_credit, failed_credit, gpa, avg_score,
                           major_rank, major_rank_total, update_time
                    FROM stu_gpa
                    WHERE student_id = ?
                    """, studentId);
            if (gpaRows.isEmpty()) {
                summaryStatus = "NOT_GENERATED";
            } else {
                summary = new LinkedHashMap<>(gpaRows.get(0));
                summary.put("rank_label", "通用专业排名（非评优排名）");
                summaryStatus = (summary.get("gpa") == null) ? "NO_COUNTABLE_CREDITS" : "AVAILABLE";
            }
        }

        List<Map<String, Object>> termSummaries = jdbc.queryForList("""
                SELECT student_id, term, term_credit, term_failed_credit, update_time
                FROM stu_term_gpa
                WHERE student_id = ?
                ORDER BY term DESC
                """, studentId);
        if (!currentTermCompleted) {
            termSummaries.removeIf(r -> currentTerm.equals(String.valueOf(r.get("term")).trim()));
        }

        List<String> availableTerms = fetchStudentPublishedTerms(studentId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("current_term", currentTerm);
        resp.put("queried_term", trimmedTerm);
        resp.put("historical_exemption", !includesCurrentTerm);
        resp.put("student", profile);
        resp.put("available_terms", availableTerms);
        resp.put("items", pagedItems);
        resp.put("total", total);
        resp.put("page", safePage);
        resp.put("page_size", safeSize);
        resp.put("summary", summary);
        resp.put("summary_status", summaryStatus);
        resp.put("term_summaries", termSummaries);
        resp.put("data_time", LocalDateTime.now().format(TS_FMT));
        return resp;
    }

    /** 读取指定学生全部已发布校内成绩（score_status=3）与已通过生效外校认定成绩（audit_status=2） */
    private List<Map<String, Object>> fetchPublishedGradeRecords(
            String studentId, String singleTerm, String startTerm, String endTerm) {
        StringBuilder schoolSql = new StringBuilder("""
                SELECT 'SCHOOL' AS record_source,
                       CAST(s.score_id AS CHAR) AS score_id,
                       CAST(s.sc_id AS CHAR) AS sc_id,
                       tc.term, tc.tc_id, lc.lesson_no,
                       c.course_id, c.course_name, c.course_type, c.credit,
                       sc.study_type, sc.stat_stage, sc.is_exempt_attend,
                       s.exam_type, s.exam_flag, s.score_type,
                       s.usual_ratio, s.usual_score, s.exam_score,
                       s.total_score, s.score_level, s.grade_point,
                       s.is_final, s.score_status, s.publish_time,
                       NULL AS host_school, NULL AS ext_course_name, NULL AS proof_no
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                WHERE sc.student_id = ? AND s.score_status = 3
                """);
        List<Object> schoolParams = new ArrayList<>();
        schoolParams.add(studentId);
        if (singleTerm != null) {
            schoolSql.append(" AND tc.term = ? ");
            schoolParams.add(singleTerm);
        }
        if (startTerm != null) {
            schoolSql.append(" AND tc.term >= ? ");
            schoolParams.add(startTerm);
        }
        if (endTerm != null) {
            schoolSql.append(" AND tc.term <= ? ");
            schoolParams.add(endTerm);
        }
        schoolSql.append(" ORDER BY tc.term DESC, c.course_id ASC, s.exam_type ASC, s.score_id ASC");

        List<Map<String, Object>> rows = new ArrayList<>(jdbc.queryForList(schoolSql.toString(), schoolParams.toArray()));

        // 联合读取校外交流成绩换算认定（audit_status=2 已通过生效）
        StringBuilder exSql = new StringBuilder("""
                SELECT 'EXCHANGE' AS record_source,
                       CONCAT('EX-', CAST(e.convert_id AS CHAR)) AS score_id,
                       NULL AS sc_id,
                       e.term, 'EXCHANGE' AS tc_id, NULL AS lesson_no,
                       c.course_id, c.course_name, c.course_type, c.credit,
                       1 AS study_type, e.stat_stage, 0 AS is_exempt_attend,
                       1 AS exam_type, 0 AS exam_flag, e.score_type,
                       NULL AS usual_ratio, NULL AS usual_score, NULL AS exam_score,
                       e.converted_score AS total_score, e.converted_level AS score_level,
                       e.grade_point, e.is_final, 3 AS score_status,
                       e.school_audit_time AS publish_time,
                       e.host_school, e.ext_course_name, e.proof_no
                FROM exchange_score_convert e
                JOIN course c ON e.course_id = c.course_id
                WHERE e.student_id = ? AND e.audit_status = 2
                """);
        List<Object> exParams = new ArrayList<>();
        exParams.add(studentId);
        if (singleTerm != null) {
            exSql.append(" AND e.term = ? ");
            exParams.add(singleTerm);
        }
        if (startTerm != null) {
            exSql.append(" AND e.term >= ? ");
            exParams.add(startTerm);
        }
        if (endTerm != null) {
            exSql.append(" AND e.term <= ? ");
            exParams.add(endTerm);
        }
        exSql.append(" ORDER BY e.term DESC, c.course_id ASC, e.convert_id ASC");
        rows.addAll(jdbc.queryForList(exSql.toString(), exParams.toArray()));

        for (Map<String, Object> r : rows) {
            int scoreType = ((Number) r.get("score_type")).intValue();
            int studyType = ((Number) r.get("study_type")).intValue();
            BigDecimal totalScore = toBigDecimal(r.get("total_score"));
            String scoreLevel = r.get("score_level") == null ? null : String.valueOf(r.get("score_level"));
            BigDecimal gp = toBigDecimal(r.get("grade_point"));
            r.put("course_type_label", courseTypeLabel(r.get("course_type") == null ? null : String.valueOf(r.get("course_type"))));
            r.put("display_score", scoreType == 1
                    ? (totalScore == null ? "—" : totalScore.toPlainString())
                    : (scoreLevel == null ? "—" : scoreLevel));
            r.put("passed", isPassed(scoreType, totalScore, scoreLevel, gp));
            r.put("excluded_from_major_gpa", studyType == 3);
        }
        rows.sort(Comparator
                .comparing((Map<String, Object> m) -> String.valueOf(m.get("term"))).reversed()
                .thenComparing(m -> String.valueOf(m.get("course_id")))
                .thenComparingInt(m -> ((Number) m.get("exam_type")).intValue()));
        return rows;
    }

    private List<String> fetchStudentPublishedTerms(String studentId) {
        return jdbc.queryForList("""
                SELECT DISTINCT term FROM (
                  SELECT tc.term
                  FROM score s
                  JOIN sc ON s.sc_id = sc.sc_id
                  JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                  WHERE sc.student_id = ? AND s.score_status = 3
                  UNION
                  SELECT e.term
                  FROM exchange_score_convert e
                  WHERE e.student_id = ? AND e.audit_status = 2
                ) t
                ORDER BY term DESC
                """, String.class, studentId, studentId);
    }

    // =========================================================================
    // 4.3 学业统计计算（P32：排除辅修 study_type=3、联合外校认定、学期独立择优、专业排名）
    // =========================================================================

    /** 管理端查看学业统计台账（stu_gpa + stu_term_gpa + 辅修排除与外校认定计入提示） */
    public Map<String, Object> listAcademicStats(String term, String majorId, Integer grade, String keyword) {
        CurrentUser user = UserContext.requireRole(
                RoleEnum.COLLEGE_SECRETARY, RoleEnum.ACADEMIC_OFFICE,
                RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        String targetTerm = (term == null || term.isBlank()) ? getCurrentTerm() : term.trim();
        validateTermFormat(targetTerm);

        StringBuilder sql = new StringBuilder("""
                SELECT st.student_id, st.student_name, st.grade, st.degree_level, st.class_name,
                       st.major_id, st.college_id,
                       COALESCE(cm.dict_name, st.college_id) AS college_name,
                       g.earned_credit, g.failed_credit, g.gpa, g.avg_score,
                       g.major_rank, g.major_rank_total, g.update_time,
                       tg.term AS stat_term, tg.term_credit, tg.term_failed_credit,
                       (SELECT COUNT(*) FROM score s2
                        JOIN sc sc2 ON s2.sc_id = sc2.sc_id
                        WHERE sc2.student_id = st.student_id AND sc2.study_type = 3 AND s2.score_status = 3) AS minor_score_count,
                       (SELECT COUNT(*) FROM exchange_score_convert e2
                        WHERE e2.student_id = st.student_id AND e2.audit_status = 2) AS exchange_score_count
                FROM student st
                LEFT JOIN stu_gpa g ON st.student_id = g.student_id
                LEFT JOIN stu_term_gpa tg ON st.student_id = tg.student_id AND tg.term = ?
                LEFT JOIN sys_code_dict cm ON cm.dict_group = 'college_map' AND cm.dict_code = st.college_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        params.add(targetTerm);

        if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            sql.append(" AND st.college_id = ? ");
            params.add(user.collegeId());
        }
        if (majorId != null && !majorId.isBlank()) {
            sql.append(" AND st.major_id = ? ");
            params.add(majorId.trim());
        }
        if (grade != null) {
            sql.append(" AND st.grade = ? ");
            params.add(grade);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (st.student_id LIKE ? OR st.student_name LIKE ? OR st.class_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY st.major_id ASC, st.grade DESC, COALESCE(g.major_rank, 999999) ASC, st.student_id ASC");

        List<Map<String, Object>> items = jdbc.queryForList(sql.toString(), params.toArray());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("term", targetTerm);
        resp.put("items", items);
        resp.put("total", items.size());
        resp.put("rule_note", "v9 规则：累计统计仅计入当前培养层次阶段且 sc.study_type<>3（排除辅修），并联合已通过生效的校外交流认定（exchange_score_convert.audit_status=2）；学期统计按教学班学期独立择优；排名为通用专业排名（非评优排名）。");
        return resp;
    }

    /** 教务处受控触发 4.3 学业统计重算（单人或全量），与 3.4/5.4 共用同一内部事务方法 */
    @Transactional
    public Map<String, Object> triggerRecomputeAcademicStats(String studentId) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        List<String> targetStudents;
        if (studentId != null && !studentId.isBlank()) {
            String sid = studentId.trim();
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE student_id = ?", Integer.class, sid);
            if (exists == null || exists == 0) {
                throw BizException.notFound("未找到本校学生【" + sid + "】（来校交换生不计入本校专业排名表）");
            }
            targetStudents = List.of(sid);
        } else {
            targetStudents = jdbc.queryForList(
                    "SELECT student_id FROM student ORDER BY student_id ASC", String.class);
        }

        Set<String> affectedGroups = new LinkedHashSet<>();
        List<Map<String, Object>> studentResults = new ArrayList<>();
        int updatedTermRows = 0;

        for (String sid : targetStudents) {
            Map<String, Object> one = recomputeSingleStudentWithoutRank(sid);
            studentResults.add(one);
            updatedTermRows += ((Number) one.get("updated_terms")).intValue();
            if (one.get("major_id") != null && one.get("grade") != null) {
                affectedGroups.add(one.get("major_id") + "|" + one.get("grade"));
            }
        }

        // 按 (major_id, grade) 整体重算同专业同年级通用排名（同分并列跳号 RANK）
        for (String groupKey : affectedGroups) {
            String[] parts = groupKey.split("\\|");
            recomputeRankForMajorGrade(parts[0], Integer.parseInt(parts[1]));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("recomputed_students", targetStudents.size());
        resp.put("updated_term_rows", updatedTermRows);
        resp.put("updated_rank_groups", affectedGroups.size());
        resp.put("computed_at", LocalDateTime.now().format(TS_FMT));
        resp.put("details", studentResults);
        return resp;
    }

    /**
     * 4.3 核心内部服务方法（供 3.4 成绩发布、5.4 成绩更正及 4.3 批量重算在原事务内调用）：
     * 不自提交、不另起事务；严格按先阶段、排除辅修 study_type=3、联合外校认定、学期独立择优计算。
     */
    public Map<String, Object> recomputeAcademicStatsInTx(String studentId) {
        Map<String, Object> one = recomputeSingleStudentWithoutRank(studentId);
        if (one.get("major_id") != null && one.get("grade") != null) {
            recomputeRankForMajorGrade(
                    String.valueOf(one.get("major_id")),
                    ((Number) one.get("grade")).intValue());
        }
        return one;
    }

    private Map<String, Object> recomputeSingleStudentWithoutRank(String studentId) {
        List<Map<String, Object>> stuRows = jdbc.queryForList(
                "SELECT student_id, degree_level, major_id, grade FROM student WHERE student_id = ? FOR UPDATE",
                studentId);
        if (stuRows.isEmpty()) {
            throw BizException.notFound("学业统计重算失败：学生【" + studentId + "】不存在");
        }
        Map<String, Object> stu = stuRows.get(0);
        String degreeLevel = stu.get("degree_level") == null ? null : String.valueOf(stu.get("degree_level")).trim();
        int statStage = mapDegreeLevelToStage(degreeLevel);

        // 读取本生本阶段全部已发布且非辅修（study_type <> 3）校内成绩 + 已通过生效外校认定成绩
        List<Map<String, Object>> allCandidates = jdbc.queryForList("""
                SELECT tc.term, tc.course_id, c.credit,
                       s.score_type, s.total_score, s.score_level, s.grade_point,
                       s.exam_type, s.publish_time, s.score_id AS sort_id
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE sc.student_id = ? AND sc.stat_stage = ? AND sc.study_type <> 3
                  AND s.score_status = 3
                UNION ALL
                SELECT e.term, e.course_id, c.credit,
                       e.score_type, e.converted_score AS total_score, e.converted_level AS score_level,
                       e.grade_point, 1 AS exam_type, e.school_audit_time AS publish_time,
                       (e.convert_id + 1000000000) AS sort_id
                FROM exchange_score_convert e
                JOIN course c ON e.course_id = c.course_id
                WHERE e.student_id = ? AND e.stat_stage = ?
                  AND e.audit_status = 2
                """, studentId, statStage, studentId, statStage);

        // 1) 累计集合 C：本阶段按 course_id 择最高一次（不跨阶段）
        Map<String, Map<String, Object>> bestByCourse = new LinkedHashMap<>();
        // 2) 学期集合 T(t)：先按 term 划分，再在学期内按 course_id 独立择最高（后学期重修不抹除旧学期失败）
        Map<String, Map<String, Map<String, Object>>> bestByTermCourse = new LinkedHashMap<>();

        for (Map<String, Object> r : allCandidates) {
            if (r.get("credit") == null) {
                throw BizException.unprocessable("SOURCE_MISSING",
                        "课程【" + r.get("course_id") + "】缺少学分定义，无法重算学业统计");
            }
            BigDecimal credit = toBigDecimal(r.get("credit"));
            if (credit.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.unprocessable("INVALID_CREDIT", "课程学分不能为负数");
            }
            int scoreType = ((Number) r.get("score_type")).intValue();
            BigDecimal totalScore = toBigDecimal(r.get("total_score"));
            String scoreLevel = r.get("score_level") == null ? null : String.valueOf(r.get("score_level"));
            BigDecimal gp = toBigDecimal(r.get("grade_point"));
            BigDecimal equiv = resolveEquivalentScore(scoreType, totalScore, scoreLevel);
            if (gp == null) {
                throw BizException.unprocessable("SOURCE_MISSING",
                        "课程【" + r.get("course_id") + "】已发布成绩缺少绩点，无法重算");
            }

            Map<String, Object> enriched = new LinkedHashMap<>(r);
            enriched.put("_credit", credit);
            enriched.put("_gp", gp);
            enriched.put("_equiv", equiv);
            enriched.put("_passed", isPassed(scoreType, totalScore, scoreLevel, gp));

            String courseId = String.valueOf(r.get("course_id"));
            String term = String.valueOf(r.get("term"));

            bestByCourse.merge(courseId, enriched, this::pickHigherRecord);
            bestByTermCourse
                    .computeIfAbsent(term, k -> new LinkedHashMap<>())
                    .merge(courseId, enriched, this::pickHigherRecord);
        }

        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal failed = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal gpWeighted = BigDecimal.ZERO;
        BigDecimal scoreWeighted = BigDecimal.ZERO;

        for (Map<String, Object> best : bestByCourse.values()) {
            BigDecimal credit = (BigDecimal) best.get("_credit");
            BigDecimal gp = (BigDecimal) best.get("_gp");
            BigDecimal equiv = (BigDecimal) best.get("_equiv");
            boolean passed = Boolean.TRUE.equals(best.get("_passed"));

            if (passed) {
                earned = earned.add(credit);
            } else {
                failed = failed.add(credit);
            }
            totalCredit = totalCredit.add(credit);
            gpWeighted = gpWeighted.add(gp.multiply(credit));
            scoreWeighted = scoreWeighted.add(equiv.multiply(credit));
        }

        BigDecimal gpa = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? gpWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;
        BigDecimal avgScore = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? scoreWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;

        jdbc.update("""
                INSERT INTO stu_gpa (student_id, earned_credit, failed_credit, gpa, avg_score, major_rank, major_rank_total, update_time)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, NOW())
                ON DUPLICATE KEY UPDATE
                  earned_credit = VALUES(earned_credit),
                  failed_credit = VALUES(failed_credit),
                  gpa = VALUES(gpa),
                  avg_score = VALUES(avg_score),
                  major_rank = IF(VALUES(avg_score) IS NULL, NULL, major_rank),
                  major_rank_total = IF(VALUES(avg_score) IS NULL, NULL, major_rank_total),
                  update_time = NOW()
                """, studentId, earned, failed, gpa, avgScore);

        int updatedTerms = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : bestByTermCourse.entrySet()) {
            String term = entry.getKey();
            BigDecimal tEarned = BigDecimal.ZERO;
            BigDecimal tFailed = BigDecimal.ZERO;
            for (Map<String, Object> best : entry.getValue().values()) {
                BigDecimal credit = (BigDecimal) best.get("_credit");
                if (Boolean.TRUE.equals(best.get("_passed"))) {
                    tEarned = tEarned.add(credit);
                } else {
                    tFailed = tFailed.add(credit);
                }
            }
            jdbc.update("""
                    INSERT INTO stu_term_gpa (student_id, term, term_credit, term_failed_credit, update_time)
                    VALUES (?, ?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE
                      term_credit = VALUES(term_credit),
                      term_failed_credit = VALUES(term_failed_credit),
                      update_time = NOW()
                    """, studentId, term, tEarned, tFailed);
            updatedTerms++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("student_id", studentId);
        summary.put("stat_stage", statStage);
        summary.put("major_id", stu.get("major_id"));
        summary.put("grade", stu.get("grade"));
        summary.put("earned_credit", earned.setScale(1, RoundingMode.HALF_UP).toPlainString());
        summary.put("failed_credit", failed.setScale(1, RoundingMode.HALF_UP).toPlainString());
        summary.put("gpa", gpa == null ? null : gpa.toPlainString());
        summary.put("avg_score", avgScore == null ? null : avgScore.toPlainString());
        summary.put("updated_terms", updatedTerms);
        return summary;
    }

    private Map<String, Object> pickHigherRecord(Map<String, Object> a, Map<String, Object> b) {
        BigDecimal gpA = (BigDecimal) a.get("_gp");
        BigDecimal gpB = (BigDecimal) b.get("_gp");
        int cmpGp = gpA.compareTo(gpB);
        if (cmpGp != 0) {
            return cmpGp > 0 ? a : b;
        }
        BigDecimal eqA = (BigDecimal) a.get("_equiv");
        BigDecimal eqB = (BigDecimal) b.get("_equiv");
        int cmpEq = eqA.compareTo(eqB);
        if (cmpEq != 0) {
            return cmpEq > 0 ? a : b;
        }
        // 同分取最近一次
        String termA = String.valueOf(a.get("term"));
        String termB = String.valueOf(b.get("term"));
        int cmpTerm = termA.compareTo(termB);
        if (cmpTerm != 0) {
            return cmpTerm > 0 ? a : b;
        }
        long idA = ((Number) a.get("sort_id")).longValue();
        long idB = ((Number) b.get("sort_id")).longValue();
        return idA >= idB ? a : b;
    }

    private void recomputeRankForMajorGrade(String majorId, int grade) {
        // 先清空无可计入成绩（avg_score IS NULL）学生的旧排名，不保留过期名次
        jdbc.update("""
                UPDATE stu_gpa g
                JOIN student st ON g.student_id = st.student_id
                SET g.major_rank = NULL, g.major_rank_total = NULL
                WHERE st.major_id = ? AND st.grade = ? AND g.avg_score IS NULL
                """, majorId, grade);

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
            BigDecimal cur = toBigDecimal(peers.get(i).get("avg_score"));
            if (prevScore == null || cur.compareTo(prevScore) != 0) {
                currentRank = i + 1;
                prevScore = cur;
            }
            jdbc.update("""
                    UPDATE stu_gpa
                    SET major_rank = ?, major_rank_total = ?, update_time = NOW()
                    WHERE student_id = ?
                    """, currentRank, totalPeers, peers.get(i).get("student_id"));
        }
    }

    // =========================================================================
    // 4.4 成绩单预览与打印（P33，含来校交换生中英文正式成绩单）
    // =========================================================================

    /** 教务处/管理员可选学生清单（含本校学生与来校交换生） */
    public List<Map<String, Object>> listTranscriptStudents() {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        List<Map<String, Object>> list = new ArrayList<>();
        list.addAll(jdbc.queryForList("""
                SELECT student_id, student_name, degree_level, class_name, 0 AS is_incoming_exchange
                FROM student
                ORDER BY student_id ASC
                """));
        list.addAll(jdbc.queryForList("""
                SELECT temp_student_no AS student_id, student_name, '本科交换生' AS degree_level,
                       sender_school_name AS class_name, 1 AS is_incoming_exchange
                FROM v_exchange_student
                ORDER BY temp_student_no ASC
                """));
        for (Map<String, Object> item : list) {
            item.put("is_incoming_exchange", ((Number) item.get("is_incoming_exchange")).intValue() == 1);
        }
        return list;
    }

    /** 4.4 生成成绩单预览与打印数据（学生端强制复核 4.1 评教门禁，保留全部历次考试记录） */
    public Map<String, Object> generateTranscriptPreview(Map<String, Object> body) {
        CurrentUser user = UserContext.requireRole(
                RoleEnum.STUDENT, RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        String requestedSid = body.get("student_id") == null ? null : String.valueOf(body.get("student_id")).trim();
        String targetStudentId;
        boolean officialMode = false;

        if (user.role() == RoleEnum.STUDENT) {
            if (user.studentId() == null || user.studentId().isBlank()) {
                throw BizException.forbidden("当前学生账号缺少学号绑定");
            }
            if (requestedSid != null && !requestedSid.isBlank() && !requestedSid.equals(user.studentId())) {
                throw BizException.forbidden("学生仅可预览和打印本人成绩单，禁止切换他人学号");
            }
            targetStudentId = user.studentId();
        } else {
            if (requestedSid == null || requestedSid.isBlank()) {
                throw BizException.unprocessable("MISSING_STUDENT_ID", "请选择需要出具成绩单的学生");
            }
            targetStudentId = requestedSid;
            officialMode = true;
        }

        String startTerm = body.get("start_term") == null ? null : String.valueOf(body.get("start_term")).trim();
        String endTerm = body.get("end_term") == null ? null : String.valueOf(body.get("end_term")).trim();
        if (startTerm != null && startTerm.isEmpty()) startTerm = null;
        if (endTerm != null && endTerm.isEmpty()) endTerm = null;
        validateTermFormat(startTerm);
        validateTermFormat(endTerm);
        if (startTerm != null && endTerm != null && startTerm.compareTo(endTerm) > 0) {
            throw BizException.unprocessable("INVALID_TERM_RANGE", "起始学期不能晚于结束学期");
        }

        String currentTerm = getCurrentTerm();
        boolean includesCurrentTerm = isRangeCoveringTerm(startTerm, endTerm, currentTerm);
        boolean currentTermCompleted = true;

        if (user.role() == RoleEnum.STUDENT) {
            Map<String, Object> gate = buildEvaluationGateInfo(targetStudentId, currentTerm);
            currentTermCompleted = Boolean.TRUE.equals(gate.get("all_completed"));
            if (includesCurrentTerm && !currentTermCompleted) {
                throw new BizException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "EVAL_REQUIRED",
                        "当前学期（" + currentTerm + "）尚有 " + gate.get("pending_count") + " 门课程未完成评教，无法预览或打印包含当期的成绩单"
                );
            }
        }

        Map<String, Object> profile = loadStudentProfile(targetStudentId);
        boolean isIncomingExchange = Boolean.TRUE.equals(profile.get("is_incoming_exchange"));
        String requestedTemplate = body.get("template") == null ? "AUTO" : String.valueOf(body.get("template")).trim().toUpperCase();
        String template = "AUTO".equals(requestedTemplate)
                ? (isIncomingExchange ? "EXCHANGE_BILINGUAL" : "STANDARD")
                : requestedTemplate;

        // 绝不按 is_final 过滤：保留正考、补考、重修、不及格、缺考等全部已发布记录
        List<Map<String, Object>> records = fetchPublishedGradeRecords(targetStudentId, null, startTerm, endTerm);

        // 计算打印区间内汇总（尤其用于来校交换生中英文成绩单）
        BigDecimal totalAttemptCredits = BigDecimal.ZERO;
        BigDecimal earnedCredits = BigDecimal.ZERO;
        BigDecimal gpWeighted = BigDecimal.ZERO;
        BigDecimal scoreWeighted = BigDecimal.ZERO;
        for (Map<String, Object> r : records) {
            BigDecimal credit = toBigDecimal(r.get("credit"));
            BigDecimal gp = toBigDecimal(r.get("grade_point"));
            int scoreType = ((Number) r.get("score_type")).intValue();
            BigDecimal totalScore = toBigDecimal(r.get("total_score"));
            String scoreLevel = r.get("score_level") == null ? null : String.valueOf(r.get("score_level"));
            if (credit != null) {
                totalAttemptCredits = totalAttemptCredits.add(credit);
                if (Boolean.TRUE.equals(r.get("passed"))) {
                    earnedCredits = earnedCredits.add(credit);
                }
                if (gp != null) {
                    gpWeighted = gpWeighted.add(gp.multiply(credit));
                }
                try {
                    BigDecimal eq = resolveEquivalentScore(scoreType, totalScore, scoreLevel);
                    scoreWeighted = scoreWeighted.add(eq.multiply(credit));
                } catch (BizException ignored) {
                    // 特殊缺考等记录不强行折算
                }
            }
        }

        BigDecimal rangeGpa = totalAttemptCredits.compareTo(BigDecimal.ZERO) > 0
                ? gpWeighted.divide(totalAttemptCredits, 2, RoundingMode.HALF_UP) : null;
        BigDecimal rangeAvg = totalAttemptCredits.compareTo(BigDecimal.ZERO) > 0
                ? scoreWeighted.divide(totalAttemptCredits, 2, RoundingMode.HALF_UP) : null;

        // 本校学生累计摘要（仅在非交换生且未受当期评教门禁隐藏时附带）
        Map<String, Object> cumulativeSummary = null;
        if (!isIncomingExchange && (officialMode || currentTermCompleted)) {
            List<Map<String, Object>> gpaRows = jdbc.queryForList(
                    "SELECT earned_credit, failed_credit, gpa, avg_score, major_rank, major_rank_total, update_time FROM stu_gpa WHERE student_id = ?",
                    targetStudentId);
            if (!gpaRows.isEmpty()) {
                cumulativeSummary = gpaRows.get(0);
            }
        }

        String issueDate = LocalDate.now().toString();
        String transcriptNo = isIncomingExchange
                ? "EX-" + currentTerm.substring(2, 4) + currentTerm.substring(7, 9) + "-" + targetStudentId.substring(Math.max(0, targetStudentId.length() - 4))
                : "TR-" + targetStudentId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        Map<String, Object> rangeSummary = new LinkedHashMap<>();
        rangeSummary.put("course_count", records.size());
        rangeSummary.put("total_credit", totalAttemptCredits.setScale(1, RoundingMode.HALF_UP).toPlainString());
        rangeSummary.put("earned_credit", earnedCredits.setScale(1, RoundingMode.HALF_UP).toPlainString());
        rangeSummary.put("gpa", rangeGpa == null ? null : rangeGpa.toPlainString());
        rangeSummary.put("avg_score", rangeAvg == null ? null : rangeAvg.toPlainString());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("template", template);
        resp.put("is_incoming_exchange", isIncomingExchange);
        resp.put("transcript_no", transcriptNo);
        resp.put("issue_date", issueDate);
        resp.put("generated_at", LocalDateTime.now().format(TS_FMT));
        resp.put("term_range_label", (startTerm == null && endTerm == null)
                ? "全部学期 / All Terms"
                : (startTerm == null ? "起始" : startTerm) + " ~ " + (endTerm == null ? "至今" : endTerm));
        resp.put("student", profile);
        resp.put("items", records);
        resp.put("range_summary", rangeSummary);
        resp.put("cumulative_summary", cumulativeSummary);
        resp.put("official_mode", officialMode);
        resp.put("footer_notice", isIncomingExchange
                ? "本成绩单仅包含已由本校教务处正式发布（score_status=3）的课程成绩，供派出院校核验与学分转换使用；经教务处核对签章后具有正式证明效力。"
                : (officialMode
                ? "本成绩单包含所选范围全部已发布考试记录（含补考、重修与原等级），累计 GPA 仅按本阶段最高最终成绩（不含辅修）计算。"
                : "原型可打印成绩明细，无正式认证效力；正式证明请通过学校教务处办理。"));
        return resp;
    }

    private boolean isRangeCoveringTerm(String startTerm, String endTerm, String currentTerm) {
        if (startTerm == null && endTerm == null) {
            return true;
        }
        if (startTerm != null && currentTerm.compareTo(startTerm) < 0) {
            return false;
        }
        if (endTerm != null && currentTerm.compareTo(endTerm) > 0) {
            return false;
        }
        return true;
    }

    // =========================================================================
    // 4.5 成绩统计报表（三分支：成绩分布 / 最终不及格名单 / 首次评优专业排名）
    // =========================================================================

    /** 4.5 报表查询条件选项（按当前登录角色严格裁剪教学班与范围） */
    public Map<String, Object> getReportOptions() {
        CurrentUser user = UserContext.requireRole(
                RoleEnum.TEACHER, RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        StringBuilder tcSql = new StringBuilder("""
                SELECT tc.tc_id, tc.term, tc.course_id, c.course_name, c.open_college,
                       COALESCE(cm.dict_name, c.open_college) AS college_name,
                       lc.lesson_no, tc.teacher_id,
                       COALESCE(emp.emp_name, tc.teacher_id) AS teacher_name
                FROM teaching_class tc
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN sys_code_dict cm ON cm.dict_group = 'college_map' AND cm.dict_code = c.open_college
                LEFT JOIN sys_code_dict tm ON tm.dict_group = 'teacher_map' AND tm.dict_code = tc.teacher_id
                LEFT JOIN t_employee emp ON emp.emp_id = tm.dict_name
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (user.role() == RoleEnum.TEACHER) {
            tcSql.append(" AND tc.teacher_id = ? ");
            params.add(resolveTeacherCode8(user));
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            tcSql.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        }
        tcSql.append(" ORDER BY tc.term DESC, tc.tc_id ASC");

        List<Map<String, Object>> classes = jdbc.queryForList(tcSql.toString(), params.toArray());
        List<String> terms = jdbc.queryForList("SELECT DISTINCT term FROM teaching_class ORDER BY term DESC", String.class);
        List<Map<String, Object>> majors = jdbc.queryForList(
                "SELECT DISTINCT major_id, grade FROM student WHERE major_id IS NOT NULL ORDER BY major_id ASC, grade DESC");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("current_term", getCurrentTerm());
        resp.put("teaching_classes", classes);
        resp.put("terms", terms);
        resp.put("major_grades", majors);
        resp.put("can_honor_rank", user.hasRole(
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN));
        return resp;
    }

    /** 4.5 统一成绩统计报表入口（DO CASE report_type） */
    public Map<String, Object> queryGradeReport(
            String reportType, String term, String tcId, Integer examType,
            String majorId, Integer grade, String startTerm, String endTerm) {
        CurrentUser user = UserContext.requireRole(
                RoleEnum.TEACHER, RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        if (reportType == null || reportType.isBlank()) {
            throw BizException.unprocessable("MISSING_REPORT_TYPE", "必须指定报表类型（DISTRIBUTION / FINAL_FAILED / HONOR_RANK）");
        }
        String rt = reportType.trim().toUpperCase();
        validateTermFormat(term);
        validateTermFormat(startTerm);
        validateTermFormat(endTerm);

        // 若指定了教学班，核验当前角色是否有权访问该教学班
        if (tcId != null && !tcId.isBlank()) {
            verifyTeachingClassAccess(user, tcId.trim());
        }

        return switch (rt) {
            case "DISTRIBUTION" -> buildDistributionReport(user, term, tcId, examType == null ? 1 : examType);
            case "FINAL_FAILED" -> buildFinalFailedReport(user, term, tcId);
            case "HONOR_RANK" -> buildHonorRankReport(user, majorId, grade, startTerm, endTerm);
            default -> throw BizException.unprocessable("UNKNOWN_REPORT_TYPE", "不支持的报表类型: " + reportType);
        };
    }

    private void verifyTeachingClassAccess(CurrentUser user, String tcId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT tc.tc_id, tc.teacher_id, c.open_college
                FROM teaching_class tc
                JOIN course c ON tc.course_id = c.course_id
                WHERE tc.tc_id = ?
                """, tcId);
        if (rows.isEmpty()) {
            throw BizException.notFound("教学班【" + tcId + "】不存在");
        }
        Map<String, Object> tc = rows.get(0);
        if (user.role() == RoleEnum.TEACHER) {
            String t8 = resolveTeacherCode8(user);
            if (!t8.equals(String.valueOf(tc.get("teacher_id")).trim())) {
                throw BizException.forbidden("教师仅可查询本人授课教学班的统计报表");
            }
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            if (!user.collegeId().equals(String.valueOf(tc.get("open_college")).trim())) {
                throw BizException.forbidden("学院教学秘书仅可查询本学院开课教学班的统计报表");
            }
        }
    }

    /** 4.5 分支一：成绩分布报表（按 score_type 分百分制 / 五级制 / 两级制独立统计，绝不混分制） */
    private Map<String, Object> buildDistributionReport(CurrentUser user, String term, String tcId, int examType) {
        if (examType != 1 && examType != 2) {
            throw BizException.unprocessable("INVALID_EXAM_TYPE", "考试类别必须为 1（正常考试）或 2（补考）");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT s.score_id, s.sc_id, s.score_type, s.exam_flag, s.total_score, s.score_level, s.grade_point,
                       tc.tc_id, tc.term, c.course_id, c.course_name
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE s.score_status = 3 AND s.exam_type = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(examType);

        if (user.role() == RoleEnum.TEACHER) {
            sql.append(" AND tc.teacher_id = ? ");
            params.add(resolveTeacherCode8(user));
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            sql.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        }
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        if (tcId != null && !tcId.isBlank()) {
            sql.append(" AND tc.tc_id = ? ");
            params.add(tcId.trim());
        }

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

        // 百分制分段：[90,100], [80,90), [70,80), [60,70), [0,60)
        int[] pctCounts = new int[5];
        String[] pctLabels = {"90~100（优秀）", "80~89.9（良好）", "70~79.9（中等）", "60~69.9（及格）", "0~59.9（不及格）"};
        BigDecimal pctSum = BigDecimal.ZERO;
        BigDecimal pctMax = null;
        BigDecimal pctMin = null;
        int pctValid = 0;
        int pctPass = 0;

        // 五级制
        Map<String, Integer> fiveMap = new LinkedHashMap<>();
        for (String lv : List.of("优", "良", "中", "及格", "不及格")) fiveMap.put(lv, 0);
        int fiveValid = 0;
        int fivePass = 0;

        // 两级制
        Map<String, Integer> twoMap = new LinkedHashMap<>();
        for (String lv : List.of("合格", "不合格")) twoMap.put(lv, 0);
        int twoValid = 0;
        int twoPass = 0;

        int specialExamCount = 0;
        int missingValueCount = 0;

        for (Map<String, Object> r : rows) {
            int st = ((Number) r.get("score_type")).intValue();
            int flag = ((Number) r.get("exam_flag")).intValue();
            if (flag != 0) {
                specialExamCount++;
            }
            if (st == 1) {
                BigDecimal val = toBigDecimal(r.get("total_score"));
                if (val == null) {
                    missingValueCount++;
                    continue;
                }
                pctValid++;
                pctSum = pctSum.add(val);
                if (pctMax == null || val.compareTo(pctMax) > 0) pctMax = val;
                if (pctMin == null || val.compareTo(pctMin) < 0) pctMin = val;
                double d = val.doubleValue();
                if (d >= 90.0) { pctCounts[0]++; pctPass++; }
                else if (d >= 80.0) { pctCounts[1]++; pctPass++; }
                else if (d >= 70.0) { pctCounts[2]++; pctPass++; }
                else if (d >= 60.0) { pctCounts[3]++; pctPass++; }
                else { pctCounts[4]++; }
            } else if (st == 2) {
                String lv = r.get("score_level") == null ? null : String.valueOf(r.get("score_level")).trim();
                if (lv == null || !fiveMap.containsKey(lv)) {
                    missingValueCount++;
                    continue;
                }
                fiveMap.put(lv, fiveMap.get(lv) + 1);
                fiveValid++;
                if (!"不及格".equals(lv)) fivePass++;
            } else if (st == 3) {
                String lv = r.get("score_level") == null ? null : String.valueOf(r.get("score_level")).trim();
                if (lv == null || !twoMap.containsKey(lv)) {
                    missingValueCount++;
                    continue;
                }
                twoMap.put(lv, twoMap.get(lv) + 1);
                twoValid++;
                if ("合格".equals(lv)) twoPass++;
            }
        }

        List<Map<String, Object>> pctBuckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", pctLabels[i]);
            b.put("count", pctCounts[i]);
            b.put("ratio", pctValid > 0
                    ? BigDecimal.valueOf(pctCounts[i] * 100.0 / pctValid).setScale(2, RoundingMode.HALF_UP).toPlainString()
                    : null);
            pctBuckets.add(b);
        }

        Map<String, Object> percentGroup = new LinkedHashMap<>();
        percentGroup.put("valid_count", pctValid);
        percentGroup.put("pass_count", pctPass);
        percentGroup.put("pass_rate", pctValid > 0
                ? BigDecimal.valueOf(pctPass * 100.0 / pctValid).setScale(2, RoundingMode.HALF_UP).toPlainString() : null);
        percentGroup.put("avg_score", pctValid > 0
                ? pctSum.divide(BigDecimal.valueOf(pctValid), 2, RoundingMode.HALF_UP).toPlainString() : null);
        percentGroup.put("max_score", pctMax == null ? null : pctMax.toPlainString());
        percentGroup.put("min_score", pctMin == null ? null : pctMin.toPlainString());
        percentGroup.put("buckets", pctBuckets);

        Map<String, Object> fiveGroup = new LinkedHashMap<>();
        fiveGroup.put("valid_count", fiveValid);
        fiveGroup.put("pass_count", fivePass);
        fiveGroup.put("pass_rate", fiveValid > 0
                ? BigDecimal.valueOf(fivePass * 100.0 / fiveValid).setScale(2, RoundingMode.HALF_UP).toPlainString() : null);
        fiveGroup.put("buckets", mapBuckets(fiveMap, fiveValid));

        Map<String, Object> twoGroup = new LinkedHashMap<>();
        twoGroup.put("valid_count", twoValid);
        twoGroup.put("pass_count", twoPass);
        twoGroup.put("pass_rate", twoValid > 0
                ? BigDecimal.valueOf(twoPass * 100.0 / twoValid).setScale(2, RoundingMode.HALF_UP).toPlainString() : null);
        twoGroup.put("buckets", mapBuckets(twoMap, twoValid));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("report_type", "DISTRIBUTION");
        resp.put("exam_type", examType);
        resp.put("exam_type_label", examType == 1 ? "正常考试" : "补考");
        resp.put("term", term);
        resp.put("tc_id", tcId);
        resp.put("total_records", rows.size());
        resp.put("special_exam_count", specialExamCount);
        resp.put("missing_value_count", missingValueCount);
        resp.put("percent_group", percentGroup);
        resp.put("five_level_group", fiveGroup);
        resp.put("two_level_group", twoGroup);
        resp.put("generated_at", LocalDateTime.now().format(TS_FMT));
        resp.put("methodology_note", "百分制按 [90,100]、[80,90)、[70,80)、[60,70)、[0,60) 左闭右开分段；五级制与两级制独立分列、不混入百分制也不计算等级算术均分；仅统计 score_status=3 已发布成绩。");
        return resp;
    }

    private List<Map<String, Object>> mapBuckets(Map<String, Integer> map, int total) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", e.getKey());
            b.put("count", e.getValue());
            b.put("ratio", total > 0
                    ? BigDecimal.valueOf(e.getValue() * 100.0 / total).setScale(2, RoundingMode.HALF_UP).toPlainString()
                    : null);
            list.add(b);
        }
        return list;
    }

    /** 4.5 分支二：当前最终不及格名单（先按本阶段同课程择最高，再投影回授权范围，补考/重修已通过者不误列） */
    private Map<String, Object> buildFinalFailedReport(CurrentUser user, String term, String tcId) {
        StringBuilder scopeSql = new StringBuilder("""
                SELECT DISTINCT sc.student_id, sc.stat_stage, tc.course_id,
                       tc.tc_id, tc.term, lc.lesson_no,
                       c.course_name, c.credit,
                       COALESCE(st.student_name, ex.student_name) AS student_name,
                       COALESCE(st.class_name, '来校交换生') AS class_name
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN student st ON sc.student_id = st.student_id
                LEFT JOIN v_exchange_student ex ON sc.student_id = ex.temp_student_no
                WHERE s.score_status = 3
                """);
        List<Object> params = new ArrayList<>();
        if (user.role() == RoleEnum.TEACHER) {
            scopeSql.append(" AND tc.teacher_id = ? ");
            params.add(resolveTeacherCode8(user));
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            scopeSql.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        }
        if (term != null && !term.isBlank()) {
            scopeSql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        if (tcId != null && !tcId.isBlank()) {
            scopeSql.append(" AND tc.tc_id = ? ");
            params.add(tcId.trim());
        }
        scopeSql.append(" ORDER BY tc.term DESC, tc.tc_id ASC, sc.student_id ASC");

        List<Map<String, Object>> scopedPairs = jdbc.queryForList(scopeSql.toString(), params.toArray());
        List<Map<String, Object>> failedItems = new ArrayList<>();
        Set<String> dedupPair = new LinkedHashSet<>();
        Set<String> distinctStudents = new LinkedHashSet<>();

        for (Map<String, Object> p : scopedPairs) {
            String sid = String.valueOf(p.get("student_id"));
            int stage = ((Number) p.get("stat_stage")).intValue();
            String cid = String.valueOf(p.get("course_id"));
            String pairKey = sid + "|" + stage + "|" + cid;
            if (!dedupPair.add(pairKey)) {
                continue;
            }

            // 跨本人该阶段同课程全部已发布记录（含补考/重修/外校认定）择最高一次
            List<Map<String, Object>> attempts = jdbc.queryForList("""
                    SELECT s.score_type, s.total_score, s.score_level, s.grade_point,
                           s.exam_type, s.exam_flag, sc.study_type, tc.term
                    FROM score s
                    JOIN sc ON s.sc_id = sc.sc_id
                    JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                    WHERE sc.student_id = ? AND sc.stat_stage = ? AND tc.course_id = ? AND s.score_status = 3
                    UNION ALL
                    SELECT e.score_type, e.converted_score AS total_score, e.converted_level AS score_level,
                           e.grade_point, 1 AS exam_type, 0 AS exam_flag, 1 AS study_type, e.term
                    FROM exchange_score_convert e
                    WHERE e.student_id = ? AND e.stat_stage = ? AND e.course_id = ? AND e.audit_status = 2
                    """, sid, stage, cid, sid, stage, cid);

            boolean everPassed = false;
            Map<String, Object> bestAttempt = null;
            BigDecimal bestGp = new BigDecimal("-1");
            for (Map<String, Object> att : attempts) {
                int st = ((Number) att.get("score_type")).intValue();
                BigDecimal ts = toBigDecimal(att.get("total_score"));
                String lv = att.get("score_level") == null ? null : String.valueOf(att.get("score_level"));
                BigDecimal gp = toBigDecimal(att.get("grade_point"));
                if (isPassed(st, ts, lv, gp)) {
                    everPassed = true;
                    break;
                }
                BigDecimal cmpGp = gp == null ? BigDecimal.ZERO : gp;
                if (bestAttempt == null || cmpGp.compareTo(bestGp) > 0) {
                    bestGp = cmpGp;
                    bestAttempt = att;
                }
            }
            if (!everPassed && bestAttempt != null) {
                distinctStudents.add(sid);
                int st = ((Number) bestAttempt.get("score_type")).intValue();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("student_id", sid);
                row.put("student_name", p.get("student_name"));
                row.put("class_name", p.get("class_name"));
                row.put("term", p.get("term"));
                row.put("tc_id", p.get("tc_id"));
                row.put("lesson_no", p.get("lesson_no"));
                row.put("course_id", cid);
                row.put("course_name", p.get("course_name"));
                row.put("credit", p.get("credit"));
                row.put("best_score", st == 1 ? bestAttempt.get("total_score") : bestAttempt.get("score_level"));
                row.put("exam_type", bestAttempt.get("exam_type"));
                row.put("study_type", bestAttempt.get("study_type"));
                row.put("exam_flag", bestAttempt.get("exam_flag"));
                failedItems.add(row);
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("report_type", "FINAL_FAILED");
        resp.put("term", term);
        resp.put("tc_id", tcId);
        resp.put("items", failedItems);
        resp.put("failed_record_count", failedItems.size());
        resp.put("distinct_student_count", distinctStudents.size());
        resp.put("generated_at", LocalDateTime.now().format(TS_FMT));
        resp.put("methodology_note", "先在同学生同阶段同课程的全部已发布成绩（含补考/重修）中确定最高成绩，已通过者自动消除当前最终不及格；同一学生多门不及格按学生去重统计总人数，且绝不向教师暴露他班明细或学生累计 GPA/排名。");
        return resp;
    }

    /** 4.5 分支三：首次考核评优专业排名（P36，仅教务处/管理员；先核验完整性证据，不全则明确阻断，绝不覆盖 stu_gpa） */
    private Map<String, Object> buildHonorRankReport(
            CurrentUser user, String majorId, Integer grade, String startTerm, String endTerm) {
        if (!user.hasRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN)) {
            throw BizException.forbidden("首次考核评优专业排名仅限教务处/研究生院/系统管理员生成");
        }
        if (majorId == null || majorId.isBlank() || grade == null) {
            throw BizException.unprocessable("MISSING_MAJOR_GRADE", "评优专业排名必须指定专业编号（major_id）与年级（grade）");
        }
        String mId = majorId.trim();
        String sTerm = (startTerm == null || startTerm.isBlank()) ? getCurrentTerm() : startTerm.trim();
        String eTerm = (endTerm == null || endTerm.isBlank()) ? sTerm : endTerm.trim();
        if (sTerm.compareTo(eTerm) > 0) {
            throw BizException.unprocessable("INVALID_TERM_RANGE", "评价期起始学期不能晚于结束学期");
        }

        // 1. 校验专业教学计划必修课集合（program_course）
        List<Map<String, Object>> planCourses = jdbc.queryForList("""
                SELECT pc.course_id, c.course_name, c.credit, c.course_type
                FROM program_course pc
                JOIN course c ON pc.course_id = c.course_id
                WHERE pc.major_id = ? AND pc.grade = ?
                ORDER BY pc.course_id ASC
                """, mId, String.valueOf(grade));

        // 2. 校验参评学生全集（student）
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT student_id, student_name, degree_level, class_name
                FROM student
                WHERE major_id = ? AND grade = ?
                ORDER BY student_id ASC
                """, mId, grade);

        List<String> blockedReasons = new ArrayList<>();
        if (planCourses.isEmpty()) {
            blockedReasons.add("缺少专业【" + mId + "】" + grade + " 级完整必修教学计划（program_course 为空），无法确认全部必修课集合。");
        }
        if (students.isEmpty()) {
            blockedReasons.add("未找到专业【" + mId + "】" + grade + " 级参评学生群体。");
        }

        // 3. 校验评价期内必修课初修正考是否全部已发布、无未决缓考
        int unpublishedCount = 0;
        int deferredCount = 0;
        List<String> unpublishedDetails = new ArrayList<>();
        if (!planCourses.isEmpty() && !students.isEmpty()) {
            List<String> courseIds = planCourses.stream().map(c -> String.valueOf(c.get("course_id"))).toList();
            String inClause = String.join(",", Collections.nCopies(courseIds.size(), "?"));
            List<Object> checkParams = new ArrayList<>();
            checkParams.add(mId);
            checkParams.add(grade);
            checkParams.add(sTerm);
            checkParams.add(eTerm);
            checkParams.addAll(courseIds);

            List<Map<String, Object>> unpubRows = jdbc.queryForList("""
                    SELECT c.course_id, c.course_name, COUNT(*) AS cnt
                    FROM score s
                    JOIN sc ON s.sc_id = sc.sc_id
                    JOIN student st ON sc.student_id = st.student_id
                    JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                    JOIN course c ON tc.course_id = c.course_id
                    WHERE st.major_id = ? AND st.grade = ?
                      AND tc.term BETWEEN ? AND ?
                      AND tc.course_id IN (""" + inClause + """
                      )
                      AND sc.study_type = 1 AND s.exam_type = 1
                      AND s.score_status <> 3
                    GROUP BY c.course_id, c.course_name
                    """, checkParams.toArray());
            for (Map<String, Object> ur : unpubRows) {
                int cnt = ((Number) ur.get("cnt")).intValue();
                unpublishedCount += cnt;
                unpublishedDetails.add(ur.get("course_id") + " " + ur.get("course_name") + "（" + cnt + " 条未发布）");
            }
            if (unpublishedCount > 0) {
                blockedReasons.add("评价期内必修课尚有未发布初次考试成绩：" + String.join("；", unpublishedDetails)
                        + "。不满足首次考核发布完成前提，严禁将未发布课程当 0 分强行生成正式评优排名。");
            }

            Integer defCnt = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM score s
                    JOIN sc ON s.sc_id = sc.sc_id
                    JOIN student st ON sc.student_id = st.student_id
                    JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                    WHERE st.major_id = ? AND st.grade = ?
                      AND tc.term BETWEEN ? AND ?
                      AND tc.course_id IN (""" + inClause + """
                      )
                      AND sc.study_type = 1 AND s.exam_type = 1
                      AND s.exam_flag = 2
                    """, Integer.class, checkParams.toArray());
            deferredCount = defCnt == null ? 0 : defCnt;
            if (deferredCount > 0) {
                blockedReasons.add("评价期内存在 " + deferredCount + " 条缓考未决记录，须核定后再生成正式评优排名。");
            }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("major_id", mId);
        evidence.put("grade", grade);
        evidence.put("start_term", sTerm);
        evidence.put("end_term", eTerm);
        evidence.put("plan_complete", !planCourses.isEmpty());
        evidence.put("plan_course_count", planCourses.size());
        evidence.put("plan_courses", planCourses);
        evidence.put("population_complete", !students.isEmpty());
        evidence.put("population_count", students.size());
        evidence.put("unpublished_count", unpublishedCount);
        evidence.put("deferred_count", deferredCount);
        evidence.put("first_attempt_publication_closed", blockedReasons.isEmpty());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("report_type", "HONOR_RANK");
        resp.put("evidence", evidence);
        resp.put("generated_at", LocalDateTime.now().format(TS_FMT));
        resp.put("methodology_note", "首次评优排名仅按评价期内专业培养计划全部必修课的第一次初修正考成绩（study_type=1 且 exam_type=1，不使用 is_final）加权计算；独立报表输出，绝不覆盖 stu_gpa 通用排名。");

        if (!blockedReasons.isEmpty()) {
            resp.put("status", "BLOCKED");
            resp.put("blocked_reasons", blockedReasons);
            resp.put("items", List.of());
            return resp;
        }

        // 完整性全部通过时，计算独立首次评优均分与并列跳号排名
        BigDecimal totalRequiredCredit = BigDecimal.ZERO;
        for (Map<String, Object> c : planCourses) {
            totalRequiredCredit = totalRequiredCredit.add(toBigDecimal(c.get("credit")));
        }

        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> stu : students) {
            String sid = String.valueOf(stu.get("student_id"));
            int stage = mapDegreeLevelToStage(String.valueOf(stu.get("degree_level")));
            BigDecimal weightedSum = BigDecimal.ZERO;
            List<String> missingCourses = new ArrayList<>();

            for (Map<String, Object> pc : planCourses) {
                String cid = String.valueOf(pc.get("course_id"));
                BigDecimal credit = toBigDecimal(pc.get("credit"));
                List<Map<String, Object>> firstRows = jdbc.queryForList("""
                        SELECT s.score_type, s.total_score, s.score_level
                        FROM score s
                        JOIN sc ON s.sc_id = sc.sc_id
                        JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                        WHERE sc.student_id = ? AND sc.stat_stage = ? AND tc.course_id = ?
                          AND tc.term BETWEEN ? AND ?
                          AND sc.study_type = 1 AND s.exam_type = 1 AND s.score_status = 3
                        ORDER BY tc.term ASC, s.score_id ASC
                        LIMIT 1
                        """, sid, stage, cid, sTerm, eTerm);
                if (firstRows.isEmpty()) {
                    missingCourses.add(cid + " " + pc.get("course_name"));
                } else {
                    Map<String, Object> fr = firstRows.get(0);
                    BigDecimal eq = resolveEquivalentScore(
                            ((Number) fr.get("score_type")).intValue(),
                            toBigDecimal(fr.get("total_score")),
                            fr.get("score_level") == null ? null : String.valueOf(fr.get("score_level")));
                    weightedSum = weightedSum.add(eq.multiply(credit));
                }
            }

            BigDecimal honorAvg = totalRequiredCredit.compareTo(BigDecimal.ZERO) > 0
                    ? weightedSum.divide(totalRequiredCredit, 2, RoundingMode.HALF_UP) : null;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("student_id", sid);
            item.put("student_name", stu.get("student_name"));
            item.put("class_name", stu.get("class_name"));
            item.put("required_credit_total", totalRequiredCredit.setScale(1, RoundingMode.HALF_UP).toPlainString());
            item.put("honor_avg_score", honorAvg == null ? null : honorAvg.toPlainString());
            item.put("missing_courses", missingCourses);
            ranked.add(item);
        }

        ranked.sort(Comparator
                .comparing((Map<String, Object> m) -> m.get("honor_avg_score") == null
                        ? new BigDecimal("-1") : new BigDecimal(String.valueOf(m.get("honor_avg_score")))).reversed()
                .thenComparing(m -> String.valueOf(m.get("student_id"))));

        BigDecimal prev = null;
        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            String avgStr = (String) ranked.get(i).get("honor_avg_score");
            BigDecimal cur = avgStr == null ? null : new BigDecimal(avgStr);
            if (cur != null) {
                if (prev == null || cur.compareTo(prev) != 0) {
                    rank = i + 1;
                    prev = cur;
                }
                ranked.get(i).put("honor_rank", rank);
                ranked.get(i).put("honor_rank_total", ranked.size());
            } else {
                ranked.get(i).put("honor_rank", null);
                ranked.get(i).put("honor_rank_total", null);
            }
        }

        resp.put("status", "READY");
        resp.put("blocked_reasons", List.of());
        resp.put("items", ranked);
        return resp;
    }

    // =========================================================================
    // 4.6 评教结果汇总（P35：tc_eval_result，匿名汇总，零参评均分为 NULL，第4组加权视图）
    // =========================================================================

    /** 4.6 查看教学班评教汇总（教师仅本人班、严禁返回逐人明细；管理员/教务处附带第4组按人数加权教师视图） */
    public Map<String, Object> listEvaluationSummaries(String term, String tcId) {
        CurrentUser user = UserContext.requireRole(
                RoleEnum.TEACHER, RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        String targetTerm = (term == null || term.isBlank()) ? getCurrentTerm() : term.trim();
        validateTermFormat(targetTerm);
        if (tcId != null && !tcId.isBlank()) {
            verifyTeachingClassAccess(user, tcId.trim());
        }

        StringBuilder sql = new StringBuilder("""
                SELECT tc.tc_id, tc.term, lc.lesson_no,
                       c.course_id, c.course_name, c.open_college,
                       COALESCE(cm.dict_name, c.open_college) AS college_name,
                       tc.teacher_id, COALESCE(emp.emp_name, tc.teacher_id) AS teacher_name,
                       (SELECT COUNT(*) FROM sc sc1 WHERE sc1.tc_id = tc.tc_id AND sc1.select_status = 1) AS enrolled_num,
                       r.eval_num, r.avg_eval_score, r.calc_time,
                       (SELECT COUNT(*) FROM teach_eval te
                        JOIN sc sc2 ON te.sc_id = sc2.sc_id
                        WHERE sc2.tc_id = tc.tc_id AND sc2.select_status = 1) AS live_valid_eval_num
                FROM teaching_class tc
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN sys_code_dict cm ON cm.dict_group = 'college_map' AND cm.dict_code = c.open_college
                LEFT JOIN sys_code_dict tm ON tm.dict_group = 'teacher_map' AND tm.dict_code = tc.teacher_id
                LEFT JOIN t_employee emp ON emp.emp_id = tm.dict_name
                LEFT JOIN tc_eval_result r ON tc.tc_id = r.tc_id
                WHERE tc.term = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(targetTerm);

        if (user.role() == RoleEnum.TEACHER) {
            sql.append(" AND tc.teacher_id = ? ");
            params.add(resolveTeacherCode8(user));
        } else if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            sql.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        }
        if (tcId != null && !tcId.isBlank()) {
            sql.append(" AND tc.tc_id = ? ");
            params.add(tcId.trim());
        }
        sql.append(" ORDER BY tc.tc_id ASC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        boolean isOffice = user.hasRole(
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        // 按教师聚合（供第4组师资管理按参评人数加权汇总参考）
        Map<String, Map<String, Object>> teacherAgg = new LinkedHashMap<>();

        for (Map<String, Object> r : rows) {
            boolean hasSnapshot = r.get("calc_time") != null;
            Integer evalNum = r.get("eval_num") == null ? null : ((Number) r.get("eval_num")).intValue();
            int liveCount = ((Number) r.get("live_valid_eval_num")).intValue();

            String summaryStatus;
            if (!hasSnapshot) {
                summaryStatus = liveCount > 0 ? "NOT_SUMMARIZED" : "NOT_SUMMARIZED_EMPTY";
            } else if (evalNum != null && evalNum == 0) {
                summaryStatus = "ZERO_EVAL";
            } else {
                summaryStatus = (liveCount != evalNum) ? "STALE" : "SUMMARIZED";
            }
            r.put("summary_status", summaryStatus);
            // 仅教务处/管理员可见 live_valid_eval_num，教师端移除实时未汇总明细计数避免反推单人提交
            if (!isOffice) {
                r.remove("live_valid_eval_num");
            }

            if (hasSnapshot && evalNum != null && evalNum > 0 && r.get("avg_eval_score") != null) {
                String tKey = String.valueOf(r.get("teacher_id"));
                Map<String, Object> agg = teacherAgg.computeIfAbsent(tKey, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teacher_id", tKey);
                    m.put("teacher_name", r.get("teacher_name"));
                    m.put("class_count", 0);
                    m.put("total_eval_num", 0);
                    m.put("_weighted_sum", BigDecimal.ZERO);
                    return m;
                });
                agg.put("class_count", ((Number) agg.get("class_count")).intValue() + 1);
                agg.put("total_eval_num", ((Number) agg.get("total_eval_num")).intValue() + evalNum);
                BigDecimal avg = toBigDecimal(r.get("avg_eval_score"));
                agg.put("_weighted_sum", ((BigDecimal) agg.get("_weighted_sum")).add(avg.multiply(BigDecimal.valueOf(evalNum))));
            }
        }

        List<Map<String, Object>> teacherWeighted = new ArrayList<>();
        for (Map<String, Object> agg : teacherAgg.values()) {
            int totalNum = ((Number) agg.get("total_eval_num")).intValue();
            BigDecimal wSum = (BigDecimal) agg.remove("_weighted_sum");
            agg.put("weighted_avg_score", totalNum > 0
                    ? wSum.divide(BigDecimal.valueOf(totalNum), 2, RoundingMode.HALF_UP).toPlainString()
                    : null);
            teacherWeighted.add(agg);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("term", targetTerm);
        resp.put("items", rows);
        resp.put("teacher_weighted_summaries", user.role() == RoleEnum.TEACHER ? List.of() : teacherWeighted);
        resp.put("can_trigger_recompute", isOffice);
        resp.put("can_view_details", user.hasRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.SYS_ADMIN));
        resp.put("anonymity_note", "教师与第4组仅可获取教学班匿名汇总（eval_num、avg_eval_score、calc_time），零参评教学班均分为 NULL；严禁外传学生学号、选课号或逐条评教意见。");
        return resp;
    }

    /** 4.6 教务处受控触发评教结果汇总（按 tc_id / sc_id 顺序加锁，零参评写 eval_num=0, avg_eval_score=NULL） */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> triggerEvaluationSummary(Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.GRADUATE_SCHOOL, RoleEnum.SYS_ADMIN);

        String term = body.get("term") == null || String.valueOf(body.get("term")).isBlank()
                ? getCurrentTerm() : String.valueOf(body.get("term")).trim();
        validateTermFormat(term);

        List<String> requestedTcIds = body.get("tc_ids") instanceof List<?> rawList
                ? rawList.stream().map(o -> String.valueOf(o).trim()).filter(s -> !s.isEmpty()).sorted().toList()
                : List.of();

        List<String> targetTcIds;
        if (!requestedTcIds.isEmpty()) {
            targetTcIds = requestedTcIds;
        } else {
            targetTcIds = jdbc.queryForList(
                    "SELECT tc_id FROM teaching_class WHERE term = ? AND tc_status = '4' ORDER BY tc_id ASC",
                    String.class, term);
        }
        if (targetTcIds.isEmpty()) {
            throw BizException.unprocessable("NO_TARGET_CLASSES", "当前学期下未找到可汇总的已发布教学班");
        }

        List<Map<String, Object>> batchResults = new ArrayList<>();
        int zeroEvalClasses = 0;

        for (String tcId : targetTcIds) {
            // 与 4.1 评教提交共用 sc 行锁秩序（按 sc_id 升序）
            jdbc.queryForList(
                    "SELECT sc_id FROM sc WHERE tc_id = ? AND select_status = 1 ORDER BY sc_id ASC FOR UPDATE",
                    tcId);

            Map<String, Object> agg = jdbc.queryForMap("""
                    SELECT COUNT(*) AS cnt, SUM(te.eval_score) AS total_score
                    FROM teach_eval te
                    JOIN sc ON te.sc_id = sc.sc_id
                    WHERE sc.tc_id = ? AND sc.select_status = 1
                    """, tcId);

            int cnt = ((Number) agg.get("cnt")).intValue();
            List<Map<String, Object>> oldRows = jdbc.queryForList(
                    "SELECT eval_num FROM tc_eval_result WHERE tc_id = ?", tcId);

            BigDecimal avg = null;
            if (cnt == 0) {
                if (!oldRows.isEmpty() && ((Number) oldRows.get(0).get("eval_num")).intValue() > 0) {
                    throw BizException.conflict("EVAL_SOURCE_CONFLICT",
                            "教学班【" + tcId + "】原有非零评教汇总记录，但当前有效评教数为 0，拒绝覆盖，请先核查选课状态变更");
                }
                zeroEvalClasses++;
            } else {
                BigDecimal sum = toBigDecimal(agg.get("total_score"));
                avg = sum.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP);
            }

            // 严格遵守 v7/v9 CHECK：eval_num=0 时 avg_eval_score 必为 NULL；eval_num>0 时 0..100
            jdbc.update("""
                    INSERT INTO tc_eval_result (tc_id, eval_num, avg_eval_score, calc_time)
                    VALUES (?, ?, ?, NOW())
                    ON DUPLICATE KEY UPDATE
                      eval_num = VALUES(eval_num),
                      avg_eval_score = VALUES(avg_eval_score),
                      calc_time = NOW()
                    """, tcId, cnt, avg);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tc_id", tcId);
            item.put("eval_num", cnt);
            item.put("avg_eval_score", avg == null ? null : avg.toPlainString());
            batchResults.add(item);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("term", term);
        resp.put("processed_classes", batchResults.size());
        resp.put("zero_eval_classes", zeroEvalClasses);
        resp.put("calc_time", LocalDateTime.now().format(TS_FMT));
        resp.put("items", batchResults);
        return resp;
    }

    /** 4.6 教务处受控评教明细查阅（界面13：仅教务处/管理员可访问，教师与学院严禁访问） */
    public List<Map<String, Object>> listEvaluationDetailsForOffice(String term, String tcId) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.SYS_ADMIN);
        validateTermFormat(term);

        StringBuilder sql = new StringBuilder("""
                SELECT CAST(te.sc_id AS CHAR) AS sc_id,
                       tc.term, tc.tc_id, lc.lesson_no,
                       c.course_id, c.course_name,
                       COALESCE(emp.emp_name, tc.teacher_id) AS teacher_name,
                       te.eval_score, te.eval_comment, te.eval_time
                FROM teach_eval te
                JOIN sc ON te.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                LEFT JOIN lesson_class lc ON tc.tc_id = lc.tc_id
                LEFT JOIN sys_code_dict tm ON tm.dict_group = 'teacher_map' AND tm.dict_code = tc.teacher_id
                LEFT JOIN t_employee emp ON emp.emp_id = tm.dict_name
                WHERE sc.select_status = 1
                """);
        List<Object> params = new ArrayList<>();
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        if (tcId != null && !tcId.isBlank()) {
            sql.append(" AND tc.tc_id = ? ");
            params.add(tcId.trim());
        }
        sql.append(" ORDER BY te.eval_time DESC, te.sc_id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    // =========================================================================
    // 严格输入解析校验
    // =========================================================================

    private long parseStrictLong(Object raw, String field) {
        if (raw == null) {
            throw BizException.unprocessable("MISSING_FIELD", "缺少必填字段: " + field,
                    Map.of(field, "不能为空"));
        }
        String s = String.valueOf(raw).trim();
        if (!s.matches("^\\d+$")) {
            throw BizException.unprocessable("INVALID_ID", field + " 必须为正整数",
                    Map.of(field, "格式非法"));
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw BizException.unprocessable("INVALID_ID", field + " 数值溢出",
                    Map.of(field, "数值非法"));
        }
    }

    private int parseStrictEvalScore(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw BizException.unprocessable("MISSING_EVAL_SCORE", "评教总分必填（0~100 整数）",
                    Map.of("eval_score", "必填"));
        }
        String s = String.valueOf(raw).trim();
        if (!s.matches("^-?\\d+$")) {
            throw BizException.unprocessable("INVALID_EVAL_SCORE", "评教总分必须为 0~100 之间的整数，不允许小数或非数字",
                    Map.of("eval_score", "必须为整数"));
        }
        int val;
        try {
            val = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw BizException.unprocessable("INVALID_EVAL_SCORE", "评教总分超出整数范围",
                    Map.of("eval_score", "超出范围"));
        }
        if (val < 0 || val > 100) {
            throw BizException.unprocessable("INVALID_EVAL_SCORE", "评教总分必须在 0~100 之间（含边界 0 与 100）",
                    Map.of("eval_score", "必须在 0~100 之间"));
        }
        return val;
    }
}
