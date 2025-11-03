package com.liang.knowledge.gateway.rag;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 会话与消息存储。
 * 当前使用内存实现；生产环境替换为 Redis 或 MongoDB 持久化。
 * <p>
 * 生产替换方案：
 * - Redis: 使用 Hash 存会话元数据，List 存消息序列，设置 TTL 自动过期
 * - MongoDB: sessions 集合 + messages 集合，按 userId + tenantId 建索引
 */
@Repository
public class ChatSessionRepository {
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();

    public ChatSession createSession(Long userId, String tenantId, String title) {
        ChatSession session = ChatSession.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .tenantId(tenantId)
                .title(title)
                .createdAt(LocalDateTime.now())
                .lastActiveAt(LocalDateTime.now())
                .messageCount(0)
                .build();
        sessions.put(session.getSessionId(), session);
        messages.put(session.getSessionId(), Collections.synchronizedList(new ArrayList<>()));
        return session;
    }

    public Optional<ChatSession> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public List<ChatSession> findByUserId(Long userId, int limit) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .sorted(Comparator.comparing(ChatSession::getLastActiveAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void appendMessage(String sessionId, ChatMessage message) {
        List<ChatMessage> msgList = messages.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        msgList.add(message);
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastActiveAt(LocalDateTime.now());
            session.setMessageCount(msgList.size());
            // 用第一条用户消息作为标题
            if (session.getTitle() == null || session.getTitle().isEmpty()) {
                if ("user".equals(message.getRole())) {
                    String title = message.getContent();
                    session.setTitle(title.length() > 50 ? title.substring(0, 50) + "..." : title);
                }
            }
        }
    }

    public List<ChatMessage> getMessages(String sessionId, int limit) {
        List<ChatMessage> msgList = messages.getOrDefault(sessionId, Collections.emptyList());
        if (msgList.size() <= limit) {
            return new ArrayList<>(msgList);
        }
        return new ArrayList<>(msgList.subList(msgList.size() - limit, msgList.size()));
    }

    public boolean deleteSession(String sessionId) {
        ChatSession removed = sessions.remove(sessionId);
        messages.remove(sessionId);
        return removed != null;
    }
}
