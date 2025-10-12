package com.liang.bbs.user.service.mapstruct;

import com.liang.bbs.user.facade.dto.ChatroomRelationDTO;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 聊天室关联对象转换器
 */
@Mapper(componentModel = "spring")
public interface ChatroomRelationMS extends CommonMS<ChatroomRelationPo, ChatroomRelationDTO> {
    ChatroomRelationMS INSTANCE = Mappers.getMapper(ChatroomRelationMS.class);
}
