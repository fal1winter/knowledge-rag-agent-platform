package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 开发者用户 DTO
 */
@Data
public class DeveloperUserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 用户名称（查询时填充）
     */
    private String userName;

    /**
     * 开发者级别：1-普通开发者 2-高级开发者 3-超级管理员
     */
    private Integer devLevel;

    /**
     * 说明
     */
    private String description;

    /**
     * 状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
