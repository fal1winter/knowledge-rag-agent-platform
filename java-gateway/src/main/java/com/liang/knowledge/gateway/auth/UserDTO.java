package com.liang.knowledge.gateway.auth;

import lombok.Data;

import java.io.Serializable;

/**
 * 网关层当前登录用户信息
 */
@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String tenantId;
    private String phone;
    private String email;
    private String picture;
    /** 角色列表，逗号分隔 */
    private String roles;
}
