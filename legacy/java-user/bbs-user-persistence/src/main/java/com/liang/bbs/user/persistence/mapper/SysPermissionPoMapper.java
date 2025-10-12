package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysPermissionPo;
import com.liang.bbs.user.persistence.entity.SysPermissionPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysPermissionPoMapper {
    long countByExample(SysPermissionPoExample example);

    int deleteByExample(SysPermissionPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysPermissionPo record);

    int insertSelective(SysPermissionPo record);

    List<SysPermissionPo> selectByExample(SysPermissionPoExample example);

    SysPermissionPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysPermissionPo record, @Param("example") SysPermissionPoExample example);

    int updateByExample(@Param("record") SysPermissionPo record, @Param("example") SysPermissionPoExample example);

    int updateByPrimaryKeySelective(SysPermissionPo record);

    int updateByPrimaryKey(SysPermissionPo record);
}