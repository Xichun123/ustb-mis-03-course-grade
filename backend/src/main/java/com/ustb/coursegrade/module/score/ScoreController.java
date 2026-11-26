package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.ApiResponse;
import com.ustb.coursegrade.common.BizException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    /** 3.1 我的课堂（本学期在前、往届在后，自动排除已停开教学班） */
    @GetMapping("/my-classes")
    public ApiResponse<List<Map<String, Object>>> myClasses(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scoreService.myClasses(term, keyword));
    }

    /** 3.1 已停开课程只读清单（不录入成绩、不计入教师工作量） */
    @GetMapping("/stopped-classes")
    public ApiResponse<List<Map<String, Object>>> stoppedClasses(
            @RequestParam(required = false) String term,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scoreService.stoppedClasses(term, keyword));
    }

    /** 3.1 课堂名单与已录成绩 */
    @GetMapping("/classes/{lessonNo}")
    public ApiResponse<Map<String, Object>> roster(@PathVariable String lessonNo) {
        return ApiResponse.ok(scoreService.roster(lessonNo));
    }

    /** 3.1 保存成绩草稿；body 里带 usual_ratio 时对整个课堂生效 */
    @PutMapping("/classes/{lessonNo}/grades")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> saveGrades(
            @PathVariable String lessonNo, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.getOrDefault("items", List.of());
        Integer ratio = parseRatio(body.get("usual_ratio"), false);
        return ApiResponse.ok("成绩已保存", scoreService.saveGrades(lessonNo, items, ratio));
    }

    /** 3.1 课堂级平时占比：一次设置，对全课堂生效 */
    @PutMapping("/classes/{lessonNo}/ratio")
    public ApiResponse<Map<String, Object>> setClassRatio(
            @PathVariable String lessonNo, @RequestBody Map<String, Object> body) {
        int ratio = parseRatio(body.get("usual_ratio"), true);
        Map<String, Object> data = scoreService.setClassRatio(lessonNo, ratio);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    /** 3.1 Excel/CSV 批量导入成绩：上传表格直接识别并录入 */
    @PostMapping("/classes/{lessonNo}/grades/import")
    public ApiResponse<Map<String, Object>> importGrades(
            @PathVariable String lessonNo,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "usual_ratio", required = false) String usualRatio,
            @RequestParam(value = "dry_run", defaultValue = "false") boolean dryRun) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error("EMPTY_FILE", "请选择要上传的 Excel 文件");
        }
        Integer ratio = parseRatio(usualRatio, false);
        Map<String, Object> data = scoreService.importGrades(
                lessonNo, file.getInputStream(), file.getOriginalFilename(), ratio, dryRun);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    /** 3.1 下载本课堂 Excel 成绩录入模板（.xlsx，预填学号与姓名，表内无需填占比） */
    @GetMapping("/classes/{lessonNo}/template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String lessonNo) {
        byte[] xlsx = scoreService.exportTemplate(lessonNo);
        String safeLessonNo = lessonNo.replaceAll("[^A-Za-z0-9_-]", "_");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("grades_" + safeLessonNo + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }

    /** 兼容统一字典 DECIMAL(5,2) 格式（如 "30.00"），同时要求平时占比必须为 0~100 的整数 */
    static Integer parseRatio(Object raw, boolean required) {
        if (raw == null) {
            if (required) {
                throw BizException.unprocessable("INVALID_RATIO", "请输入课堂平时占比（0~100 整数）");
            }
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            if (required) {
                throw BizException.unprocessable("INVALID_RATIO", "请输入课堂平时占比（0~100 整数）");
            }
            return null;
        }
        try {
            BigDecimal bd = new BigDecimal(text).stripTrailingZeros();
            if (bd.scale() > 0) {
                throw BizException.unprocessable("INVALID_RATIO", "平时占比须为 0~100 之间的整数，不支持小数：" + text);
            }
            int value = bd.intValueExact();
            if (value < 0 || value > 100) {
                throw BizException.unprocessable("INVALID_RATIO", "平时占比须在 0~100 之间");
            }
            return value;
        } catch (BizException e) {
            throw e;
        } catch (ArithmeticException | NumberFormatException e) {
            throw BizException.unprocessable("INVALID_RATIO", "平时占比格式无效：" + text);
        }
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
        Map<String, Object> data = scoreService.publish(lessonNo);
        int finalized = data.get("finalized_corrections") instanceof Number n ? n.intValue() : 0;
        String msg = finalized > 0
                ? "成绩已发布，同步完结 " + finalized + " 条成绩更正并生效"
                : "成绩已发布";
        return ApiResponse.ok(msg, data);
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

    /** 3.5 教务处直接认定外校成绩（成绩单交回教务处后一步生效） */
    @PostMapping("/exchange/{convertId}/recognize")
    public ApiResponse<Map<String, Object>> recognizeExchange(
            @PathVariable long convertId, @RequestBody(required = false) Map<String, Object> body) {
        String opinion = body == null || body.get("opinion") == null ? null : String.valueOf(body.get("opinion"));
        Map<String, Object> data = scoreService.recognizeExchange(convertId, opinion);
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    /** 3.5 认定候选课程：按课程名检索校内课程，标明是否必修 */
    @GetMapping("/exchange/candidates")
    public ApiResponse<List<Map<String, Object>>> exchangeCandidates(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(scoreService.listCourseCandidates(keyword));
    }
}
