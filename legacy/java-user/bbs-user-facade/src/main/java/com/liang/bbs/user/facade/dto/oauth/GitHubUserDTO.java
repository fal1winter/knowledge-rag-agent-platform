package com.liang.bbs.user.facade.dto.oauth;

import lombok.Data;

import java.io.Serializable;

/**
 * GitHub 用户信息 DTO
 */
@Data
public class GitHubUserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * GitHub 用户ID
     */
    private Long id;

    /**
     * GitHub 登录名
     */
    private String login;

    /**
     * 用户昵称
     */
    private String name;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * GitHub 主页
     */
    private String htmlUrl;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 公司
     */
    private String company;

    /**
     * 位置
     */
    private String location;
}
