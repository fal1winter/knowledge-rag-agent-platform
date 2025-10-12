package com.liang.bbs.user.facade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 通用实体机构关联DTO
 *
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntityInstitutionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 实体类型: user/paper/scholar/chatroom等
     */
    private String entityType;

    /**
     * 实体ID
     */
    private Long entityId;

    /**
     * 机构ID
     */
    private Long institutionId;

    /**
     * 机构名称（冗余字段，方便前端显示）
     */
    private String institutionName;

    /**
     * 关系类型: owner/admin/member/author/current等
     */
    private String relationType;

    /**
     * 是否为主要关联
     */
    private Byte isPrimary;

    /**
     * 扩展数据（JSON格式）
     */
    private String extraData;

    /**
     * 开始时间
     */
    private LocalDate startDate;

    /**
     * 结束时间
     */
    private LocalDate endDate;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
