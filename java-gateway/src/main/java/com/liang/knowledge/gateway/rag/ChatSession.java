package com.liang.knowledge.gateway.rag;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话元数据。
 */
@Data
@Builder
public class ChatSession {
    private String sessionId;
    private Long userId;
    private String tenantId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private int messageCount;
}
