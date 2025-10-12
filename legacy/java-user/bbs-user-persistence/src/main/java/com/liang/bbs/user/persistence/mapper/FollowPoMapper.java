package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.FollowPo;
import com.liang.bbs.user.persistence.entity.FollowPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FollowPoMapper {
    Integer countByExample(FollowPoExample example);

    int deleteByExample(FollowPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(FollowPo record);

    int insertSelective(FollowPo record);

    List<FollowPo> selectByExample(FollowPoExample example);

    FollowPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") FollowPo record, @Param("example") FollowPoExample example);

    int updateByExample(@Param("record") FollowPo record, @Param("example") FollowPoExample example);

    int updateByPrimaryKeySelective(FollowPo record);

    int updateByPrimaryKey(FollowPo record);
}