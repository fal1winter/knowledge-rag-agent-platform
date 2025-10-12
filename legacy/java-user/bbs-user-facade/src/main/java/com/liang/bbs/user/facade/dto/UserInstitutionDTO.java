package com.liang.bbs.user.facade.dto;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户机构关联DTO
 *
 */
@Data
public class UserInstitutionDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 机构ID
     */
    private Long institutionId;

    /**
     * 机构名称
     */
    private String institutionName;

    /**
     * 是否为主机构
     */
    private Byte isPrimary;

    /**
     * 用户在机构中的角色: owner/admin/member
     */
    private String role;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
