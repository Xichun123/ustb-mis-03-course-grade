package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务③ 成绩录入与审核 3.1–3.5 · 蒋作】REST 接口
 * 课堂号统一用 lesson_no；教师从本人课堂列表进入，不手输课堂号。
 */
@RestController
@RequestMapping("/api/v1/score")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /** 3.1 我的课堂（本学期在前、往届在后） */
    @GetMapping("/my-classes")
    public ApiResponse<List<Map<String, Object>>> myClasses(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scoreService.myClasses(term, keyword));
    }

    /** 3.1 课堂名单与已录成绩 */
    @GetMapping("/classes/{lessonNo}")
    public ApiResponse<Map<String, Object>> roster(@PathVariable String lessonNo) {
        return ApiResponse.ok(scoreService.roster(lessonNo));
    }

    /** 3.1 保存成绩草稿 */
    @PutMapping("/classes/{lessonNo}/grades")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> saveGrades(
            @PathVariable String lessonNo, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        return ApiResponse.ok("成绩已保存", scoreService.saveGrades(lessonNo, items));
    }

    /** 3.2 总评计算与提交，返回 6 位核对码 */
    @PostMapping("/classes/{lessonNo}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable String lessonNo) {
        return ApiResponse.ok("提交成功，已生成核对码", scoreService.submit(lessonNo));
    }

    /** 3.2 成绩登记表（含核对码，用于打印） */
    @GetMapping("/classes/{lessonNo}/sheet")
    public ApiResponse<Map<String, Object>> sheet(@PathVariable String lessonNo) {
        return ApiResponse.ok(scoreService.sheet(lessonNo));
    }

    /** 3.3 学院待核对列表 */
    @GetMapping("/audit/pending")
    public ApiResponse<List<Map<String, Object>>> auditPending(
            @RequestParam(required = false) String term) {
        return ApiResponse.ok(scoreService.auditPending(term));
    }

    /** 3.3 输码核对：1 通过 / 2 退回 */
    @PostMapping("/classes/{lessonNo}/audit")
    public ApiResponse<Map<String, Object>> audit(
            @PathVariable String lessonNo, @RequestBody Map<String, Object> body) {
        String code = body.get("verify_code") == null ? null : String.valueOf(body.get("verify_code"));
        int result = Integer.parseInt(String.valueOf(body.getOrDefault("result", 1)));
        String opinion = body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        Map<String, Object> data = scoreService.audit(lessonNo, code, result, opinion);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    /** 3.4 待发布列表 */
    @GetMapping("/publish/pending")
    public ApiResponse<List<Map<String, Object>>> publishPending(
            @RequestParam(required = false) String term) {
        return ApiResponse.ok(scoreService.publishPending(term));
    }

    /** 3.4 发布 */
    @PostMapping("/classes/{lessonNo}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String lessonNo) {
        return ApiResponse.ok("成绩已发布", scoreService.publish(lessonNo));
    }

    /** 3.5 校外交流成绩认定列表 */
    @GetMapping("/exchange")
    public ApiResponse<List<Map<String, Object>>> exchange(
            @RequestParam(required = false) String student_id,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scoreService.listExchange(student_id, keyword));
    }

    /** 3.5 校外交流成绩换算与认定 */
    @PostMapping("/exchange")
    public ApiResponse<Map<String, Object>> createExchange(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("校外成绩已登记", scoreService.createExchange(body));
    }

    /** 3.5 学院审核校外交流成绩认定 */
    @PostMapping("/exchange/{convertId}/college-audit")
    public ApiResponse<Map<String, Object>> collegeAuditExchange(
            @PathVariable long convertId, @RequestBody Map<String, Object> body) {
        int decision = Integer.parseInt(String.valueOf(body.getOrDefault("decision", 1)));
        String opinion = body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        Map<String, Object> data = scoreService.collegeAuditExchange(convertId, decision, opinion);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    /** 3.5 教务处审批校外交流成绩认定 */
    @PostMapping("/exchange/{convertId}/school-approve")
    public ApiResponse<Map<String, Object>> schoolApproveExchange(
            @PathVariable long convertId, @RequestBody Map<String, Object> body) {
        int decision = Integer.parseInt(String.valueOf(body.getOrDefault("decision", 1)));
        String opinion = body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        Map<String, Object> data = scoreService.schoolApproveExchange(convertId, decision, opinion);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }
}
