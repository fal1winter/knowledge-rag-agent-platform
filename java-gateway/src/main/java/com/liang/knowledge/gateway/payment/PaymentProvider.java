package com.liang.knowledge.gateway.payment;

import java.util.Map;

public interface PaymentProvider {
    PayChannel channel();

    PaymentCreateResult create(PaymentOrder order);

    PaymentCallbackResult verifyAndParseNotify(Map<String, String> params, String rawBody, Map<String, String> headers);
}
