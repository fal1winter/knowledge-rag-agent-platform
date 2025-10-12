package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.ChatRoomPo;
import com.liang.bbs.user.persistence.entity.ChatRoomPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatRoomPoMapper {
    long countByExample(ChatRoomPoExample example);

    int deleteByExample(ChatRoomPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(ChatRoomPo record);

    int insertSelective(ChatRoomPo record);

    List<ChatRoomPo> selectByExample(ChatRoomPoExample example);

    ChatRoomPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") ChatRoomPo record, @Param("example") ChatRoomPoExample example);

    int updateByExample(@Param("record") ChatRoomPo record, @Param("example") ChatRoomPoExample example);

    int updateByPrimaryKeySelective(ChatRoomPo record);

    int updateByPrimaryKey(ChatRoomPo record);
}