package com.liang.bbs.user.facade.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 关注统计信息DTO
 */
@Data
@Accessors(chain = true)
public class FollowCountDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 关注数量（我关注了多少人）
     */
    private Integer followCount;

    /**
     * 粉丝数量（有多少人关注我）
     */
    private Integer fansCount;
}