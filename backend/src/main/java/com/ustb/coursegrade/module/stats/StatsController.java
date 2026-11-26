package com.ustb.coursegrade.module.stats;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务④ 成绩查询与统计（4.1–4.6）· 负责人：黎家俊】REST 接口
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/module-info")
    public ApiResponse<Map<String, Object>> moduleInfo() {
        return ApiResponse.ok(Map.of(
                "module", "成绩查询与统计",
                "status", "运行中",
                "features", List.of(
                        "学生评教",
                        "成绩查询",
                        "学业统计计算",
                        "成绩单预览与打印",
                        "成绩统计报表",
                        "评教结果汇总"
                )
        ));
    }

    // ==================== 4.1 学生评教 ====================

    @GetMapping("/my/evaluations/pending")
    public ApiResponse<Map<String, Object>> myPendingEvaluations(
            @RequestParam(required = false) String term) {
        return ApiResponse.ok(statsService.getMyEvaluationPending(term));
    }

    @PostMapping("/my/evaluations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitEvaluation(
            @RequestBody Map<String, Object> body) {
        Map<String, Object> data = statsService.submitEvaluation(body);
        boolean already = Boolean.TRUE.equals(data.get("already_submitted"));
        if (already) {
            return ResponseEntity.ok(ApiResponse.ok("该课程已完成评教，不可重复修改", data));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("评教提交成功", data));
    }

    // ==================== 4.2 本人成绩查询 ====================

    @GetMapping("/my/grades")
    public ApiResponse<Map<String, Object>> myGrades(
            @RequestParam(required = false) String term,
            @RequestParam(required = false, defaultValue = "false") Boolean only_final,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        return ApiResponse.ok(statsService.queryMyGrades(term, only_final, page, page_size));
    }

    // ==================== 4.3 学业统计台账与重算 ====================

    @GetMapping("/academic-stats")
    public ApiResponse<Map<String, Object>> listAcademicStats(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String major_id,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(statsService.listAcademicStats(term, major_id, grade, keyword));
    }

    @GetMapping("/academic-progress")
    public ApiResponse<Map<String, Object>> academicProgress(
            @RequestParam(required = false) String student_id) {
        return ApiResponse.ok(statsService.getAcademicProgress(student_id));
    }

    @GetMapping("/students/{studentId}/academic-profile")
    public ApiResponse<Map<String, Object>> studentAcademicProfile(
            @PathVariable String studentId,
            @RequestParam(required = false) String term) {
        return ApiResponse.ok(statsService.getStudentAcademicProfile(studentId, term));
    }

    @PostMapping("/academic-stats/recompute")
    public ApiResponse<Map<String, Object>> recomputeAcademicStats(
            @RequestBody(required = false) Map<String, Object> body) {
        String studentId = (body != null && body.get("student_id") != null)
                ? String.valueOf(body.get("student_id")) : null;
        return ApiResponse.ok("学业统计重算完成", statsService.triggerRecomputeAcademicStats(studentId));
    }

    // ==================== 4.4 成绩单预览与打印 ====================

    @GetMapping("/transcript/students")
    public ApiResponse<List<Map<String, Object>>> transcriptStudents() {
        return ApiResponse.ok(statsService.listTranscriptStudents());
    }

    @PostMapping("/my/transcript-preview")
    public ApiResponse<Map<String, Object>> transcriptPreview(
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(statsService.generateTranscriptPreview(body == null ? Map.of() : body));
    }

    // ==================== 4.5 成绩统计报表 ====================

    @GetMapping("/grade-reports/options")
    public ApiResponse<Map<String, Object>> reportOptions() {
        return ApiResponse.ok(statsService.getReportOptions());
    }

    @GetMapping("/grade-reports")
    public ApiResponse<Map<String, Object>> gradeReports(
            @RequestParam String report_type,
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String tc_id,
            @RequestParam(required = false) Integer exam_type,
            @RequestParam(required = false) String major_id,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String start_term,
            @RequestParam(required = false) String end_term) {
        return ApiResponse.ok(statsService.queryGradeReport(
                report_type, term, tc_id, exam_type, major_id, grade, start_term, end_term));
    }

    // ==================== 4.6 评教结果汇总 ====================

    @GetMapping("/teaching-evaluation-summaries")
    public ApiResponse<Map<String, Object>> evaluationSummaries(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String tc_id) {
        return ApiResponse.ok(statsService.listEvaluationSummaries(term, tc_id));
    }

    @PostMapping("/teaching-evaluation-summaries/recompute")
    public ApiResponse<Map<String, Object>> recomputeEvaluationSummaries(
            @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok("教学班评教结果汇总已完成",
                statsService.triggerEvaluationSummary(body == null ? Map.of() : body));
    }

    @GetMapping("/teaching-evaluation-details")
    public ApiResponse<List<Map<String, Object>>> evaluationDetails(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String tc_id) {
        return ApiResponse.ok(statsService.listEvaluationDetailsForOffice(term, tc_id));
    }
}
