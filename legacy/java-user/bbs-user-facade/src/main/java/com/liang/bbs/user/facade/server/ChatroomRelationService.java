package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.ChatroomRelationDTO;

import java.util.List;

/**
 * 聊天室关联服务接口
 */
public interface ChatroomRelationService {

    /**
     * 创建聊天室关联
     * @param dto 关联信息
     * @return 创建的关联
     */
    ChatroomRelationDTO createRelation(ChatroomRelationDTO dto);

    /**
     * 删除聊天室关联
     * @param id 关联ID
     * @return 是否成功
     */
    Boolean deleteRelation(Long id);

    /**
     * 根据聊天室ID和实体类型、实体ID删除关联
     * @param chatroomId 聊天室ID
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 是否成功
     */
    Boolean deleteRelation(Integer chatroomId, String entityType, Long entityId);

    /**
     * 根据类型和实体ID查询关联的聊天室
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 关联列表
     */
    List<ChatroomRelationDTO> getRelationsByEntity(String entityType, Long entityId);

    /**
     * 根据聊天室ID查询所有关联的实体
     * @param chatroomId 聊天室ID
     * @return 关联列表
     */
    List<ChatroomRelationDTO> getRelationsByChatroom(Integer chatroomId);

    /**
     * 根据聊天室ID和实体类型查询关联的实体
     * @param chatroomId 聊天室ID
     * @param entityType 实体类型
     * @return 关联列表
     */
    List<ChatroomRelationDTO> getRelationsByChatroomAndType(Integer chatroomId, String entityType);

    /**
     * 检查关联是否存在
     * @param chatroomId 聊天室ID
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 是否存在
     */
    Boolean existsRelation(Integer chatroomId, String entityType, Long entityId);
}
