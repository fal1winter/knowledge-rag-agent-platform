package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源权限 DTO
 */
@Data
public class ResourcePermissionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    private Long id;

    /**
     * 资源类型：chatroom/paper/scholar
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private Long resourceId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 用户名称（查询时填充）
     */
    private String userName;

    /**
     * 角色类型：owner/admin/member
     */
    private String roleType;

    /**
     * 具体权限列表
     */
    private List<String> permissions;

    /**
     * 授权人用户ID
     */
    private Integer grantedBy;

    /**
     * 授权人名称（查询时填充）
     */
    private String grantedByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
