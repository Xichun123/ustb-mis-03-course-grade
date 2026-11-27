package com.ustb.coursegrade.common;

import com.ustb.coursegrade.common.auth.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全班跨组公共只读接口：
 * - GET /api/v1/cross-group/terms    动态获取第 2 组学期主数据（aca_term）及当前学期
 * - GET /api/v1/cross-group/overview 获取全班跨组数据联动实时概览（工作台看板）
 */
@RestController
@RequestMapping("/api/v1/cross-group")
public class CrossGroupController {

    private final CrossGroupDataService crossGroupDataService;

    public CrossGroupController(CrossGroupDataService crossGroupDataService) {
        this.crossGroupDataService = crossGroupDataService;
    }

    @GetMapping("/terms")
    public ApiResponse<Map<String, Object>> terms() {
        UserContext.requireUser();
        return ApiResponse.ok(crossGroupDataService.getTermsPayload());
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        UserContext.requireUser();
        return ApiResponse.ok(crossGroupDataService.getCrossGroupOverview());
    }
}
