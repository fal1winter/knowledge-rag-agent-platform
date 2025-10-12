package com.liang.knowledge.gateway.order;

import lombok.Data;

@Data
public class MaterialOrderSnapshot {
    private String orderNo;
    private Long buyerId;
    private Integer priceCredits;
    private Integer payType;
    private Integer status;
}
