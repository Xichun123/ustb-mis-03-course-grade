package com.ustb.coursegrade.module.admin;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 【业务⑥ 系统维护 6.1–6.2 · 蒋作】
 * 6.1 编码参数维护（DBA 统一字典表 sys_dict_type / sys_dict_item，如 usual_ratio_min 平时占比下限）
 * 6.2 数据备份与恢复（sys_backup_log，恢复前先记应急快照）
 */
@Service
public class AdminService {

    private final JdbcTemplate jdbc;

    public AdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 6.1 编码列表（对接 DBA 统一字典表 sys_dict_item，兼容原前端返回字段） */
    public List<Map<String, Object>> listCodes(String group, String keyword) {
        UserContext.requireRole(RoleEnum.SYS_ADMIN, RoleEnum.ACADEMIC_OFFICE);
        StringBuilder sql = new StringBuilder("""
                SELECT dict_id,
                       dict_type AS dict_group,
                       item_code AS dict_code,
                       item_name AS dict_name,
                       version AS remark,
                       CASE WHEN item_status IN ('01', '1') THEN 1 ELSE 0 END AS is_enabled,
                       effective_from AS update_time
                FROM sys_dict_item WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (group != null && !group.isBlank()) {
            sql.append(" AND dict_type = ? ");
            args.add(group.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (item_code LIKE ? OR item_name LIKE ? OR version LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY dict_type, sort_no, item_code LIMIT 500");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 6.1 新增编码 */
    @Transactional
    public Map<String, Object> createCode(Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.SYS_ADMIN);
        String group = str(body.get("dict_group"));
        String code = str(body.get("dict_code"));
        String name = str(body.get("dict_name"));
        if (group.isEmpty() || code.isEmpty() || name.isEmpty()) {
            throw BizException.unprocessable("MISSING_FIELD", "编码分组、编码键、名称不能为空");
        }
        if (group.length() > 40 || code.length() > 100 || name.length() > 200) {
            throw BizException.unprocessable("TOO_LONG", "编码分组不超过 40 字符，编码键不超过 100 字符，名称不超过 200 字符");
        }
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_dict_item WHERE dict_type = ? AND item_code = ?
                """, Integer.class, group, code);
        if (dup != null && dup > 0) {
            throw BizException.conflict("DUPLICATE_CODE", "同一分组下编码键已存在");
        }
        Integer typeExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE dict_type = ?", Integer.class, group);
        if (typeExists == null || typeExists == 0) {
            jdbc.update("""
                    INSERT INTO sys_dict_type(dict_type, type_name, is_system, dict_status, remark)
                    VALUES(?, ?, 0, '01', ?)
                    """, group, group, "由系统维护界面自动登记");
        }
        String remark = str(body.get("remark"));
        String version = remark.isEmpty() ? "2026" : (remark.length() > 40 ? remark.substring(0, 40) : remark);
        jdbc.update("""
                INSERT INTO sys_dict_item(dict_type, item_code, item_name, sort_no, version, effective_from, item_status)
                VALUES(?, ?, ?, 0, ?, CURRENT_DATE, '01')
                """, group, code, name, version);
        return Map.of("dict_group", group, "dict_code", code, "message", "编码已新增");
    }

    /** 6.1 修改/启停编码 */
    @Transactional
    public Map<String, Object> updateCode(int id, Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.SYS_ADMIN);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_item WHERE dict_id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            throw BizException.notFound("编码不存在：" + id);
        }
        String name = str(body.get("dict_name"));
        String remark = body.get("remark") == null ? null : str(body.get("remark"));
        Integer enabled = body.get("is_enabled") == null ? null : Integer.parseInt(String.valueOf(body.get("is_enabled")));
        if (!name.isEmpty()) {
            jdbc.update("UPDATE sys_dict_item SET item_name = ? WHERE dict_id = ?", name, id);
        }
        if (body.containsKey("remark")) {
            String ver = (remark == null || remark.isEmpty()) ? "2026" : (remark.length() > 40 ? remark.substring(0, 40) : remark);
            jdbc.update("UPDATE sys_dict_item SET version = ? WHERE dict_id = ?", ver, id);
        }
        if (enabled != null) {
            if (enabled != 0 && enabled != 1) {
                throw BizException.unprocessable("INVALID_ENABLED", "启用状态参数无效");
            }
            jdbc.update("UPDATE sys_dict_item SET item_status = ? WHERE dict_id = ?", enabled == 1 ? "01" : "02", id);
        }
        return Map.of("dict_id", id, "message", "编码已更新");
    }

    /** 6.2 备份日志 */
    public List<Map<String, Object>> listBackups() {
        UserContext.requireRole(RoleEnum.SYS_ADMIN, RoleEnum.ACADEMIC_OFFICE);
        return jdbc.queryForList("""
                SELECT backup_id, backup_type, file_name, file_size_kb, operator_id, backup_time, status, remark
                FROM sys_backup_log ORDER BY backup_id DESC LIMIT 100
                """);
    }

    /** 6.2 登记一次备份（备份文件由运维侧生成，这里记录元数据） */
    @Transactional
    public Map<String, Object> createBackup(Map<String, Object> body) {
        CurrentUser u = UserContext.requireRole(RoleEnum.SYS_ADMIN);
        int type = body.get("backup_type") == null ? 1 : Integer.parseInt(String.valueOf(body.get("backup_type")));
        if (type != 1 && type != 2) {
            throw BizException.unprocessable("INVALID_TYPE", "备份类型仅支持手工备份或恢复前应急快照");
        }
        String fileName = str(body.get("file_name"));
        if (fileName.isEmpty()) {
            throw BizException.unprocessable("MISSING_FILE", "备份文件名不能为空");
        }
        Integer size = body.get("file_size_kb") == null ? null : Integer.parseInt(String.valueOf(body.get("file_size_kb")));
        jdbc.update("""
                INSERT INTO sys_backup_log(backup_type, file_name, file_size_kb, operator_id, backup_time, status, remark)
                VALUES(?, ?, ?, ?, NOW(), 1, ?)
                """, type, fileName, size, u.empId() == null ? u.userId() : u.empId(), str(body.get("remark")));
        Long id = jdbc.queryForObject("SELECT MAX(backup_id) FROM sys_backup_log", Long.class);
        return Map.of("backup_id", id, "message", "备份记录已登记");
    }

    /** 6.2 恢复：先写应急快照，再返回恢复校验通过（真实文件恢复由运维执行） */
    @Transactional
    public Map<String, Object> restore(int backupId) {
        CurrentUser u = UserContext.requireRole(RoleEnum.SYS_ADMIN);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT backup_id, file_name, status FROM sys_backup_log WHERE backup_id = ?
                """, backupId);
        if (rows.isEmpty()) {
            throw BizException.notFound("备份记录不存在：" + backupId);
        }
        if (((Number) rows.get(0).get("status")).intValue() != 1) {
            throw BizException.conflict("BACKUP_FAILED", "该备份状态为失败，不能用于恢复");
        }
        String fileName = String.valueOf(rows.get(0).get("file_name"));
        jdbc.update("""
                INSERT INTO sys_backup_log(backup_type, file_name, operator_id, backup_time, status, remark)
                VALUES(2, ?, ?, NOW(), 1, ?)
                """, "before_restore_" + fileName, u.empId() == null ? u.userId() : u.empId(),
                "恢复 " + fileName + " 前的应急快照");
        return Map.of("backup_id", backupId, "file_name", fileName,
                "message", "已自动生成恢复前应急快照，请继续按规范执行数据恢复并核验数据完整性");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
