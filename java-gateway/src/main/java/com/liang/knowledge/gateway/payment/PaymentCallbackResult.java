package com.liang.knowledge.gateway.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCallbackResult {
    private boolean success;
    private String orderNo;
    private String providerTradeNo;
    private String message;
}
