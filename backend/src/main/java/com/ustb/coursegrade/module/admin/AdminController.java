package com.ustb.coursegrade.module.admin;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【业务⑥ 系统维护 6.1–6.2 · 蒋作】REST 接口
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 6.1 编码列表 */
    @GetMapping("/codes")
    public ApiResponse<List<Map<String, Object>>> codes(
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(adminService.listCodes(group, keyword));
    }

    /** 6.1 新增编码 */
    @PostMapping("/codes")
    public ApiResponse<Map<String, Object>> createCode(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("编码已新增", adminService.createCode(body));
    }

    /** 6.1 修改/启停编码 */
    @PutMapping("/codes/{id}")
    public ApiResponse<Map<String, Object>> updateCode(
            @PathVariable int id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("编码已更新", adminService.updateCode(id, body));
    }

    /** 6.2 备份日志 */
    @GetMapping("/backups")
    public ApiResponse<List<Map<String, Object>>> backups() {
        return ApiResponse.ok(adminService.listBackups());
    }

    /** 6.2 登记备份 */
    @PostMapping("/backups")
    public ApiResponse<Map<String, Object>> createBackup(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("备份记录已登记", adminService.createBackup(body));
    }

    /** 6.2 恢复（先写应急快照） */
    @PostMapping("/backups/{id}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable int id) {
        return ApiResponse.ok("已生成恢复前应急快照", adminService.restore(id));
    }
}
