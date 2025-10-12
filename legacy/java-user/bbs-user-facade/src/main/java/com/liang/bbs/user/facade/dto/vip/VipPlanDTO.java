package com.liang.bbs.user.facade.dto.vip;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP套餐DTO
 */
@Data
public class VipPlanDTO implements Serializable {
    private Integer id;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 时长（天）
     */
    private Integer durationDays;

    /**
     * 价格（积分）
     */
    private BigDecimal price;

    /**
     * 原价（用于显示折扣）
     */
    private BigDecimal originalPrice;

    /**
     * 套餐描述
     */
    private String description;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态（0:下架, 1:上架）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 折扣百分比（前端计算用）
     */
    private Integer discount;

    private static final long serialVersionUID = 1L;
}
