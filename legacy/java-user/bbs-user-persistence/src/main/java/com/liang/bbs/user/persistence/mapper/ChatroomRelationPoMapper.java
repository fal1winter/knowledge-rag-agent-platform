package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.ChatroomRelationPo;
import com.liang.bbs.user.persistence.entity.ChatroomRelationPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatroomRelationPoMapper {
    long countByExample(ChatroomRelationPoExample example);

    int deleteByExample(ChatroomRelationPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ChatroomRelationPo record);

    int insertSelective(ChatroomRelationPo record);

    List<ChatroomRelationPo> selectByExample(ChatroomRelationPoExample example);

    ChatroomRelationPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ChatroomRelationPo record, @Param("example") ChatroomRelationPoExample example);

    int updateByExample(@Param("record") ChatroomRelationPo record, @Param("example") ChatroomRelationPoExample example);

    int updateByPrimaryKeySelective(ChatroomRelationPo record);

    int updateByPrimaryKey(ChatroomRelationPo record);
}