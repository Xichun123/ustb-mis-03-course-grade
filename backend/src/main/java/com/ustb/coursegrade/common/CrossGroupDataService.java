package com.ustb.coursegrade.common;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 全班跨组数据联动服务（严格只读查询第 1/2/4/5/7/9/13/15 组及第 16 组公共主数据）。
 * 所有可选外部表读取均具备优雅降级能力，兼容全班合库过渡期空表或测试环境最小库。
 */
@Service
public class CrossGroupDataService {

    private final JdbcTemplate jdbc;

    public CrossGroupDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =========================================================================
    // 1. 第 2 组 学期主数据（aca_term）+ 第 16 组 公共参数（al_dict_item）
    // =========================================================================

    public String getCurrentTerm() {
        List<String> paramVals = queryColumnSafe("""
                SELECT item_name FROM al_dict_item
                 WHERE dict_type = 'system_param' AND item_code = 'current_term'
                   AND item_status IN ('01', '1')
                 ORDER BY dict_id LIMIT 1
                """, String.class);
        if (!paramVals.isEmpty() && paramVals.get(0) != null && !paramVals.get(0).isBlank()) {
            return paramVals.get(0).trim();
        }
        List<String> activeTerms = queryColumnSafe("""
                SELECT term FROM aca_term
                 WHERE term_status IN ('01', '1')
                 ORDER BY term DESC LIMIT 1
                """, String.class);
        if (!activeTerms.isEmpty() && activeTerms.get(0) != null && !activeTerms.get(0).isBlank()) {
            return activeTerms.get(0).trim();
        }
        return "2026-2027-1";
    }

