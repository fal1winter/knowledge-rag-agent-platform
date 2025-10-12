package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 */
@Data
public class UserLoginDTO implements Serializable {
    /**
     * 用户名称
     */
    private String name;
    /**
     * 用户密码
     */
    private String password;

    private static final long serialVersionUID = 1L;

}
