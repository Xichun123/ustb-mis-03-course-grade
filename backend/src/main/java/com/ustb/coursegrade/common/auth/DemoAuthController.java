package com.ustb.coursegrade.common.auth;

import com.ustb.coursegrade.common.ApiResponse;
import com.ustb.coursegrade.common.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一身份认证与当前会话接口（兼容真实学号/工号登录与内部单测头）
 */
@RestController
@RequestMapping("/api/v1/auth")
public class DemoAuthController {

    private static final Map<String, CurrentUser> DEMO_ACCOUNTS = new LinkedHashMap<>();
    private static final Map<String, String> LOGIN_ALIASES = Map.ofEntries(
            Map.entry("user1", "college_0001"),
            Map.entry("user2", "office_admin"),
            Map.entry("user3", "office_director"),
            Map.entry("user4", "teacher_wang"),
            Map.entry("user5", "teacher_li"),
            Map.entry("user6", "stu_undergrad"),
            Map.entry("user7", "stu_unrated"),
            Map.entry("user8", "stu_grad"),
            Map.entry("user9", "sys_admin"),
            Map.entry("user10", "stu_retake"),
            Map.entry("user11", "stu_exchange"),
            Map.entry("user12", "college_0002"),
            Map.entry("user13", "grad_school")
    );
    private static final Set<String> VALID_PASSWORDS = Set.of("1", "123456", "ustb2026", "admin123");

    static {
        DEMO_ACCOUNTS.put("office_admin", new CurrentUser(
                "office_admin", "林建华（教务处经办）", RoleEnum.ACADEMIC_OFFICE,
                null, "A000000001", null, null, "ORG900"));
        DEMO_ACCOUNTS.put("office_director", new CurrentUser(
                "office_director", "吴海峰（教务处处长）", RoleEnum.ACADEMIC_DIRECTOR,
                null, "A000000002", null, null, "ORG900"));
        DEMO_ACCOUNTS.put("college_0001", new CurrentUser(
                "college_0001", "周敏（经管学院教学秘书）", RoleEnum.COLLEGE_SECRETARY,
                null, "E000010001", null, "0001", "ORG001"));
        DEMO_ACCOUNTS.put("college_0002", new CurrentUser(
                "college_0002", "赵刚（计通学院教学秘书）", RoleEnum.COLLEGE_SECRETARY,
                null, "E000020001", null, "0002", "ORG002"));
        DEMO_ACCOUNTS.put("teacher_wang", new CurrentUser(
                "teacher_wang", "王明远（任课教师）", RoleEnum.TEACHER,
                null, "T000010001", "T0010001", "0001", "ORG001"));
        DEMO_ACCOUNTS.put("teacher_li", new CurrentUser(
                "teacher_li", "李晓静（任课教师）", RoleEnum.TEACHER,
                null, "T000010002", "T0010002", "0001", "ORG001"));
        DEMO_ACCOUNTS.put("stu_undergrad", new CurrentUser(
                "stu_undergrad", "张晨曦（本科·信管241）", RoleEnum.STUDENT,
                "U202440001", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_unrated", new CurrentUser(
                "stu_unrated", "林思远（本科·信管241）", RoleEnum.STUDENT,
                "U202440002", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_retake", new CurrentUser(
                "stu_retake", "刘浩然（本科·信管231）", RoleEnum.STUDENT,
                "U202340004", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_grad", new CurrentUser(
                "stu_grad", "许嘉豪（研究生·管科硕261）", RoleEnum.STUDENT,
                "M202610001", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_exchange", new CurrentUser(
                "stu_exchange", "Alex Mueller（来校交换生）", RoleEnum.STUDENT,
                "EX20260001", null, null, "0001", null));
        DEMO_ACCOUNTS.put("grad_school", new CurrentUser(
                "grad_school", "孙梅（研究生院）", RoleEnum.GRADUATE_SCHOOL,
                null, "G000000001", null, null, "ORG901"));
        DEMO_ACCOUNTS.put("sys_admin", new CurrentUser(
                "sys_admin", "系统管理员", RoleEnum.SYS_ADMIN,
                null, "S000000001", null, null, "ORG999"));
    }

    public static CurrentUser findAccount(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String q = key.trim();
        CurrentUser direct = DEMO_ACCOUNTS.get(q);
        if (direct != null) {
            return direct;
        }
        String aliasTarget = LOGIN_ALIASES.get(q.toLowerCase());
        if (aliasTarget != null && DEMO_ACCOUNTS.containsKey(aliasTarget)) {
            return DEMO_ACCOUNTS.get(aliasTarget);
        }
        for (CurrentUser u : DEMO_ACCOUNTS.values()) {
            if (q.equalsIgnoreCase(u.studentId())
                    || q.equalsIgnoreCase(u.empId())
                    || q.equalsIgnoreCase(u.teacherCode8())) {
                return u;
            }
        }
        return null;
    }

    /** 账号密码登录（支持学号、教职工号或账号名） */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw BizException.unprocessable("INVALID_CREDENTIALS", "请输入用户名与密码");
        }
        CurrentUser user = findAccount(username);
        if (user == null || !VALID_PASSWORDS.contains(password.trim())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", user.userId());
        data.put("user", user);
        return ApiResponse.ok("登录成功", data);
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.ok("已退出登录", Map.of("logged_out", true));
    }

    @GetMapping("/demo-accounts")
    public ApiResponse<List<CurrentUser>> listDemoAccounts() {
        return ApiResponse.ok(List.copyOf(DEMO_ACCOUNTS.values()));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> currentUser() {
        return ApiResponse.ok(UserContext.requireUser());
    }
}
