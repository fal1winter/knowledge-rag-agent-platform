package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.SysRoleDTO;
import com.liang.bbs.user.persistence.entity.SysRolePo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SysRoleMS extends CommonMS<SysRolePo, SysRoleDTO> {
    SysRoleMS INSTANCE = Mappers.getMapper(SysRoleMS.class);
}
