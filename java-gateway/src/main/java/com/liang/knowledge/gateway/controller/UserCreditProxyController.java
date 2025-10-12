package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.auth.UnauthorizedException;
import com.liang.knowledge.gateway.user.UserCreditGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户积分代理接口。仅允许查询自身积分或 ADMIN 角色查询他人。
 */
@RestController
@RequestMapping("/api/gateway/users")
public class UserCreditProxyController {
    private final UserCreditGateway userCreditGateway;

    public UserCreditProxyController(UserCreditGateway userCreditGateway) {
        this.userCreditGateway = userCreditGateway;
    }

    @GetMapping("/{userId}/credits")
    public Map<String, Object> getCredits(@PathVariable Long userId) {
        Long currentUserId = SecurityContext.requireUserId();
        // 只允许查自己的积分，或 ADMIN 查任意用户
        if (!currentUserId.equals(userId)) {
            String roles = SecurityContext.get().getRoles();
            if (roles == null || !roles.contains("ADMIN")) {
                throw new UnauthorizedException("无权查看他人积分");
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("credits", userCreditGateway.getCredits(userId));
        return result;
    }
}
