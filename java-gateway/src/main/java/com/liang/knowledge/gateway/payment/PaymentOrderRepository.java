package com.liang.knowledge.gateway.payment;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
