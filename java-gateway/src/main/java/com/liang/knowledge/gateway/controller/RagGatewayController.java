package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.auth.MaterialAccessChecker;
import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.auth.UnauthorizedException;
import com.liang.knowledge.gateway.rag.RagChatRequest;
import com.liang.knowledge.gateway.rag.RagGatewayClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 对话网关入口。
 * 从 SecurityContext 注入 tenantId / userId，忽略客户端传入值；
 * 若请求指定了 materialIds，校验当前用户是否拥有访问权限。
 */
@RestController
@RequestMapping("/api/gateway/rag")
public class RagGatewayController {
    private final RagGatewayClient ragGatewayClient;
    private final MaterialAccessChecker materialAccessChecker;

    public RagGatewayController(RagGatewayClient ragGatewayClient, MaterialAccessChecker materialAccessChecker) {
        this.ragGatewayClient = ragGatewayClient;
        this.materialAccessChecker = materialAccessChecker;
    }

    @PostMapping("/chat")
    public Map chat(@RequestBody RagChatRequest request) {
        Long userId = SecurityContext.requireUserId();
        String tenantId = SecurityContext.requireTenantId();

        // 覆盖客户端传入的身份信息，以网关鉴权结果为准
        request.setUserId(userId);
        request.setTenantId(tenantId);

        // 素材访问权限校验：用户必须拥有（购买/免费领取）对应素材
        List<String> materialIds = request.getMaterialIds();
        if (materialIds != null && !materialIds.isEmpty()) {
            if (!materialAccessChecker.hasAccess(userId, materialIds)) {
                throw new UnauthorizedException("无权访问指定素材，请先购买或领取");
            }
        }

        return ragGatewayClient.chat(request);
    }
}
