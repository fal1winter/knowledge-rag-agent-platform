package com.liang.bbs.user.facade.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 好友搜索DTO
 */
@Data
@Accessors(chain = true)
public class FriendSearchDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 好友ID
     */
    private Integer friendId;

    /**
     * 状态(0:已删除,1:正常,2:拉黑)
     */
    private Byte status;

    /**
     * 好友备注（模糊搜索）
     */
    private String remark;

    /**
     * 页码
     */
    private Integer page;

    /**
     * 每页数量
     */
    private Integer pageSize;
}

