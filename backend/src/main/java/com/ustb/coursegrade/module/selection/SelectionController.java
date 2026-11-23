package com.ustb.coursegrade.module.selection;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 【业务② 学生选课（2.1–2.7）· 负责人：张文浩】
 * 请在本包 com.ustb.coursegrade.module.selection 内补充 Service 与接口实现，参考 module/prep/。
 */
@RestController
@RequestMapping("/api/v1/selection")
public class SelectionController {

    @GetMapping("/module-info")
    public ApiResponse<Map<String, Object>> moduleInfo() {
        return ApiResponse.ok(Map.of(
                "module", "业务② 学生选课",
                "owner", "张文浩",
                "status", "待开发（骨架已就绪）",
                "leaf_modules", List.of(
                        "2.1 课程查询与教学班选择",
                        "2.2 选课与退选办理",
                        "2.3 选课校验（冲突/容量/学分/限选/研究生方向/重修免听）",
                        "2.4 录取与超额筛选",
                        "2.5 停开处理",
                        "2.6 选课结果与上课安排查询",
                        "2.7 上课名单调整"
                )
        ));
    }
}
