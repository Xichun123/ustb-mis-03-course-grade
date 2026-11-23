package com.ustb.coursegrade.common.auth;

import com.ustb.coursegrade.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示账号切换与当前身份查询接口（全组共用，与初始化测试数据完全对应）
 */
@RestController
@RequestMapping("/api/v1/auth")
public class DemoAuthController {

    private static final Map<String, CurrentUser> DEMO_ACCOUNTS = new LinkedHashMap<>();

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
                "stu_unrated", "林思远（本科·未评教演示）", RoleEnum.STUDENT,
                "U202440002", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_retake", new CurrentUser(
                "stu_retake", "刘浩然（本科·重修生）", RoleEnum.STUDENT,
                "U202340004", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_grad", new CurrentUser(
                "stu_grad", "许嘉豪（研究生·管科硕261）", RoleEnum.STUDENT,
                "M202610001", null, null, "0001", null));
        DEMO_ACCOUNTS.put("stu_exchange", new CurrentUser(
                "stu_exchange", "Alex Mueller（来校交换生）", RoleEnum.STUDENT,
                "EX20260001", null, null, "0001", null));
        DEMO_ACCOUNTS.put("grad_school", new CurrentUser(
                "grad_school", "孙梅（研究生院审核）", RoleEnum.GRADUATE_SCHOOL,
                null, "G000000001", null, null, "ORG901"));
        DEMO_ACCOUNTS.put("sys_admin", new CurrentUser(
                "sys_admin", "系统管理员", RoleEnum.SYS_ADMIN,
                null, "S000000001", null, null, "ORG999"));
    }

    public static CurrentUser findAccount(String key) {
        return DEMO_ACCOUNTS.get(key);
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
