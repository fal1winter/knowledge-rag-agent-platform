package com.liang.knowledge.gateway.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 素材访问权限校验器。
 * 调用素材订单服务验证用户是否已购买/领取指定素材。
 */
@Component
public class MaterialAccessChecker {
    private final RestTemplate restTemplate;
    private final String orderEndpoint;

    public MaterialAccessChecker(
            RestTemplate restTemplate,
            @Value("${material-service.order-endpoint}") String orderEndpoint
    ) {
        this.restTemplate = restTemplate;
        this.orderEndpoint = orderEndpoint.endsWith("/") ? orderEndpoint.substring(0, orderEndpoint.length() - 1) : orderEndpoint;
    }

    /**
     * 校验用户是否拥有给定素材列表的访问权限。
     * 调用订单服务 GET /api/orders/check?userId={userId}&materialIds={ids}
     * 返回 true 表示全部可访问。
     */
    @SuppressWarnings("unchecked")
    public boolean hasAccess(Long userId, List<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return true;
        }
        try {
            String ids = String.join(",", materialIds);
            String url = orderEndpoint + "/orders/check?userId=" + userId + "&materialIds=" + ids;
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result == null) {
                return false;
            }
            Object accessible = result.get("accessible");
            return Boolean.TRUE.equals(accessible);
        } catch (Exception e) {
            // 订单服务不可用时拒绝访问，安全优先
            return false;
        }
    }
}
