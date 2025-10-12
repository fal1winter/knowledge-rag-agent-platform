package com.liang.bbs.rest.controller;

import lombok.Data;

@Data
public class MaterialOrderPaymentConfirmRequest {
    private Long userId;
    private String paymentOrderNo;
    private String providerTradeNo;
    private String payChannel;
}
