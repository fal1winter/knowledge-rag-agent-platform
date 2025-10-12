package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysRolePo;
import com.liang.bbs.user.persistence.entity.SysRolePoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysRolePoMapper {
    long countByExample(SysRolePoExample example);

    int deleteByExample(SysRolePoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysRolePo record);

    int insertSelective(SysRolePo record);

    List<SysRolePo> selectByExample(SysRolePoExample example);

    SysRolePo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysRolePo record, @Param("example") SysRolePoExample example);

    int updateByExample(@Param("record") SysRolePo record, @Param("example") SysRolePoExample example);

    int updateByPrimaryKeySelective(SysRolePo record);

    int updateByPrimaryKey(SysRolePo record);
}