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
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 【业务③ 成绩录入与审核 3.1–3.5 · 蒋作（已合入主干并对齐 v9 实库）】
 * 以课堂号 lesson_no 统一管理：一个课堂号可对应多个教学班 tc_id（不同班级/重修/辅修都在同一课堂号下）。
 */
@Service
public class ScoreService {

    private static final int DEFAULT_RATIO_MIN = 30;
    private static final char[] VERIFY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public ScoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 3.1 我的课堂列表（按课堂号聚合，本学期在前、往届在后） */
    public List<Map<String, Object>> myClasses(String term, String keyword) {
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT lc.lesson_no, MIN(lc.tc_id) AS tc_id, tc.term, tc.course_id, c.course_name, c.credit,
                       c.open_college, GROUP_CONCAT(DISTINCT tc.class_ids ORDER BY tc.class_ids SEPARATOR '、') AS class_ids,
                       (SELECT COUNT(*) FROM sc JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND sc.select_status = 1) AS student_count,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 1) AS submitted_count,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 3) AS published_count
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (u.role() == RoleEnum.TEACHER) {
            sql.append(" AND (TRIM(tc.teacher_id) = ? OR TRIM(tc.teacher_id) = ?) ");
            args.add(u.empId());
            args.add(u.teacherCode8() == null ? "" : u.teacherCode8());
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
        sql.append(" GROUP BY lc.lesson_no, tc.term, tc.course_id, c.course_name, c.credit, c.open_college ");
        sql.append(" ORDER BY tc.term DESC, lc.lesson_no ASC ");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.1 课堂名单与已录成绩（聚合该课堂号下全部教学班，兼容来校交换生） */
    public Map<String, Object> roster(String lessonNo) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireUser();
        if (u.role() == RoleEnum.TEACHER && !owns(head, u)) {
            throw BizException.forbidden("该课堂不属于当前教师");
        }
        List<String> tcIds = tcIds(head);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT CAST(sc.sc_id AS CHAR) AS sc_id, sc.tc_id, sc.student_id,
                       COALESCE(st.student_name, ex.student_name, sc.student_id) AS student_name,
                       COALESCE(st.class_name, ex.sender_school_name, '交流生') AS class_name,
                       sc.study_type, sc.stu_level,
                       CAST(s.score_id AS CHAR) AS score_id,
                       s.usual_ratio, s.usual_score, s.exam_score, s.exam_flag, s.score_type,
                       s.score_level, s.total_score, s.grade_point, s.score_status
                FROM sc
                LEFT JOIN student st ON sc.student_id = st.student_id
                LEFT JOIN v_exchange_student ex ON ex.temp_student_no = sc.student_id
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id IN (%s) AND sc.select_status = 1
                ORDER BY sc.student_id
                """.formatted(marks(tcIds)), tcIds.toArray());
        Map<String, Object> data = new LinkedHashMap<>(head);
        data.put("students", rows);
        data.put("ratio_min", ratioMin());
        data.put("usual_ratio", classRatio(lessonNo));
        data.put("default_score_type", defaultScoreType(head));
        boolean canSeeCode = u.role() == RoleEnum.TEACHER || u.role() == RoleEnum.SYS_ADMIN;
        data.put("verify_code", canSeeCode ? latestVerifyCode(lessonNo) : null);
        return data;
    }

    /** 3.1 保存成绩草稿（最低平时占比仅在录入保存时校验一次） */
    @Transactional
    public Map<String, Object> saveGrades(String lessonNo, List<Map<String, Object>> items) {
        return saveGrades(lessonNo, items, null);
    }

    /**
     * 3.1 保存成绩草稿：classRatio 非空时对整个课堂生效（前端只需在课堂顶部录入一次占比）。
     */
    @Transactional
    public Map<String, Object> saveGrades(String lessonNo, List<Map<String, Object>> items, Integer classRatio) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        if (u.role() == RoleEnum.TEACHER && !owns(head, u)) {
            throw BizException.forbidden("只能录入本人任教的课堂");
        }
        int min = ratioMin();
        if (classRatio != null && (classRatio < min || classRatio > 100)) {
            throw BizException.unprocessable("RATIO_TOO_LOW",
                    "课堂平时占比 " + classRatio + "% 低于规定的下限 " + min + "%，本次录入无效");
        }
        List<String> tcIds = tcIds(head);
        Set<Long> allowed = new HashSet<>(jdbc.queryForList(
                "SELECT sc_id FROM sc WHERE tc_id IN (" + marks(tcIds) + ") AND select_status = 1",
                Long.class, tcIds.toArray()));
        int saved = 0;
        for (Map<String, Object> item : items) {
            Long scId = toLong(item.get("sc_id"));
            if (scId == null || !allowed.contains(scId)) {
                throw BizException.unprocessable("INVALID_STUDENT", "名单中不存在该选课记录：" + item.get("sc_id"));
            }
            List<Integer> curStatuses = jdbc.queryForList(
                    "SELECT score_status FROM score WHERE sc_id = ? AND exam_type = 1", Integer.class, scId);
            if (!curStatuses.isEmpty()) {
                int st = curStatuses.get(0);
                if (st == 1 || st == 2 || st == 3) {
                    throw BizException.conflict("SCORE_LOCKED",
                            "该成绩记录已提交审核或正式发布，不能直接修改（已发布成绩请通过成绩更正申请流程办理）");
                }
            }
            int ratio = classRatio != null ? classRatio : toInt(item.get("usual_ratio"), min);
            if (ratio < min || ratio > 100) {
                throw BizException.unprocessable("RATIO_TOO_LOW",
                        "平时占比 " + ratio + "% 低于规定的下限 " + min + "%，本次录入无效");
            }
            BigDecimal usual = toScore(item.get("usual_score"), "平时成绩");
            BigDecimal exam = toScore(item.get("exam_score"), "期末成绩");
            int flag = toInt(item.get("exam_flag"), 0);
            if (flag < 0 || flag > 3) {
                throw BizException.unprocessable("INVALID_EXAM_FLAG", "考试情况仅支持正常、缺考、缓考或违纪");
            }
            int scoreType = toInt(item.get("score_type"), 1);
            if (scoreType < 1 || scoreType > 3) {
                throw BizException.unprocessable("INVALID_SCORE_TYPE", "记分方式仅支持百分制、五级制或两级制");
            }
            String level = str(item.get("score_level"));
            if (scoreType == 1) {
                level = null;
            } else if (scoreType == 2) {
                if (!level.isEmpty() && !Set.of("优", "良", "中", "及格", "不及格").contains(level)) {
                    throw BizException.unprocessable("INVALID_LEVEL", "五级制成绩只能为 优/良/中/及格/不及格");
                }
                if (level.isEmpty()) {
                    level = null;
                }
            } else {
                if (!level.isEmpty() && !Set.of("合格", "不合格").contains(level)) {
                    throw BizException.unprocessable("INVALID_LEVEL", "两级制成绩只能为 合格/不合格");
                }
                if (level.isEmpty()) {
                    level = null;
                }
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

    /** 3.1 课堂级平时占比：一次设置，对整个课堂未提交的成绩生效 */
    @Transactional
    public Map<String, Object> setClassRatio(String lessonNo, int ratio) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        if (u.role() == RoleEnum.TEACHER && !owns(head, u)) {
            throw BizException.forbidden("只能设置本人任教课堂的平时占比");
        }
        int min = ratioMin();
        if (ratio < min || ratio > 100) {
            throw BizException.unprocessable("RATIO_TOO_LOW",
                    "课堂平时占比 " + ratio + "% 低于规定的下限 " + min + "%，不能用于本课堂");
        }
        List<String> tcIds = tcIds(head);
        List<Map<String, Object>> rosterStates = jdbc.queryForList("""
                SELECT sc.sc_id, s.score_id, s.score_status
                FROM sc
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id IN (%s) AND sc.select_status = 1
                """.formatted(marks(tcIds)), tcIds.toArray());
        if (rosterStates.isEmpty()) {
            throw BizException.unprocessable("EMPTY_ROSTER", "该课堂暂无已选中学生，无法设置平时占比");
        }
        int lockedCount = 0;
        int pendingNew = 0;
        for (Map<String, Object> r : rosterStates) {
            if (r.get("score_id") == null) {
                pendingNew++;
            } else {
                int st = ((Number) r.get("score_status")).intValue();
                if (st >= 1 && st <= 3) {
                    lockedCount++;
                }
            }
        }
        if (lockedCount == rosterStates.size()) {
            throw BizException.conflict("SCORE_LOCKED", "该课堂全部成绩已提交或发布，无法再修改平时占比");
        }
        int updated = jdbc.update("""
                UPDATE score SET usual_ratio = ?
                 WHERE exam_type = 1 AND score_status IN (0, 4)
                   AND sc_id IN (SELECT sc_id FROM sc WHERE tc_id IN (%s) AND select_status = 1)
                """.formatted(marks(tcIds)), append(new Object[]{ratio}, tcIds));
        String message;
        if (updated > 0 && pendingNew == 0) {
            message = "课堂平时占比已设为 " + ratio + "%，已应用到 " + updated + " 条未提交成绩";
        } else if (updated > 0) {
            message = "课堂平时占比已设为 " + ratio + "%，已更新 " + updated + " 条草稿，其余 " + pendingNew + " 名未录入学生将在保存或导入时生效";
        } else {
            message = "课堂平时占比已设为 " + ratio + "%（当前尚无已保存草稿，将在保存或导入成绩时按 " + ratio + "% 统一生效）";
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("lesson_no", lessonNo);
        resp.put("usual_ratio", ratio);
        resp.put("updated", updated);
        resp.put("pending_new", pendingNew);
        resp.put("persisted", updated > 0);
        resp.put("message", message);
        return resp;
    }

    /** 课堂当前占比：取已录记录中出现最多的值；还没有记录时返回 null */
    private Integer classRatio(String lessonNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.usual_ratio AS ratio, COUNT(*) AS n
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN lesson_class lc ON sc.tc_id = lc.tc_id
                WHERE lc.lesson_no = ? AND s.exam_type = 1 AND s.usual_ratio IS NOT NULL
                GROUP BY s.usual_ratio
                ORDER BY n DESC, s.usual_ratio DESC
                LIMIT 1
                """, lessonNo);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("ratio")).intValue();
    }

    /**
     * 3.1 Excel / CSV 批量导入：百分制支持 学号、平时成绩、期末成绩（可选 考试情况）；等级制支持 学号、等级成绩（可选 考试情况）。
     * 整个课堂只用一个平时占比：参数优先，其次课堂已设占比，最后取参数下限。
     */
    @Transactional
    public Map<String, Object> importGrades(String lessonNo, java.io.InputStream in, String filename, Integer ratioParam) {
        return importGrades(lessonNo, in, filename, ratioParam, false);
    }

    /** dryRun=true 时只解析与校验、不写库，用于导入前预览 */
    @Transactional
    public Map<String, Object> importGrades(String lessonNo, java.io.InputStream in, String filename,
                                            Integer ratioParam, boolean dryRun) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        if (u.role() == RoleEnum.TEACHER && !owns(head, u)) {
            throw BizException.forbidden("只能导入本人任教课堂的成绩");
        }
        int min = ratioMin();
        Integer existing = classRatio(lessonNo);
        int ratio = ratioParam != null ? ratioParam : (existing != null ? existing : min);
        if (ratio < min || ratio > 100) {
            throw BizException.unprocessable("RATIO_TOO_LOW",
                    "课堂平时占比 " + ratio + "% 低于规定的下限 " + min + "%");
        }
        int defaultType = defaultScoreType(head);
        List<String> tcIds = tcIds(head);
        Map<String, Long> roster = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> existingScores = new HashMap<>();
        jdbc.queryForList("""
                SELECT sc.sc_id, sc.student_id, s.score_id, s.score_status, s.score_type
                FROM sc
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id IN (%s) AND sc.select_status = 1
                """.formatted(marks(tcIds)), tcIds.toArray())
                .forEach(r -> {
                    long scId = ((Number) r.get("sc_id")).longValue();
                    roster.put(String.valueOf(r.get("student_id")), scId);
                    if (r.get("score_id") != null) {
                        existingScores.put(scId, r);
                    }
                });

        List<Map<String, String>> rows;
        try {
            rows = ScoreExcelParser.parse(in, filename);
        } catch (Exception e) {
            throw BizException.unprocessable("FILE_PARSE_ERROR", "表格解析失败：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw BizException.unprocessable("EMPTY_FILE", "表格里没有可导入的数据行");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int skippedCount = 0;
        Set<String> seen = new HashSet<>();
        for (Map<String, String> r : rows) {
            String sid = r.getOrDefault("student_id", "").trim();
            int rowNo = toInt(r.get("row_no"), 0);
            if (sid.isEmpty()) {
                errors.add(Map.of("row", rowNo, "student_id", "", "message", "学号为空"));
                continue;
            }
            Long scId = roster.get(sid);
            if (scId == null) {
                errors.add(Map.of("row", rowNo, "student_id", sid, "message", "该学号不在本课堂名单中"));
                continue;
            }
            if (!seen.add(sid)) {
                errors.add(Map.of("row", rowNo, "student_id", sid, "message", "同一学号在表格中重复出现"));
                continue;
            }
            Map<String, Object> existingRow = existingScores.get(scId);
            if (existingRow != null && existingRow.get("score_status") != null) {
                int st = ((Number) existingRow.get("score_status")).intValue();
                if (st >= 1 && st <= 3) {
                    skippedCount++;
                    errors.add(Map.of("row", rowNo, "student_id", sid,
                            "message", dryRun
                                    ? "预览提示：该成绩已提交/已发布，正式导入时会被跳过"
                                    : "成绩已提交/已发布，不能通过导入修改"));
                    continue;
                }
            }
            int targetScoreType = (existingRow != null && existingRow.get("score_type") != null)
                    ? ((Number) existingRow.get("score_type")).intValue()
                    : defaultType;
            try {
                int examFlag = parseExamFlag(r.get("exam_flag"));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sc_id", scId);
                item.put("usual_ratio", ratio);
                item.put("exam_flag", examFlag);
                item.put("score_type", targetScoreType);
                if (targetScoreType == 1) {
                    item.put("usual_score", parseOptionalScore(r.get("usual_score"), "平时成绩"));
                    item.put("exam_score", parseOptionalScore(r.get("exam_score"), "期末成绩"));
                    item.put("score_level", null);
                } else {
                    String rawLevel = r.getOrDefault("score_level", "").trim();
                    if (rawLevel.isEmpty()) {
                        rawLevel = r.getOrDefault("exam_score", "").trim();
                    }
                    String validatedLevel = parseOptionalLevel(rawLevel, targetScoreType);
                    if (validatedLevel == null && examFlag == 0) {
                        String typeName = targetScoreType == 2 ? "五级制（优/良/中/及格/不及格）" : "两级制（合格/不合格）";
                        throw BizException.unprocessable("LEVEL_REQUIRED",
                                "该课堂/记录为" + typeName + "记分，请填写有效等级，不能直接导入百分制分数");
                    }
                    item.put("usual_score", null);
                    item.put("exam_score", null);
                    item.put("score_level", validatedLevel);
                }
                items.add(item);
            } catch (BizException e) {
                errors.add(Map.of("row", rowNo, "student_id", sid, "message", e.getMessage()));
            }
        }
        if (dryRun) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("lesson_no", lessonNo);
            preview.put("usual_ratio", ratio);
            preview.put("dry_run", true);
            preview.put("imported", 0);
            preview.put("preview_count", items.size());
            preview.put("skipped", skippedCount);
            preview.put("failed", errors.size());
            preview.put("errors", errors);
            String msg = items.isEmpty()
                    ? "预览完成：没有可导入的有效行（" + errors.size() + " 行将被跳过或有误）"
                    : "预览通过 " + items.size() + " 行" + (errors.isEmpty() ? "，确认后可正式导入" : "，" + errors.size() + " 行将被跳过或有误");
            preview.put("message", msg);
            return preview;
        }
        if (items.isEmpty()) {
            throw BizException.unprocessable("NO_VALID_ROW", "没有可导入的有效行：" + summarizeErrors(errors));
        }
        Map<String, Object> saved = saveGrades(lessonNo, items, ratio);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lesson_no", lessonNo);
        result.put("usual_ratio", ratio);
        result.put("imported", saved.get("saved"));
        result.put("skipped", skippedCount);
        result.put("failed", errors.size());
        result.put("errors", errors);
        result.put("message", "已导入 " + saved.get("saved") + " 条成绩" + (errors.isEmpty() ? "" : "，" + errors.size() + " 行未导入"));
        return result;
    }

    private static String parseOptionalLevel(String raw, int scoreType) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.equals("—") || v.equals("-")) {
            return null;
        }
        if (scoreType == 2) {
            if (!Set.of("优", "良", "中", "及格", "不及格").contains(v)) {
                throw BizException.unprocessable("INVALID_LEVEL",
                        "五级制课堂不能导入「" + v + "」，等级只能为 优/良/中/及格/不及格");
            }
            return v;
        }
        if (!Set.of("合格", "不合格").contains(v)) {
            throw BizException.unprocessable("INVALID_LEVEL",
                    "两级制课堂不能导入「" + v + "」，等级只能为 合格/不合格");
        }
        return v;
    }

    private static BigDecimal parseOptionalScore(String raw, String label) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty() || v.equals("—") || v.equals("-")) {
            return null;
        }
        BigDecimal d;
        try {
            d = new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw BizException.unprocessable("INVALID_SCORE", label + "不是数字：" + v);
        }
        if (d.signum() < 0 || d.compareTo(new BigDecimal("100")) > 0) {
            throw BizException.unprocessable("SCORE_OUT_OF_RANGE", label + "须在 0~100 之间");
        }
        return d;
    }

    private static int parseExamFlag(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return 0;
        }
        return switch (v) {
            case "0", "正常" -> 0;
            case "1", "缺考" -> 1;
            case "2", "缓考" -> 2;
            case "3", "违纪" -> 3;
            default -> throw BizException.unprocessable("INVALID_EXAM_FLAG", "考试情况只能为正常/缺考/缓考/违纪");
        };
    }

    private static String summarizeErrors(List<Map<String, Object>> errors) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(errors.size(), 5); i++) {
            parts.add("第" + errors.get(i).get("row") + "行 " + errors.get(i).get("message"));
        }
        return String.join("；", parts) + (errors.size() > 5 ? " 等" : "");
    }

    /** 3.2 总评计算与提交：只校验输入数据规范，计算后置 1，写入 score_audit 批次并生成 6 位合法核对随机码 */
    @Transactional
    public Map<String, Object> submit(String lessonNo) {
        Map<String, Object> head = requireClass(lessonNo);
        CurrentUser u = UserContext.requireRole(RoleEnum.TEACHER, RoleEnum.SYS_ADMIN);
        if (u.role() == RoleEnum.TEACHER && !owns(head, u)) {
            throw BizException.forbidden("只能提交本人任教的课堂");
        }
        List<String> tcIds = tcIds(head);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT sc.sc_id, sc.student_id, s.score_id, s.usual_ratio, s.usual_score, s.exam_score,
                       s.exam_flag, s.score_type, s.score_level, s.score_status
                FROM sc
                LEFT JOIN score s ON s.sc_id = sc.sc_id AND s.exam_type = 1
                WHERE sc.tc_id IN (%s) AND sc.select_status = 1
                FOR UPDATE
                """.formatted(marks(tcIds)), tcIds.toArray());
        if (rows.isEmpty()) {
            throw BizException.unprocessable("EMPTY_ROSTER", "该课堂没有已选中学生");
        }
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        boolean hasDraft = false;
        for (Map<String, Object> r : rows) {
            String sid = String.valueOf(r.get("student_id"));
            if (r.get("score_id") == null) {
                missing.add(sid);
                continue;
            }
            int st = ((Number) r.get("score_status")).intValue();
            if (st == 0 || st == 4) {
                hasDraft = true;
            }
            int flag = r.get("exam_flag") == null ? 0 : ((Number) r.get("exam_flag")).intValue();
            int scoreType = r.get("score_type") == null ? 1 : ((Number) r.get("score_type")).intValue();
            int ratio = r.get("usual_ratio") == null ? -1 : ((Number) r.get("usual_ratio")).intValue();
            BigDecimal usual = r.get("usual_score") == null ? null : new BigDecimal(String.valueOf(r.get("usual_score")));
            BigDecimal exam = r.get("exam_score") == null ? null : new BigDecimal(String.valueOf(r.get("exam_score")));
            String level = str(r.get("score_level"));
            if (ratio < 0 || ratio > 100) {
                invalid.add(sid + "占比非法");
            } else if (scoreType == 1) {
                if (flag == 0 && (usual == null || exam == null)) {
                    invalid.add(sid + "缺项");
                } else if ((usual != null && (usual.signum() < 0 || usual.compareTo(new BigDecimal("100")) > 0))
                        || (exam != null && (exam.signum() < 0 || exam.compareTo(new BigDecimal("100")) > 0))) {
                    invalid.add(sid + "超范围");
                }
            } else if (scoreType == 2) {
                if (flag == 0 && !Set.of("优", "良", "中", "及格", "不及格").contains(level)) {
                    invalid.add(sid + "等级非法");
                }
            } else if (scoreType == 3) {
                if (flag == 0 && !Set.of("合格", "不合格").contains(level)) {
                    invalid.add(sid + "等级非法");
                }
            }
        }
        if (!missing.isEmpty()) {
            throw BizException.unprocessable("MISSING_SCORE", "以下学生还没有录入成绩：" + String.join("、", missing));
        }
        if (!invalid.isEmpty()) {
            throw BizException.unprocessable("INVALID_SCORE", "数据不符合规范：" + String.join("、", invalid));
        }
        if (!hasDraft) {
            throw BizException.conflict("ALREADY_SUBMITTED", "该课堂没有待提交（录入中/退回修改）的成绩");
        }

        LocalDateTime submitTime = nextUniqueSubmitTime(lessonNo);
        Timestamp ts = Timestamp.valueOf(submitTime);
        for (Map<String, Object> r : rows) {
            int st = ((Number) r.get("score_status")).intValue();
            if (st != 0 && st != 4) {
                continue;
            }
            int flag = r.get("exam_flag") == null ? 0 : ((Number) r.get("exam_flag")).intValue();
            int scoreType = r.get("score_type") == null ? 1 : ((Number) r.get("score_type")).intValue();
            int ratio = ((Number) r.get("usual_ratio")).intValue();
            BigDecimal usual = r.get("usual_score") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(r.get("usual_score")));
            BigDecimal exam = r.get("exam_score") == null ? null : new BigDecimal(String.valueOf(r.get("exam_score")));
            String level = str(r.get("score_level"));
            BigDecimal total = null;
            BigDecimal gp = null;
            if (scoreType == 1) {
                if (flag == 0 && exam != null) {
                    total = usual.multiply(BigDecimal.valueOf(ratio))
                            .add(exam.multiply(BigDecimal.valueOf(100 - ratio)))
                            .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
                    gp = percentToPoint(total);
                } else if (flag == 1 || flag == 3) {
                    total = BigDecimal.ZERO;
                    gp = BigDecimal.ZERO;
                }
            } else {
                if (flag == 0 && !level.isEmpty()) {
                    gp = levelToPoint(level);
                } else if (flag == 1 || flag == 3) {
                    level = (scoreType == 2) ? "不及格" : "不合格";
                    gp = BigDecimal.ZERO;
                }
            }
            jdbc.update("""
                    UPDATE score SET total_score = ?, score_level = ?, grade_point = ?,
                           score_status = 1, submit_time = ?
                     WHERE sc_id = ? AND exam_type = 1
                    """, total, scoreType == 1 ? null : (level.isEmpty() ? null : level), gp, ts, r.get("sc_id"));
        }

        String code = generateVerifyCode();
        jdbc.update("""
                INSERT INTO score_audit (lesson_no, submit_time, verify_code, auditor_id, audit_result, audit_opinion, audit_time)
                VALUES (?, ?, ?, NULL, 0, NULL, NULL)
                """, lessonNo, ts, code);
        return Map.of("lesson_no", lessonNo, "submitted", rows.size(), "verify_code", code, "submit_time", submitTime.toString());
    }

    /** 3.2/3.3 成绩登记表（含核对码） */
    public Map<String, Object> sheet(String lessonNo) {
        return roster(lessonNo);
    }

    /** 3.3 学院待核对课堂（按课堂号聚合） */
    public List<Map<String, Object>> auditPending(String term) {
        CurrentUser u = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT lc.lesson_no, MIN(lc.tc_id) AS tc_id, tc.term, c.course_name, c.open_college,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 1) AS wait_count,
                       (SELECT MAX(s.submit_time) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1) AS submit_time
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE EXISTS (SELECT 1 FROM score s JOIN sc ON s.sc_id = sc.sc_id
                               JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                               WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 1)
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
        sql.append(" GROUP BY lc.lesson_no, tc.term, c.course_name, c.open_college ");
        sql.append(" ORDER BY tc.term DESC, lc.lesson_no ");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.3 学院输码核对（对齐 v9 score_audit.lesson_no 与 verify_code）：通过置 2，退回置 4 并必填差异说明 */
    @Transactional
    public Map<String, Object> audit(String lessonNo, String verifyCode, int result, String opinion) {
        CurrentUser u = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        Map<String, Object> head = requireClass(lessonNo);
        String college = String.valueOf(head.get("open_college"));
        if (u.role() == RoleEnum.COLLEGE_SECRETARY && u.collegeId() != null && !u.collegeId().equals(college)) {
            throw BizException.forbidden("只能核对本院开设课程");
        }
        if (result != 1 && result != 2) {
            throw BizException.unprocessable("INVALID_RESULT", "请选择核对结论（通过或退回）");
        }
        String op = opinion == null ? "" : opinion.trim();
        if (result == 2 && op.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "退回时必须填写差异说明");
        }
        List<String> tcIds = tcIds(head);
        Integer wait = jdbc.queryForObject("""
                SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                 WHERE sc.tc_id IN (%s) AND s.exam_type = 1 AND s.score_status = 1
                """.formatted(marks(tcIds)), Integer.class, tcIds.toArray());
        if (wait == null || wait == 0) {
            throw BizException.conflict("NOT_SUBMITTED", "该课堂没有待核对（已提交）的成绩");
        }

        List<Map<String, Object>> pendingAudits = jdbc.queryForList("""
                SELECT audit_id, verify_code, submit_time
                FROM score_audit
                WHERE lesson_no = ? AND audit_result = 0
                ORDER BY submit_time DESC, audit_id DESC
                LIMIT 1 FOR UPDATE
                """, lessonNo);
        if (pendingAudits.isEmpty()) {
            throw BizException.conflict("NO_PENDING_AUDIT", "未找到该课堂最新的待核对批次记录");
        }
        Map<String, Object> batch = pendingAudits.get(0);
        String expectedCode = String.valueOf(batch.get("verify_code")).trim();
        String inputCode = verifyCode == null ? "" : verifyCode.trim().toUpperCase(Locale.ROOT);
        if (!expectedCode.equalsIgnoreCase(inputCode)) {
            throw BizException.unprocessable("CODE_MISMATCH", "核对码与系统不一致，请对照纸质成绩登记表重新输入");
        }

        long auditId = ((Number) batch.get("audit_id")).longValue();
        jdbc.update("""
                UPDATE score_audit
                SET auditor_id = ?, audit_result = ?, audit_opinion = ?, audit_time = NOW()
                WHERE audit_id = ? AND audit_result = 0
                """, u.empId(), result, op.isEmpty() ? (result == 1 ? "核对纸质成绩单无误" : op) : op, auditId);
        jdbc.update("""
                UPDATE score SET score_status = ?
                 WHERE sc_id IN (SELECT sc_id FROM sc WHERE tc_id IN (%s)) AND exam_type = 1 AND score_status = 1
                """.formatted(marks(tcIds)), append(new Object[]{result == 1 ? 2 : 4}, tcIds));
        return Map.of("lesson_no", lessonNo, "audit_id", String.valueOf(auditId), "result", result,
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
                SELECT lc.lesson_no, MIN(lc.tc_id) AS tc_id, tc.term, c.course_name,
                       (SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                         JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                         WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 2) AS ready_count
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE EXISTS (SELECT 1 FROM score s JOIN sc ON s.sc_id = sc.sc_id
                               JOIN lesson_class lc2 ON sc.tc_id = lc2.tc_id
                               WHERE lc2.lesson_no = lc.lesson_no AND s.exam_type = 1 AND s.score_status = 2)
                """ + termSql + """
                GROUP BY lc.lesson_no, tc.term, c.course_name
                ORDER BY tc.term DESC, lc.lesson_no
                """, args.toArray());
    }

    /** 3.4 成绩发布：校验本次提交批次已审核通过（score_audit.lesson_no），置 3、精准重标 is_final 并刷新学业统计 */
    @Transactional
    public Map<String, Object> publish(String lessonNo) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        Map<String, Object> head = requireClass(lessonNo);
        List<String> tcIds = tcIds(head);
        Integer ready = jdbc.queryForObject("""
                SELECT COUNT(*) FROM score s JOIN sc ON s.sc_id = sc.sc_id
                 WHERE sc.tc_id IN (%s) AND s.exam_type = 1 AND s.score_status = 2
                """.formatted(marks(tcIds)), Integer.class, tcIds.toArray());
        if (ready == null || ready == 0) {
            throw BizException.conflict("NOT_READY", "该课堂没有学院核对通过的成绩");
        }
        List<Map<String, Object>> latestAudit = jdbc.queryForList("""
                SELECT audit_id, audit_result, submit_time
                FROM score_audit
                WHERE lesson_no = ?
                ORDER BY submit_time DESC, audit_id DESC
                LIMIT 1
                """, lessonNo);
        if (latestAudit.isEmpty() || ((Number) latestAudit.get(0).get("audit_result")).intValue() != 1) {
            throw BizException.conflict("NO_AUDIT", "该课堂最新提交批次尚未通过学院核对，不能发布");
        }

        jdbc.update("""
                UPDATE score SET score_status = 3, publish_time = NOW()
                 WHERE sc_id IN (SELECT sc_id FROM sc WHERE tc_id IN (%s)) AND exam_type = 1 AND score_status = 2
                """.formatted(marks(tcIds)), tcIds.toArray());

        String courseId = String.valueOf(head.get("course_id"));
        String term = String.valueOf(head.get("term"));
        List<Map<String, Object>> targets = jdbc.queryForList("""
                SELECT DISTINCT sc.student_id, sc.stat_stage
                FROM score s JOIN sc ON s.sc_id = sc.sc_id
                WHERE sc.tc_id IN (%s) AND s.exam_type = 1 AND s.score_status = 3
                """.formatted(marks(tcIds)), tcIds.toArray());
        for (Map<String, Object> st : targets) {
            String sid = String.valueOf(st.get("student_id"));
            int stage = ((Number) st.get("stat_stage")).intValue();
            recomputeIsFinalJoint(sid, stage, courseId);
            recomputeAcademicStats(sid, stage, term);
        }
        return Map.of("lesson_no", lessonNo, "published", ready);
    }

    /** 3.5 校外交流成绩认定：列表 */
    public List<Map<String, Object>> listExchange(String studentId, String keyword) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT CAST(e.convert_id AS CHAR) AS convert_id, e.student_id,
                       COALESCE(st.student_name, e.student_id) AS student_name,
                       e.exchange_apply_id, e.term, e.stat_stage, e.host_school,
                       e.ext_course_code, e.ext_course_name, e.ext_credit, e.ext_score_raw, e.ext_scale_type,
                       e.course_id, COALESCE(c.course_name, e.course_id) AS course_name,
                       e.score_type, e.converted_score, e.converted_level, e.grade_point,
                       e.is_final, e.proof_no, e.audit_status,
                       e.college_audit_opinion, e.school_audit_opinion, e.apply_time
                FROM exchange_score_convert e
                LEFT JOIN student st ON e.student_id = st.student_id
                LEFT JOIN course c ON e.course_id = c.course_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (studentId != null && !studentId.isBlank()) {
            sql.append(" AND e.student_id = ? ");
            args.add(studentId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.ext_course_name LIKE ? OR e.host_school LIKE ? OR e.proof_no LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY e.convert_id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 3.5 校外交流成绩认定：登记并换算 */
    @Transactional
    public Map<String, Object> createExchange(Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
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
        boolean extRequired = Boolean.parseBoolean(String.valueOf(body.getOrDefault("ext_required", "false")));
        List<Map<String, Object>> target = jdbc.queryForList(
                "SELECT course_id, course_name, course_type, credit FROM course WHERE course_id = ?", courseId);
        if (target.isEmpty()) {
            throw BizException.notFound("本校课程号不存在：" + courseId);
        }
        String targetType = str(target.get(0).get("course_type"));
        if (extRequired && !isRequiredType(targetType)) {
            throw BizException.unprocessable("REQUIRED_COURSE_MISMATCH",
                    "外校课程按必修认定时，本校对应课程也必须是必修课；当前 " + courseId
                            + " 是「" + courseTypeName(targetType) + "」");
        }
        BigDecimal credit = dec(body.get("ext_credit"));
        if (credit == null || credit.signum() <= 0) {
            throw BizException.unprocessable("INVALID_CREDIT", "外校学分必须大于 0");
        }
        int scale = toInt(body.get("ext_scale_type"), 1);
        if (scale < 1 || scale > 4) {
            throw BizException.unprocessable("INVALID_SCALE", "外校记分制仅支持百分制、字母等级、绩点或其他");
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
            throw BizException.unprocessable("INVALID_SCORE_TYPE", "换算后记分方式仅支持百分制、五级制或两级制");
        }
        BigDecimal gp = converted != null ? percentToPoint(converted) : levelToPoint(level);
        String applyId = str(body.get("exchange_apply_id"));
        String extCode = str(body.get("ext_course_code"));
        jdbc.update("""
                INSERT INTO exchange_score_convert(student_id, exchange_apply_id, term, stat_stage, host_school,
                    ext_course_code, ext_course_name, ext_credit, ext_score_raw, ext_scale_type, course_id,
                    score_type, converted_score, converted_level, grade_point, is_final, proof_no,
                    version_no, audit_status, apply_time)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 1, 0, NOW())
                """, sid, applyId.isEmpty() ? null : applyId, term, toInt(body.get("stat_stage"), 1), host,
                extCode.isEmpty() ? null : extCode, extName, credit, extRaw, scale, courseId,
                scoreType, converted, level.isEmpty() ? null : level, gp, proofNo);
        return Map.of("student_id", sid, "course_id", courseId, "grade_point", gp,
                "message", "校外成绩已登记，待学院审核、教务处审批后生效");
    }

    /** 3.5 学院审核校外成绩认定（0 -> 1 通过 / 0 -> 3 驳回） */
    @Transactional
    public Map<String, Object> collegeAuditExchange(long convertId, int decision, String opinion) {
        CurrentUser u = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        if (decision != 1 && decision != 2) {
            throw BizException.unprocessable("INVALID_DECISION", "请选择审核决定（通过或驳回）");
        }
        String op = opinion == null ? "" : opinion.trim();
        if (decision == 2 && op.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "学院驳回时必须填写审核意见");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT convert_id, audit_status FROM exchange_score_convert WHERE convert_id = ? FOR UPDATE", convertId);
        if (rows.isEmpty()) {
            throw BizException.notFound("校外成绩认定记录不存在");
        }
        if (((Number) rows.get(0).get("audit_status")).intValue() != 0) {
            throw BizException.conflict("INVALID_STATUS", "仅待学院审核状态的记录可执行学院审核");
        }
        int nextStatus = (decision == 1) ? 1 : 3;
        jdbc.update("""
                UPDATE exchange_score_convert
                SET audit_status = ?, college_auditor_id = ?, college_audit_opinion = ?, college_audit_time = NOW()
                WHERE convert_id = ? AND audit_status = 0
                """, nextStatus, u.empId(), op.isEmpty() ? "学院审核通过" : op, convertId);
        return Map.of("convert_id", String.valueOf(convertId), "audit_status", nextStatus,
                "message", decision == 1 ? "学院审核通过，已转教务处审批" : "学院已驳回该认定申请");
    }

    /** 3.5 教务处审批校外成绩认定（1 -> 2 生效并联合重标 is_final & 刷新学业统计 / 1 -> 3 驳回） */
    @Transactional
    public Map<String, Object> schoolApproveExchange(long convertId, int decision, String opinion) {
        CurrentUser u = UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        if (decision != 1 && decision != 2) {
            throw BizException.unprocessable("INVALID_DECISION", "请选择审批决定（通过或驳回）");
        }
        String op = opinion == null ? "" : opinion.trim();
        if (decision == 2 && op.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "教务处驳回时必须填写审批意见");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT convert_id, student_id, term, stat_stage, course_id, audit_status FROM exchange_score_convert WHERE convert_id = ? FOR UPDATE",
                convertId);
        if (rows.isEmpty()) {
            throw BizException.notFound("校外成绩认定记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (((Number) row.get("audit_status")).intValue() != 1) {
            throw BizException.conflict("INVALID_STATUS", "仅待教务处审批状态的记录可执行审批");
        }
        int nextStatus = (decision == 1) ? 2 : 3;
        jdbc.update("""
                UPDATE exchange_score_convert
                SET audit_status = ?, school_auditor_id = ?, school_audit_opinion = ?, school_audit_time = NOW()
                WHERE convert_id = ? AND audit_status = 1
                """, nextStatus, u.empId(), op.isEmpty() ? "教务处审批通过并生效" : op, convertId);
        if (decision == 1) {
            String sid = String.valueOf(row.get("student_id"));
            int stage = ((Number) row.get("stat_stage")).intValue();
            String courseId = String.valueOf(row.get("course_id"));
            String term = String.valueOf(row.get("term"));
            recomputeIsFinalJoint(sid, stage, courseId);
            recomputeAcademicStats(sid, stage, term);
        }
        return Map.of("convert_id", String.valueOf(convertId), "audit_status", nextStatus,
                "message", decision == 1 ? "教务处审批通过，已计入本校成绩与 GPA" : "教务处已驳回该认定申请");
    }

    /** 3.5 教务处认定：成绩单交回教务处后，由教务处直接把外校课程认定为校内课程并生效 */
    @Transactional
    public Map<String, Object> recognizeExchange(long convertId, String opinion) {
        CurrentUser u = UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT convert_id, student_id, term, stat_stage, course_id, audit_status, college_auditor_id
                FROM exchange_score_convert WHERE convert_id = ? FOR UPDATE
                """, convertId);
        if (rows.isEmpty()) {
            throw BizException.notFound("校外成绩认定记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        int status = ((Number) row.get("audit_status")).intValue();
        if (status == 2) {
            throw BizException.conflict("ALREADY_EFFECTIVE", "该认定已经生效，无需重复认定");
        }
        if (status == 3) {
            throw BizException.conflict("REJECTED", "该认定已被驳回，请重新登记后再认定");
        }
        String schoolOpinion = (opinion == null || opinion.isBlank()) ? "教务处认定：外校课程与本校课程对应，成绩与学分直接计入" : opinion.trim();
        String collegeOpinion = row.get("college_auditor_id") == null ? "教务处直接认定" : "学院已审核";
        jdbc.update("""
                UPDATE exchange_score_convert
                SET audit_status = 2,
                    college_auditor_id = COALESCE(college_auditor_id, ?),
                    college_audit_opinion = COALESCE(college_audit_opinion, ?),
                    college_audit_time = COALESCE(college_audit_time, NOW()),
                    school_auditor_id = ?, school_audit_opinion = ?, school_audit_time = NOW()
                WHERE convert_id = ?
                """, u.empId(), collegeOpinion, u.empId(), schoolOpinion, convertId);
        String sid = String.valueOf(row.get("student_id"));
        int stage = ((Number) row.get("stat_stage")).intValue();
        String courseId = String.valueOf(row.get("course_id"));
        String term = String.valueOf(row.get("term"));
        recomputeIsFinalJoint(sid, stage, courseId);
        recomputeAcademicStats(sid, stage, term);
        return Map.of("convert_id", String.valueOf(convertId), "audit_status", 2,
                "message", "教务处已认定，外校成绩与学分已计入本校成绩");
    }

    /** 3.5 认定候选课程：按课程名检索校内课程，标明是否必修，供学院与教务处做学分对应 */
    public List<Map<String, Object>> listCourseCandidates(String keyword) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);
        StringBuilder sql = new StringBuilder("""
                SELECT course_id, course_name, course_type, credit, open_college
                FROM course WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (course_name LIKE ? OR course_id LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY course_type, course_id LIMIT 50");
        List<Map<String, Object>> list = jdbc.queryForList(sql.toString(), args.toArray());
        for (Map<String, Object> c : list) {
            String type = str(c.get("course_type"));
            c.put("course_type_name", courseTypeName(type));
            c.put("required", isRequiredType(type));
        }
        return list;
    }

    private static String courseTypeName(String type) {
        return switch (type) {
            case "11" -> "校级必修";
            case "12" -> "院级必修";
            case "13" -> "专业必修";
            case "14" -> "专业选修";
            case "15" -> "通识选修";
            case "16" -> "实践环节";
            default -> "其他";
        };
    }

    private static boolean isRequiredType(String type) {
        return Set.of("11", "12", "13").contains(type);
    }

    // ---------- 内部工具与统计同步 ----------

    /** 联合 score(status=3) 与 exchange_score_convert(status=2) 按 (student_id, stat_stage, course_id) 唯一标记 is_final */
    private void recomputeIsFinalJoint(String studentId, int statStage, String courseId) {
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                SELECT 'SCORE' AS src, s.score_id AS rec_id,
                       COALESCE(s.grade_point, 0) AS gp, COALESCE(s.total_score, 0) AS num_score,
                       s.publish_time AS eff_time
                FROM score s
                JOIN sc ON s.sc_id = sc.sc_id
                JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                WHERE sc.student_id = ? AND sc.stat_stage = ? AND tc.course_id = ? AND s.score_status = 3
                UNION ALL
                SELECT 'EXCHANGE' AS src, e.convert_id AS rec_id,
                       COALESCE(e.grade_point, 0) AS gp, COALESCE(e.converted_score, 0) AS num_score,
                       e.school_audit_time AS eff_time
                FROM exchange_score_convert e
                WHERE e.student_id = ? AND e.stat_stage = ? AND e.course_id = ? AND e.audit_status = 2
                ORDER BY gp DESC, num_score DESC, eff_time DESC, rec_id DESC
                """, studentId, statStage, courseId, studentId, statStage, courseId);
        if (candidates.isEmpty()) {
            return;
        }
        String bestSrc = String.valueOf(candidates.get(0).get("src"));
        long bestId = ((Number) candidates.get(0).get("rec_id")).longValue();
        for (Map<String, Object> c : candidates) {
            String src = String.valueOf(c.get("src"));
            long id = ((Number) c.get("rec_id")).longValue();
            int flag = (src.equals(bestSrc) && id == bestId) ? 1 : 0;
            if ("SCORE".equals(src)) {
                jdbc.update("UPDATE score SET is_final = ? WHERE score_id = ?", flag, id);
            } else {
                jdbc.update("UPDATE exchange_score_convert SET is_final = ? WHERE convert_id = ?", flag, id);
            }
        }
    }

    /** 同事务刷新 stu_gpa、stu_term_gpa 与同专业同年级排名（严格排除 sc.study_type=3 辅修） */
    private void recomputeAcademicStats(String studentId, int statStage, String term) {
        Integer stuExists = jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE student_id = ?", Integer.class, studentId);
        if (stuExists == null || stuExists == 0) {
            return;
        }
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
            BigDecimal num = resolveNumericScore(r.get("score_type"), r.get("total_score"), r.get("score_level"));
            if (gp.compareTo(BigDecimal.ZERO) > 0) {
                earned = earned.add(credit);
            } else {
                failed = failed.add(credit);
            }
            totalCredit = totalCredit.add(credit);
            gpWeighted = gpWeighted.add(gp.multiply(credit));
            scoreWeighted = scoreWeighted.add(num.multiply(credit));
        }
        BigDecimal gpa = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? gpWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;
        BigDecimal avgScore = totalCredit.compareTo(BigDecimal.ZERO) > 0
                ? scoreWeighted.divide(totalCredit, 2, RoundingMode.HALF_UP) : null;

        jdbc.update("""
                INSERT INTO stu_gpa (student_id, earned_credit, failed_credit, gpa, avg_score, update_time)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                  earned_credit = VALUES(earned_credit), failed_credit = VALUES(failed_credit),
                  gpa = VALUES(gpa), avg_score = VALUES(avg_score), update_time = NOW()
                """, studentId, earned, failed, gpa, avgScore);

        List<Map<String, Object>> termRows = jdbc.queryForList("""
                SELECT course_id, MAX(credit) AS credit, MAX(gp) AS best_gp FROM (
                  SELECT tc.course_id, c.credit, COALESCE(s.grade_point, 0) AS gp
                  FROM score s JOIN sc ON s.sc_id = sc.sc_id
                  JOIN teaching_class tc ON sc.tc_id = tc.tc_id
                  JOIN course c ON tc.course_id = c.course_id
                  WHERE sc.student_id = ? AND sc.stat_stage = ? AND sc.study_type <> 3
                    AND tc.term = ? AND s.score_status = 3
                  UNION ALL
                  SELECT e.course_id, c.credit, COALESCE(e.grade_point, 0) AS gp
                  FROM exchange_score_convert e JOIN course c ON e.course_id = c.course_id
                  WHERE e.student_id = ? AND e.stat_stage = ? AND e.term = ? AND e.audit_status = 2
                ) t GROUP BY course_id
                """, studentId, statStage, term, studentId, statStage, term);
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
                  term_credit = VALUES(term_credit), term_failed_credit = VALUES(term_failed_credit), update_time = NOW()
                """, studentId, term, termEarned, termFailed);

        List<Map<String, Object>> stuMeta = jdbc.queryForList(
                "SELECT major_id, grade FROM student WHERE student_id = ?", studentId);
        if (!stuMeta.isEmpty() && stuMeta.get(0).get("major_id") != null && stuMeta.get(0).get("grade") != null) {
            String majorId = String.valueOf(stuMeta.get(0).get("major_id"));
            Object grade = stuMeta.get(0).get("grade");
            List<Map<String, Object>> peers = jdbc.queryForList("""
                    SELECT g.student_id, g.avg_score
                    FROM stu_gpa g JOIN student st ON g.student_id = st.student_id
                    WHERE st.major_id = ? AND st.grade = ? AND g.avg_score IS NOT NULL
                    ORDER BY g.avg_score DESC, g.student_id ASC
                    """, majorId, grade);
            int totalPeers = peers.size();
            BigDecimal prev = null;
            int rank = 0;
            for (int i = 0; i < peers.size(); i++) {
                BigDecimal cur = new BigDecimal(String.valueOf(peers.get(i).get("avg_score")));
                if (prev == null || cur.compareTo(prev) != 0) {
                    rank = i + 1;
                    prev = cur;
                }
                jdbc.update("UPDATE stu_gpa SET major_rank = ?, major_rank_total = ? WHERE student_id = ?",
                        rank, totalPeers, peers.get(i).get("student_id"));
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

    private LocalDateTime nextUniqueSubmitTime(String lessonNo) {
        LocalDateTime candidate = LocalDateTime.now().withNano(0);
        List<Timestamp> latest = jdbc.queryForList(
                "SELECT MAX(submit_time) FROM score_audit WHERE lesson_no = ?", Timestamp.class, lessonNo);
        if (!latest.isEmpty() && latest.get(0) != null) {
            LocalDateTime last = latest.get(0).toLocalDateTime().withNano(0);
            if (!candidate.isAfter(last)) {
                candidate = last.plusSeconds(1);
            }
        }
        return candidate;
    }

    private String latestVerifyCode(String lessonNo) {
        List<String> codes = jdbc.queryForList("""
                SELECT verify_code FROM score_audit
                WHERE lesson_no = ?
                ORDER BY submit_time DESC, audit_id DESC
                LIMIT 1
                """, String.class, lessonNo);
        return codes.isEmpty() ? null : codes.get(0);
    }

    private static String generateVerifyCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(VERIFY_ALPHABET[RANDOM.nextInt(VERIFY_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String marks(List<String> ids) {
        return String.join(",", Collections.nCopies(ids.size(), "?"));
    }

    private static Object[] append(Object[] head, List<String> tail) {
        Object[] all = Arrays.copyOf(head, head.length + tail.size());
        for (int i = 0; i < tail.size(); i++) {
            all[head.length + i] = tail.get(i);
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private static List<String> tcIds(Map<String, Object> head) {
        return (List<String>) head.get("tc_ids");
    }

    @SuppressWarnings("unchecked")
    private static boolean owns(Map<String, Object> head, CurrentUser u) {
        return ((List<String>) head.get("teacher_ids")).stream()
                .anyMatch(t -> t.equals(u.empId()) || t.equals(u.teacherCode8()));
    }

    private static int defaultScoreType(Map<String, Object> head) {
        return "2".equals(str(head.get("assess_type"))) ? 2 : 1;
    }

    private Map<String, Object> requireClass(String lessonNo) {
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT lc.lesson_no, lc.tc_id, tc.term, tc.course_id, c.course_name, c.credit,
                       c.open_college, c.assess_type, tc.teacher_id, tc.class_ids
                FROM lesson_class lc
                JOIN teaching_class tc ON lc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE lc.lesson_no = ?
                ORDER BY lc.tc_id
                """, lessonNo);
        if (list.isEmpty()) {
            throw BizException.notFound("课堂号不存在：" + lessonNo);
        }
        Map<String, Object> head = new LinkedHashMap<>(list.get(0));
        List<String> ids = new ArrayList<>();
        List<String> teachers = new ArrayList<>();
        for (Map<String, Object> r : list) {
            ids.add(String.valueOf(r.get("tc_id")));
            teachers.add(String.valueOf(r.get("teacher_id")).trim());
        }
        head.put("tc_ids", ids);
        head.put("teacher_ids", teachers);
        return head;
    }

    private int ratioMin() {
        List<String> vals = jdbc.queryForList("""
                SELECT dict_name FROM sys_code_dict
                 WHERE is_enabled = 1
                   AND ((dict_group = 'system_param' AND dict_code = 'usual_ratio_min')
                        OR dict_group = 'usual_ratio_min')
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

    /** 校发〔2021〕46 号附表 3（与业务④⑤完全一致） */
    private static BigDecimal percentToPoint(BigDecimal score) {
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
