package com.liang.bbs.user.persistence.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SysUserInstitutionPo implements Serializable {
    private Long id;

    private Integer userId;

    private Long institutionId;

    private Byte isPrimary;

    /**
     * 用户在机构中的角色: owner/admin/member
     */
    private String role;

    private LocalDateTime createdAt;

    private static final long serialVersionUID = 1L;
}