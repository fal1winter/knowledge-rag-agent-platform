package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.persistence.entity.UserPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 */

@Mapper(componentModel = "spring")
public interface UserMS extends CommonMS<UserPo, UserDTO> {
    UserMS INSTANCE = Mappers.getMapper(UserMS.class);
}
