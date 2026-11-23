package com.ustb.coursegrade.common.auth;

import com.ustb.coursegrade.common.BizException;

/**
 * 线程级登录身份持有器：所有业务模块统一通过 UserContext.requireUser() 获取操作人，
 * 严禁信任前端请求体传入的学号/工号。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser requireUser() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw BizException.unauthorized("未登录或会话无效，请先选择演示账号登录");
        }
        return u;
    }

    public static CurrentUser requireRole(RoleEnum... roles) {
        CurrentUser u = requireUser();
        if (!u.hasRole(roles)) {
            throw BizException.forbidden("当前角色【" + u.role().getLabel() + "】无权执行该操作");
        }
        return u;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
