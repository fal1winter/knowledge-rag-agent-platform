package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysRolePermissionPo;
import com.liang.bbs.user.persistence.entity.SysRolePermissionPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysRolePermissionPoMapper {
    long countByExample(SysRolePermissionPoExample example);

    int deleteByExample(SysRolePermissionPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysRolePermissionPo record);

    int insertSelective(SysRolePermissionPo record);

    List<SysRolePermissionPo> selectByExample(SysRolePermissionPoExample example);

    SysRolePermissionPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysRolePermissionPo record, @Param("example") SysRolePermissionPoExample example);

    int updateByExample(@Param("record") SysRolePermissionPo record, @Param("example") SysRolePermissionPoExample example);

    int updateByPrimaryKeySelective(SysRolePermissionPo record);

    int updateByPrimaryKey(SysRolePermissionPo record);
}