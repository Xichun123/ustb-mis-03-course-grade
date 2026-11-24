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
 * 6.1 编码参数维护（sys_code_dict，如 usual_ratio_min 平时占比下限）
 * 6.2 数据备份与恢复（sys_backup_log，恢复前先记应急快照）
 */
@Service
public class AdminService {

    private final JdbcTemplate jdbc;

    public AdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 6.1 编码列表 */
    public List<Map<String, Object>> listCodes(String group, String keyword) {
        UserContext.requireRole(RoleEnum.SYS_ADMIN, RoleEnum.ACADEMIC_OFFICE);
        StringBuilder sql = new StringBuilder("""
                SELECT dict_id, dict_group, dict_code, dict_name, remark, is_enabled, update_time
                FROM sys_code_dict WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (group != null && !group.isBlank()) {
            sql.append(" AND dict_group = ? ");
            args.add(group.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (dict_code LIKE ? OR dict_name LIKE ? OR remark LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY dict_group, dict_code LIMIT 500");
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
        if (group.length() > 40 || code.length() > 40 || name.length() > 100) {
            throw BizException.unprocessable("TOO_LONG", "编码分组/键不超过 40 字符，名称不超过 100 字符");
        }
        Integer dup = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_code_dict WHERE dict_group = ? AND dict_code = ?
                """, Integer.class, group, code);
        if (dup != null && dup > 0) {
            throw BizException.conflict("DUPLICATE_CODE", "同一分组下编码键已存在");
        }
        jdbc.update("""
                INSERT INTO sys_code_dict(dict_group, dict_code, dict_name, remark, is_enabled)
                VALUES(?, ?, ?, ?, 1)
                """, group, code, name, str(body.get("remark")));
        return Map.of("dict_group", group, "dict_code", code, "message", "编码已新增");
    }

    /** 6.1 修改/启停编码 */
    @Transactional
    public Map<String, Object> updateCode(int id, Map<String, Object> body) {
        UserContext.requireRole(RoleEnum.SYS_ADMIN);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_code_dict WHERE dict_id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            throw BizException.notFound("编码不存在：" + id);
        }
        String name = str(body.get("dict_name"));
        String remark = body.get("remark") == null ? null : str(body.get("remark"));
        Integer enabled = body.get("is_enabled") == null ? null : Integer.parseInt(String.valueOf(body.get("is_enabled")));
        if (!name.isEmpty()) {
            jdbc.update("UPDATE sys_code_dict SET dict_name = ? WHERE dict_id = ?", name, id);
        }
        if (body.containsKey("remark")) {
            jdbc.update("UPDATE sys_code_dict SET remark = ? WHERE dict_id = ?", remark, id);
        }
        if (enabled != null) {
            if (enabled != 0 && enabled != 1) {
                throw BizException.unprocessable("INVALID_ENABLED", "is_enabled 只能为 0 或 1");
            }
            jdbc.update("UPDATE sys_code_dict SET is_enabled = ? WHERE dict_id = ?", enabled, id);
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
            throw BizException.unprocessable("INVALID_TYPE", "备份类型只能为 1手工备份 / 2恢复前应急快照");
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
                "message", "已生成恢复前应急快照，可执行恢复；恢复后请重新运行 v9 校验脚本");
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
