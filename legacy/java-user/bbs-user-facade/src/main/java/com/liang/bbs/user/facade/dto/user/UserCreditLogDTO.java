package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户积分流水DTO
 */
@Data
public class UserCreditLogDTO implements Serializable {
    private Long id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 积分变动数量（正数为增加，负数为减少）
     */
    private Integer amount;

    /**
     * 变动后余额
     */
    private Integer balance;

    /**
     * 类型：ADMIN_ADD(管理员充值), PURCHASE(购买资料), REFUND(退款),
     * PURCHASE_PACKAGE(购买资料包), PURCHASE_VIP(购买VIP)
     */
    private String type;

    /**
     * 关联ID（如订单ID）
     */
    private Long relatedId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 操作人ID（管理员充值时使用）
     */
    private Integer operatorId;

    /**
     * 操作人名称（用于前端显示）
     */
    private String operatorName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
