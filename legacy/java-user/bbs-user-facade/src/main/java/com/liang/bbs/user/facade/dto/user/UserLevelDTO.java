package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 */
@Data
public class UserLevelDTO implements Serializable {
    /**
     * 用户等级编号
     */
    private Integer id;

    /**
     * 用户id
     */
    private Integer userid;

    /**
     * 等级（Lv6）
     */
    private String level;

    /**
     * 积分
     */
    private Integer points;

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
