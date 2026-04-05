package com.liang.knowledge.gateway.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 会话与消息存储。
 * 当前使用内存实现；生产环境替换为 Redis 或 MongoDB 持久化。
 * <p>
 * 设计要点：
 * - ConcurrentHashMap 保证并发安全
 * - 定时清理过期会话，防止内存无限增长
 * - 单会话消息数上限保护，避免长会话占用过多内存
 * <p>
 * 生产替换方案：
 * - Redis: 使用 Hash 存会话元数据，List 存消息序列，设置 TTL 自动过期
 * - MongoDB: sessions 集合 + messages 集合，按 userId + tenantId 建索引
 */
@Repository
public class ChatSessionRepository {
    private static final Logger log = LoggerFactory.getLogger(ChatSessionRepository.class);

    /** 会话过期时间：超过此时长未活跃的会话将被自动清理 */
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    /** 单会话最大消息数，超出后截断最早的消息 */
    private static final int MAX_MESSAGES_PER_SESSION = 200;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);

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

    /**
     * 追加消息到会话，并更新会话活跃时间。
     * 当消息数超过上限时，截断最早的消息（滑动窗口语义）。
     */
    public void appendMessage(String sessionId, ChatMessage message) {
        List<ChatMessage> msgList = messages.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()));
        msgList.add(message);
        totalMessagesProcessed.incrementAndGet();

        // 滑动窗口：超出上限时移除最早的消息
        if (msgList.size() > MAX_MESSAGES_PER_SESSION) {
            synchronized (msgList) {
                int excess = msgList.size() - MAX_MESSAGES_PER_SESSION;
                if (excess > 0) {
                    msgList.subList(0, excess).clear();
                }
            }
        }

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

    /**
     * 定时清理过期会话（每 10 分钟执行一次）。
     * 超过 SESSION_TTL 未活跃的会话自动回收，释放内存。
     */
    @Scheduled(fixedDelay = 600_000)
    public void evictExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minus(SESSION_TTL);
        int evicted = 0;
        Iterator<Map.Entry<String, ChatSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ChatSession> entry = it.next();
            if (entry.getValue().getLastActiveAt().isBefore(cutoff)) {
                it.remove();
                messages.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("会话过期清理: 回收 {} 个不活跃会话, 当前存活 {}", evicted, sessions.size());
        }
    }

    // --- 监控指标 ---

    /** 当前存活会话数 */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /** 累计处理消息总数 */
    public long getTotalMessagesProcessed() {
        return totalMessagesProcessed.get();
    }

    /** 当前内存中消息总条数 */
    public long getCurrentMessageCount() {
        return messages.values().stream().mapToLong(List::size).sum();
    }
}
