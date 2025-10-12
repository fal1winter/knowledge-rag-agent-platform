package com.liang.knowledge.gateway.auth;

/**
 * 线程级安全上下文，持有当前请求的登录用户信息。
 */
public final class SecurityContext {
    private static final ThreadLocal<UserDTO> HOLDER = new ThreadLocal<>();

    private SecurityContext() {}

    public static void set(UserDTO user) {
        HOLDER.set(user);
    }

    public static UserDTO get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long requireUserId() {
        UserDTO user = get();
        if (user == null || user.getUserId() == null) {
            throw new UnauthorizedException("未登录或 Token 已过期");
        }
        return user.getUserId();
    }

    public static String requireTenantId() {
        UserDTO user = get();
        if (user == null || user.getTenantId() == null) {
            throw new UnauthorizedException("未登录或租户信息缺失");
        }
        return user.getTenantId();
    }
}
