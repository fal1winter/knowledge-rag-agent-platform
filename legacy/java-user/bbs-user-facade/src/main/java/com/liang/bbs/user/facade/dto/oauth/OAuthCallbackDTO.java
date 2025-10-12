package com.liang.bbs.user.facade.dto.oauth;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth 回调参数 DTO
 */
@Data
public class OAuthCallbackDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 授权码
     */
    private String code;

    /**
     * 状态参数（防止CSRF攻击）
     */
    private String state;
}
