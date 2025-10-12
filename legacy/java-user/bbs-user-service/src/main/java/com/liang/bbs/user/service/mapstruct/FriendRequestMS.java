package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.FriendRequestDTO;
import com.liang.bbs.user.persistence.entity.FriendRequestPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 好友请求映射器
 */
@Mapper(componentModel = "spring")
public interface FriendRequestMS extends CommonMS<FriendRequestPo, FriendRequestDTO> {
    FriendRequestMS INSTANCE = Mappers.getMapper(FriendRequestMS.class);

    @Override
    @Mapping(target = "status", expression = "java(booleanToByte(p.getStatus()))")
    FriendRequestDTO toDTO(FriendRequestPo p);

    @Override
    @Mapping(target = "status", expression = "java(byteToBoolean(d.getStatus()))")
    FriendRequestPo toPo(FriendRequestDTO d);

    default Byte booleanToByte(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? (byte) 1 : (byte) 0;
    }

    default Boolean byteToBoolean(Byte value) {
        if (value == null) {
            return null;
        }
        return value != 0;
    }
}

