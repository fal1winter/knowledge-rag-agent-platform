package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.ResourcePermissionPo;
import com.liang.bbs.user.persistence.entity.ResourcePermissionPoExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResourcePermissionPoMapper {
    long countByExample(ResourcePermissionPoExample example);

    int deleteByExample(ResourcePermissionPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ResourcePermissionPo record);

    int insertSelective(ResourcePermissionPo record);

    List<ResourcePermissionPo> selectByExample(ResourcePermissionPoExample example);

    ResourcePermissionPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ResourcePermissionPo record, @Param("example") ResourcePermissionPoExample example);

    int updateByExample(@Param("record") ResourcePermissionPo record, @Param("example") ResourcePermissionPoExample example);

    int updateByPrimaryKeySelective(ResourcePermissionPo record);

    int updateByPrimaryKey(ResourcePermissionPo record);
}
