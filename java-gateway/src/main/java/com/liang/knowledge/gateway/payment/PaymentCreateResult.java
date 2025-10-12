package com.liang.knowledge.gateway.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCreateResult {
    private String orderNo;
    private PayChannel channel;
    private PaymentStatus status;
    private String payForm;
    private String codeUrl;
    private int credits;
}
