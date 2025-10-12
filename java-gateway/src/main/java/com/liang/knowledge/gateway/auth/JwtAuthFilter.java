package com.liang.knowledge.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * JWT 鉴权过滤器。
 * 从 Authorization: Bearer xxx 中解析用户信息，写入 SecurityContext。
 * 白名单路径（健康检查、支付回调等）直接放行。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${auth.jwt.secret:default-hmac-secret-change-in-production}")
    private String jwtSecret;

    private static final String[] WHITELIST = {
            "/health",
            "/api/payments/alipay/notify",
            "/api/payments/wechat/notify",
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (isWhitelisted(path)) {
                chain.doFilter(request, response);
                return;
            }

            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                sendUnauthorized(response, "缺少 Authorization 头");
                return;
            }

            String token = header.substring(BEARER_PREFIX.length()).trim();
            UserDTO user = parseToken(token);
            if (user == null) {
                sendUnauthorized(response, "Token 无效或已过期");
                return;
            }

            SecurityContext.set(user);
            chain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private UserDTO parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            // 验证签名
            String signInput = parts[0] + "." + parts[1];
            String expectedSig = hmacSha256(signInput);
            if (!constantTimeEquals(expectedSig, parts[2])) {
                return null;
            }
            // 解析 payload
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = MAPPER.readValue(payloadBytes, Map.class);

            // 检查过期
            Object expObj = payload.get("exp");
            if (expObj instanceof Number) {
                long exp = ((Number) expObj).longValue();
                if (System.currentTimeMillis() / 1000 > exp) {
                    return null;
                }
            }

            UserDTO user = new UserDTO();
            user.setUserId(toLong(payload.get("userId")));
            user.setTenantId(toString(payload.get("tenantId")));
            user.setUsername(toString(payload.get("username")));
            user.setRoles(toString(payload.get("roles")));
            return user;
        } catch (Exception e) {
            return null;
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private boolean isWhitelisted(String path) {
        for (String prefix : WHITELIST) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
