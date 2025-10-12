package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;

/**
 */

@Data
public class RoleOutDTO implements Serializable {
    private Integer id;

    /**
     * 角色编号(代码)
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
     * 描述
     */
    private String description;

    private static final long serialVersionUID = 1L;

}
