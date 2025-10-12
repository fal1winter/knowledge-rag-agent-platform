package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.ChatRoomDTO;
import com.liang.bbs.user.persistence.entity.ChatRoomPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 */

@Mapper(componentModel = "spring")
public interface ChatRoomMS extends CommonMS<ChatRoomPo, ChatRoomDTO> {
    ChatRoomMS INSTANCE = Mappers.getMapper(ChatRoomMS.class);
}
