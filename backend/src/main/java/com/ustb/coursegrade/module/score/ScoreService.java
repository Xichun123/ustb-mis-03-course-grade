package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 【业务③ 成绩录入与审核 3.1–3.5 · 蒋作】
 * 以课堂号 lesson_no 组织：教师只看本人课堂；平时占比低于参数 sys_code_dict(usual_ratio_min) 时拒绝保存。
 */
@Service
public class ScoreService {

    private static final int DEFAULT_RATIO_MIN = 30;
    private static final String CODE_SALT = "USTB03-SCORE-2026";

    private final JdbcTemplate jdbc;

    public ScoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 3.1 我的课堂列表（本学期在前、往届在后） */
    public List<Map<String, Object>> myClasses(String term, String keyword) {
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT lc.lesson_no, lc.tc_id, tc.term, tc.course_id, c.course_name, c.credit,
                       c.open_college, tc.class_ids, tc.actual_num,
                       (SELECT COUNT(*) FROM sc WHERE sc.tc_id = lc.tc_id AND sc.select_status = 1) AS student_count,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1 AND s.score_status = 1) AS submitted_count
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (u.role() == RoleEnum.TEACHER) {
            sql.append(" AND TRIM(tc.teacher_id) = ? ");
            args.add(u.empId());
        }
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            args.add(term.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (lc.lesson_no LIKE ? OR c.course_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY tc.term DESC, lc.lesson_no ASC ");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.1 课堂名单与已录成绩 */
    public Map<String, Object> roster(String lessonNo) {
        Map<String, Object> head = requireClass(lessonNo);
        UserContext.requireUser();
        CurrentUser u = UserContext.get();
        if (u.role() == RoleEnum.TEACHER && !Objects.equals(String.valueOf(head.get("teacher_id")).trim(), u.empId())) {
            throw BizException.forbidden("该课堂不属于当前教师");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT sc.sc_id, sc.student_id, st.student_name, st.class_name, sc.study_type, sc.stu_level,
                       s.score_id, s.usual_ratio, s.usual_score, s.exam_score, s.exam_flag, s.score_type,
                       s.score_level, s.total_score, s.grade_point, s.score_status
                FROM sc
                JOIN student st ON sc.student_id = st.student_id
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id = ? AND sc.select_status = 1
                ORDER BY sc.student_id
                """, head.get("tc_id"));
        int ratioMin = ratioMin();
        Map<String, Object> data = new LinkedHashMap<>(head);
        data.put("students", rows);
        data.put("ratio_min", ratioMin);
        data.put("verify_code", rows.isEmpty() ? null : buildCode(lessonNo, head.get("tc_id")));
        return data;
    }

    /** 3.1 保存成绩草稿（课堂号 + 名单，含不同班级/重修/辅修） */
    @Transactional
    public Map<String, Object> saveGrades(String lessonNo, List<Map<String, Object>> items) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER);
        if (!Objects.equals(String.valueOf(head.get("teacher_id")).trim(), u.empId())) {
            throw BizException.forbidden("只能录入本人任教的课堂");
        }
        int min = ratioMin();
        Set<Long> allowed = new HashSet<>(jdbc.queryForList(
                "SELECT sc_id FROM sc WHERE tc_id = ? AND select_status = 1", Long.class, head.get("tc_id")));
        int saved = 0;
        for (Map<String, Object> item : items) {
            Long scId = toLong(item.get("sc_id"));
            if (scId == null || !allowed.contains(scId)) {
                throw BizException.unprocessable("INVALID_STUDENT", "名单中不存在该选课记录：" + item.get("sc_id"));
            }
            int ratio = toInt(item.get("usual_ratio"), min);
            if (ratio < min || ratio > 100) {
                throw BizException.unprocessable("RATIO_TOO_LOW",
                        "平时占比 " + ratio + "% 低于规定的下限 " + min + "%，本次录入无效");
            }
            BigDecimal usual = toScore(item.get("usual_score"), "平时成绩");
            BigDecimal exam = toScore(item.get("exam_score"), "期末成绩");
            int flag = toInt(item.get("exam_flag"), 0);
            if (flag < 0 || flag > 3) {
                throw BizException.unprocessable("INVALID_EXAM_FLAG", "考试情况只能为 0正常/1缺考/2缓考/3违纪");
            }
            int scoreType = toInt(item.get("score_type"), 1);
            String level = item.get("score_level") == null ? null : String.valueOf(item.get("score_level")).trim();
            if (scoreType == 2 && !Set.of("优", "良", "中", "及格", "不及格").contains(level)) {
                throw BizException.unprocessable("INVALID_LEVEL", "五级制成绩只能为 优/良/中/及格/不及格");
            }
            int updated = jdbc.update("""
                    UPDATE score SET usual_ratio = ?, usual_score = ?, exam_score = ?, exam_flag = ?,
                           score_type = ?, score_level = ?, score_status = 0
                     WHERE sc_id = ? AND exam_type = 1
                    """, ratio, usual, exam, flag, scoreType, level, scId);
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO score(sc_id, exam_type, exam_flag, score_type, usual_ratio, usual_score,
                                          exam_score, score_level, score_status, input_teacher_id)
                        VALUES(?, 1, ?, ?, ?, ?, ?, ?, 0, ?)
                        """, scId, flag, scoreType, ratio, usual, exam, level, u.empId());
            }
            saved++;
        }
        return Map.of("saved", saved, "ratio_min", min);
    }

