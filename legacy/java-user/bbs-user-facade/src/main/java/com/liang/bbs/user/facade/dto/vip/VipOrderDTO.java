package com.liang.bbs.user.facade.dto.vip;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP订单DTO
 */
@Data
public class VipOrderDTO implements Serializable {
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 套餐ID
     */
    private Integer planId;

    /**
     * 套餐名称
     */
    private String planName;

    /**
     * 时长（天）
     */
    private Integer durationDays;

    /**
     * 购买价格
     */
    private BigDecimal price;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 状态（1:已支付, 2:已退款）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
