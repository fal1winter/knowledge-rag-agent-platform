package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.rag.ChatMessage;
import com.liang.knowledge.gateway.rag.ChatSession;
import com.liang.knowledge.gateway.rag.ChatSessionRepository;
import com.liang.knowledge.gateway.auth.UnauthorizedException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理接口。
 * 提供会话列表、历史消息查询、会话删除能力。
 */
@RestController
@RequestMapping("/api/gateway/sessions")
public class RagSessionController {
    private final ChatSessionRepository sessionRepository;

    public RagSessionController(ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 获取当前用户的会话列表，按最近活跃时间倒序。
     */
    @GetMapping
    public Map<String, Object> listSessions(
            @RequestParam(defaultValue = "20") int limit
    ) {
        Long userId = SecurityContext.requireUserId();
        List<ChatSession> sessions = sessionRepository.findByUserId(userId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", sessions);
        return result;
    }

    /**
     * 获取指定会话的消息历史。
     */
    @GetMapping("/{sessionId}/messages")
    public Map<String, Object> getMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        Long userId = SecurityContext.requireUserId();
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new UnauthorizedException("无权访问该会话");
        }
        List<ChatMessage> messages = sessionRepository.getMessages(sessionId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", messages);
        return result;
    }

    /**
     * 删除指定会话及其所有消息。
     */
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        Long userId = SecurityContext.requireUserId();
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new UnauthorizedException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new UnauthorizedException("无权删除该会话");
        }
        sessionRepository.deleteSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "已删除");
        return result;
    }
}
