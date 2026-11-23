package com.ustb.coursegrade.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "03组-选课及成绩管理系统");
        info.put("schema_version", "v9 (14 tables, 134 columns, 82 CHECKs)");
        info.put("server_time", LocalDateTime.now().toString());
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer ownTables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT TABLE_NAME) FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" +
                    "'select_round','round_class','select_limit','sc','select_log','lesson_class'," +
                    "'score','score_audit','score_correction','stu_gpa','stu_term_gpa'," +
                    "'teach_eval','tc_eval_result','exchange_score_convert')",
                    Integer.class);
            info.put("database", dbName);
            info.put("db_connected", true);
            info.put("own_v9_tables", ownTables);
        } catch (Exception e) {
            info.put("db_connected", false);
            info.put("db_error", e.getMessage());
        }
        return ApiResponse.ok(info);
    }
}
