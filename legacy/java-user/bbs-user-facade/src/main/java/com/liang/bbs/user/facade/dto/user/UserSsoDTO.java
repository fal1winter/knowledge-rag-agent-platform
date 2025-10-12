package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

import com.liang.bbs.user.facade.dto.RoleSsoDTO;

/**
 */

@Data
public class UserSsoDTO implements Serializable {
    /**
     * 用户id
     */
    private Integer userId;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 性别
     */
    private Integer gender;
    /**
     * 生日
     */
    private String birthday;
    /**
     * 电话
     */
    private String phone;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 头像
     */
    private String picture;
    /**
     * 简介
     */
    private String intro;
    /**
     * 角色列表
     */
    private List<RoleSsoDTO> roles;
    /**
     * 组织架构
     */
    private Integer orgId;

    private static final long serialVersionUID = 1L;

}
