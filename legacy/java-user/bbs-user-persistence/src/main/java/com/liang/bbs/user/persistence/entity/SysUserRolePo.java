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
public class SysUserRolePo implements Serializable {
    private Long id;

    private Integer userId;

    private Long roleId;

    private LocalDateTime createdAt;

    private static final long serialVersionUID = 1L;
}