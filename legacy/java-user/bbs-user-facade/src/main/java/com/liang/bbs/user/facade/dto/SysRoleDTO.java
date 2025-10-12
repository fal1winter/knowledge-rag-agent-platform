package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 角色DTO
 */
@Data
@ApiModel(value = "SysRole对象", description = "角色表")
public class SysRoleDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("角色ID")
    private Long id;

    @ApiModelProperty("角色编码")
    private String roleCode;

    @ApiModelProperty("角色名称")
    private String roleName;

    @ApiModelProperty("描述")
    private String description;

    @ApiModelProperty("状态: 1-启用, 0-禁用")
    private Integer status;

    @ApiModelProperty("数据权限范围: 1-全部, 2-本机构, 3-本机构及下级, 4-仅本人")
    private Integer dataScope;

    @ApiModelProperty("权限列表")
    private List<SysPermissionDTO> permissions;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
