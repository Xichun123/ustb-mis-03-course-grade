package com.ustb.coursegrade.common.auth;

/**
 * 本组涉及的 7 类业务角色（语义定义，后续可与 DBA t_user.role_code 映射）
 */
public enum RoleEnum {
    STUDENT("学生"),
    TEACHER("任课教师"),
    COLLEGE_SECRETARY("学院教学秘书"),
    ACADEMIC_OFFICE("教务处经办"),
    ACADEMIC_DIRECTOR("教务处处长"),
    GRADUATE_SCHOOL("研究生院"),
    SYS_ADMIN("系统管理员");

    private final String label;

    RoleEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
