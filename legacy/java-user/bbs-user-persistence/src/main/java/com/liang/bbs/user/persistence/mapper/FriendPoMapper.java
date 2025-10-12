package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.FriendPo;
import com.liang.bbs.user.persistence.entity.FriendPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FriendPoMapper {
    long countByExample(FriendPoExample example);

    int deleteByExample(FriendPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(FriendPo record);

    int insertSelective(FriendPo record);

    List<FriendPo> selectByExample(FriendPoExample example);

    FriendPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") FriendPo record, @Param("example") FriendPoExample example);

    int updateByExample(@Param("record") FriendPo record, @Param("example") FriendPoExample example);

    int updateByPrimaryKeySelective(FriendPo record);

    int updateByPrimaryKey(FriendPo record);
}