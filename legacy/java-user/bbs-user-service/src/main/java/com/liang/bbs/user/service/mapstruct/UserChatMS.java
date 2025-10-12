package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.ChatRoomDTO;
import com.liang.bbs.user.facade.dto.UserChatDTO;
import com.liang.bbs.user.persistence.entity.ChatRoomPo;
import com.liang.bbs.user.persistence.entity.UserChat;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 */
@Mapper(componentModel = "spring")
public interface UserChatMS extends CommonMS<UserChat, UserChatDTO> {
    UserChatMS INSTANCE = Mappers.getMapper(UserChatMS.class);
}
