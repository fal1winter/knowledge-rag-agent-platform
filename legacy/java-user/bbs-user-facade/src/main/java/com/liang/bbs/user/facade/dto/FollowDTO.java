package com.liang.bbs.user.facade.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 关注信息DTO
 */
@Data
@Accessors(chain = true)
public class FollowDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Integer id;

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
     * 关注时间
     */
    private Date time;

    /**
     * 关注状态
     */
    private String status;
    
    private Object dto;
}