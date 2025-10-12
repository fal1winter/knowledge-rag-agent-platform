package com.liang.knowledge.gateway.rag;

import lombok.Data;

import java.util.List;

@Data
public class RagChatRequest {
    private String tenantId;
    private Long userId;
    private String message;
    private String sessionId;
    private List<String> materialIds;
    private boolean forceAgentic;
}
