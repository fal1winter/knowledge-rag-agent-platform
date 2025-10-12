package com.liang.bbs.user.facade.dto.user;

import com.liang.bbs.user.facade.dto.RoleOutDTO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 */

@Data
public class UserListDTO implements Serializable {
    /**
     * 用户id
     */
    private Integer id;

    /**
     * 用户名
     */
    private String name;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 生日
     */
    private String birthday;

    /**
     * 年龄
     */
    private Integer age;

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
     * 简介
     */
    private String intro;

    /**
     * 拥有的角色
     */
    private List<RoleOutDTO> roles;

    /**
     * 所属组织架构id
     */
    private Integer orgId;

    /**
     * 所属组织架构
     */
    private List<Map<String, Object>> org;

    /**
     * 状态(0禁用,1启用)
     */
    private Boolean state;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;

}
