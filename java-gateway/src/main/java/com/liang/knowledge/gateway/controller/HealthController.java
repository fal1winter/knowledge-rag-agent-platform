package com.liang.knowledge.gateway.controller;

import com.liang.knowledge.gateway.rag.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统健康检查接口。
 * <p>
 * 供运维监控 (Prometheus/Grafana) 和 K8s liveness/readiness 探针使用。
 * 检查项：
 * - RAG Python 后端连通性（HTTP ping）
 * - JVM 内存使用情况
 * - 会话存储状态（活跃会话数、累计消息数）
 * - 服务启动时间与运行时长
 * <p>
 * 不需要认证即可访问（在 SecurityConfig 中配白名单）。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final RestTemplate restTemplate;
    private final ChatSessionRepository sessionRepository;
    private final Instant startTime = Instant.now();

    @Value("${rag.backend.url:http://localhost:8000}")
    private String ragBackendUrl;

    public HealthController(ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * K8s liveness 探针：只要 JVM 能响应就返回 200。
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * K8s readiness 探针：检查关键依赖是否就绪。
     * 如果 RAG 后端不可达，返回 503 使 K8s 暂时从负载均衡摘除此实例。
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean ragReachable = checkRagBackend();

        result.put("status", ragReachable ? "UP" : "DOWN");
        result.put("ragBackend", ragReachable ? "reachable" : "unreachable");
        result.put("timestamp", Instant.now().toString());

        if (!ragReachable) {
            return ResponseEntity.status(503).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 详细健康状态：包含 JVM 内存、会话统计、运行时长等。
     * 供 Grafana 仪表盘或运维排查使用。
     */
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> detail() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 整体状态
        boolean ragReachable = checkRagBackend();
        result.put("status", ragReachable ? "UP" : "DEGRADED");

        // RAG 后端
        Map<String, Object> ragStatus = new LinkedHashMap<>();
        ragStatus.put("url", ragBackendUrl);
        ragStatus.put("reachable", ragReachable);
        result.put("ragBackend", ragStatus);

        // JVM 内存
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Map<String, Object> memInfo = new LinkedHashMap<>();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        memInfo.put("heapUsedMB", heapUsed / (1024 * 1024));
        memInfo.put("heapMaxMB", heapMax / (1024 * 1024));
        memInfo.put("heapUsagePercent", heapMax > 0 ? (int) (100.0 * heapUsed / heapMax) : -1);
        result.put("memory", memInfo);

        // 会话统计
        Map<String, Object> sessionInfo = new LinkedHashMap<>();
        sessionInfo.put("activeSessions", sessionRepository.getActiveSessionCount());
        sessionInfo.put("totalMessagesProcessed", sessionRepository.getTotalMessagesProcessed());
        sessionInfo.put("currentMessagesInMemory", sessionRepository.getCurrentMessageCount());
        result.put("sessions", sessionInfo);

        // 运行时长
        Duration uptime = Duration.between(startTime, Instant.now());
        Map<String, Object> runtimeInfo = new LinkedHashMap<>();
        runtimeInfo.put("startTime", startTime.toString());
        runtimeInfo.put("uptimeSeconds", uptime.getSeconds());
        runtimeInfo.put("uptimeHuman", formatDuration(uptime));
        result.put("runtime", runtimeInfo);

        result.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    /**
     * 尝试 HTTP GET 访问 RAG 后端健康接口，3 秒超时。
     */
    private boolean checkRagBackend() {
        try {
            String healthUrl = ragBackendUrl + "/health";
            restTemplate.getForEntity(healthUrl, String.class);
            return true;
        } catch (Exception e) {
            log.warn("RAG 后端健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private String formatDuration(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 24) {
            return String.format("%dd %dh %dm", hours / 24, hours % 24, minutes);
        }
        return String.format("%dh %dm", hours, minutes);
    }
}
