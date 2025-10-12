package com.liang.bbs.user.facade.dto.user;

import com.liang.bbs.user.facade.dto.user.UserListDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserForumDTO extends UserListDTO implements Serializable {
    /**
     * 获得的点赞数
     */
    private Long likeCount;

    /**
     * 获得的阅读量
     */
    private Long readCount;

    /**
     * 积分
     */
    private Integer points;

    /**
     * 等级（Lv6）
     */
    private String level;

    /**
     * 是否关注
     */
    private Boolean isFollow;

    private static final long serialVersionUID = 1L;
}
