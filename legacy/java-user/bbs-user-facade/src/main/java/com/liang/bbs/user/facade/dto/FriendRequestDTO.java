package com.liang.bbs.user.facade.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 好友请求DTO
 */
@Data
@Accessors(chain = true)
public class FriendRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 发起用户ID
     */
    private Integer fromUserId;

    /**
     * 接收用户ID
     */
    private Integer toUserId;

    /**
     * 请求消息
     */
    private String message;

    /**
     * 状态(0:待处理,1:已同意,2:已拒绝,3:已过期)
     */
    private Byte status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 发起用户信息（用于前端显示）
     */
    private Object fromUserInfo;
}

