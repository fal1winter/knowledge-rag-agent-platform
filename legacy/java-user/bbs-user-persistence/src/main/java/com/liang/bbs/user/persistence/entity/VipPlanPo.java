package com.liang.bbs.user.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VIP套餐表
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VipPlanPo implements Serializable {
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

    private static final long serialVersionUID = 1L;
}
