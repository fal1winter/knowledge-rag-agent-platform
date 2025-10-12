package com.liang.bbs.user.facade.server;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.basic.ResponseResult;
import com.liang.bbs.user.facade.dto.ChatRoomDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.liang.bbs.user.facade.dto.UserRoomDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatroomService {
    boolean createChatroom(ChatRoomDTO chatroomDto, Integer creatorId);

    Boolean addmember(Integer roomId, Integer userId);

    ResponseResult SendMessage(Integer userid, Integer roomid, String message);

    ResponseResult SendPic(UserSsoDTO user, ChatRoomDTO room, String message);

    ResponseResult getHistory(Integer roomId, LocalDateTime time);

    ResponseResult DeleteMemeber(Integer roomid, Integer userId);

    Boolean Fastcreate(Integer userId, UserSsoDTO user);

    /**
     * 快速创建私聊房间（如果已存在则返回已有房间ID）
     */
    Integer FastcreateAndGetRoomId(Integer userId, UserSsoDTO user);

    Boolean Listcreate(List<Integer> userId, UserSsoDTO user);

    List<ChatRoomDTO> GetallRoombyUserId(Integer userId);

    List<ChatRoomDTO> GetallRoom();

    ResponseResult updateinfo(ChatRoomDTO chatRoomDto);

    ChatRoomDTO GetRoomInfo(Integer roomId);

    List<UserRoomDTO> GetRoomMember(Integer roomId);

    List<ChatRoomDTO> searchRooms(String keyword);

    /**
     * 创建关联聊天室（先创建聊天室，再创建关联）
     * @param roomName 聊天室名称
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param creatorId 创建者ID
     * @return 创建的聊天室
     */
    ChatRoomDTO createRelatedChatroom(String roomName, String entityType, Long entityId, Integer creatorId);

    /**
     * 更新聊天室信息
     * @param chatRoomDto 聊天室信息
     * @return 更新后的聊天室
     */
    ChatRoomDTO updateChatroom(ChatRoomDTO chatRoomDto);
}