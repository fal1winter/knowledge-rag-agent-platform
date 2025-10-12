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
public class SysRolePermissionPo implements Serializable {
    private Long id;

    private Long roleId;

    private Long permissionId;

    private LocalDateTime createdAt;

    private static final long serialVersionUID = 1L;
}