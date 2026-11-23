package com.ustb.coursegrade.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 演示与开发阶段身份解析拦截器：
 * 通过请求头 X-Demo-User 解析预置的测试账号，后续接入 DBA t_user 鉴权只需替换此拦截器，
 * 所有业务模块的 UserContext.requireUser() 调用代码零修改。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String DEMO_USER_HEADER = "X-Demo-User";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userKey = request.getHeader(DEMO_USER_HEADER);
        if (userKey == null || userKey.isBlank()) {
            // 默认以教务处经办身份方便浏览器直测 GET 接口；前端会显式携带所选账号
            UserContext.set(DemoAuthController.findAccount("office_admin"));
            return true;
        }
        CurrentUser user = DemoAuthController.findAccount(userKey.trim());
        if (user != null) {
            UserContext.set(user);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
