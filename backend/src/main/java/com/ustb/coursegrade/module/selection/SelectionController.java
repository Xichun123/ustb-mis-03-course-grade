package com.ustb.coursegrade.module.selection;

import com.ustb.coursegrade.common.ApiResponse;
import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.module.selection.SelectionCatalogProperties.CourseType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务② 学生选课（2.1–2.7）· 负责人：张文浩】
 * 接口复用公共身份与响应协议；2.5停开在编码确认前只提供预览。
 */
@RestController
@RequestMapping("/api/v1/selection")
public class SelectionController {

    private final SelectionService service;
    public SelectionController(SelectionService service){this.service=service;}
    @GetMapping("/module-info") public ApiResponse<?> info(){return ApiResponse.ok(Map.of("module","学生选课","status","运行中","features",List.of("课程查询","选课与退选","选课校验","录取与筛选","停开预览","结果与课表名单","名单调整")));}
    @GetMapping("/context") public ApiResponse<?> context(){return ApiResponse.ok(service.context());}
    @GetMapping("/courses") public ApiResponse<?> courses(@RequestParam int roundId,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(defaultValue="") String courseType){
        if(page<1 || page>100000 || pageSize<1 || pageSize>100 || keyword.length()>100)throw BizException.unprocessable("BAD_QUERY","分页或查询条件无效");
        CourseType filter;
        try {
            filter = CourseType.fromParam(courseType);
        } catch (IllegalArgumentException e) {
            throw BizException.unprocessable("BAD_QUERY", "课程类型无效");
        }
        return ApiResponse.ok(service.courses(roundId,keyword.trim(),page,pageSize,filter));
    }
    @GetMapping("/results") public ApiResponse<?> results(@RequestParam String term){return ApiResponse.ok(service.results(term(term)));}
    @GetMapping("/timetable") public ApiResponse<?> timetable(@RequestParam String term,@RequestParam(defaultValue="1") int week){if(week<1 || week>16)throw BizException.unprocessable("BAD_WEEK","教学周须为1—16");return ApiResponse.ok(service.timetable(term(term),week));}
    @PostMapping("/check") public ApiResponse<?> check(@RequestBody SelectionService.Application request){return ApiResponse.ok(service.check(request));}
    @PostMapping("/applications") public ApiResponse<?> enroll(@RequestBody SelectionService.Application request){return ApiResponse.ok(service.enroll(request));}
    @PostMapping("/records/{scId}/withdraw") public ApiResponse<?> withdraw(@PathVariable String scId,@RequestBody SelectionService.Withdrawal request){if(!scId.matches("[1-9][0-9]{0,18}"))throw BizException.unprocessable("BAD_ID","选课记录号无效");return ApiResponse.ok(service.withdraw(scId,request));}
    @GetMapping("/teaching-classes") public ApiResponse<?> classes(@RequestParam String term){return ApiResponse.ok(service.teachingClasses(term(term)));}
    @GetMapping("/teaching-classes/{tcId}/roster") public ApiResponse<?> roster(@PathVariable String tcId,@RequestParam(defaultValue="") String keyword){if(keyword.length()>100)throw BizException.unprocessable("BAD_QUERY","关键词过长");return ApiResponse.ok(service.roster(tcId,keyword.trim()));}
    @PostMapping("/teaching-classes/{tcId}/adjustments") public ApiResponse<?> adjust(@PathVariable String tcId,@RequestBody SelectionService.Adjustment request){return ApiResponse.ok(service.adjust(tcId,request));}
    @PostMapping("/rounds/{roundId}/screen") public ApiResponse<?> screen(@PathVariable int roundId){return ApiResponse.ok(service.screen(roundId));}
    @GetMapping("/stop-preview") public ApiResponse<?> stop(@RequestParam String term){return ApiResponse.ok(service.stopPreview(term(term)));}
    private String term(String term){if(term==null || !term.matches("[0-9]{4}-[0-9]{4}-[12]"))throw BizException.unprocessable("BAD_TERM","学年学期格式应为2026-2027-1");return term;}
}
