package com.liang.bbs.user.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 机构角色关联实体
 */
@Data
public class SysInstitutionRolePo {
    private Long id;
    private Long institutionId;
    private Long roleId;
    private LocalDateTime createTime;
}
