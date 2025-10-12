package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.SysPermissionDTO;
import com.liang.bbs.user.persistence.entity.SysPermissionPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SysPermissionMS extends CommonMS<SysPermissionPo, SysPermissionDTO> {
    SysPermissionMS INSTANCE = Mappers.getMapper(SysPermissionMS.class);
}
