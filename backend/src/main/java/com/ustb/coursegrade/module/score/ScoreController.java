package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 【业务③ 成绩录入与审核（3.1–3.5）· 负责人：蒋作】
 * 请在本包 com.ustb.coursegrade.module.score 内补充 Service 与接口实现，严格遵循 v9 lesson_no 课堂号口径。
 */
@RestController
@RequestMapping("/api/v1/score")
public class ScoreController {

    @GetMapping("/module-info")
    public ApiResponse<Map<String, Object>> moduleInfo() {
        return ApiResponse.ok(Map.of(
                "module", "业务③ 成绩录入与审核",
                "owner", "蒋作",
                "status", "待开发（骨架已就绪）",
                "leaf_modules", List.of(
                        "3.1 成绩录入（按本人课堂号 lesson_no 列表点选录分）",
                        "3.2 总评计算与提交（生成6位随机核对码与纸质登记表）",
                        "3.3 学院核对（输入随机码核验通过或退回）",
                        "3.4 成绩发布（重标 is_final 并触发 4.3）",
                        "3.5 校外交流成绩换算与认定（v8 新增）"
                )
        ));
    }
}
