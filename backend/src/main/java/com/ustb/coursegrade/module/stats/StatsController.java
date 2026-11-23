package com.ustb.coursegrade.module.stats;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 【业务④ 成绩查询与统计（4.1–4.6）· 负责人：黎家俊】
 * 请在本包 com.ustb.coursegrade.module.stats 内补充 Service 与接口实现，注意：
 * 1. 未完成本学期评教时拦截查分（4.1 -> 4.2）
 * 2. 4.3 学业统计须排除 sc.study_type=3（辅修），并联合 exchange_score_convert（audit_status=2）
 * 3. 4.4 成绩单打印含来校交换生中英文正式成绩单
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    @GetMapping("/module-info")
    public ApiResponse<Map<String, Object>> moduleInfo() {
        return ApiResponse.ok(Map.of(
                "module", "业务④ 成绩查询与统计",
                "owner", "黎家俊",
                "status", "待开发（骨架已就绪）",
                "leaf_modules", List.of(
                        "4.1 学生评教（最简评教，供第4组汇总）",
                        "4.2 成绩查询（先评教后查分）",
                        "4.3 学业统计计算（GPA/学分/排名，排除辅修）",
                        "4.4 成绩单打印（含来校交换生中英文成绩单）",
                        "4.5 成绩统计报表（成绩分布/不及格/评优排名）",
                        "4.6 评教结果汇总（按教学班汇总，零参评均分为NULL）"
                )
        ));
    }
}
