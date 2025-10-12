package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 权限DTO
 */
@Data
@ApiModel(value = "SysPermission对象", description = "权限表")
public class SysPermissionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("权限ID")
    private Long id;

    @ApiModelProperty("权限编码")
    private String permCode;

    @ApiModelProperty("权限名称")
    private String permName;

    @ApiModelProperty("类型: menu-菜单, button-按钮, api-接口")
    private String permType;

    @ApiModelProperty("父权限ID")
    private Long parentId;

    @ApiModelProperty("路径/URL")
    private String path;

    @ApiModelProperty("图标")
    private String icon;

    @ApiModelProperty("排序")
    private Integer sortOrder;

    @ApiModelProperty("状态: 1-启用, 0-禁用")
    private Integer status;

    @ApiModelProperty("子权限")
    private List<SysPermissionDTO> children;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;
}
