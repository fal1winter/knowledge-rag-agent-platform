package com.liang.bbs.user.facade.dto.oauth;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信用户信息 DTO
 * 
 */
@Data
public class WeChatUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识 (openid)
     */
    private String openid;

    /**
     * 用户统一标识 (unionid，只有在用户将公众号绑定到微信开放平台帐号后才会出现)
     */
    private String unionid;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户性别 (1-男，2-女，0-未知)
     */
    private Integer sex;

    /**
     * 用户所在省份
     */
    private String province;

    /**
     * 用户所在城市
     */
    private String city;

    /**
     * 用户所在国家
     */
    private String country;

    /**
     * 用户头像 URL
     */
    private String headimgurl;

    /**
     * 用户特权信息
     */
    private String[] privilege;
}
