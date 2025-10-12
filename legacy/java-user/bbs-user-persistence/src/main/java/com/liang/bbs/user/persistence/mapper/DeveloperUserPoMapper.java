package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.DeveloperUserPo;
import com.liang.bbs.user.persistence.entity.DeveloperUserPoExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DeveloperUserPoMapper {
    long countByExample(DeveloperUserPoExample example);

    int deleteByExample(DeveloperUserPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(DeveloperUserPo record);

    int insertSelective(DeveloperUserPo record);

    List<DeveloperUserPo> selectByExample(DeveloperUserPoExample example);

    DeveloperUserPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") DeveloperUserPo record, @Param("example") DeveloperUserPoExample example);

    int updateByExample(@Param("record") DeveloperUserPo record, @Param("example") DeveloperUserPoExample example);

    int updateByPrimaryKeySelective(DeveloperUserPo record);

    int updateByPrimaryKey(DeveloperUserPo record);
}
