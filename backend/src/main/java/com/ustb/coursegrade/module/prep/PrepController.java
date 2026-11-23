package com.ustb.coursegrade.module.prep;

import com.ustb.coursegrade.common.ApiResponse;
import com.ustb.coursegrade.common.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务① 选课准备与发布 · 朱鳞太】REST 接口（1.1–1.5）
 */
@RestController
@RequestMapping("/api/v1/prep")
public class PrepController {

    private final PrepService prepService;

    public PrepController(PrepService prepService) {
        this.prepService = prepService;
    }

    @GetMapping("/teaching-classes")
    public ApiResponse<PageResult<Map<String, Object>>> listTeachingClasses(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String college_id,
            @RequestParam(required = false) String tc_status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.ok(prepService.listTeachingClasses(
                term, keyword, college_id, tc_status, page, Math.min(page_size, 100)));
    }

    @GetMapping("/teaching-classes/{tcId}/limits")
    public ApiResponse<Map<String, Object>> getLimits(@PathVariable String tcId) {
        return ApiResponse.ok(prepService.getTeachingClassLimits(tcId));
    }

    @PutMapping("/teaching-classes/{tcId}/limits")
    @SuppressWarnings("unchecked")
    public ApiResponse<List<Map<String, Object>>> replaceLimits(
            @PathVariable String tcId,
            @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> limits = (List<Map<String, Object>>) body.getOrDefault("limits", List.of());
        return ApiResponse.ok("限选条件已更新", prepService.replaceLimits(tcId, limits));
    }

    @GetMapping("/selection-rounds")
    public ApiResponse<PageResult<Map<String, Object>>> listRounds(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) Integer round_status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.ok(prepService.listRounds(term, round_status, page, Math.min(page_size, 100)));
    }

    @GetMapping("/selection-rounds/{roundId}")
    public ApiResponse<Map<String, Object>> getRound(@PathVariable int roundId) {
        return ApiResponse.ok(prepService.getRoundDetail(roundId));
    }

    @PostMapping("/selection-rounds")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRound(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("选课轮次草稿已创建", prepService.createRound(body)));
    }

    @PutMapping("/selection-rounds/{roundId}")
    public ApiResponse<Map<String, Object>> updateRound(
            @PathVariable int roundId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("选课轮次草稿已更新", prepService.updateRound(roundId, body));
    }

    @PostMapping("/selection-rounds/{roundId}/submit")
    public ApiResponse<Map<String, Object>> submitRound(@PathVariable int roundId) {
        return ApiResponse.ok("已提交教务处处长审批", prepService.submitRound(roundId));
    }

    @PostMapping("/selection-rounds/{roundId}/decision")
    public ApiResponse<Map<String, Object>> decideRound(
            @PathVariable int roundId, @RequestBody Map<String, Object> body) {
        int decision = Integer.parseInt(String.valueOf(body.get("decision")));
        String opinion = body.get("approve_opinion") == null ? null : String.valueOf(body.get("approve_opinion"));
        String msg = decision == 1 ? "审批通过，等待教务处发布" : "已退回草稿修改";
        return ApiResponse.ok(msg, prepService.decideRound(roundId, decision, opinion));
    }

    @PostMapping("/selection-rounds/{roundId}/publish")
    public ApiResponse<Map<String, Object>> publishRound(@PathVariable int roundId) {
        return ApiResponse.ok("选课通知已发布", prepService.publishRound(roundId));
    }
}
