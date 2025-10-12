package com.liang.knowledge.gateway.payment;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentOrder {
    private String orderNo;
    private Long userId;
    private String tenantId;
    private String subject;
    private int amountFen;
    private int credits;
    private PayChannel channel;
    private PaymentStatus status;
    private String providerTradeNo;
    private String relatedBizType;
    private String relatedBizId;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
