package com.ustb.coursegrade.common.auth;

/**
 * 当前登录用户上下文（GetCurrentUser 抽象）
 */
public record CurrentUser(
        String userId,
        String displayName,
        RoleEnum role,
        String studentId,
        String empId,
        String teacherCode8,
        String collegeId,
        String orgId
) {
    public boolean hasRole(RoleEnum... allowed) {
        for (RoleEnum r : allowed) {
            if (this.role == r) {
                return true;
            }
        }
        return false;
    }
}
