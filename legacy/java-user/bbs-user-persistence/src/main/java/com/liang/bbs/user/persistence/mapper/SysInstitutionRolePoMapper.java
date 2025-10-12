package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysInstitutionRolePo;
import com.liang.bbs.user.persistence.entity.SysInstitutionRolePoExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysInstitutionRolePoMapper {
    long countByExample(SysInstitutionRolePoExample example);

    int deleteByExample(SysInstitutionRolePoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysInstitutionRolePo record);

    int insertSelective(SysInstitutionRolePo record);

    List<SysInstitutionRolePo> selectByExample(SysInstitutionRolePoExample example);

    SysInstitutionRolePo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysInstitutionRolePo record, @Param("example") SysInstitutionRolePoExample example);

    int updateByPrimaryKeySelective(SysInstitutionRolePo record);
}
