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
        info.put("service", "选课与成绩管理系统");
        info.put("status", "UP");
        info.put("server_time", LocalDateTime.now().toString());
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer ownTables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT TABLE_NAME) FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" +
                    "'sel_select_round','sel_round_class','sel_select_limit','sel_sc','sel_select_log','sel_lesson_class'," +
                    "'sel_score','sel_score_audit','sel_score_correction','sel_stu_gpa','sel_stu_term_gpa'," +
                    "'sel_teach_eval','sel_tc_eval_result','sel_exchange_score_convert')",
                    Integer.class);
            info.put("database", dbName);
            info.put("db_connected", true);
            info.put("tables_ready", ownTables);
        } catch (Exception e) {
            info.put("db_connected", false);
            info.put("db_error", e.getMessage());
        }
        return ApiResponse.ok(info);
    }
}
