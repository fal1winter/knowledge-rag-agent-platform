package com.liang.knowledge.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.knowledge.gateway.auth.MaterialAccessChecker;
import com.liang.knowledge.gateway.auth.SecurityContext;
import com.liang.knowledge.gateway.auth.UnauthorizedException;
import com.liang.knowledge.gateway.rag.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 对话网关入口。
 * 从 SecurityContext 注入 tenantId / userId，忽略客户端传入值；
 * 若请求指定了 materialIds，校验当前用户是否拥有访问权限。
 * 提供同步 chat 和 SSE 流式 chat/stream 两种调用方式。
 */
@RestController
@RequestMapping("/api/gateway/rag")
public class RagGatewayController {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RagGatewayClient ragGatewayClient;
    private final RagStreamClient ragStreamClient;
    private final MaterialAccessChecker materialAccessChecker;
    private final ChatSessionRepository sessionRepository;

    public RagGatewayController(
            RagGatewayClient ragGatewayClient,
            RagStreamClient ragStreamClient,
            MaterialAccessChecker materialAccessChecker,
            ChatSessionRepository sessionRepository
    ) {
        this.ragGatewayClient = ragGatewayClient;
        this.ragStreamClient = ragStreamClient;
        this.materialAccessChecker = materialAccessChecker;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 同步对话接口，返回完整响应。
     */
    @PostMapping("/chat")
    public RagChatResponse chat(@RequestBody RagChatRequest request) {
        prepareRequest(request);
        Map<String, Object> raw = ragGatewayClient.chat(request);
        RagChatResponse response = RagChatResponse.fromRawMap(raw);

        // 记录消息到会话
        recordMessages(request, response.getAnswer());

        return response;
    }

    /**
     * SSE 流式对话接口。
     * 前端通过 EventSource 或 fetch + ReadableStream 消费。
     * 每个 SSE event 的 data 为一段增量文本 token。
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody RagChatRequest request) {
        prepareRequest(request);

        // 超时 120 秒，覆盖复杂 agentic 检索场景
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder fullAnswer = new StringBuilder();

        try {
            String requestJson = MAPPER.writeValueAsString(buildPythonBody(request));
            // 异步流式读取，避免阻塞 Servlet 线程
            Thread streamThread = new Thread(() -> {
                try {
                    ragStreamClient.stream(
                            requestJson,
                            token -> {
                                try {
                                    fullAnswer.append(token);
                                    emitter.send(SseEmitter.event().data(token));
                                } catch (Exception e) {
                                    emitter.completeWithError(e);
                                }
                            },
                            () -> {
                                try {
                                    emitter.send(SseEmitter.event().data("[DONE]"));
                                    emitter.complete();
                                    recordMessages(request, fullAnswer.toString());
                                } catch (Exception e) {
                                    emitter.completeWithError(e);
                                }
                            },
                            emitter::completeWithError
                    );
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }, "rag-stream-" + request.getSessionId());
            streamThread.setDaemon(true);
            streamThread.start();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 统一请求预处理：注入鉴权身份、校验素材权限、初始化会话。
     */
    private void prepareRequest(RagChatRequest request) {
        Long userId = SecurityContext.requireUserId();
        String tenantId = SecurityContext.requireTenantId();

        // 覆盖客户端传入的身份信息，以网关鉴权结果为准
        request.setUserId(userId);
        request.setTenantId(tenantId);

        // 素材访问权限校验
        List<String> materialIds = request.getMaterialIds();
        if (materialIds != null && !materialIds.isEmpty()) {
            if (!materialAccessChecker.hasAccess(userId, materialIds)) {
                throw new UnauthorizedException("无权访问指定素材，请先购买或领取");
            }
        }

        // 无 sessionId 时自动创建会话
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            ChatSession session = sessionRepository.createSession(userId, tenantId, null);
            request.setSessionId(session.getSessionId());
        }
    }

    /**
     * 记录用户提问和助手回复到会话存储。
     */
    private void recordMessages(RagChatRequest request, String answer) {
        try {
            ChatMessage userMsg = ChatMessage.builder()
                    .messageId(UUID.randomUUID().toString().replace("-", ""))
                    .sessionId(request.getSessionId())
                    .role("user")
                    .content(request.getMessage())
                    .createdAt(LocalDateTime.now())
                    .build();
            sessionRepository.appendMessage(request.getSessionId(), userMsg);

            if (answer != null && !answer.isEmpty()) {
                ChatMessage assistantMsg = ChatMessage.builder()
                        .messageId(UUID.randomUUID().toString().replace("-", ""))
                        .sessionId(request.getSessionId())
                        .role("assistant")
                        .content(answer)
                        .createdAt(LocalDateTime.now())
                        .build();
                sessionRepository.appendMessage(request.getSessionId(), assistantMsg);
            }
        } catch (Exception ignored) {
            // 消息记录失败不影响主流程
        }
    }

    private Map<String, Object> buildPythonBody(RagChatRequest request) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("tenant_id", request.getTenantId());
        body.put("user_id", String.valueOf(request.getUserId()));
        body.put("message", request.getMessage());
        body.put("session_id", request.getSessionId());
        body.put("material_ids", request.getMaterialIds());
        body.put("force_agentic", request.isForceAgentic());
        return body;
    }
}
