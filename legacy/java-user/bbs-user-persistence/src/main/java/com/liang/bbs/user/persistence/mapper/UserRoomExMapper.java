package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserRoom;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import java.time.LocalDateTime;
import java.util.List;

/**
 */
public interface UserRoomExMapper {
//     @Results(id = "userRoomMap", value = {
//     @Result(column = "user_id", property = "userId"),
//     @Result(column = "chatroom_id", property = "chatroomId"),
//     @Result(column = "join_time", property = "joinTime")
// })
    @Select("select chatroom_id from user_chatroom where user_id = #{userId} and exited = 0")
    List<Integer> selectRoomByUserId(@Param("userId") Integer userId);

    
    
    @Select("select user_id from user_chatroom where chatroom_id = #{chatroomId}")
    List<Integer> selectUserByRoomId(@Param("chatroomId") Integer chatroomId);
    
    @Delete("delete from user_chatroom where user_id = #{userId} and chatroom_id = #{chatroomId} and exited = 0")
    int deleteUserRoom(@Param("userId") Integer userId, @Param("chatroomId") Integer chatroomId);
    
    @Update("update user_chatroom set exited = 1 where user_id = #{userId} and chatroom_id = #{chatroomId} and exited = 0")
    int exitRoom(@Param("userId") Integer userId, @Param("chatroomId") Integer chatroomId);
    
    @Select("select * from user_chatroom where chatroom_id = #{roomId}")
    List<UserRoom> getRoomMember(Integer roomId);

    /**
     * 查找两个用户共同的房间ID列表
     */
    @Select("SELECT a.chatroom_id FROM user_chatroom a " +
            "INNER JOIN user_chatroom b ON a.chatroom_id = b.chatroom_id " +
            "WHERE a.user_id = #{userId1} AND b.user_id = #{userId2} " +
            "AND (a.exited = 0 OR a.exited IS NULL) AND (b.exited = 0 OR b.exited IS NULL)")
    List<Integer> selectCommonRooms(@Param("userId1") Integer userId1, @Param("userId2") Integer userId2);
}
