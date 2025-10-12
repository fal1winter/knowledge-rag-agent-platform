package com.liang.bbs.user.service.impl;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.user.facade.dto.ChatRoomDTO;
import com.liang.bbs.user.facade.dto.ChatroomRelationDTO;
import com.liang.bbs.user.facade.dto.UserChatDTO;
import com.liang.bbs.user.facade.dto.UserRoomDTO;
import com.liang.bbs.user.facade.enums.EntityTypeEnum;
import com.liang.bbs.user.facade.server.ChatroomService;
import com.liang.bbs.user.persistence.entity.ChatRoomPo;
import com.liang.bbs.user.persistence.entity.ChatRoomPoExample;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPo;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPoExample;
import com.liang.bbs.user.persistence.mapper.ChatRoomPoMapper;
import com.liang.bbs.user.persistence.mapper.ChatroomRelationPoMapper;
import com.liang.bbs.user.service.mapstruct.ChatRoomMS;
import com.liang.bbs.user.service.mapstruct.UserChatMS;
import com.liang.bbs.user.service.mapstruct.UserRoomMS;

import lombok.extern.slf4j.Slf4j;

import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.liang.bbs.user.persistence.mapper.UserRoomExMapper;
import com.liang.bbs.user.persistence.mapper.UserRoomMapper;
import com.liang.bbs.user.persistence.entity.UserChat;
import com.liang.bbs.user.persistence.entity.UserRoom;
import com.liang.bbs.user.persistence.entity.UserRoomExample;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.liang.bbs.user.facade.dto.ChatRoomDTO;

/**
 * <p>
 * 聊天室表 服务实现类
 * </p>
 *
 */
@Component
@Slf4j
@Service
public class ChatroomServiceImpl implements ChatroomService {
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private ChatRoomPoMapper chatroompomapper;
    @Autowired
    private ChatroomRelationPoMapper chatroomRelationPoMapper;
    @Autowired
    private UserRoomExMapper urex;//获取po方法属性对不齐
    @Autowired
    private UserRoomMapper urm;

    @Override
    public boolean createChatroom(ChatRoomDTO chatroomDto, Integer creatorId) {
        
        ChatRoomPo aa = ChatRoomMS.INSTANCE.toPo(chatroomDto);
        aa.setCreatorId(creatorId);

        aa.setCreatedAt(new Date());
        aa.setUpdatedAt(new Date());
        aa.setStatus(1);
        chatroompomapper.insert(aa);
        addmember(aa.getId(), creatorId);
        return true;
    }

    public Boolean addmember(Integer roomid, Integer userid) {

        UserRoom userroom = new UserRoom();
        userroom.setChatroomId(roomid);
        userroom.setUserId(userid);
        userroom.setJoinTime(LocalDateTime.now());
        userroom.setRole("member");
        userroom.setExited((byte) 0);
        urm.insert(userroom);
        return true;
    }

    public ResponseResult SendMessage(Integer userid, Integer roomid, String message) {
        UserChat userchat = new UserChat();
        userchat.setRoomid(roomid);
        userchat.setUserid(userid);
        userchat.setContent(message);
        userchat.setType("message");
        userchat.setTime(LocalDateTime.now());
        mongoTemplate.insert(Collections.singletonList(userchat), UserChat.class);
        return ResponseResult.success(ResponseCode.SUCCESS);
    }

    public ResponseResult SendPic(UserSsoDTO user, ChatRoomDTO room, String message) {
        UserChat userchat = new UserChat();
        userchat.setRoomid(room.getId());
        userchat.setUserid(user.getUserId());
        userchat.setContent(message);
        userchat.setType("pic");
        userchat.setTime(LocalDateTime.now());
        mongoTemplate.insert(Collections.singletonList(userchat), UserChat.class);
        return ResponseResult.success(ResponseCode.SUCCESS);
    }

    public ResponseResult getHistory(Integer roomId, LocalDateTime time) {
        Query query = new Query(Criteria.where("roomid").is(roomId)
                .and("time").lt(time));
        List<UserChat> messages = mongoTemplate.find(query, UserChat.class);
        List<UserChatDTO> dtoList = UserChatMS.INSTANCE.toDTO(messages);

        return ResponseResult.success(dtoList);
    }

    public ResponseResult DeleteMemeber(Integer roomid, Integer userId) {
        urex.deleteUserRoom(userId, roomid);
        return ResponseResult.success(ResponseCode.SUCCESS);

    }

