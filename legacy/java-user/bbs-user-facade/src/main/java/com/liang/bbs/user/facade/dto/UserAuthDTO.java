package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户权限信息DTO（包含角色和权限）
 */
@Data
@ApiModel(value = "UserAuth对象", description = "用户权限信息")
public class UserAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("用户ID")
    private Integer userId;

    @ApiModelProperty("用户名")
    private String username;

    @ApiModelProperty("角色列表")
    private List<SysRoleDTO> roles;

    @ApiModelProperty("角色编码集合")
    private Set<String> roleCodes;

    @ApiModelProperty("权限编码集合")
    private Set<String> permissionCodes;

    @ApiModelProperty("数据权限范围: 1-全部, 2-本机构, 3-本机构及下级, 4-仅本人")
    private Integer dataScope;

    @ApiModelProperty("所属机构ID列表")
    private List<Long> institutionIds;

    @ApiModelProperty("主机构ID")
    private Long primaryInstitutionId;
}
