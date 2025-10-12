package com.liang.knowledge.gateway.order;

public interface MaterialOrderGateway {
    MaterialOrderSnapshot getPaymentSnapshot(String orderNo);

    void confirmPaid(String orderNo, Long userId, String paymentOrderNo, String providerTradeNo, String payChannel);
}
