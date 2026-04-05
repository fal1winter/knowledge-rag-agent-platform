package com.liang.knowledge.gateway.ratelimit;

import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.auth.UserDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 限流拦截器，在 Controller 执行前检查用户是否超出请求配额。
 * <p>
 * 拦截 /api/gateway/rag/** 路径，其他路径（会话列表、支付等）不限流。
 * 触发限流时返回 HTTP 429 并携带 Retry-After 和 X-RateLimit-Remaining 头。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        // 仅对 RAG 对话接口限流
        if (!path.startsWith("/api/gateway/rag/")) {
            return true;
        }

        UserDTO user = SecurityContext.get();
        if (user == null) {
            // 未认证请求由 JwtAuthFilter 拦截，此处放行
            return true;
        }

        String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";
        Long userId = user.getUserId();

        if (!rateLimiter.tryAcquire(tenantId, userId)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            response.setHeader("X-RateLimit-Remaining", "0");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }

        int remaining = rateLimiter.remaining(tenantId, userId);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        return true;
    }
}
