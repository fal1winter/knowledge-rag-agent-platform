package com.liang.knowledge.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局异常处理器。
 * <p>
 * 设计原则：
 * - 对外统一返回 {code, message, traceId} 结构，隐藏内部异常堆栈
 * - 对内记录完整异常日志（含 traceId），便于关联排查
 * - 按异常类型映射到对应的 HTTP 状态码，避免泄露内部实现细节
 * <p>
 * 覆盖场景：
 * - 401 鉴权失败（JWT 过期/无效）
 * - 400 参数校验失败（@Valid 注解触发）
 * - 408 上游服务超时（Python RAG 后端不可达）
 * - 500 未预期异常（兜底处理）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 鉴权异常 → 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * 参数校验异常 → 400 Bad Request
     * 触发场景：@RequestBody 中 @Valid 校验不通过
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 缺少必填参数 → 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = String.format("缺少必填参数: %s (%s)", ex.getParameterName(), ex.getParameterType());
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 参数类型不匹配 → 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("参数 %s 类型错误，期望 %s",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 上游服务不可达 → 502 Bad Gateway
     * 触发场景：Python RAG 后端连接超时或拒绝连接
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamTimeout(ResourceAccessException ex) {
        String traceId = generateTraceId();
        log.error("[{}] 上游服务不可达: {}", traceId, ex.getMessage());
        Map<String, Object> body = new HashMap<>(4);
        body.put("code", 502);
        body.put("message", "RAG 服务暂时不可用，请稍后重试");
        body.put("traceId", traceId);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * 兜底异常处理 → 500 Internal Server Error
     * 隐藏内部堆栈，仅暴露 traceId 供用户反馈
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        String traceId = generateTraceId();
        log.error("[{}] 未预期异常: {}", traceId, ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>(4);
        body.put("code", 500);
        body.put("message", "服务内部错误，请联系管理员并提供 traceId");
        body.put("traceId", traceId);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("code", status.value());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
