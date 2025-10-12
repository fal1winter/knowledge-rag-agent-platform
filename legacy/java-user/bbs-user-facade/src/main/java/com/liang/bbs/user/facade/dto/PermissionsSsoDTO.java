package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 */
@Data
public class PermissionsSsoDTO implements Serializable {
    /**
     * 权限id
     */
    private Integer id;
    /**
     * 权限名称
     */
    private String name;
    /**
     * 权限等级
     */
    private String grade;
    

    private static final long serialVersionUID = 1L;

}