    public boolean isBlockSelectionOnArrearsEnabled() {
        List<String> vals = queryColumnSafe("""
                SELECT item_name FROM al_dict_item
                 WHERE dict_type = 'system_param' AND item_code = 'block_selection_on_arrears'
                   AND item_status IN ('01', '1')
                 ORDER BY dict_id LIMIT 1
                """, String.class);
        if (vals.isEmpty() || vals.get(0) == null) {
            return false;
        }
        String v = vals.get(0).trim().toLowerCase(Locale.ROOT);
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    public List<Map<String, Object>> listTerms() {
        String current = getCurrentTerm();
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT term, academic_year, term_no, start_date, end_date, term_status
                  FROM aca_term
                 ORDER BY term DESC
                """);
        if (rows.isEmpty()) {
            List<String> fallback = queryColumnSafe(
                    "SELECT DISTINCT term FROM aca_teaching_class WHERE term IS NOT NULL ORDER BY term DESC",
                    String.class);
            if (fallback.isEmpty()) {
                fallback = List.of(current);
            }
            List<Map<String, Object>> res = new ArrayList<>();
            for (String t : fallback) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("term", t);
                m.put("label", formatTermLabel(t, null));
                m.put("term_status", t.equals(current) ? "01" : "02");
                m.put("is_current", t.equals(current));
                res.add(m);
            }
            return res;
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String term = str(r.get("term"));
            Integer termNo = r.get("term_no") instanceof Number n ? n.intValue() : null;
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("term", term);
            m.put("label", formatTermLabel(term, termNo));
            m.put("is_current", term.equals(current));
            res.add(m);
        }
        return res;
    }

    public List<String> listTermCodes() {
        return listTerms().stream()
                .map(m -> str(m.get("term")))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    public Map<String, Object> getTermsPayload() {
        String current = getCurrentTerm();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current_term", current);
        payload.put("items", listTerms());
        return payload;
    }

    public static String formatTermLabel(String term, Integer termNo) {
        if (term == null || term.isBlank()) {
            return "";
        }
        String t = term.trim();
        int no = termNo != null ? termNo : (t.endsWith("-1") ? 1 : (t.endsWith("-2") ? 2 : 0));
        String season = switch (no) {
            case 1 -> "秋";
            case 2 -> "春";
            case 3 -> "夏";
            default -> "";
        };
        if (t.length() >= 9 && !season.isEmpty()) {
            return t + "（" + t.substring(0, 4) + season + "）";
        }
        return t;
    }

    // =========================================================================
    // 2. 第 7 组 固定资产（ast_building / aca_room）与第 9 组 实验室（lab_*）
    // =========================================================================

    public Map<String, Map<String, Object>> loadRoomLookup() {
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT r.room_id, r.room_code, r.room_name, r.building_id,
                       b.building_name, b.address AS building_address,
                       r.floor, r.room_number, r.room_type, r.capacity,
                       r.has_projector, r.computer_num, r.equip_desc, r.room_status
                  FROM aca_room r
                  LEFT JOIN ast_building b ON b.building_id = r.building_id
                """);
        Map<String, Map<String, Object>> lookup = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> info = new LinkedHashMap<>();
            String roomId = str(r.get("room_id"));
            String roomCode = str(r.get("room_code"));
            String roomName = str(r.get("room_name"));
            String roomType = str(r.get("room_type"));
            info.put("room_id", roomId);
            info.put("room_code", roomCode);
            info.put("room_name", roomName);
            info.put("building_id", str(r.get("building_id")));
            info.put("building_name", str(r.get("building_name")));
            info.put("building_address", str(r.get("building_address")));
            info.put("floor", r.get("floor"));
            info.put("room_number", str(r.get("room_number")));
            info.put("room_type", roomType);
            info.put("room_type_label", "02".equals(roomType) ? "教学实验室" : "多媒体教室");
            info.put("capacity", r.get("capacity") instanceof Number n ? n.intValue() : null);
            info.put("has_projector", "1".equals(str(r.get("has_projector"))));
            info.put("computer_num", r.get("computer_num") instanceof Number n ? n.intValue() : 0);
            info.put("equip_desc", str(r.get("equip_desc")));
            if (!roomId.isEmpty()) lookup.put(roomId, info);
            if (!roomCode.isEmpty()) lookup.put(roomCode, info);
            if (!roomName.isEmpty()) lookup.put(roomName, info);
        }
        return lookup;
    }

    public Map<String, Map<String, Object>> loadLabLookupByRoomId() {
        List<Map<String, Object>> labs = queryListSafe("""
                SELECT l.lab_id, l.lab_name, l.room_id, l.manager_emp_id,
                       COALESCE(e.emp_name, l.manager_emp_id) AS manager_name,
                       l.capacity, l.open_time, l.close_time, l.lab_status,
                       l.building, l.floor, l.room_number, l.location, l.lab_type, l.description
                  FROM lab_laboratory l
                  LEFT JOIN hr_employee e ON e.emp_id = l.manager_emp_id
                """);
        if (labs.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Map<String, Object>>> closuresByLab = new HashMap<>();
        List<Map<String, Object>> closures = queryListSafe("""
                SELECT closure_id, lab_id, start_at, end_at, reason, closure_status
                  FROM lab_closure
                 ORDER BY start_at DESC
                """);
        for (Map<String, Object> c : closures) {
            String st = str(c.get("closure_status")).toLowerCase(Locale.ROOT);
            if ("01".equals(st) || "1".equals(st) || "ac".equals(st) || "active".equals(st)) {
                closuresByLab.computeIfAbsent(str(c.get("lab_id")), k -> new ArrayList<>()).add(new LinkedHashMap<>(c));
            }
        }

        Map<String, List<Map<String, Object>>> resByLab = new HashMap<>();
        List<Map<String, Object>> reservations = queryListSafe("""
                SELECT reservation_id, lab_id, date, start_at, end_at, purpose,
                       participants, reservation_status, course_id, course_name, course_teacher, week_no
                  FROM lab_reservation
                 ORDER BY date DESC, start_at ASC
                """);
        for (Map<String, Object> res : reservations) {
            String st = str(res.get("reservation_status")).toLowerCase(Locale.ROOT);
            if (!"00".equals(st) && !"0".equals(st) && !"ca".equals(st) && !"cancelled".equals(st) && !"rj".equals(st)) {
                resByLab.computeIfAbsent(str(res.get("lab_id")), k -> new ArrayList<>()).add(new LinkedHashMap<>(res));
            }
        }

        Map<String, Map<String, Object>> byRoom = new HashMap<>();
        for (Map<String, Object> l : labs) {
            String labId = str(l.get("lab_id"));
            String roomId = str(l.get("room_id"));
            Map<String, Object> info = new LinkedHashMap<>(l);
            List<Map<String, Object>> activeClosures = closuresByLab.getOrDefault(labId, List.of());
            List<Map<String, Object>> labRes = resByLab.getOrDefault(labId, List.of());
            info.put("active_closures", activeClosures);
            info.put("has_active_closure", !activeClosures.isEmpty());
            info.put("reservations", labRes);
            if (!roomId.isEmpty()) {
                byRoom.put(roomId, info);
            }
            String bldRoom = str(l.get("building")) + str(l.get("room_number"));
            if (!bldRoom.isEmpty()) {
                byRoom.putIfAbsent(bldRoom, info);
            }
        }
        return byRoom;
    }

    /** 将第 7 组教室资产、第 9 组实验室与封闭预警、第 2 组已批调课记录附加到排课条目上（不破坏原有字段） */
    public void enrichScheduleSlots(
            List<Map<String, Object>> schedules,
            Integer tcCapacity,
            Map<String, Map<String, Object>> roomLookup,
            Map<String, Map<String, Object>> labByRoom,
            Map<String, List<Map<String, Object>>> adjustmentsBySchedId) {
        if (schedules == null || schedules.isEmpty()) {
            return;
        }
        for (Map<String, Object> slot : schedules) {
            String rawRoom = str(slot.get("room_id"));
            Map<String, Object> room = rawRoom.isEmpty() ? null : roomLookup.get(rawRoom);
            if (room != null) {
                slot.put("room_info", room);
                Integer seatCap = room.get("capacity") instanceof Number n ? n.intValue() : null;
                boolean overCap = tcCapacity != null && seatCap != null && tcCapacity > seatCap;
                slot.put("room_over_capacity", overCap);
            }
            String resolvedRoomId = room != null ? str(room.get("room_id")) : rawRoom;
            Map<String, Object> lab = !resolvedRoomId.isEmpty() ? labByRoom.get(resolvedRoomId) : null;
            if (lab == null && !rawRoom.isEmpty()) {
                lab = labByRoom.get(rawRoom);
            }
            if (lab != null) {
                slot.put("lab_info", lab);
            }
            String schedId = str(slot.get("sched_id"));
            if (!schedId.isEmpty() && adjustmentsBySchedId != null) {
                List<Map<String, Object>> adjs = adjustmentsBySchedId.get(schedId);
                if (adjs != null && !adjs.isEmpty()) {
                    slot.put("adjustments", adjs);
                }
            }
        }
    }

    // =========================================================================
    // 3. 第 2 组 调课记录（aca_adjust_record）、排课冲突（aca_schedule_conflict）、通识计划（aca_general_plan）
    // =========================================================================

    public Map<String, List<Map<String, Object>>> loadApprovedAdjustmentsBySchedIds(Collection<String> schedIds) {
        if (schedIds == null || schedIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = schedIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT adjust_id, sched_id, adjust_type, old_value, new_value,
                       reason, approve_status, operated_at, valid_from, approve_opinion
                  FROM aca_adjust_record
                 WHERE sched_id IN (%s) AND approve_status IN ('1', '01', '2', '02')
                 ORDER BY operated_at DESC, adjust_id DESC
                """.formatted(ph), ids.toArray());
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            map.computeIfAbsent(str(r.get("sched_id")), k -> new ArrayList<>()).add(new LinkedHashMap<>(r));
        }
        return map;
    }

    public Map<String, List<Map<String, Object>>> loadUnresolvedConflictsByTcIds(Collection<String> tcIds) {
        if (tcIds == null || tcIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = tcIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT conflict_id, tc_id, other_tc_id, conflict_type, conflict_desc,
                       suggest_plan, found_time, handle_status
                  FROM aca_schedule_conflict
                 WHERE tc_id IN (%s) AND handle_status IN ('0', '00')
                 ORDER BY found_time DESC, conflict_id DESC
                """.formatted(ph), ids.toArray());
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            map.computeIfAbsent(str(r.get("tc_id")), k -> new ArrayList<>()).add(new LinkedHashMap<>(r));
        }
        return map;
    }

    public Map<String, String> loadGeneralPlanCategories(Collection<String> courseIds, String term) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = courseIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String schoolYear = (term != null && term.trim().length() >= 9) ? term.trim().substring(0, 9) : null;
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT school_year, course_id, plan_category
                  FROM aca_general_plan
                 WHERE course_id IN (%s)
                 ORDER BY school_year DESC
                """.formatted(ph), ids.toArray());
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String cid = str(r.get("course_id"));
            String yr = str(r.get("school_year"));
            String cat = str(r.get("plan_category"));
            if (cat.isEmpty()) continue;
            if (schoolYear != null && schoolYear.equals(yr)) {
                result.put(cid, cat);
            } else {
                result.putIfAbsent(cid, cat);
            }
        }
        return result;
    }

    // =========================================================================
    // 4. 第 4 组 师资信息与请假/出国（hr_*）+ 第 15 组 教师出访（exg_ob_teacher_status）
    // =========================================================================

    public Map<String, Map<String, Object>> loadTeacherProfiles(Collection<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = empIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Map<String, String> titleDict = new HashMap<>();
        for (Map<String, Object> row : queryListSafe(
                "SELECT item_code, item_name FROM al_dict_item WHERE dict_type = 'HR_TITLE' AND item_status IN ('01','1')")) {
            titleDict.put(str(row.get("item_code")), str(row.get("item_name")));
        }

        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT e.emp_id, e.emp_name, e.title_code, e.edu_level, e.emp_status,
                       e.org_id, COALESCE(o.org_name, e.org_id) AS org_name
                  FROM hr_employee e
                  LEFT JOIN org_organization o ON o.org_id = e.org_id
                 WHERE e.emp_id IN (%s)
                """.formatted(ph), ids.toArray());
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String empId = str(r.get("emp_id"));
            String code = str(r.get("title_code"));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("emp_id", empId);
            info.put("emp_name", str(r.get("emp_name")));
            info.put("title_code", code);
            info.put("title_label", resolveTitleLabel(code, titleDict));
            info.put("edu_level", str(r.get("edu_level")));
            info.put("emp_status", str(r.get("emp_status")));
            info.put("org_name", str(r.get("org_name")));
            map.put(empId, info);
        }
        return map;
    }

    public Map<String, List<Map<String, Object>>> loadTeacherAlerts(Collection<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = empIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<String, List<Map<String, Object>>> alerts = new HashMap<>();

        for (Map<String, Object> r : queryListSafe("""
                SELECT emp_id, leave_type, start_at, end_at, days, reason, leave_status
                  FROM hr_leave
                 WHERE emp_id IN (%s) AND leave_status IN ('02', '2', '1', '01')
                 ORDER BY start_at DESC
                """.formatted(ph), ids.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_group", "第 4 组（师资管理）");
            item.put("alert_type", "LEAVE");
            item.put("label", "请假：" + str(r.get("leave_type")));
            item.put("period", str(r.get("start_at")) + " 至 " + str(r.get("end_at")));
            item.put("detail", str(r.get("reason")));
            alerts.computeIfAbsent(str(r.get("emp_id")), k -> new ArrayList<>()).add(item);
        }

        for (Map<String, Object> r : queryListSafe("""
                SELECT emp_id, abroad_type, country, start_date, end_date, approve_doc, apply_status
                  FROM hr_abroad
                 WHERE emp_id IN (%s) AND apply_status IN ('02', '2', '1', '01')
                 ORDER BY start_date DESC
                """.formatted(ph), ids.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_group", "第 4 组（师资管理）");
            item.put("alert_type", "ABROAD");
            item.put("label", "因公出国：" + str(r.get("country")));
            item.put("period", str(r.get("start_date")) + " 至 " + str(r.get("end_date")));
            item.put("detail", str(r.get("abroad_type"))
                    + (str(r.get("approve_doc")).isEmpty() ? "" : "（" + str(r.get("approve_doc")) + "）"));
            alerts.computeIfAbsent(str(r.get("emp_id")), k -> new ArrayList<>()).add(item);
        }

        for (Map<String, Object> r : queryListSafe("""
                SELECT emp_id, trip_id, current_status, effective_time, end_at
                  FROM exg_ob_teacher_status
                 WHERE emp_id IN (%s) AND current_status IN ('02', '2', '01', '1')
                 ORDER BY effective_time DESC
                """.formatted(ph), ids.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_group", "第 15 组（对外交流管理）");
            item.put("alert_type", "OUTBOUND_TRIP");
            item.put("label", "因公出访计划");
            String endText = str(r.get("end_at"));
            item.put("period", str(r.get("effective_time")) + (endText.isEmpty() ? "" : " 至 " + endText));
            item.put("detail", "出访单号 " + str(r.get("trip_id")));
            alerts.computeIfAbsent(str(r.get("emp_id")), k -> new ArrayList<>()).add(item);
        }

        return alerts;
    }

    private static String resolveTitleLabel(String code, Map<String, String> dict) {
        if (code == null || code.isBlank()) return "";
        if (dict.containsKey(code)) return dict.get(code);
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "PROF" -> "教授";
            case "ASSOC_PROF" -> "副教授";
            case "LECTURER" -> "讲师";
            case "ASSISTANT" -> "助教";
            case "ENGINEER" -> "工程师";
            default -> code;
        };
    }

    // =========================================================================
    // 5. 第 1 组 注册/辅修/转专业 + 第 5 组 财务欠费 + 第 2 组 培养方案/双学位/学分转换
    // =========================================================================

    /** 第 1 组：新学期注册核验（无记录默认放行，存在非注册成功记录时拦截并说明原因） */
    public Map<String, Object> getRegistrationStatus(String studentId, String term) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("student_id", studentId);
        res.put("term", term);
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT student_id, term, enrollment_status, payment_status, info_confirm,
                       registration_status, registration_time, abnormal_reason
                  FROM std_registration
                 WHERE student_id = ? AND term = ?
                 LIMIT 1
                """, studentId, term);
        if (rows.isEmpty()) {
            res.put("has_record", false);
            res.put("registered", true);
            res.put("blocked", false);
            res.put("status_label", "未同步注册记录（兼容放行）");
            res.put("abnormal_reason", null);
            return res;
        }
        Map<String, Object> r = rows.get(0);
        String st = str(r.get("registration_status"));
        boolean ok = "01".equals(st) || "1".equals(st) || "已注册".equals(st) || "注册成功".equals(st);
        String reason = str(r.get("abnormal_reason"));
        if (!ok && reason.isEmpty()) {
            reason = "本学期学籍注册尚未完成，请先联系学院学籍办补办报到注册";
        }
        res.put("has_record", true);
        res.put("registered", ok);
        res.put("blocked", !ok);
        res.put("registration_status", st);
        res.put("payment_status", str(r.get("payment_status")));
        res.put("info_confirm", str(r.get("info_confirm")));
        res.put("registration_time", r.get("registration_time") == null ? null : String.valueOf(r.get("registration_time")));
        res.put("status_label", ok ? "本学期已注册" : "未完成新学期注册");
        res.put("abnormal_reason", ok ? null : reason);
        return res;
    }

    /** 第 5 组：财务欠费核验（默认温馨提醒，可在系统参数开启欠费拦截选课；无记录默认放行） */
    public Map<String, Object> getFinanceStatus(String studentId) {
        boolean blockEnabled = isBlockSelectionOnArrearsEnabled();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("student_id", studentId);
        res.put("block_on_arrears_enabled", blockEnabled);

        List<Map<String, Object>> rows = queryListSafe("""
                SELECT CAST(charge_no AS CHAR(64)) AS charge_no,
                       CAST(charge_item AS CHAR(64)) AS charge_item,
                       CAST(charge_period AS CHAR(64)) AS charge_period,
                       receivable_amount, received_amount, arrears_amount,
                       CAST(pay_method AS CHAR(64)) AS pay_method,
                       invoice_no, audit_status
                  FROM fin_charge
                 WHERE student_id = ?
                 ORDER BY charge_period DESC, charge_no DESC
                """, studentId);
        if (rows.isEmpty()) {
            res.put("has_records", false);
            res.put("has_arrears", false);
            res.put("blocked", false);
            res.put("total_receivable", "0.00");
            res.put("total_received", "0.00");
            res.put("total_arrears", "0.00");
            res.put("arrears_count", 0);
            res.put("status_label", "暂无财务账单记录");
            res.put("warning_message", null);
            res.put("items", List.of());
            return res;
        }

        Map<String, String> itemNames = new HashMap<>();
        for (Map<String, Object> it : queryListSafe("SELECT charge_item_id, charge_item_name FROM fin_charge_item")) {
            itemNames.put(str(it.get("charge_item_id")), str(it.get("charge_item_name")));
        }

        BigDecimal totalRec = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalArr = BigDecimal.ZERO;
        int arrearsCount = 0;
        List<String> arrearsLabels = new ArrayList<>();
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            BigDecimal rec = toBigDecimal(r.get("receivable_amount"));
            BigDecimal paid = toBigDecimal(r.get("received_amount"));
            BigDecimal arr = toBigDecimal(r.get("arrears_amount"));
            totalRec = totalRec.add(rec);
            totalPaid = totalPaid.add(paid);
            totalArr = totalArr.add(arr);
            String code = str(r.get("charge_item"));
            String itemName = itemNames.getOrDefault(code, code.isEmpty() ? "学杂费" : code);
            Map<String, Object> item = new LinkedHashMap<>(r);
            item.put("charge_item_name", itemName);
            item.put("receivable_amount", fmtMoney(rec));
            item.put("received_amount", fmtMoney(paid));
            item.put("arrears_amount", fmtMoney(arr));
            formatted.add(item);
            if (arr.compareTo(BigDecimal.ZERO) > 0) {
                arrearsCount++;
                arrearsLabels.add(itemName + " " + fmtMoney(arr) + " 元");
            }
        }
        boolean hasArrears = totalArr.compareTo(BigDecimal.ZERO) > 0;
        boolean blocked = hasArrears && blockEnabled;
        res.put("has_records", true);
        res.put("has_arrears", hasArrears);
        res.put("blocked", blocked);
        res.put("total_receivable", fmtMoney(totalRec));
        res.put("total_received", fmtMoney(totalPaid));
        res.put("total_arrears", fmtMoney(totalArr));
        res.put("arrears_count", arrearsCount);
        res.put("status_label", hasArrears ? ("存在欠费 " + fmtMoney(totalArr) + " 元") : "学费及住宿费已缴清");
        if (hasArrears) {
            String detail = String.join("、", arrearsLabels);
            res.put("warning_message", blocked
                    ? "财务系统显示当前存在欠费 " + fmtMoney(totalArr) + " 元（" + detail + "），按当前系统参数已暂停选课，请先缴清后再办理。"
                    : "财务系统提醒：当前尚有未缴费用 " + fmtMoney(totalArr) + " 元（" + detail + "），请及时前往财务系统核对缴纳（当前仅提醒，不影响选课）。");
        } else {
            res.put("warning_message", null);
        }
        res.put("items", formatted);
        return res;
    }

    /** 第 1 组（std_minor_degree）+ 第 2 组（aca_dual_enroll）辅修与双学位获批资格 */
    public Map<String, Object> getMinorDualQualification(String studentId) {
        Map<String, Object> res = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();

        List<Map<String, Object>> minorRows = queryListSafe("""
                SELECT m.apply_id, m.apply_category, m.target_major, m.apply_status,
                       m.final_result, m.final_opinion, m.submitted_at
                  FROM std_minor_degree m
                  JOIN std_student s ON s.person_id = m.person_id
                 WHERE s.student_id = ?
                 ORDER BY m.submitted_at DESC
                """, studentId);
        for (Map<String, Object> r : minorRows) {
            String st = str(r.get("apply_status"));
            String fr = str(r.get("final_result"));
            boolean approved = "03".equals(st) || "3".equals(st) || "1".equals(st) || "01".equals(st) || fr.contains("通过");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_group", "第 1 组（学籍管理）");
            item.put("record_id", str(r.get("apply_id")));
            item.put("category", str(r.get("apply_category")));
            item.put("target_major", str(r.get("target_major")));
            item.put("approved", approved);
            item.put("status_label", approved ? "终审通过" : (fr.isEmpty() ? "审核中" : fr));
            item.put("opinion", str(r.get("final_opinion")));
            records.add(item);
        }

        List<Map<String, Object>> dualRows = queryListSafe("""
                SELECT d.enroll_id, d.major_id, COALESCE(am.major_name, d.major_id) AS major_name,
                       d.enroll_year, d.audit_status, d.finish_status, d.enroll_date, d.remark
                  FROM aca_dual_enroll d
                  LEFT JOIN aca_major am ON am.major_id = d.major_id
                 WHERE d.student_id = ?
                 ORDER BY d.enroll_year DESC
                """, studentId);
        for (Map<String, Object> r : dualRows) {
            String st = str(r.get("audit_status"));
            boolean approved = "1".equals(st) || "01".equals(st) || "2".equals(st) || "02".equals(st);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source_group", "第 2 组（专业设置及排课）");
            item.put("record_id", str(r.get("enroll_id")));
            item.put("category", "双学位");
            item.put("target_major", str(r.get("major_name")));
            item.put("enroll_year", str(r.get("enroll_year")));
            item.put("approved", approved);
            item.put("status_label", approved ? "已录取修读" : "审核中");
            item.put("opinion", str(r.get("remark")));
            records.add(item);
        }

        boolean approvedAny = records.stream().anyMatch(m -> Boolean.TRUE.equals(m.get("approved")));
        long totalQualRows = Math.max(0L, queryCountSafe("std_minor_degree"))
                + Math.max(0L, queryCountSafe("aca_dual_enroll"));
        res.put("approved", approvedAny);
        res.put("qualification_data_available", totalQualRows > 0);
        res.put("records", records);
        return res;
    }

    /** 第 2 组 专业培养方案总表（aca_major_program） */
    public Map<String, Object> getMajorProgramInfo(String majorId, String grade) {
        if (majorId == null || majorId.isBlank()) return null;
        List<Map<String, Object>> rows = List.of();
        if (grade != null && !grade.isBlank()) {
            rows = queryListSafe("""
                    SELECT major_id, grade, program_type, version, total_credit,
                           CAST(credit_rule AS CHAR(200)) AS credit_rule,
                           goal_desc, plan_status, dual_quota, published_at, review_opinion
                      FROM aca_major_program
                     WHERE major_id = ? AND grade = ? AND program_type = '1'
                     LIMIT 1
                    """, majorId.trim(), grade.trim());
        }
        if (rows.isEmpty()) {
            rows = queryListSafe("""
                    SELECT major_id, grade, program_type, version, total_credit,
                           CAST(credit_rule AS CHAR(200)) AS credit_rule,
                           goal_desc, plan_status, dual_quota, published_at, review_opinion
                      FROM aca_major_program
                     WHERE major_id = ? AND program_type = '1'
                     ORDER BY grade DESC LIMIT 1
                    """, majorId.trim());
        }
        if (rows.isEmpty()) return null;
        Map<String, Object> r = new LinkedHashMap<>(rows.get(0));
        Map<String, BigDecimal> parsedRules = parseCreditRule(str(r.get("credit_rule")));
        r.put("parsed_credit_rules", parsedRules);
        return r;
    }

    /** 解析 "11:2.0;12:6.5;16:2.0" 格式的分类学分最低要求（兼容空值或纯数字） */
    public static Map<String, BigDecimal> parseCreditRule(String raw) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank() || !raw.contains(":")) {
            return map;
        }
        for (String seg : raw.split("[;；,，]+")) {
            String s = seg.trim();
            int idx = s.indexOf(':');
            if (idx <= 0 || idx >= s.length() - 1) continue;
            String code = s.substring(0, idx).trim();
            String val = s.substring(idx + 1).trim();
            try {
                map.put(code, new BigDecimal(val));
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    /** 第 1 组转专业结果（std_major_transfer）+ 第 2 组学分转换明细（aca_credit_apply / aca_credit_item） */
    public Map<String, Object> getMajorTransferAndCreditInfo(String studentId) {
        List<Map<String, Object>> transfers = queryListSafe("""
                SELECT t.application_term, t.current_major_id,
                       COALESCE(m1.major_name, t.current_major_id) AS from_major_name,
                       t.target_major_id,
                       COALESCE(m2.major_name, t.target_major_id) AS to_major_name,
                       t.weighted_average_score, t.current_major_rank, t.current_major_rank_total,
                       t.application_status, t.admission_result, t.decision_date, t.effective_term
                  FROM std_major_transfer t
                  LEFT JOIN aca_major m1 ON m1.major_id = t.current_major_id
                  LEFT JOIN aca_major m2 ON m2.major_id = t.target_major_id
                 WHERE t.student_id = ?
                 ORDER BY t.submitted_at DESC
                """, studentId);

        List<Map<String, Object>> creditApplies = queryListSafe("""
                SELECT a.apply_id, a.from_major, COALESCE(m1.major_name, a.from_major) AS from_major_name,
                       a.to_major, COALESCE(m2.major_name, a.to_major) AS to_major_name,
                       a.apply_credit, a.apply_date, a.audit_status, a.audit_opinion, a.audit_date
                  FROM aca_credit_apply a
                  LEFT JOIN aca_major m1 ON m1.major_id = a.from_major
                  LEFT JOIN aca_major m2 ON m2.major_id = a.to_major
                 WHERE a.student_id = ?
                 ORDER BY a.apply_date DESC
                """, studentId);

        List<Map<String, Object>> creditItems = new ArrayList<>();
        for (Map<String, Object> ap : creditApplies) {
            String applyId = str(ap.get("apply_id"));
            List<Map<String, Object>> items = queryListSafe("""
                    SELECT i.apply_id, i.seq_no, i.source_course_id,
                           COALESCE(c1.course_name, i.source_course_id) AS source_course_name,
                           i.source_credit, i.target_course_id,
                           COALESCE(c2.course_name, i.target_course_id) AS target_course_name,
                           i.target_credit, i.item_status
                      FROM aca_credit_item i
                      LEFT JOIN aca_course c1 ON c1.course_id = i.source_course_id
                      LEFT JOIN aca_course c2 ON c2.course_id = i.target_course_id
                     WHERE i.apply_id = ?
                     ORDER BY i.seq_no ASC
                    """, applyId);
            ap.put("items", items);
            creditItems.addAll(items);
        }

        if (transfers.isEmpty() && creditApplies.isEmpty()) {
            return null;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("transfers", transfers);
        res.put("credit_applies", creditApplies);
        res.put("converted_course_count", creditItems.size());
        return res;
    }

    // =========================================================================
    // 6. 第 13 组 研究生全链路信息（方向、方案要求、个人培养计划、导师）
    // =========================================================================

    public Map<String, Object> getGraduateProfile(String studentId) {
        List<Map<String, Object>> assigns = queryListSafe("""
                SELECT a.assignment_id, a.scheme_id, a.direction_id, d.direction_name,
                       a.valid_from, a.valid_to
                  FROM grad_scheme_assignment a
                  LEFT JOIN grad_direction d ON d.direction_id = a.direction_id
                 WHERE a.student_id = ?
                   AND a.valid_from <= CURRENT_DATE
                   AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
                 ORDER BY a.valid_from DESC LIMIT 1
                """, studentId);
        if (assigns.isEmpty()) {
            return null;
        }
        Map<String, Object> assign = assigns.get(0);
        String schemeId = str(assign.get("scheme_id"));
        String dirId = str(assign.get("direction_id"));
        String dirName = str(assign.get("direction_name"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scheme_id", schemeId);
        res.put("direction_id", dirId.isEmpty() ? null : dirId);
        res.put("direction_name", dirName.isEmpty() ? dirId : dirName);

        List<Map<String, Object>> schemes = queryListSafe("""
                SELECT scheme_id, major_id, grade, level, version_no, min_credit, max_credit, publish_status
                  FROM grad_training_scheme
                 WHERE scheme_id = ? LIMIT 1
                """, schemeId);
        if (!schemes.isEmpty()) {
            Map<String, Object> sch = schemes.get(0);
            res.put("scheme_version", str(sch.get("version_no")));
            res.put("scheme_level", str(sch.get("level")));
            res.put("min_credit", sch.get("min_credit"));
            res.put("max_credit", sch.get("max_credit"));
        }

        List<Map<String, Object>> reqs = queryListSafe("""
                SELECT scheme_line_id, group_code, min_credit, max_credit, min_count
                  FROM grad_scheme_requirement
                 WHERE scheme_id = ?
                 ORDER BY scheme_line_id ASC
                """, schemeId);
        List<Map<String, Object>> formattedReqs = new ArrayList<>();
        for (Map<String, Object> rq : reqs) {
            Map<String, Object> m = new LinkedHashMap<>(rq);
            m.put("group_label", gradGroupLabel(str(rq.get("group_code"))));
            formattedReqs.add(m);
        }
        res.put("scheme_requirements", formattedReqs);

        List<Map<String, Object>> plans = queryListSafe("""
                SELECT plan_id, scheme_id, version_no, plan_status
                  FROM grad_training_plan
                 WHERE student_id = ?
                 ORDER BY version_no DESC LIMIT 1
                """, studentId);
        Map<String, Map<String, Object>> planCourseByCourseId = new LinkedHashMap<>();
        if (!plans.isEmpty()) {
            Map<String, Object> plan = plans.get(0);
            res.put("plan_id", str(plan.get("plan_id")));
            res.put("plan_status", str(plan.get("plan_status")));
            List<Map<String, Object>> pcs = queryListSafe("""
                    SELECT pc.course_id, COALESCE(c.course_name, pc.course_id) AS course_name,
                           pc.course_source, pc.planned_term, pc.credit_snapshot
                      FROM grad_plan_course pc
                      LEFT JOIN aca_course c ON c.course_id = pc.course_id
                     WHERE pc.plan_id = ?
                     ORDER BY pc.planned_term ASC, pc.course_id ASC
                    """, str(plan.get("plan_id")));
            for (Map<String, Object> pc : pcs) {
                planCourseByCourseId.put(str(pc.get("course_id")), new LinkedHashMap<>(pc));
            }
            res.put("personal_plan_courses", pcs);
        } else {
            res.put("personal_plan_courses", List.of());
        }
        res.put("personal_plan_course_map", planCourseByCourseId);

        List<Map<String, Object>> mentors = queryListSafe("""
                SELECT gm.mentor_user_id, gm.is_primary, gm.valid_from, gm.valid_to,
                       COALESCE(e.emp_name, gm.mentor_user_id) AS mentor_name,
                       e.title_code, e.edu_level
                  FROM grad_student_mentor gm
                  LEFT JOIN hr_employee e ON e.emp_id = gm.mentor_user_id OR e.person_id = gm.mentor_user_id
                 WHERE gm.student_id = ?
                   AND (gm.valid_to IS NULL OR gm.valid_to >= CURRENT_DATE)
                 ORDER BY gm.is_primary DESC, gm.valid_from DESC
                """, studentId);
        Map<String, String> titleDict = Map.of();
        List<Map<String, Object>> formattedMentors = new ArrayList<>();
        for (Map<String, Object> m : mentors) {
            Map<String, Object> mm = new LinkedHashMap<>(m);
            mm.put("title_label", resolveTitleLabel(str(m.get("title_code")), titleDict));
            mm.put("is_primary", m.get("is_primary") instanceof Number n && n.intValue() == 1);
            formattedMentors.add(mm);
        }
        res.put("mentors", formattedMentors);
        return res;
    }

    private static String gradGroupLabel(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "CORE" -> "学位核心课";
            case "DIR" -> "研究方向课";
            case "PRAC" -> "专业实践与学术活动";
            case "ELEC" -> "公共/方向选修课";
            default -> code;
        };
    }

    // =========================================================================
    // 7. 第 15 组 对外交流管理扩展（来校导师意见、留学生、返校备案视图、外校成绩单）
    // =========================================================================

    public Map<String, Object> getIncomingExchangeSupervisorOpinion(String tempStudentNo) {
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT o.opinion_id, o.apply_id, o.emp_id, o.emp_name, o.college_name,
                       o.accept_opinion, o.course_plan, o.submitted_at
                  FROM exg_temp_student ts
                  JOIN exg_admission adm ON ts.admission_id = adm.admission_id
                  JOIN exg_supervisor_opinion o ON o.apply_id = adm.apply_id
                 WHERE ts.temp_student_id = ?
                 ORDER BY o.submitted_at DESC LIMIT 1
                """, tempStudentNo);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    public Map<String, Object> getInternationalStudent(String studentId) {
        List<Map<String, Object>> rows = queryListSafe("""
                SELECT student_id, person_name, nationality, passport_no,
                       college_name, major_name, class_name, degree_level,
                       enroll_date, expected_graduation_date, scholarship_program,
                       register_status, student_status
                  FROM exg_is_student
                 WHERE student_id = ? LIMIT 1
                """, studentId);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    public Map<String, Map<String, Object>> loadInternationalStudentMap() {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> r : queryListSafe("""
                SELECT student_id, nationality, scholarship_program, degree_level
                  FROM exg_is_student
                """)) {
            map.put(str(r.get("student_id")), new LinkedHashMap<>(r));
        }
        return map;
    }

    /** 查询第 15 组返校备案状态（支持国际访学 + 国内访学） */
    public List<Map<String, Object>> listExchangeCreditReady(String studentId, boolean readyOnly) {
        StringBuilder sql = new StringBuilder("""
                SELECT v.application_no, v.student_id,
                       COALESCE(p.person_name, v.student_id) AS student_name,
                       v.term_ref, v.partner_school_snapshot, v.ready_flag,
                       v.exchange_type, v.actual_return_date
                  FROM v_exchange_credit_ready v
                  LEFT JOIN std_student s ON s.student_id = v.student_id
                  LEFT JOIN sys_person p ON p.person_id = s.person_id
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (studentId != null && !studentId.isBlank()) {
            sql.append(" AND v.student_id = ? ");
            args.add(studentId.trim());
        }
        if (readyOnly) {
            sql.append(" AND v.ready_flag = 1 ");
        }
        sql.append(" ORDER BY v.ready_flag DESC, v.student_id ASC ");

        List<Map<String, Object>> rows = queryListSafe(sql.toString(), args.toArray());
        // 兼容旧版 5 列视图（无 exchange_type / actual_return_date 时降级查询）
        if (rows.isEmpty() && !viewHasExtendedColumns()) {
            StringBuilder fallback = new StringBuilder("""
                    SELECT v.application_no, v.student_id,
                           COALESCE(p.person_name, v.student_id) AS student_name,
                           v.term_ref, v.partner_school_snapshot, v.ready_flag
                      FROM v_exchange_credit_ready v
                      LEFT JOIN std_student s ON s.student_id = v.student_id
                      LEFT JOIN sys_person p ON p.person_id = s.person_id
                     WHERE 1=1
                    """);
            if (studentId != null && !studentId.isBlank()) {
                fallback.append(" AND v.student_id = ? ");
            }
            if (readyOnly) {
                fallback.append(" AND v.ready_flag = 1 ");
            }
            fallback.append(" ORDER BY v.ready_flag DESC, v.student_id ASC ");
            rows = queryListSafe(fallback.toString(), args.toArray());
        }
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>(r);
            String exType = str(r.get("exchange_type"));
            if (exType.isEmpty()) exType = "INTL";
            item.put("exchange_type", exType);
            item.put("exchange_type_label", "DOMESTIC".equalsIgnoreCase(exType) ? "国内访学" : "国际交流");
            boolean ready = r.get("ready_flag") instanceof Number n && n.intValue() == 1;
            item.put("ready", ready);
            res.add(item);
        }
        return res;
    }

    public boolean hasExchangeCreditReadyData() {
        return queryCountSafe("v_exchange_credit_ready") > 0;
    }

    public List<Map<String, Object>> listForeignTranscripts(String studentId) {
        StringBuilder sql = new StringBuilder("""
                SELECT CAST(ft.foreign_transcript_id AS CHAR(64)) AS foreign_transcript_id,
                       ft.student_id,
                       COALESCE(p.person_name, ft.student_id) AS student_name,
                       ft.partner_school, ft.study_term, ft.foreign_course_id,
                       ft.foreign_course_name, ft.foreign_credit, ft.foreign_score, ft.issue_date
                  FROM exg_vst_foreign_transcript ft
                  LEFT JOIN std_student s ON s.student_id = ft.student_id
                  LEFT JOIN sys_person p ON p.person_id = s.person_id
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (studentId != null && !studentId.isBlank()) {
            sql.append(" AND ft.student_id = ? ");
            args.add(studentId.trim());
        }
        sql.append(" ORDER BY ft.issue_date DESC, ft.foreign_course_id ASC ");
        return queryListSafe(sql.toString(), args.toArray());
    }

    // =========================================================================
    // 8. 工作台「全班跨组数据联动一览」实时概览
    // =========================================================================

    public Map<String, Object> getCrossGroupOverview() {
        List<Map<String, Object>> groups = new ArrayList<>();

        groups.add(buildGroupCard(1, "第 1 组", "学籍管理", "双向协同",
                List.of("std_student", "aca_class", "std_registration", "std_minor_degree", "std_major_transfer"),
                List.of("v_sel_stu_academic_summary", "v_sel_stu_credit_by_category", "v_sel_stu_course_grades"),
                "学籍身份与行政班、新学期注册核验、辅修/双学位资格、转专业记录；回供 3 个学业与成绩只读视图"));

        groups.add(buildGroupCard(2, "第 2 组", "专业设置及排课", "双向协同",
                List.of("aca_term", "aca_major", "aca_course", "aca_course_id", "aca_teaching_class",
                        "aca_schedule_result", "aca_program_course", "aca_major_program",
                        "aca_general_plan", "aca_adjust_record", "aca_schedule_conflict",
                        "aca_dual_enroll", "aca_credit_apply", "aca_credit_item"),
                List.of(),
                "动态学期与培养方案、开课计划与排课结果、调课与排课冲突预警、双学位录取与学分转换；回写实选人数与停开状态"));

        groups.add(buildGroupCard(4, "第 4 组", "师资管理", "双向协同",
                List.of("hr_employee", "hr_leave", "hr_abroad"),
                List.of("sel_tc_eval_result"),
                "任课教师姓名/职称/学历、教师请假与因公出国教学提醒；回供教学班匿名评教汇总与停开工作量核减"));

        groups.add(buildGroupCard(5, "第 5 组", "财务管理", "只读接入",
                List.of("fin_charge_item", "fin_charge"),
                List.of(),
                "选课与学业进度欠费温馨提醒，支持在系统参数中按需切换“欠费拦截选课”开关"));

        groups.add(buildGroupCard(7, "第 7 组", "固定资产管理", "只读接入",
                List.of("ast_building", "aca_room"),
                List.of(),
                "教学楼栋位置、教室座位数与多媒体/机位配置展示，教学班容量超过教室座位时自动触发超容预警"));

        groups.add(buildGroupCard(9, "第 9 组", "实验室管理", "只读接入",
                List.of("lab_laboratory", "lab_closure", "lab_reservation"),
                List.of(),
                "实验课对应实验室开放时间与负责人展示、临时封闭维护预警、课程上机预约联动"));

        groups.add(buildGroupCard(13, "第 13 组", "研究生管理", "只读接入",
                List.of("grad_direction", "grad_training_scheme", "grad_scheme_requirement",
                        "grad_scheme_assignment", "grad_scheme_course", "grad_training_plan",
                        "grad_plan_course", "grad_student_mentor"),
                List.of(),
                "研究生研究方向中文名、培养方案分类学分要求、个人培养计划拟修课程标注、导师指导关系展示"));

        groups.add(buildGroupCard(15, "第 15 组", "对外交流管理", "只读接入",
                List.of("exg_application", "exg_admission", "exg_temp_student", "exg_vst_departure",
                        "exg_vst_return_report", "exg_vst_return_filing", "exg_dv_filing",
                        "exg_dv_return_report", "exg_dv_return_filing", "exg_vst_foreign_transcript",
                        "exg_is_student", "exg_supervisor_opinion", "exg_ob_teacher_status"),
                List.of("v_exchange_student", "v_exchange_credit_ready"),
                "来校交换生选课与导师选课建议、来华留学生双语成绩单、国际/国内访学返校核验与外校成绩单自动带入、教师因公出访提醒"));

        groups.add(buildGroupCard(16, "第 16 组", "公共主数据与字典", "基础底座",
                List.of("org_organization", "sys_person", "al_dict_type", "al_dict_item"),
                List.of(),
                "全校组织机构、统一自然人主索引、系统参数配置与跨组统一业务字典"));

        int totalTables = 0;
        int activeTables = 0;
        long totalRecords = 0;
        int bidirectionalCount = 0;
        for (Map<String, Object> g : groups) {
            totalTables += ((Number) g.get("table_count")).intValue();
            activeTables += ((Number) g.get("active_table_count")).intValue();
            totalRecords += ((Number) g.get("total_records")).longValue();
            if ("双向协同".equals(g.get("direction"))) {
                bidirectionalCount++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("connected_business_groups", 8);
        summary.put("total_connected_groups", groups.size());
        summary.put("bidirectional_groups", bidirectionalCount);
        summary.put("total_upstream_tables", totalTables);
        summary.put("active_upstream_tables", activeTables);
        summary.put("total_upstream_records", totalRecords);
        summary.put("current_term", getCurrentTerm());
        summary.put("block_selection_on_arrears", isBlockSelectionOnArrearsEnabled());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("summary", summary);
        resp.put("groups", groups);
        return resp;
    }

    private Map<String, Object> buildGroupCard(
            int groupNo, String groupLabel, String systemName, String direction,
            List<String> tables, List<String> views, String scenarios) {
        long totalRows = 0;
        int activeTables = 0;
        List<Map<String, Object>> tableStats = new ArrayList<>();
        for (String tbl : tables) {
            long cnt = queryCountSafe(tbl);
            if (cnt >= 0) {
                activeTables++;
                totalRows += cnt;
            }
            Map<String, Object> ts = new LinkedHashMap<>();
            ts.put("table_name", tbl);
            ts.put("row_count", Math.max(0L, cnt));
            ts.put("available", cnt >= 0);
            tableStats.add(ts);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("group_no", groupNo);
        card.put("group_label", groupLabel);
        card.put("system_name", systemName);
        card.put("direction", direction);
        card.put("table_count", tables.size());
        card.put("active_table_count", activeTables);
        card.put("view_count", views.size());
        card.put("views", views);
        card.put("total_records", totalRows);
        card.put("scenarios", scenarios);
        card.put("status", activeTables > 0 ? "CONNECTED" : "STANDBY");
        card.put("tables", tableStats);
        return card;
    }

    // =========================================================================
    // 内部安全查询工具
    // =========================================================================

    private boolean viewHasExtendedColumns() {
        try {
            jdbc.queryForList("SELECT exchange_type, actual_return_date FROM v_exchange_credit_ready LIMIT 1");
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private List<Map<String, Object>> queryListSafe(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private <T> List<T> queryColumnSafe(String sql, Class<T> type, Object... args) {
        try {
            return jdbc.queryForList(sql, type, args);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private long queryCountSafe(String tableOrView) {
        if (tableOrView == null || !tableOrView.matches("^[a-zA-Z0-9_]+$")) {
            return -1L;
        }
        try {
            Long cnt = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableOrView, Long.class);
            return cnt == null ? 0L : cnt;
        } catch (DataAccessException e) {
            return -1L;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String fmtMoney(BigDecimal val) {
        return (val == null ? BigDecimal.ZERO : val).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
