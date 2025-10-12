package com.liang.bbs.user.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开发者用户实体
 */
@Data
public class DeveloperUserPo {
    private Long id;
    private Integer userId;
    private Integer devLevel;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
