package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysUserInstitutionPo;
import com.liang.bbs.user.persistence.entity.SysUserInstitutionPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SysUserInstitutionPoMapper {
    long countByExample(SysUserInstitutionPoExample example);

    int deleteByExample(SysUserInstitutionPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysUserInstitutionPo record);

    int insertSelective(SysUserInstitutionPo record);

    List<SysUserInstitutionPo> selectByExample(SysUserInstitutionPoExample example);

    SysUserInstitutionPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysUserInstitutionPo record, @Param("example") SysUserInstitutionPoExample example);

    int updateByExample(@Param("record") SysUserInstitutionPo record, @Param("example") SysUserInstitutionPoExample example);

    int updateByPrimaryKeySelective(SysUserInstitutionPo record);

    int updateByPrimaryKey(SysUserInstitutionPo record);
}