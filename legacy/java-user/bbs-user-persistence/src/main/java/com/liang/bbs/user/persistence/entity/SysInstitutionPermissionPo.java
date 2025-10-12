package com.liang.bbs.user.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 机构权限关联实体
 */
@Data
public class SysInstitutionPermissionPo {
    private Long id;
    private Long institutionId;
    private Long permissionId;
    private LocalDateTime createTime;
}
