package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.SysInstitutionPermissionPo;
import com.liang.bbs.user.persistence.entity.SysInstitutionPermissionPoExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysInstitutionPermissionPoMapper {
    long countByExample(SysInstitutionPermissionPoExample example);

    int deleteByExample(SysInstitutionPermissionPoExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SysInstitutionPermissionPo record);

    int insertSelective(SysInstitutionPermissionPo record);

    List<SysInstitutionPermissionPo> selectByExample(SysInstitutionPermissionPoExample example);

    SysInstitutionPermissionPo selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") SysInstitutionPermissionPo record, @Param("example") SysInstitutionPermissionPoExample example);

    int updateByPrimaryKeySelective(SysInstitutionPermissionPo record);
}
