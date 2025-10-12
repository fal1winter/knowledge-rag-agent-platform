package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 */
@Data
public class UserEmailDTO implements Serializable {
    private Integer id;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 验证码
     */
    private String code;

    /**
     * 新密码（找回密码时使用）
     */
    private String newPassword;

    private static final long serialVersionUID = 1L;

}