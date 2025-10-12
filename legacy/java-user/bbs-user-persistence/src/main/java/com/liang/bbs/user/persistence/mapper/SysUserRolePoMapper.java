package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysUserRolePo;
import com.liang.bbs.user.persistence.entity.SysUserRolePoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysUserRolePoMapper {
    long countByExample(SysUserRolePoExample example);

    int deleteByExample(SysUserRolePoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysUserRolePo record);

    int insertSelective(SysUserRolePo record);

    List<SysUserRolePo> selectByExample(SysUserRolePoExample example);

    SysUserRolePo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysUserRolePo record, @Param("example") SysUserRolePoExample example);

    int updateByExample(@Param("record") SysUserRolePo record, @Param("example") SysUserRolePoExample example);

    int updateByPrimaryKeySelective(SysUserRolePo record);

    int updateByPrimaryKey(SysUserRolePo record);
}