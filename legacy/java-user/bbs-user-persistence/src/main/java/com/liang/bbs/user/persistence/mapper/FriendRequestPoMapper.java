package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.FriendRequestPo;
import com.liang.bbs.user.persistence.entity.FriendRequestPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FriendRequestPoMapper {
    long countByExample(FriendRequestPoExample example);

    int deleteByExample(FriendRequestPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(FriendRequestPo record);

    int insertSelective(FriendRequestPo record);

    List<FriendRequestPo> selectByExample(FriendRequestPoExample example);

    FriendRequestPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") FriendRequestPo record, @Param("example") FriendRequestPoExample example);

    int updateByExample(@Param("record") FriendRequestPo record, @Param("example") FriendRequestPoExample example);

    int updateByPrimaryKeySelective(FriendRequestPo record);

    int updateByPrimaryKey(FriendRequestPo record);
}