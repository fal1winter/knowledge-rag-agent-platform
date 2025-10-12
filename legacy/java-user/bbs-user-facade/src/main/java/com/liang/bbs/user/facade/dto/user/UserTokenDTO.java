package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 */
@Data
public class UserTokenDTO implements Serializable {
    /**
     * 用户id
     */
    private Integer userid;
    /**
     * 用户名称
     */
    private String username;
    /**
     * token
     */
    private String token;

    private static final long serialVersionUID = 1L;

}
