package com.liang.knowledge.gateway.payment;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付订单存储。
 * 当前使用内存实现，适用于单实例开发和测试。
 * <p>
 * 生产替换方案：
 * - MySQL/PostgreSQL: 基于 Spring Data JPA 或 MyBatis，payment_orders 表建 order_no 唯一索引
 * - Redis: 作为热数据缓存层，设置 TTL = 7d，冷数据落盘到关系型数据库
 */
@Repository
public class PaymentOrderRepository {
    private final Map<String, PaymentOrder> orders = new ConcurrentHashMap<>();

    public void save(PaymentOrder order) {
        orders.put(order.getOrderNo(), order);
    }

    public Optional<PaymentOrder> findByOrderNo(String orderNo) {
        return Optional.ofNullable(orders.get(orderNo));
    }
}
