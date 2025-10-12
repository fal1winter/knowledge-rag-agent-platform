package com.liang.bbs.user.facade.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 关注搜索条件DTO
 */
@Data
@Accessors(chain = true)
public class FollowSearchDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 关注类型
     */
    private String type;

    /**
     * 被关注的目标ID
     */
    private Integer targetid;

    /**
     * 关注者用户ID
     */
    private Integer userid;

    /**
     * 关注状态
     */
    private String status;
}