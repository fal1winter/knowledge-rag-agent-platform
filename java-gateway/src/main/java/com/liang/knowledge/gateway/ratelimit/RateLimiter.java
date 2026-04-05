package com.liang.knowledge.gateway.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 滑动窗口限流器。
 * <p>
 * 按 (tenantId, userId) 二元组限流，防止单用户高频调用冲击 RAG 后端。
 * 默认窗口 60 秒，每用户最多 30 次请求。
 * <p>
 * 生产环境建议替换为 Redis + Lua 脚本方案（支持多实例共享计数），
 * 当前内存实现适合单实例部署或开发调试。
 *
 * <pre>
 * 设计考量：
 * - 固定窗口有边界突发问题（窗口交界处 2x 流量），滑动窗口通过双桶插值缓解
 * - 每 5 分钟清理过期桶，避免长时间运行后内存膨胀
 * - 限流粒度为 user 级别；租户级别限流由网关上游 Nginx/Kong 负责
 * </pre>
 */
@Component
public class RateLimiter {

    private static final int DEFAULT_WINDOW_SECONDS = 60;
    private static final int DEFAULT_MAX_REQUESTS = 30;

    private final int windowSeconds;
    private final int maxRequests;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /** 上次清理过期桶的时间 */
    private volatile long lastCleanupEpoch = System.currentTimeMillis() / 1000;

    public RateLimiter() {
        this(DEFAULT_WINDOW_SECONDS, DEFAULT_MAX_REQUESTS);
    }

    public RateLimiter(int windowSeconds, int maxRequests) {
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    /**
     * 判断请求是否允许通过。
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @return true 表示放行，false 表示触发限流
     */
    public boolean tryAcquire(String tenantId, Long userId) {
        String key = tenantId + ":" + userId;
        long nowEpoch = System.currentTimeMillis() / 1000;

        // 定期清理（每 5 分钟）
        if (nowEpoch - lastCleanupEpoch > 300) {
            cleanup(nowEpoch);
            lastCleanupEpoch = nowEpoch;
        }

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        return counter.tryIncrement(nowEpoch, windowSeconds, maxRequests);
    }

    /**
     * 获取用户当前窗口内剩余可用请求数。
     */
    public int remaining(String tenantId, Long userId) {
        String key = tenantId + ":" + userId;
        WindowCounter counter = counters.get(key);
        if (counter == null) {
            return maxRequests;
        }
        long nowEpoch = System.currentTimeMillis() / 1000;
        int used = counter.currentCount(nowEpoch, windowSeconds);
        return Math.max(0, maxRequests - used);
    }

    private void cleanup(long nowEpoch) {
        counters.entrySet().removeIf(entry ->
                nowEpoch - entry.getValue().lastAccessEpoch > windowSeconds * 2L
        );
    }

    /**
     * 滑动窗口计数器：使用当前桶和上一桶的加权插值近似滑动窗口。
     * <p>
     * 比精确滑动窗口（记录每次请求时间戳）内存开销低一个数量级，
     * 精度损失在 ±10% 范围内，对限流场景足够。
     */
    static class WindowCounter {
        volatile long currentWindowStart;
        final AtomicInteger currentCount = new AtomicInteger(0);
        volatile int previousCount = 0;
        volatile long lastAccessEpoch = 0;

        boolean tryIncrement(long nowEpoch, int windowSeconds, int maxRequests) {
            lastAccessEpoch = nowEpoch;
            long windowStart = (nowEpoch / windowSeconds) * windowSeconds;

            if (windowStart != currentWindowStart) {
                // 进入新窗口，滚动计数
                synchronized (this) {
                    if (windowStart != currentWindowStart) {
                        if (windowStart - currentWindowStart >= 2L * windowSeconds) {
                            previousCount = 0;
                        } else {
                            previousCount = currentCount.get();
                        }
                        currentCount.set(0);
                        currentWindowStart = windowStart;
                    }
                }
            }

            // 滑动窗口近似：当前桶计数 + 上一桶按剩余时间比例加权
            int approximateCount = currentCount(nowEpoch, windowSeconds);
            if (approximateCount >= maxRequests) {
                return false;
            }

            currentCount.incrementAndGet();
            return true;
        }

        int currentCount(long nowEpoch, int windowSeconds) {
            long windowStart = (nowEpoch / windowSeconds) * windowSeconds;
            double elapsed = (double) (nowEpoch - windowStart) / windowSeconds;
            double previousWeight = 1.0 - elapsed;
            return currentCount.get() + (int) (previousCount * previousWeight);
        }
    }
}
