package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 */
@Data
public class RoleSsoDTO implements Serializable {
    /**
     * 角色id
     */
    private Integer id;
    /**
     * 角色代码
     */
    private String code;
    /**
     * 角色名称
     */
    private String name;
    /**
     * 角色等级
     */
    private String grade;
    /**
     * 权限id
     */
    private List<PermissionsSsoDTO> permissions;

    private static final long serialVersionUID = 1L;

}
