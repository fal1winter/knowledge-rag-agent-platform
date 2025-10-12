package com.liang.knowledge.gateway.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * RAG Python 服务调用客户端。
 * 携带内部网关密钥以通过 Python 端信任校验。
 */
@Component
public class RagGatewayClient {
    private final RestTemplate restTemplate;
    private final String ragBaseUrl;
    private final String gatewaySecret;

    public RagGatewayClient(
            RestTemplate restTemplate,
            @Value("${rag.python-api-base-url}") String ragBaseUrl,
            @Value("${auth.gateway-internal-secret:}") String gatewaySecret
    ) {
        this.restTemplate = restTemplate;
        this.ragBaseUrl = ragBaseUrl;
        this.gatewaySecret = gatewaySecret;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(RagChatRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("tenant_id", request.getTenantId());
        body.put("user_id", String.valueOf(request.getUserId()));
        body.put("message", request.getMessage());
        body.put("session_id", request.getSessionId());
        body.put("material_ids", request.getMaterialIds());
        body.put("force_agentic", request.isForceAgentic());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (gatewaySecret != null && !gatewaySecret.isEmpty()) {
            headers.set("X-Gateway-Secret", gatewaySecret);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(ragBaseUrl + "/api/chat", entity, Map.class);
    }
}
