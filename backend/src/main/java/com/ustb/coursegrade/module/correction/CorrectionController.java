package com.ustb.coursegrade.module.correction;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务⑤ 成绩更正申请 · 朱鳞太】REST 接口（5.1–5.4）
 */
@RestController
@RequestMapping("/api/v1/correction")
public class CorrectionController {

    private final CorrectionService correctionService;

    public CorrectionController(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> listCandidates(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(correctionService.listCandidates(term, keyword));
    }

    @PostMapping("/score-corrections")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("成绩更正申请已提交学院审核", correctionService.submitCorrection(body)));
    }

    @GetMapping("/score-corrections")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(required = false) Integer apply_status,
            @RequestParam(required = false) String term) {
        return ApiResponse.ok(correctionService.listCorrections(apply_status, term));
    }

    @PostMapping("/score-corrections/{applyId}/college-decision")
    public ApiResponse<Map<String, Object>> collegeDecision(
            @PathVariable long applyId, @RequestBody Map<String, Object> body) {
        int decision = Integer.parseInt(String.valueOf(body.get("decision")));
        String opinion = body.get("college_audit_opinion") == null ? null : String.valueOf(body.get("college_audit_opinion"));
        Map<String, Object> data = correctionService.collegeDecision(applyId, decision, opinion);
        return ApiResponse.ok(String.valueOf(data.getOrDefault("message",
                decision == 1 ? "学院已批准更正并解锁该学生成绩，请教师重录并重新提交纸质成绩单" : "学院已驳回申请")),
                data);
    }

    @PostMapping("/score-corrections/{applyId}/office-decision")
    public ApiResponse<Map<String, Object>> officeDecision(
            @PathVariable long applyId, @RequestBody Map<String, Object> body) {
        int decision = Integer.parseInt(String.valueOf(body.get("decision")));
        String opinion = body.get("audit_opinion") == null ? null : String.valueOf(body.get("audit_opinion"));
        return ApiResponse.ok("成绩更正重录后请在成绩发布页统一发布",
                correctionService.officeDecision(applyId, decision, opinion));
    }

    @GetMapping("/my-results")
    public ApiResponse<List<Map<String, Object>>> myResults(@RequestParam(required = false) String term) {
        return ApiResponse.ok(correctionService.listMyCorrectionResults(term));
    }
}
