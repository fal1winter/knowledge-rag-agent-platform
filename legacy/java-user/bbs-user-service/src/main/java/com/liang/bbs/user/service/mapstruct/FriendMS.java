package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.FriendDTO;
import com.liang.bbs.user.persistence.entity.FriendPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 好友信息映射器
 */
@Mapper(componentModel = "spring")
public interface FriendMS extends CommonMS<FriendPo, FriendDTO> {
    FriendMS INSTANCE = Mappers.getMapper(FriendMS.class);

    @Override
    @Mapping(target = "status", expression = "java(booleanToByte(p.getStatus()))")
    FriendDTO toDTO(FriendPo p);

    @Override
    @Mapping(target = "status", expression = "java(byteToBoolean(d.getStatus()))")
    FriendPo toPo(FriendDTO d);

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

