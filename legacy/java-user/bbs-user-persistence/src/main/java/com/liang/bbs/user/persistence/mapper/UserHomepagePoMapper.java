package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserHomepagePo;
import com.liang.bbs.user.persistence.entity.UserHomepagePoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserHomepagePoMapper {
    long countByExample(UserHomepagePoExample example);

    int deleteByExample(UserHomepagePoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UserHomepagePo record);

    int insertSelective(UserHomepagePo record);

    List<UserHomepagePo> selectByExampleWithBLOBs(UserHomepagePoExample example);

    List<UserHomepagePo> selectByExample(UserHomepagePoExample example);

    UserHomepagePo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") UserHomepagePo record, @Param("example") UserHomepagePoExample example);

    int updateByExampleWithBLOBs(@Param("record") UserHomepagePo record, @Param("example") UserHomepagePoExample example);

    int updateByExample(@Param("record") UserHomepagePo record, @Param("example") UserHomepagePoExample example);

    int updateByPrimaryKeySelective(UserHomepagePo record);

    int updateByPrimaryKeyWithBLOBs(UserHomepagePo record);

    int updateByPrimaryKey(UserHomepagePo record);

    List<UserHomepagePo> selectActivitiesByTopic(@Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);
}