    public Boolean Fastcreate(Integer userId, UserSsoDTO user) {

        Integer creatorid = user.getUserId();

        ChatRoomPo chat = new ChatRoomPo();
        chat.setCreatorId(creatorid);
        chat.setName("群聊");
        chat.setMaxMembers(2);
        chat.setIsPrivate(0);
        chat.setStatus(1);
        chatroompomapper.insert(chat);
        chat.setName("群聊" + chat.getId());
        chatroompomapper.updateByPrimaryKeySelective(chat);

        UserRoom cum = new UserRoom();
        cum.setChatroomId(chat.getId());
        cum.setUserId(creatorid);
        cum.setJoinTime(LocalDateTime.now());
        urm.insert(cum);

        UserRoom um = new UserRoom();
        um.setChatroomId(chat.getId());
        um.setUserId(userId);
        um.setJoinTime(LocalDateTime.now());
        urm.insert(um);
        return true;
    }

    public Boolean Listcreate(List<Integer> userId, UserSsoDTO user) {
        Integer creatorid = user.getUserId();

        ChatRoomPo chat = new ChatRoomPo();
        chat.setCreatorId(creatorid);
        chat.setMaxMembers(100);
        chat.setIsPrivate(0);
        chat.setStatus(1);
        chatroompomapper.insert(chat);
        for (Integer id : userId) {
            UserRoom um = new UserRoom();
            um.setChatroomId(chat.getId());
            um.setUserId(id);
            um.setJoinTime(LocalDateTime.now());
            urm.insert(um);
        }

        return true;
    }

    @Override
    public Integer FastcreateAndGetRoomId(Integer userId, UserSsoDTO user) {
        Integer creatorId = user.getUserId();

        // 先查找两人是否已有共同的2人私聊房间
        List<Integer> commonRoomIds = urex.selectCommonRooms(creatorId, userId);
        if (commonRoomIds != null && !commonRoomIds.isEmpty()) {
            // 检查是否有 maxMembers=2 的私聊房间
            for (Integer roomId : commonRoomIds) {
                ChatRoomPo room = chatroompomapper.selectByPrimaryKey(roomId);
                if (room != null && room.getMaxMembers() != null && room.getMaxMembers() == 2) {
                    return room.getId();
                }
            }
        }

        // 不存在则创建新房间
        ChatRoomPo chat = new ChatRoomPo();
        chat.setCreatorId(creatorId);
        chat.setName("私聊_" + creatorId + "_" + userId + "_" + System.currentTimeMillis());
        chat.setMaxMembers(2);
        chat.setIsPrivate(1);
        chat.setStatus(1);
        chatroompomapper.insert(chat);
        chat.setName("私聊" + chat.getId());
        chatroompomapper.updateByPrimaryKeySelective(chat);

        UserRoom cum = new UserRoom();
        cum.setChatroomId(chat.getId());
        cum.setUserId(creatorId);
        cum.setJoinTime(LocalDateTime.now());
        cum.setRole("member");
        cum.setExited((byte) 0);
        urm.insert(cum);

        UserRoom um = new UserRoom();
        um.setChatroomId(chat.getId());
        um.setUserId(userId);
        um.setJoinTime(LocalDateTime.now());
        um.setRole("member");
        um.setExited((byte) 0);
        urm.insert(um);

        return chat.getId();
    }

    public List<ChatRoomDTO> GetallRoombyUserId(Integer userId) {

        List<Integer> roomIds = urex.selectRoomByUserId(userId);
        if(roomIds.isEmpty()){
            return new ArrayList<>();
        }
        ChatRoomPoExample example = new ChatRoomPoExample();
        example.createCriteria().andIdIn(roomIds);
        List<ChatRoomPo> poList = chatroompomapper.selectByExample(example);
        List<ChatRoomDTO> dtoList = ChatRoomMS.INSTANCE.toDTO(poList);
        return dtoList;
    }

    public List<ChatRoomDTO> GetallRoom() {

        List<ChatRoomPo> poList = chatroompomapper.selectByExample(null);
        List<ChatRoomDTO> dtoList = ChatRoomMS.INSTANCE.toDTO(poList);
        return dtoList;
    }

    public ResponseResult updateinfo(ChatRoomDTO chatRoomDto) {

        ChatRoomPo po = ChatRoomMS.INSTANCE.toPo(chatRoomDto);
        chatroompomapper.updateByPrimaryKey(po);

        return ResponseResult.success(ResponseCode.SUCCESS);
    }

