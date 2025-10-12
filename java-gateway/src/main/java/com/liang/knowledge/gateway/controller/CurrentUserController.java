package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.auth.UserDTO;
import com.liang.knowledge.gateway.user.UserCreditGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 当前登录用户信息接口，身份从 JWT 中获取。
 */
@RestController
public class CurrentUserController {
    private final UserCreditGateway userCreditGateway;

    public CurrentUserController(UserCreditGateway userCreditGateway) {
        this.userCreditGateway = userCreditGateway;
    }

    @GetMapping("/api/user/me")
    public Map<String, Object> getCurrentUser() {
        UserDTO user = SecurityContext.get();
        SecurityContext.requireUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("tenantId", user.getTenantId());
        data.put("credits", userCreditGateway.getCredits(user.getUserId()));
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("data", data);
        return response;
    }

    @GetMapping("/api/user/credits")
    public Map<String, Object> getCredits() {
        Long userId = SecurityContext.requireUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("credits", userCreditGateway.getCredits(userId));
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("data", data);
        return response;
    }
}
