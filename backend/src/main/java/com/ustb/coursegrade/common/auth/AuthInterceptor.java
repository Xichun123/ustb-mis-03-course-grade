package com.ustb.coursegrade.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一身份解析拦截器：支持 Authorization Bearer 令牌与 X-Demo-User 头
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String DEMO_USER_HEADER = "X-Demo-User";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userKey = null;
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && auth.length() > 7) {
            userKey = auth.substring(7).trim();
        }
        if (userKey == null || userKey.isBlank()) {
            userKey = request.getHeader(DEMO_USER_HEADER);
        }
        if (userKey == null || userKey.isBlank()) {
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
