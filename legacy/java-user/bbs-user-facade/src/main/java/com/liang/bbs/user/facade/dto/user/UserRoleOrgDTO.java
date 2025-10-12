package com.liang.bbs.user.facade.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 */

@Data
public class UserRoleOrgDTO implements Serializable {
    /**
     * 用户id
     */
    private Integer userid;
    /**
     * 角色id
     */
    private List<Integer> roleIds;
    /**
     * 组织结构id
     */
    private Integer orgId;

    private static final long serialVersionUID = 1L;

}
