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
public class SysRolePo implements Serializable {
    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    private Byte status;

    private Byte dataScope;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private static final long serialVersionUID = 1L;
}