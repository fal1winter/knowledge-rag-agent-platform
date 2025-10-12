package com.liang.knowledge.gateway.payment;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PaymentRequest {
    @NotNull
    private Long userId;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String subject;

    @Min(1)
    private int amountFen;

    @NotNull
    private PayChannel channel;

    private String relatedBizType = "knowledge_credit_recharge";
    private String relatedBizId;
}
