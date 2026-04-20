package com.liang.knowledge.gateway.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 支付订单存储。
 * 当前使用内存实现，适用于单实例开发和测试。
 * <p>
 * 功能：
 * - 基于订单号的 CRUD
 * - 基于用户 ID 的订单列表查询（分页、排序）
 * - 基于状态的订单查询（用于对账和超时关单）
 * - 定时清理已完成的过期订单，防止内存膨胀
 * <p>
 * 生产替换方案：
 * - MySQL/PostgreSQL: 基于 Spring Data JPA 或 MyBatis，payment_orders 表建 order_no 唯一索引
 * - 建议索引: (user_id, created_at DESC), (status, created_at), (order_no UNIQUE)
 * - Redis: 作为热数据缓存层，设置 TTL = 7d，冷数据落盘到关系型数据库
 * <p>
 * 并发安全：
 * - ConcurrentHashMap 保证并发安全
 * - 写操作幂等（save 为 put 语义）
 * - 状态变更建议在 Service 层加分布式锁（当前单实例无需）
 */
@Repository
public class PaymentOrderRepository {
    private static final Logger log = LoggerFactory.getLogger(PaymentOrderRepository.class);

    /** 已完成订单保留天数，超过后自动清理 */
    private static final int COMPLETED_ORDER_RETENTION_DAYS = 30;
    /** 未支付订单超时时间（分钟），超过后自动关单 */
    private static final int UNPAID_TIMEOUT_MINUTES = 30;

    private final Map<String, PaymentOrder> orders = new ConcurrentHashMap<>();
    /** 用户维度索引：userId → orderNo 列表 */
    private final Map<Long, List<String>> userOrderIndex = new ConcurrentHashMap<>();

    public void save(PaymentOrder order) {
        orders.put(order.getOrderNo(), order);
        // 维护用户索引
        userOrderIndex
                .computeIfAbsent(order.getUserId(), k -> new ArrayList<>())
                .add(order.getOrderNo());
    }

    public Optional<PaymentOrder> findByOrderNo(String orderNo) {
        return Optional.ofNullable(orders.get(orderNo));
    }

    /**
     * 查询用户的订单列表，按创建时间降序。
     *
     * @param userId 用户 ID
     * @param limit  最大返回数
     * @return 订单列表（最新在前）
     */
    public List<PaymentOrder> findByUserId(Long userId, int limit) {
        List<String> orderNos = userOrderIndex.getOrDefault(userId, Collections.emptyList());
        return orderNos.stream()
                .map(orders::get)
                .filter(o -> o != null)
                .sorted(Comparator.comparing(PaymentOrder::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定状态的所有订单（用于对账巡检和超时关单）。
     *
     * @param status 目标状态
     * @return 符合条件的订单列表
     */
    public List<PaymentOrder> findByStatus(PaymentStatus status) {
        return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 统计用户在指定时间范围内的订单数量（用于风控频次检查）。
     */
    public long countByUserIdSince(Long userId, LocalDateTime since) {
        List<String> orderNos = userOrderIndex.getOrDefault(userId, Collections.emptyList());
        return orderNos.stream()
                .map(orders::get)
                .filter(o -> o != null && o.getCreatedAt().isAfter(since))
                .count();
    }

    /**
     * 更新订单状态。仅在状态合法流转时更新，否则忽略。
     *
     * @return true 表示更新成功，false 表示订单不存在或状态不允许变更
     */
    public boolean updateStatus(String orderNo, PaymentStatus newStatus) {
        PaymentOrder order = orders.get(orderNo);
        if (order == null) {
            return false;
        }
        // 简单的状态机校验：不允许从终态回退
        PaymentStatus current = order.getStatus();
        if (isTerminalStatus(current)) {
            log.warn("订单 {} 已处于终态 {}, 忽略状态变更 → {}", orderNo, current, newStatus);
            return false;
        }
        order.setStatus(newStatus);
        if (PaymentStatus.PAID.equals(newStatus)) {
            order.setPaidAt(LocalDateTime.now());
        }
        return true;
    }

    /**
     * 定时关闭超时未支付的订单（每 5 分钟执行）。
     * 超过 UNPAID_TIMEOUT_MINUTES 未完成支付的订单自动标记为 CLOSED。
     */
    @Scheduled(fixedDelay = 300_000)
    public void closeTimeoutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(UNPAID_TIMEOUT_MINUTES);
        int closed = 0;
        for (PaymentOrder order : orders.values()) {
            if (PaymentStatus.PENDING.equals(order.getStatus())
                    && order.getCreatedAt().isBefore(cutoff)) {
                order.setStatus(PaymentStatus.CLOSED);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("超时关单: 关闭 {} 笔未支付订单", closed);
        }
    }

    /**
     * 定时清理已完成的过期订单（每小时执行）。
     * 超过保留期的终态订单从内存中移除。
     * <p>
     * 生产环境不需要此逻辑（数据库有独立归档策略）。
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void evictExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(COMPLETED_ORDER_RETENTION_DAYS);
        int evicted = 0;
        Iterator<Map.Entry<String, PaymentOrder>> it = orders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PaymentOrder> entry = it.next();
            PaymentOrder order = entry.getValue();
            if (isTerminalStatus(order.getStatus())
                    && order.getCreatedAt().isBefore(cutoff)) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("过期订单清理: 回收 {} 笔终态订单, 当前存活 {}", evicted, orders.size());
        }
    }

    // --- 监控指标 ---

    /** 当前订单总数 */
    public int getOrderCount() {
        return orders.size();
    }

    /** 按状态统计订单分布 */
    public Map<PaymentStatus, Long> getStatusDistribution() {
        return orders.values().stream()
                .collect(Collectors.groupingBy(PaymentOrder::getStatus, Collectors.counting()));
    }

    private boolean isTerminalStatus(PaymentStatus status) {
        return PaymentStatus.PAID.equals(status)
                || PaymentStatus.CLOSED.equals(status)
                || PaymentStatus.FAILED.equals(status);
    }
}
