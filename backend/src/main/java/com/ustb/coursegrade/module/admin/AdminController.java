package com.ustb.coursegrade.module.admin;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 【业务⑥ 系统维护（6.1–6.2）· 负责人：蒋作】
 * 请在本包 com.ustb.coursegrade.module.admin 内补充编码维护（sys_code_dict）与备份恢复（sys_backup_log）实现。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/module-info")
    public ApiResponse<Map<String, Object>> moduleInfo() {
        return ApiResponse.ok(Map.of(
                "module", "业务⑥ 系统维护",
                "owner", "蒋作",
                "status", "待开发（骨架已就绪）",
                "leaf_modules", List.of(
                        "6.1 编码维护（学期、平时占比最低值、学院/工号映射）",
                        "6.2 数据备份与恢复（备份日志、应急快照与恢复校验）"
                )
        ));
    }
}
