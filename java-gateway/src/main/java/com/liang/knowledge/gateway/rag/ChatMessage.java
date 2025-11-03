package com.liang.knowledge.gateway.rag;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单条对话消息记录。
 */
@Data
@Builder
public class ChatMessage {
    private String messageId;
    private String sessionId;
    private String role;
    private String content;
    private RagChatResponse.RouteInfo route;
    private LocalDateTime createdAt;
}
