package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 */

@Data
public class UserPasswordDTO implements Serializable {
    private Integer id;
    /**
     * 旧密码
     */
    private String oldPassword;
    /**
     * 新密码
     */
    private String newPassword;

    private static final long serialVersionUID = 1L;

}