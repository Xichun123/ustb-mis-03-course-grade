package com.ustb.coursegrade.module.prep;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.PageResult;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 【业务① 选课准备与发布（模块 1.1–1.5）· 负责人：朱鳞太】
 * 全组标准样板实现：展示统一权限校验、事务控制、v9 CHECK 前置校验与分页查询写法。
 */
@Service
public class PrepService {

    private final JdbcTemplate jdbc;

    public PrepService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 1.1 开课计划导入（只读检索第 2 组已发布教学班及限选概览） */
    public PageResult<Map<String, Object>> listTeachingClasses(
            String term, String keyword, String collegeId, String tcStatus, int page, int pageSize) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR,
                RoleEnum.COLLEGE_SECRETARY, RoleEnum.SYS_ADMIN);

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        CurrentUser user = UserContext.requireUser();
        if (user.role() == RoleEnum.COLLEGE_SECRETARY && user.collegeId() != null) {
            where.append(" AND c.open_college = ? ");
            params.add(user.collegeId());
        } else if (collegeId != null && !collegeId.isBlank()) {
            where.append(" AND c.open_college = ? ");
            params.add(collegeId.trim());
        }

        if (term != null && !term.isBlank()) {
            where.append(" AND tc.term = ? ");
            params.add(term.trim());
        }
        if (tcStatus != null && !tcStatus.isBlank()) {
            where.append(" AND tc.tc_status = ? ");
            params.add(tcStatus.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (tc.tc_id LIKE ? OR c.course_id LIKE ? OR c.course_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        String countSql = "SELECT COUNT(*) FROM teaching_class tc JOIN course c ON tc.course_id = c.course_id " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        if (total == null) {
            total = 0L;
        }

        int offset = Math.max(0, (page - 1) * pageSize);
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add(offset);

        String dataSql = """
                SELECT tc.tc_id, tc.term, tc.course_id, c.course_name, c.course_type, c.credit,
                       c.open_college, tc.teacher_id, tc.class_ids, tc.capacity, tc.actual_num,
                       tc.tc_type, tc.tc_status,
                       (SELECT COUNT(*) FROM select_limit sl WHERE sl.tc_id = tc.tc_id) AS limit_count,
                       (SELECT lc.lesson_no FROM lesson_class lc WHERE lc.tc_id = tc.tc_id LIMIT 1) AS lesson_no
                FROM teaching_class tc
                JOIN course c ON tc.course_id = c.course_id
                """ + where + " ORDER BY tc.term DESC, tc.tc_id ASC LIMIT ? OFFSET ?";

        List<Map<String, Object>> items = jdbc.queryForList(dataSql, dataParams.toArray());
        return new PageResult<>(items, total, page, pageSize);
    }

    /** 1.2 查看某教学班现有限选条件及是否允许编辑 */
    public Map<String, Object> getTeachingClassLimits(String tcId) {
        CurrentUser user = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.SYS_ADMIN);

        List<Map<String, Object>> tcRows = jdbc.queryForList("""
                SELECT tc.tc_id, tc.term, tc.course_id, c.course_name, c.open_college,
                       tc.class_ids, tc.tc_type, tc.tc_status
                FROM teaching_class tc
                JOIN course c ON tc.course_id = c.course_id
                WHERE tc.tc_id = ?
                """, tcId);
        if (tcRows.isEmpty()) {
            throw BizException.notFound("教学班不存在: " + tcId);
        }
        Map<String, Object> tc = tcRows.get(0);

        List<Map<String, Object>> limits = jdbc.queryForList(
                "SELECT limit_id, tc_id, limit_type, limit_value FROM select_limit WHERE tc_id = ? ORDER BY limit_type, limit_value",
                tcId);

        Integer lockedRounds = jdbc.queryForObject("""
                SELECT COUNT(*) FROM round_class rc
                JOIN select_round sr ON rc.round_id = sr.round_id
                WHERE rc.tc_id = ? AND sr.round_status IN (1, 2, 3, 5)
                """, Integer.class, tcId);

        boolean collegeMatch = user.role() != RoleEnum.COLLEGE_SECRETARY
                || Objects.equals(user.collegeId(), String.valueOf(tc.get("open_college")));
        boolean editable = collegeMatch && (lockedRounds == null || lockedRounds == 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("teaching_class", tc);
        result.put("limits", limits);
        result.put("editable", editable);
        result.put("lock_reason", editable ? null :
                (!collegeMatch ? "仅开课学院教学秘书或教务处可维护该班限选条件" : "该教学班已关联待审/已审/进行中选课轮次，限选条件已锁定"));
        return result;
    }

    /** 1.2 整体替换某教学班的限选条件（同类型 OR、不同类型 AND，防重复并校验锁定） */
    @Transactional
    public List<Map<String, Object>> replaceLimits(String tcId, List<Map<String, Object>> rawLimits) {
        CurrentUser user = UserContext.requireRole(RoleEnum.COLLEGE_SECRETARY,
                RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);

        List<Map<String, Object>> tcRows = jdbc.queryForList("""
                SELECT tc.tc_id, c.open_college
                FROM teaching_class tc JOIN course c ON tc.course_id = c.course_id
                WHERE tc.tc_id = ? FOR UPDATE
                """, tcId);
        if (tcRows.isEmpty()) {
            throw BizException.notFound("教学班不存在: " + tcId);
        }
        if (user.role() == RoleEnum.COLLEGE_SECRETARY
                && !Objects.equals(user.collegeId(), String.valueOf(tcRows.get(0).get("open_college")))) {
            throw BizException.forbidden("无权修改其他学院教学班的限选条件");
        }

        Integer locked = jdbc.queryForObject("""
                SELECT COUNT(*) FROM round_class rc
                JOIN select_round sr ON rc.round_id = sr.round_id
                WHERE rc.tc_id = ? AND sr.round_status IN (1, 2, 3, 5)
                """, Integer.class, tcId);
        if (locked != null && locked > 0) {
            throw BizException.conflict("LIMIT_LOCKED",
                    "该教学班已关联待审批、已审批或进行中的选课轮次，不可修改限选条件");
        }

        Set<String> dedup = new LinkedHashSet<>();
        List<Object[]> batchArgs = new ArrayList<>();
        if (rawLimits != null) {
            for (Map<String, Object> item : rawLimits) {
                int type = parseInt(item.get("limit_type"), "limit_type");
                if (type < 1 || type > 4) {
                    throw BizException.unprocessable("INVALID_LIMIT_TYPE", "限制类型仅支持按年级、专业、学院或培养层次设置");
                }
                String val = item.get("limit_value") == null ? "" : String.valueOf(item.get("limit_value")).trim();
                if (val.isEmpty() || val.length() > 20) {
                    throw BizException.unprocessable("INVALID_LIMIT_VALUE", "限制值不能为空且不超过 20 字符");
                }
                String key = type + "::" + val;
                if (dedup.add(key)) {
                    batchArgs.add(new Object[]{tcId, type, val});
                }
            }
        }

        jdbc.update("DELETE FROM select_limit WHERE tc_id = ?", tcId);
        if (!batchArgs.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO select_limit (tc_id, limit_type, limit_value) VALUES (?, ?, ?)", batchArgs);
        }
        return jdbc.queryForList(
                "SELECT limit_id, tc_id, limit_type, limit_value FROM select_limit WHERE tc_id = ? ORDER BY limit_type, limit_value",
                tcId);
    }

    /** 1.3/1.5 查询选课轮次列表（学生只看已发布/进行中/已结束，管理人员按权限查看全部） */
    public PageResult<Map<String, Object>> listRounds(String term, Integer roundStatus, int page, int pageSize) {
        CurrentUser user = UserContext.requireUser();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (user.role() == RoleEnum.STUDENT) {
            where.append(" AND sr.round_status IN (2, 3, 4) ");
        }
        if (term != null && !term.isBlank()) {
            where.append(" AND sr.term = ? ");
            params.add(term.trim());
        }
        if (roundStatus != null) {
            where.append(" AND sr.round_status = ? ");
            params.add(roundStatus);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM select_round sr " + where, Long.class, params.toArray());
        if (total == null) {
            total = 0L;
        }

        int offset = Math.max(0, (page - 1) * pageSize);
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add(offset);

        String sql = """
                SELECT sr.*,
                       (SELECT COUNT(*) FROM round_class rc WHERE rc.round_id = sr.round_id) AS class_count
                FROM select_round sr
                """ + where + " ORDER BY sr.round_id DESC LIMIT ? OFFSET ?";
        return new PageResult<>(jdbc.queryForList(sql, dataParams.toArray()), total, page, pageSize);
    }

    /** 1.3/1.5 轮次详情（含绑定教学班列表） */
    public Map<String, Object> getRoundDetail(int roundId) {
        CurrentUser user = UserContext.requireUser();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM select_round WHERE round_id = ?", roundId);
        if (rows.isEmpty()) {
            throw BizException.notFound("选课轮次不存在: " + roundId);
        }
        Map<String, Object> round = new LinkedHashMap<>(rows.get(0));
        int status = ((Number) round.get("round_status")).intValue();
        if (user.role() == RoleEnum.STUDENT && status != 2 && status != 3 && status != 4) {
            throw BizException.forbidden("学生仅可查看已发布、进行中或已结束的选课轮次");
        }
        List<Map<String, Object>> classes = jdbc.queryForList("""
                SELECT rc.tc_id, tc.term, tc.course_id, c.course_name, c.credit,
                       tc.teacher_id, tc.capacity, tc.actual_num, tc.tc_type, tc.tc_status
                FROM round_class rc
                JOIN teaching_class tc ON rc.tc_id = tc.tc_id
                JOIN course c ON tc.course_id = c.course_id
                WHERE rc.round_id = ? ORDER BY rc.tc_id
                """, roundId);
        round.put("classes", classes);
        return round;
    }

    /** 1.3 新建选课轮次草稿（状态 0） */
    @Transactional
    public Map<String, Object> createRound(Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        RoundPayload p = validateAndParsePayload(body);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO select_round
                      (term, round_name, round_type, target_level, start_time, end_time,
                       max_credit, select_mode, allow_retake, retake_ignore_conflict,
                       round_status, notice_content)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.term);
            ps.setString(2, p.roundName);
            ps.setInt(3, p.roundType);
            ps.setInt(4, p.targetLevel);
            ps.setTimestamp(5, Timestamp.valueOf(p.startTime));
            ps.setTimestamp(6, Timestamp.valueOf(p.endTime));
            ps.setBigDecimal(7, p.maxCredit);
            ps.setInt(8, p.selectMode);
            ps.setInt(9, p.allowRetake);
            ps.setInt(10, p.retakeIgnoreConflict);
            ps.setString(11, p.noticeContent);
            return ps;
        }, keyHolder);

        int roundId = Objects.requireNonNull(keyHolder.getKey()).intValue();
        replaceRoundClasses(roundId, p.term, p.tcIds);
        return getRoundDetail(roundId);
    }

    /** 1.3 编辑选课轮次草稿（仅状态 0 可改） */
    @Transactional
    public Map<String, Object> updateRound(int roundId, Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        int status = lockAndGetRoundStatus(roundId);
        if (status != 0) {
            throw BizException.conflict("ROUND_NOT_DRAFT", "仅草稿状态（0）的选课轮次允许编辑");
        }
        RoundPayload p = validateAndParsePayload(body);
        jdbc.update("""
                UPDATE select_round
                SET term=?, round_name=?, round_type=?, target_level=?, start_time=?, end_time=?,
                    max_credit=?, select_mode=?, allow_retake=?, retake_ignore_conflict=?, notice_content=?
                WHERE round_id=? AND round_status=0
                """, p.term, p.roundName, p.roundType, p.targetLevel,
                Timestamp.valueOf(p.startTime), Timestamp.valueOf(p.endTime),
                p.maxCredit, p.selectMode, p.allowRetake, p.retakeIgnoreConflict, p.noticeContent, roundId);

        replaceRoundClasses(roundId, p.term, p.tcIds);
        return getRoundDetail(roundId);
    }

    /** 1.3 提交审批（0 -> 1，按 v7 规则清空上一次退回审批信息） */
    @Transactional
    public Map<String, Object> submitRound(int roundId) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        Map<String, Object> row = lockAndGetRound(roundId);
        int status = ((Number) row.get("round_status")).intValue();
        if (status != 0) {
            throw BizException.conflict("INVALID_STATUS", "当前状态非草稿，不能提交审批");
        }
        String notice = row.get("notice_content") == null ? "" : String.valueOf(row.get("notice_content")).trim();
        if (notice.isEmpty()) {
            throw BizException.unprocessable("NOTICE_REQUIRED", "提交审批前必须填写选课通知内容");
        }
        Integer classCount = jdbc.queryForObject("SELECT COUNT(*) FROM round_class WHERE round_id = ?", Integer.class, roundId);
        if (classCount == null || classCount == 0) {
            throw BizException.unprocessable("CLASSES_REQUIRED", "提交审批前至少须关联 1 个已发布教学班");
        }
        verifyBoundClassesPublished(roundId);

        jdbc.update("""
                UPDATE select_round
                SET round_status = 1, approver_id = NULL, approve_time = NULL, approve_opinion = NULL
                WHERE round_id = ? AND round_status = 0
                """, roundId);
        return getRoundDetail(roundId);
    }

    /** 1.4 教务处处长审批（1 -> 5 通过 / 1 -> 0 退回且意见必填） */
    @Transactional
    public Map<String, Object> decideRound(int roundId, int decision, String opinion) {
        CurrentUser user = UserContext.requireRole(RoleEnum.ACADEMIC_DIRECTOR, RoleEnum.SYS_ADMIN);
        if (decision != 1 && decision != 2) {
            throw BizException.unprocessable("INVALID_DECISION", "请选择审批决定（通过或退回）");
        }
        String trimmedOpinion = opinion == null ? "" : opinion.trim();
        if (decision == 2 && trimmedOpinion.isEmpty()) {
            throw BizException.unprocessable("OPINION_REQUIRED", "退回选课方案时必须填写审批意见");
        }
        Map<String, Object> row = lockAndGetRound(roundId);
        int status = ((Number) row.get("round_status")).intValue();
        if (status != 1) {
            throw BizException.conflict("INVALID_STATUS", "仅待审批状态的选课轮次可执行审批");
        }

        int nextStatus = (decision == 1) ? 5 : 0;
        jdbc.update("""
                UPDATE select_round
                SET round_status = ?, approver_id = ?, approve_time = NOW(), approve_opinion = ?
                WHERE round_id = ? AND round_status = 1
                """, nextStatus, user.empId(), trimmedOpinion.isEmpty() ? "同意" : trimmedOpinion, roundId);
        return getRoundDetail(roundId);
    }

    /** 1.5 选课通知发布（5 -> 2，若已到开始时间同事务推进至 3） */
    @Transactional
    public Map<String, Object> publishRound(int roundId) {
        UserContext.requireRole(RoleEnum.ACADEMIC_OFFICE, RoleEnum.SYS_ADMIN);
        Map<String, Object> row = lockAndGetRound(roundId);
        int status = ((Number) row.get("round_status")).intValue();
        if (status != 5) {
            throw BizException.conflict("INVALID_STATUS", "仅已审批待发布状态的选课轮次允许发布");
        }
        String notice = row.get("notice_content") == null ? "" : String.valueOf(row.get("notice_content")).trim();
        if (notice.isEmpty()) {
            throw BizException.unprocessable("NOTICE_REQUIRED", "选课通知内容不能为空");
        }
        verifyBoundClassesPublished(roundId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = toLocalDateTime(row.get("start_time"));
        LocalDateTime end = toLocalDateTime(row.get("end_time"));
        int nextStatus = (!now.isBefore(start) && now.isBefore(end)) ? 3 : 2;

        jdbc.update("""
                UPDATE select_round
                SET round_status = ?, publish_time = NOW()
                WHERE round_id = ? AND round_status = 5
                """, nextStatus, roundId);
        return getRoundDetail(roundId);
    }

    // ---------------- 内部校验工具 ----------------

    private Map<String, Object> lockAndGetRound(int roundId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM select_round WHERE round_id = ? FOR UPDATE", roundId);
        if (rows.isEmpty()) {
            throw BizException.notFound("选课轮次不存在: " + roundId);
        }
        return rows.get(0);
    }

    private int lockAndGetRoundStatus(int roundId) {
        return ((Number) lockAndGetRound(roundId).get("round_status")).intValue();
    }

    private void verifyBoundClassesPublished(int roundId) {
        List<String> invalid = jdbc.queryForList("""
                SELECT rc.tc_id FROM round_class rc
                JOIN teaching_class tc ON rc.tc_id = tc.tc_id
                WHERE rc.round_id = ? AND tc.tc_status <> '4'
                """, String.class, roundId);
        if (!invalid.isEmpty()) {
            throw BizException.unprocessable("UNPUBLISHED_CLASS",
                    "以下教学班已取消或未发布，请移除后再提交/发布: " + String.join(", ", invalid));
        }
    }

    private void replaceRoundClasses(int roundId, String term, List<String> tcIds) {
        jdbc.update("DELETE FROM round_class WHERE round_id = ?", roundId);
        if (tcIds == null || tcIds.isEmpty()) {
            return;
        }
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String id : tcIds) {
            if (id != null && !id.isBlank()) {
                uniqueIds.add(id.trim());
            }
        }
        for (String tcId : uniqueIds) {
            List<Map<String, Object>> tcRows = jdbc.queryForList(
                    "SELECT term, tc_status FROM teaching_class WHERE tc_id = ?", tcId);
            if (tcRows.isEmpty()) {
                throw BizException.unprocessable("CLASS_NOT_FOUND", "教学班不存在: " + tcId);
            }
            if (!Objects.equals(term, String.valueOf(tcRows.get(0).get("term")))) {
                throw BizException.unprocessable("TERM_MISMATCH", "教学班 " + tcId + " 的学期与轮次学期不一致");
            }
            if (!"4".equals(String.valueOf(tcRows.get(0).get("tc_status")))) {
                throw BizException.unprocessable("CLASS_NOT_PUBLISHED", "仅已发布的教学班可加入选课轮次: " + tcId);
            }
            jdbc.update("INSERT INTO round_class (round_id, tc_id) VALUES (?, ?)", roundId, tcId);
        }
    }

    @SuppressWarnings("unchecked")
    private RoundPayload validateAndParsePayload(Map<String, Object> body) {
        String term = requireStr(body, "term", 11);
        String roundName = requireStr(body, "round_name", 30);
        int roundType = parseInt(body.get("round_type"), "round_type");
        int targetLevel = parseInt(body.getOrDefault("target_level", 1), "target_level");
        int selectMode = parseInt(body.get("select_mode"), "select_mode");
        int allowRetake = parseInt(body.getOrDefault("allow_retake", 0), "allow_retake");
        int retakeIgnore = parseInt(body.getOrDefault("retake_ignore_conflict", 0), "retake_ignore_conflict");

        if (roundType < 1 || roundType > 3) {
            throw BizException.unprocessable("INVALID_ROUND_TYPE", "轮次类型仅支持预选、正选或补退选");
        }
        if (targetLevel < 1 || targetLevel > 3) {
            throw BizException.unprocessable("INVALID_TARGET_LEVEL", "适用学生仅支持本科生、研究生或全部");
        }
        if (selectMode != 1 && selectMode != 2) {
            throw BizException.unprocessable("INVALID_SELECT_MODE", "选课方式仅支持先到先得或超额筛选");
        }
        if (retakeIgnore == 1 && allowRetake != 1) {
            throw BizException.unprocessable("INVALID_RETAKE_PAIR", "仅在开放重修生选课时才可开启冲突免听");
        }

        LocalDateTime start = parseDateTime(body.get("start_time"), "start_time");
        LocalDateTime end = parseDateTime(body.get("end_time"), "end_time");
        if (!end.isAfter(start)) {
            throw BizException.unprocessable("INVALID_TIME_RANGE", "结束时间必须晚于开始时间");
        }

        BigDecimal maxCredit;
        try {
            maxCredit = new BigDecimal(String.valueOf(body.get("max_credit")));
        } catch (Exception e) {
            throw BizException.unprocessable("INVALID_MAX_CREDIT", "学分上限格式非法");
        }
        if (maxCredit.compareTo(BigDecimal.ZERO) <= 0 || maxCredit.compareTo(new BigDecimal("99.9")) > 0) {
            throw BizException.unprocessable("INVALID_MAX_CREDIT", "学分上限必须大于 0 且不超过 99.9");
        }

        String notice = body.get("notice_content") == null ? null : String.valueOf(body.get("notice_content"));
        List<String> tcIds = body.get("tc_ids") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new RoundPayload(term, roundName, roundType, targetLevel, start, end,
                maxCredit, selectMode, allowRetake, retakeIgnore, notice, tcIds);
    }

    private String requireStr(Map<String, Object> body, String key, int maxLen) {
        Object val = body.get(key);
        if (val == null || String.valueOf(val).trim().isEmpty()) {
            throw BizException.unprocessable("MISSING_FIELD", "请填写" + fieldLabel(key));
        }
        String s = String.valueOf(val).trim();
        if (s.length() > maxLen) {
            throw BizException.unprocessable("FIELD_TOO_LONG", fieldLabel(key) + "长度不能超过 " + maxLen + " 个字符");
        }
        return s;
    }

    private int parseInt(Object raw, String key) {
        if (raw == null) {
            throw BizException.unprocessable("MISSING_FIELD", "请选择或填写" + fieldLabel(key));
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw BizException.unprocessable("INVALID_INT", fieldLabel(key) + "格式不正确");
        }
    }

    private LocalDateTime parseDateTime(Object raw, String key) {
        if (raw == null) {
            throw BizException.unprocessable("MISSING_FIELD", "请选择" + fieldLabel(key));
        }
        String s = String.valueOf(raw).trim().replace("T", " ");
        if (s.length() == 16) {
            s = s + ":00";
        }
        if (s.length() > 19) {
            s = s.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception e) {
            throw BizException.unprocessable("INVALID_DATETIME", fieldLabel(key) + "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String fieldLabel(String key) {
        return switch (key) {
            case "term" -> "学年学期";
            case "round_name" -> "轮次名称";
            case "round_type" -> "轮次类型";
            case "target_level" -> "适用学生";
            case "select_mode" -> "选课方式";
            case "allow_retake" -> "允许重修";
            case "retake_ignore_conflict" -> "重修免听";
            case "start_time" -> "开始时间";
            case "end_time" -> "结束时间";
            case "limit_type" -> "限选类型";
            default -> "相关项";
        };
    }

    private LocalDateTime toLocalDateTime(Object raw) {
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (raw instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(raw).replace(" ", "T"));
    }

    private record RoundPayload(
            String term, String roundName, int roundType, int targetLevel,
            LocalDateTime startTime, LocalDateTime endTime, BigDecimal maxCredit,
            int selectMode, int allowRetake, int retakeIgnoreConflict,
            String noticeContent, List<String> tcIds) {
    }
}