    /** 3.2 总评计算与提交：校验数据规范 → 计算 → 置 1 → 生成 6 位核对码 */
    @Transactional
    public Map<String, Object> submit(String lessonNo) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER);
        if (!Objects.equals(String.valueOf(head.get("teacher_id")).trim(), u.empId())) {
            throw BizException.forbidden("只能提交本人任教的课堂");
        }
        Object tcId = head.get("tc_id");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT sc.sc_id, sc.student_id, s.score_id, s.usual_ratio, s.usual_score, s.exam_score,
                       s.exam_flag, s.score_type, s.score_level
                FROM sc
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id = ? AND sc.select_status = 1
                """, tcId);
        if (rows.isEmpty()) {
            throw BizException.unprocessable("EMPTY_ROSTER", "该课堂没有已选中学生");
        }
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        int min = ratioMin();
        for (Map<String, Object> r : rows) {
            String sid = String.valueOf(r.get("student_id"));
            if (r.get("score_id") == null) {
                missing.add(sid);
                continue;
            }
            int flag = r.get("exam_flag") == null ? 0 : ((Number) r.get("exam_flag")).intValue();
            int ratio = r.get("usual_ratio") == null ? -1 : ((Number) r.get("usual_ratio")).intValue();
            BigDecimal usual = r.get("usual_score") == null ? null : new BigDecimal(String.valueOf(r.get("usual_score")));
            BigDecimal exam = r.get("exam_score") == null ? null : new BigDecimal(String.valueOf(r.get("exam_score")));
            if (ratio < min || ratio > 100) {
                invalid.add(sid + "占比");
            } else if (flag == 0 && (usual == null || exam == null)) {
                invalid.add(sid + "缺项");
            } else if ((usual != null && (usual.signum() < 0 || usual.compareTo(new BigDecimal("100")) > 0))
                    || (exam != null && (exam.signum() < 0 || exam.compareTo(new BigDecimal("100")) > 0))) {
                invalid.add(sid + "超范围");
            }
        }
        if (!missing.isEmpty()) {
            throw BizException.unprocessable("MISSING_SCORE", "以下学生还没有录入成绩：" + String.join("、", missing));
        }
        if (!invalid.isEmpty()) {
            throw BizException.unprocessable("INVALID_SCORE", "数据不符合规范：" + String.join("、", invalid));
        }
        for (Map<String, Object> r : rows) {
            int flag = r.get("exam_flag") == null ? 0 : ((Number) r.get("exam_flag")).intValue();
            int ratio = ((Number) r.get("usual_ratio")).intValue();
            BigDecimal usual = r.get("usual_score") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(r.get("usual_score")));
            BigDecimal exam = r.get("exam_score") == null ? null : new BigDecimal(String.valueOf(r.get("exam_score")));
            BigDecimal total = null;
            BigDecimal gp = null;
            if (flag == 0 && exam != null) {
                total = usual.multiply(BigDecimal.valueOf(ratio))
                        .add(exam.multiply(BigDecimal.valueOf(100 - ratio)))
                        .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
                gp = gradePoint(total);
            } else if (flag == 1) {
                total = BigDecimal.ZERO;
                gp = BigDecimal.ZERO;
            }
            jdbc.update("""
                    UPDATE score SET total_score = ?, grade_point = ?, score_status = 1, submit_time = NOW()
                     WHERE sc_id = ? AND exam_type = 1
                    """, total, gp, r.get("sc_id"));
        }
        String code = buildCode(lessonNo, tcId);
        return Map.of("lesson_no", lessonNo, "submitted", rows.size(), "verify_code", code);
    }

    /** 3.2/3.3 成绩登记表（含核对码） */
    public Map<String, Object> sheet(String lessonNo) {
        Map<String, Object> data = roster(lessonNo);
        return data;
    }

    /** 3.3 学院待核对课堂 */
    public List<Map<String, Object>> auditPending(String term) {
        CurrentUser u = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT lc.lesson_no, lc.tc_id, tc.term, c.course_name, c.open_college, tc.class_ids,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1 AND s.score_status = 1) AS wait_count,
                       (SELECT MAX(s.submit_time) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1) AS submit_time
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE EXISTS (SELECT 1 FROM score s JOIN sc ON s.sc_id = sc.sc_id
                               WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1 AND s.score_status = 1)
                """);
        List<Object> args = new ArrayList<>();
        if (u.role() == RoleEnum.COLLEGE_SECRETARY && u.collegeId() != null) {
            sql.append(" AND c.open_college = ? ");
            args.add(u.collegeId());
        }
        if (term != null && !term.isBlank()) {
            sql.append(" AND tc.term = ? ");
            args.add(term.trim());
        }
        sql.append(" ORDER BY tc.term DESC, lc.lesson_no ");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.3 输入 6 位核对码：通过置 2，退回置 4 并记审核意见 */
    @Transactional
    public Map<String, Object> audit(String lessonNo, String verifyCode, int result, String opinion) {
        CurrentUser u = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        Map<String, Object> head = requireClass(lessonNo);
        String college = String.valueOf(head.get("open_college"));
        if (u.role() == RoleEnum.COLLEGE_SECRETARY && u.collegeId() != null && !u.collegeId().equals(college)) {
            throw BizException.forbidden("只能核对本院开设课程");
        }
        if (result != 1 && result != 2) {
            throw BizException.unprocessable("INVALID_RESULT", "核对结论只能为 1通过 / 2退回");
        }
        if (result == 2 && (opinion == null || opinion.isBlank())) {
            throw BizException.unprocessable("OPINION_REQUIRED", "退回时必须填写差异说明");
        }
        String expect = buildCode(lessonNo, head.get("tc_id"));
        if (!expect.equals(verifyCode == null ? "" : verifyCode.trim())) {
            throw BizException.unprocessable("CODE_MISMATCH", "核对码与系统不一致，请对照纸质成绩登记表重新输入");
        }
        Integer status = jdbc.queryForObject("""
                SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                 WHERE sc.tc_id = ? AND s.exam_type = 1 AND s.score_status = 1
                """, Integer.class, head.get("tc_id"));
        if (status == null || status == 0) {
            throw BizException.conflict("NOT_SUBMITTED", "该课堂没有待核对（已提交）的成绩");
        }
        jdbc.update("""
                INSERT INTO score_audit(tc_id, auditor_id, audit_result, audit_opinion, audit_time)
                VALUES(?, ?, ?, ?, NOW())
                """, head.get("tc_id"), u.empId(), result, opinion == null ? null : opinion.trim());
        jdbc.update("""
                UPDATE score SET score_status = ?
                 WHERE sc_id IN (SELECT sc_id FROM sc WHERE tc_id = ?) AND exam_type = 1 AND score_status = 1
                """, result == 1 ? 2 : 4, head.get("tc_id"));
        return Map.of("lesson_no", lessonNo, "result", result,
                "message", result == 1 ? "核对通过，等待教务处发布" : "已退回任课教师二次确认");
    }

    /** 3.4 待发布课堂 */
    public List<Map<String, Object>> publishPending(String term) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        List<Object> args = new ArrayList<>();
        String termSql = "";
        if (term != null && !term.isBlank()) {
            termSql = " AND tc.term = ? ";
            args.add(term.trim());
        }
        return jdbc.queryForList("""
                SELECT lc.lesson_no, lc.tc_id, tc.term, c.course_name,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1 AND s.score_status = 2) AS ready_count
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE EXISTS (SELECT 1 FROM score s JOIN sc ON s.sc_id = sc.sc_id
                               WHERE sc.tc_id = lc.tc_id AND s.exam_type = 1 AND s.score_status = 2)
                """ + termSql + " ORDER BY tc.term DESC, lc.lesson_no ", args.toArray());
    }

    /** 3.4 发布：校验本次提交后的审核通过记录，置 3 并重算 is_final */
    @Transactional
    public Map<String, Object> publish(String lessonNo) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        Map<String, Object> head = requireClass(lessonNo);
        Object tcId = head.get("tc_id");
        Integer pass = jdbc.queryForObject("""
                SELECT COUNT(*) FROM score_audit a
                 WHERE a.tc_id = ? AND a.audit_result = 1
                   AND a.audit_time >= COALESCE((SELECT MAX(s.submit_time)
                                                   FROM score s JOIN sc ON s.sc_id = sc.sc_id
                                                  WHERE sc.tc_id = a.tc_id AND s.exam_type = 1), a.audit_time)
                """, Integer.class, tcId);
        Integer ready = jdbc.queryForObject("""
                SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                 WHERE sc.tc_id = ? AND s.exam_type = 1 AND s.score_status = 2
                """, Integer.class, tcId);
        if (ready == null || ready == 0) {
            throw BizException.conflict("NOT_READY", "该课堂没有学院核对通过的成绩");
        }
        if (pass == null || pass == 0) {
            throw BizException.conflict("NO_AUDIT", "学院尚未核对本次提交，不能发布");
        }
        jdbc.update("""
                UPDATE score SET score_status = 3, publish_time = NOW()
                 WHERE sc_id IN (SELECT sc_id FROM sc WHERE tc_id = ?) AND exam_type = 1 AND score_status = 2
                """, tcId);
        // 重算 is_final：同一学生同一课程已发布成绩中最高的一条
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT DISTINCT sc.student_id FROM score s JOIN sc ON s.sc_id = sc.sc_id
                 WHERE sc.tc_id = ? AND s.exam_type = 1 AND s.score_status = 3
                """, tcId);
        for (Map<String, Object> st : students) {
            String sid = String.valueOf(st.get("student_id"));
            jdbc.update("""
                    UPDATE score SET is_final = 0
                     WHERE sc_id IN (SELECT sc_id FROM sc WHERE student_id = ?)
                    """, sid);
            Long best = jdbc.queryForObject("""
                    SELECT s.score_id FROM score s JOIN sc ON s.sc_id = sc.sc_id
                     JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                     WHERE sc.student_id = ? AND s.score_status = 3
                       AND tc.course_id = (SELECT course_id FROM teaching_class WHERE tc_id = ?)
                     ORDER BY s.total_score DESC, s.score_id DESC LIMIT 1
                    """, Long.class, sid, tcId);
            if (best != null) {
                jdbc.update("UPDATE score SET is_final = 1 WHERE score_id = ?", best);
            }
        }
        return Map.of("lesson_no", lessonNo, "published", ready);
    }

    /** 3.5 校外交流成绩认定：列表 */
    public List<Map<String, Object>> listExchange(String studentId, String keyword) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT convert_id, student_id, exchange_apply_id, term, stat_stage, host_school,
                       ext_course_code, ext_course_name, ext_credit, ext_score_raw, ext_scale_type,
                       course_id, score_type, converted_score, converted_level, grade_point, is_final
                FROM exchange_score_convert WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (studentId != null && !studentId.isBlank()) {
            sql.append(" AND student_id = ? ");
            args.add(studentId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (ext_course_name LIKE ? OR host_school LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY convert_id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.5 校外交流成绩认定：登记并换算 */
    @Transactional
    public Map<String, Object> createExchange(Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        String sid = str(body.get("student_id"));
        String term = str(body.get("term"));
        String host = str(body.get("host_school"));
        String extName = str(body.get("ext_course_name"));
        String courseId = str(body.get("course_id"));
        String proofNo = str(body.get("proof_no"));
        String extRaw = str(body.get("ext_score_raw"));
        if (sid.isEmpty() || term.isEmpty() || host.isEmpty() || extName.isEmpty()
                || courseId.isEmpty() || proofNo.isEmpty() || extRaw.isEmpty()) {
            throw BizException.unprocessable("MISSING_FIELD",
                    "学号、学期、外校、外校课程名、本校课程号、成绩单编号、外校原始成绩不能为空");
        }
        BigDecimal credit = dec(body.get("ext_credit"));
        if (credit == null || credit.signum() <= 0) {
            throw BizException.unprocessable("INVALID_CREDIT", "外校学分必须大于 0");
        }
        int scale = toInt(body.get("ext_scale_type"), 1);
        if (scale < 1 || scale > 4) {
            throw BizException.unprocessable("INVALID_SCALE", "外校记分制只能为 1百分制/2字母等级/3绩点/4其他");
        }
        int scoreType = toInt(body.get("score_type"), 1);
        BigDecimal converted = dec(body.get("converted_score"));
        String level = str(body.get("converted_level"));
        if (scoreType == 1) {
            if (converted == null || converted.signum() < 0 || converted.compareTo(new BigDecimal("100")) > 0) {
                throw BizException.unprocessable("INVALID_SCORE", "百分制须填 0~100 的换算成绩");
            }
            level = "";
        } else if (scoreType == 2) {
            if (!Set.of("优", "良", "中", "及格", "不及格").contains(level)) {
                throw BizException.unprocessable("INVALID_LEVEL", "五级制等级只能为 优/良/中/及格/不及格");
            }
            converted = null;
        } else if (scoreType == 3) {
            if (!Set.of("合格", "不合格").contains(level)) {
                throw BizException.unprocessable("INVALID_LEVEL", "两级制等级只能为 合格/不合格");
            }
            converted = null;
        } else {
            throw BizException.unprocessable("INVALID_SCORE_TYPE", "换算后记分方式只能为 1/2/3");
        }
        BigDecimal gp = converted != null ? percentToPoint(converted) : levelToPoint(level);
        jdbc.update("""
                INSERT INTO exchange_score_convert(student_id, exchange_apply_id, term, stat_stage, host_school,
                    ext_course_code, ext_course_name, ext_credit, ext_score_raw, ext_scale_type, course_id,
                    score_type, converted_score, converted_level, grade_point, is_final, proof_no,
                    version_no, audit_status, apply_time)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 1, 0, NOW())
                """, sid, str(body.get("exchange_apply_id")), term, toInt(body.get("stat_stage"), 1), host,
                str(body.get("ext_course_code")), extName, credit, extRaw, scale, courseId,
                scoreType, converted, level.isEmpty() ? null : level, gp, proofNo);
        return Map.of("student_id", sid, "course_id", courseId, "grade_point", gp,
                "message", "校外成绩已登记，待学院审核、教务处审批后生效");
    }

    // ---------- 内部工具 ----------

    private Map<String, Object> requireClass(String lessonNo) {
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT lc.lesson_no, lc.tc_id, tc.term, tc.course_id, c.course_name, c.credit,
                       c.open_college, tc.teacher_id, tc.class_ids, tc.actual_num
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE lc.lesson_no = ?
                """, lessonNo);
        if (list.isEmpty()) {
            throw BizException.notFound("课堂号不存在：" + lessonNo);
        }
        return list.get(0);
    }

    private int ratioMin() {
        List<String> vals = jdbc.queryForList("""
                SELECT dict_name FROM sys_code_dict
                 WHERE dict_group = 'usual_ratio_min' AND is_enabled = 1
                 ORDER BY dict_id LIMIT 1
                """, String.class);
        if (vals.isEmpty()) {
            return DEFAULT_RATIO_MIN;
        }
        try {
            return Integer.parseInt(vals.get(0).trim());
        } catch (NumberFormatException e) {
            return DEFAULT_RATIO_MIN;
        }
    }

    /** 6 位核对码：课堂号 + 选课记录号集合的摘要，打印在成绩登记表上，学院输码比对 */
    private String buildCode(String lessonNo, Object tcId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT sc.sc_id FROM sc WHERE sc.tc_id = ? AND sc.select_status = 1 ORDER BY sc.sc_id
                """, Long.class, tcId);
        long h = 17;
        for (Long id : ids) {
            h = h * 31 + id;
        }
        h = h * 131 + lessonNo.hashCode() + CODE_SALT.hashCode();
        return String.format("%06d", Math.abs(h) % 1_000_000);
    }

    private static BigDecimal gradePoint(BigDecimal total) {
        return total == null ? null : percentToPoint(total);
    }

    /** 校发〔2021〕46 号附表 3：只有 4.00/3.70/3.40/3.00/2.40/2.00/1.00/0.00 八档 */
    private static BigDecimal percentToPoint(BigDecimal score) {
        double v = score.doubleValue();
        if (v >= 90) {
            return new BigDecimal("4.00");
        } else if (v >= 85) {
            return new BigDecimal("3.70");
        } else if (v >= 80) {
            return new BigDecimal("3.40");
        } else if (v >= 75) {
            return new BigDecimal("3.00");
        } else if (v >= 70) {
            return new BigDecimal("2.40");
        } else if (v >= 65) {
            return new BigDecimal("2.00");
        } else if (v >= 60) {
            return new BigDecimal("1.00");
        }
        return new BigDecimal("0.00");
    }

    private static BigDecimal levelToPoint(String level) {
        return switch (level) {
            case "优" -> new BigDecimal("4.00");
            case "良" -> new BigDecimal("3.40");
            case "中" -> new BigDecimal("2.40");
            case "及格", "合格" -> new BigDecimal("1.00");
            default -> new BigDecimal("0.00");
        };
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static Long toLong(Object o) {
        try {
            return o == null ? null : Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toInt(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static BigDecimal dec(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toScore(Object o, String label) {
        BigDecimal v = dec(o);
        if (v != null && (v.signum() < 0 || v.compareTo(new BigDecimal("100")) > 0)) {
            throw BizException.unprocessable("SCORE_OUT_OF_RANGE", label + "须在 0~100 之间");
        }
        return v;
    }
}
