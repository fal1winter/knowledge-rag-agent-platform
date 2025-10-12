package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.ChatroomRelationDTO;
import com.liang.bbs.user.facade.enums.EntityTypeEnum;
import com.liang.bbs.user.facade.server.ChatroomRelationService;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPo;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPoExample;
import com.liang.bbs.user.persistence.mapper.ChatroomRelationPoMapper;
import com.liang.bbs.user.service.mapstruct.ChatroomRelationMS;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天室关联服务实现类
 */
@Component
@Slf4j
@Service
public class ChatroomRelationServiceImpl implements ChatroomRelationService {

    @Autowired
    private ChatroomRelationPoMapper chatroomRelationPoMapper;

    @Override
    public ChatroomRelationDTO createRelation(ChatroomRelationDTO dto) {
        log.info("创建聊天室关联: chatroomId={}, entityType={}, entityId={}",
                dto.getChatroomId(), dto.getEntityType(), dto.getEntityId());

        // 验证实体类型
        if (!EntityTypeEnum.isValid(dto.getEntityType())) {
            throw new IllegalArgumentException("无效的实体类型: " + dto.getEntityType());
        }

        // 检查是否已存在
        if (existsRelation(dto.getChatroomId(), dto.getEntityType(), dto.getEntityId())) {
            log.warn("关联已存在: chatroomId={}, entityType={}, entityId={}",
                    dto.getChatroomId(), dto.getEntityType(), dto.getEntityId());
            // 返回已存在的关联
            List<ChatroomRelationDTO> existing = getRelationsByEntity(dto.getEntityType(), dto.getEntityId());
            return existing.stream()
                    .filter(r -> r.getChatroomId().equals(dto.getChatroomId()))
                    .findFirst()
                    .orElse(null);
        }

        ChatroomRelationPo po = ChatroomRelationMS.INSTANCE.toPo(dto);
        po.setCreatedAt(LocalDateTime.now());
        chatroomRelationPoMapper.insertSelective(po);

        return ChatroomRelationMS.INSTANCE.toDTO(po);
    }

    @Override
    public Boolean deleteRelation(Long id) {
        log.info("删除聊天室关联: id={}", id);
        int result = chatroomRelationPoMapper.deleteByPrimaryKey(id);
        return result > 0;
    }

    @Override
    public Boolean deleteRelation(Integer chatroomId, String entityType, Long entityId) {
        log.info("删除聊天室关联: chatroomId={}, entityType={}, entityId={}",
                chatroomId, entityType, entityId);

        ChatroomRelationPoExample example = new ChatroomRelationPoExample();
        example.createCriteria()
                .andChatroomIdEqualTo(chatroomId)
                .andEntityTypeEqualTo(entityType)
                .andEntityIdEqualTo(entityId);

        int result = chatroomRelationPoMapper.deleteByExample(example);
        return result > 0;
    }

    @Override
    public List<ChatroomRelationDTO> getRelationsByEntity(String entityType, Long entityId) {
        log.info("根据实体查询关联: entityType={}, entityId={}", entityType, entityId);

        ChatroomRelationPoExample example = new ChatroomRelationPoExample();
        example.createCriteria()
                .andEntityTypeEqualTo(entityType)
                .andEntityIdEqualTo(entityId);
        example.setOrderByClause("created_at DESC");

        List<ChatroomRelationPo> poList = chatroomRelationPoMapper.selectByExample(example);
        return ChatroomRelationMS.INSTANCE.toDTO(poList);
    }

    @Override
    public List<ChatroomRelationDTO> getRelationsByChatroom(Integer chatroomId) {
        log.info("根据聊天室查询关联: chatroomId={}", chatroomId);

        ChatroomRelationPoExample example = new ChatroomRelationPoExample();
        example.createCriteria().andChatroomIdEqualTo(chatroomId);
        example.setOrderByClause("entity_type, created_at DESC");

        List<ChatroomRelationPo> poList = chatroomRelationPoMapper.selectByExample(example);
        return ChatroomRelationMS.INSTANCE.toDTO(poList);
    }

    @Override
    public List<ChatroomRelationDTO> getRelationsByChatroomAndType(Integer chatroomId, String entityType) {
        log.info("根据聊天室和类型查询关联: chatroomId={}, entityType={}", chatroomId, entityType);

        ChatroomRelationPoExample example = new ChatroomRelationPoExample();
        example.createCriteria()
                .andChatroomIdEqualTo(chatroomId)
                .andEntityTypeEqualTo(entityType);
        example.setOrderByClause("created_at DESC");

        List<ChatroomRelationPo> poList = chatroomRelationPoMapper.selectByExample(example);
        return ChatroomRelationMS.INSTANCE.toDTO(poList);
    }

    @Override
    public Boolean existsRelation(Integer chatroomId, String entityType, Long entityId) {
        ChatroomRelationPoExample example = new ChatroomRelationPoExample();
        example.createCriteria()
                .andChatroomIdEqualTo(chatroomId)
                .andEntityTypeEqualTo(entityType)
                .andEntityIdEqualTo(entityId);

        long count = chatroomRelationPoMapper.countByExample(example);
        return count > 0;
    }
}
