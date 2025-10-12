package com.liang.bbs.user.persistence.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源权限实体
 */
@Data
public class ResourcePermissionPo {
    private Long id;
    private String resourceType;
    private Long resourceId;
    private Integer userId;
    private String roleType;
    private String permissions;
    private Integer grantedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
