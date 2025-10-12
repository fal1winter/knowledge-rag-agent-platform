package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.UserRoomDTO;
import com.liang.bbs.user.persistence.entity.UserRoom;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 */

@Mapper(componentModel = "spring")
public interface UserRoomMS extends CommonMS<UserRoom, UserRoomDTO> {
    UserRoomMS INSTANCE = Mappers.getMapper(UserRoomMS.class);
}
