package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 
 */
@Data
public class UserDTO implements Serializable {
    private Integer id;
    /**
     * 用户名
     */
    private String name;

    /**
     * 密码
     */
    private String password;

    /**
     * 盐值
     */
    private String salt;

    /**
     * 性别(0:男,1:女)
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
     * 职位
     */
    private String position;

    /**
     * 公司
     */
    private String company;

    /**
     * 个人主页
     */
    private String homePage;

    /**
     * 个人简介
     */
    private String intro;

    /**
     * 组织架构id
     */
    private Integer orgId;

    /**
     * 状态(0禁用,1启用)
     */
    private Boolean state;

    /**
     * 三方登录ID
     */
    private String authId;

    /**
     * 来源（默认null（南生论坛）,GITEE,GITHUB等）
     */
    private String authSource;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 模糊搜索关键字（用于搜索name、email、phone等字段）
     */
    private String keyword;

    /**
     * 用户积分
     */
    private Integer credits;

    /**
     * VIP过期时间
     */
    private LocalDateTime vipExpireTime;

    private static final long serialVersionUID = 1L;

}
