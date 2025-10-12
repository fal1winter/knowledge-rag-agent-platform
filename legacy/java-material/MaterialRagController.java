package com.liang.bbs.rest.controller;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.material.facade.service.MaterialService;
import com.liang.bbs.rest.config.login.NoNeedLogin;
import com.liang.bbs.rest.dto.agent.AgentChatRequest;
import com.liang.bbs.rest.dto.agent.AgentChatResponse;
import com.liang.bbs.rest.dto.agent.AgentConversation;
import com.liang.bbs.rest.service.agent.LangChainAgentService;
import com.liang.bbs.user.facade.utils.UserContextUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资料 RAG 对话 Controller
 * 路由：/bbs/material/rag/{materialId}/...
 */
@RestController
@RequestMapping("/bbs/material/rag")
@Api(tags = "资料RAG对话")
@Slf4j
public class MaterialRagController {

    @Autowired
    private LangChainAgentService agentService;

    @Reference
    private MaterialService materialService;

    // ----------------------------------------------------------------
    // 权限检查
    // ----------------------------------------------------------------

    private Integer currentUserId() {
        try {
            return UserContextUtils.currentUser().getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验用户是否有权访问该资料（已购买或免费或本人上传） */
    private boolean hasAccess(Long materialId, Long userId) {
        try {
            return materialService.checkAccess(materialId, userId);
        } catch (Exception e) {
            log.warn("checkAccess error materialId={} userId={}: {}", materialId, userId, e.getMessage());
            return false;
        }
    }

    private String currentAnonymousId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String anonymousId = request.getHeader("X-Anonymous-Id");
        if (anonymousId == null || anonymousId.trim().isEmpty()) {
            return null;
        }
        return anonymousId.trim();
    }

    // ----------------------------------------------------------------
    // 会话管理
    // ----------------------------------------------------------------

    @NoNeedLogin
    @GetMapping("/{materialId}/sessions")
    @ApiOperation("获取该资料的会话列表")
    public ResponseResult<List<Map<String, Object>>> getSessions(@PathVariable Long materialId,
                                                                 HttpServletRequest request) {
        Integer userId = currentUserId();
        List<AgentConversation> sessions;
        if (userId == null) {
            if (materialId > 0) {
                return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
            }
            sessions = agentService.getMaterialSessions(currentAnonymousId(request), materialId);
        } else {
            sessions = agentService.getMaterialSessions(userId, materialId);
        }
        List<Map<String, Object>> result = sessions.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", s.getId());
            m.put("title", s.getTitle());
            m.put("materialId", s.getMaterialId());
            m.put("createTime", s.getCreateTime());
            m.put("updateTime", s.getUpdateTime());
            m.put("messageCount", s.getMessages() == null ? 0 : s.getMessages().size());
            return m;
        }).collect(Collectors.toList());

        return ResponseResult.success(result);
    }

    @NoNeedLogin
    @DeleteMapping("/{materialId}/sessions/{sessionId}")
    @ApiOperation("删除该资料下的某个会话")
    public ResponseResult<Boolean> deleteSession(
            @PathVariable Long materialId,
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Integer userId = currentUserId();
        if (userId == null) {
            if (materialId > 0) {
                return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
            }
            String anonymousId = currentAnonymousId(request);
            AgentConversation conv = agentService.getSession(sessionId);
            boolean ok = conv != null && materialId.equals(conv.getMaterialId())
                    && anonymousId != null
                    && anonymousId.equals(conv.getAnonymousId());
            if (ok) {
                ok = agentService.deleteSession(sessionId, anonymousId);
            }
            return ResponseResult.success(ok);
        }
        boolean ok = agentService.deleteSession(sessionId, userId);
        return ResponseResult.success(ok);
    }

    // ----------------------------------------------------------------
    // 历史消息
    // ----------------------------------------------------------------

    @NoNeedLogin
    @GetMapping("/{materialId}/history")
    @ApiOperation("获取会话历史消息")
    public ResponseResult<List<AgentConversation.AgentMessage>> getHistory(
            @PathVariable Long materialId,
            @RequestParam String sessionId,
            HttpServletRequest request) {
        Integer userId = currentUserId();
        AgentConversation conv = agentService.getSession(sessionId);
        if (conv == null || !materialId.equals(conv.getMaterialId())) {
            return ResponseResult.build(ResponseCode.ERROR, null);
        }
        if (userId == null) {
            if (materialId > 0) {
                return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
            }
            String anonymousId = currentAnonymousId(request);
            if (anonymousId == null || !anonymousId.equals(conv.getAnonymousId())) {
                return ResponseResult.build(ResponseCode.ERROR, null);
            }
            return ResponseResult.success(conv.getMessages());
        }
        if (!userId.equals(conv.getUserId())) {
            return ResponseResult.build(ResponseCode.ERROR, null);
        }
        return ResponseResult.success(conv.getMessages());
    }

    // ----------------------------------------------------------------
    // 非流式对话
    // ----------------------------------------------------------------

    @NoNeedLogin
    @PostMapping("/{materialId}/chat")
    @ApiOperation("资料RAG对话（非流式）")
    public ResponseResult<AgentChatResponse> chat(
            @PathVariable Long materialId,
            @RequestBody AgentChatRequest request,
            HttpServletRequest httpServletRequest) {
        Integer userId = currentUserId();
        String anonymousId = null;
        if (userId == null) {
            if (materialId > 0) {
                return ResponseResult.build(ResponseCode.NOT_LOGIN, null);
            }
            userId = -1;
            anonymousId = currentAnonymousId(httpServletRequest);
        }
        if (materialId > 0 && !hasAccess(materialId, userId.longValue())) {
            return ResponseResult.build(ResponseCode.ERROR, null);
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseResult.build(ResponseCode.ERROR, null);
        }

        // 获取或创建绑定该资料的会话
        AgentConversation conv = agentService.getOrCreateMaterialConversation(
                request.getSessionId(), userId, anonymousId, materialId);
        request.setSessionId(conv.getId());

        AgentChatResponse response = agentService.chat(conv.getId(), request.getMessage().trim(), userId);
        return ResponseResult.success(response);
    }

    // ----------------------------------------------------------------
    // 流式对话（SSE）
    // ----------------------------------------------------------------

    @NoNeedLogin
    @PostMapping(value = "/{materialId}/chat/stream", produces = "text/event-stream")
    @ApiOperation("资料RAG对话（流式SSE）")
    public SseEmitter chatStream(
            @PathVariable Long materialId,
            @RequestBody AgentChatRequest request,
            HttpServletRequest httpServletRequest) {
        SseEmitter emitter = new SseEmitter(300_000L);

        Integer userId = currentUserId();
        String anonymousId = null;
        if (userId == null) {
            if (materialId > 0) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"未登录\"}"));
                } catch (Exception ignored) {}
                emitter.complete();
                return emitter;
            }
            // materialId=0 通用对话允许匿名访问，使用匿名用户id -1
            userId = -1;
            anonymousId = currentAnonymousId(httpServletRequest);
        }

        if (materialId > 0 && !hasAccess(materialId, userId.longValue())) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"无访问权限\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"消息不能为空\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // 获取或创建绑定该资料的会话
        AgentConversation conv = agentService.getOrCreateMaterialConversation(
                request.getSessionId(), userId, anonymousId, materialId);
        String sessionId = conv.getId();

        log.info("资料RAG流式对话 materialId={} sessionId={} userId={}", materialId, sessionId, userId);
        agentService.chatStream(sessionId, request.getMessage().trim(), userId, emitter);
        return emitter;
    }
}