    public ChatRoomDTO GetRoomInfo(Integer roomId) {
        ChatRoomPo po = chatroompomapper.selectByPrimaryKey(roomId);
        ChatRoomDTO dto = ChatRoomMS.INSTANCE.toDTO(po);
        return dto;
    }

    public List<UserRoomDTO> GetRoomMember(Integer roomId) {
        UserRoomExample example = new UserRoomExample();
        example.createCriteria().andChatroomIdEqualTo(roomId);
        List<UserRoom> po = urm.selectByExample(example);

        List<UserRoomDTO> dto = UserRoomMS.INSTANCE.toDTO(po);
        return dto;
    }

    @Override
    public List<ChatRoomDTO> searchRooms(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        ChatRoomPoExample example = new ChatRoomPoExample();
        String searchPattern = "%" + keyword.trim() + "%";
        
        // 在房间名和描述中搜索关键字
        example.createCriteria()
            .andNameLike(searchPattern)
            .andStatusEqualTo(1); // 只返回状态为1的正常房间
            
        // 或者条件：描述中包含关键字
        example.or(example.createCriteria()
            .andDescriptionLike(searchPattern)
            .andStatusEqualTo(1));
            
        example.setOrderByClause("created_at DESC");
        
        List<ChatRoomPo> poList = chatroompomapper.selectByExample(example);
        List<ChatRoomDTO> dtoList = ChatRoomMS.INSTANCE.toDTO(poList);
        return dtoList;
    }

    @Override
    public ChatRoomDTO createRelatedChatroom(String roomName, String entityType, Long entityId, Integer creatorId) {
        log.info("创建关联聊天室: roomName={}, entityType={}, entityId={}, creatorId={}",
                roomName, entityType, entityId, creatorId);

        // 验证实体类型
        if (!EntityTypeEnum.isValid(entityType)) {
            throw new IllegalArgumentException("无效的实体类型: " + entityType);
        }

        // 1. 创建聊天室
        ChatRoomPo chatRoom = new ChatRoomPo();
        chatRoom.setName(roomName);
        chatRoom.setCreatorId(creatorId);
        chatRoom.setMaxMembers(100);
        chatRoom.setIsPrivate(0);
        chatRoom.setStatus(1);
        chatRoom.setCreatedAt(new Date());
        chatRoom.setUpdatedAt(new Date());
        chatroompomapper.insert(chatRoom);

        // 2. 将创建者加入聊天室
        addmember(chatRoom.getId(), creatorId);

        // 3. 创建关联关系
        ChatroomRelationPo relation = new ChatroomRelationPo();
        relation.setChatroomId(chatRoom.getId());
        relation.setEntityType(entityType);
        relation.setEntityId(entityId);
        relation.setCreatedBy(creatorId);
        relation.setCreatedAt(LocalDateTime.now());
        chatroomRelationPoMapper.insertSelective(relation);

        log.info("关联聊天室创建成功: chatroomId={}", chatRoom.getId());
        return ChatRoomMS.INSTANCE.toDTO(chatRoom);
    }

    @Override
    public ChatRoomDTO updateChatroom(ChatRoomDTO chatRoomDto) {
        log.info("更新聊天室信息: roomId={}", chatRoomDto.getId());

        ChatRoomPo po = chatroompomapper.selectByPrimaryKey(chatRoomDto.getId());
        if (po == null) {
            throw new IllegalArgumentException("聊天室不存在: " + chatRoomDto.getId());
        }

        // 只更新非空字段
        if (chatRoomDto.getName() != null) {
            po.setName(chatRoomDto.getName());
        }
        if (chatRoomDto.getDescription() != null) {
            po.setDescription(chatRoomDto.getDescription());
        }
        if (chatRoomDto.getMaxMembers() != null) {
            po.setMaxMembers(chatRoomDto.getMaxMembers());
        }
        if (chatRoomDto.getIsPrivate() != null) {
            po.setIsPrivate(chatRoomDto.getIsPrivate());
        }
        if (chatRoomDto.getAvatarUrl() != null) {
            po.setAvatarUrl(chatRoomDto.getAvatarUrl());
        }
        po.setUpdatedAt(new Date());

        chatroompomapper.updateByPrimaryKeySelective(po);
        return ChatRoomMS.INSTANCE.toDTO(po);
    }

}
