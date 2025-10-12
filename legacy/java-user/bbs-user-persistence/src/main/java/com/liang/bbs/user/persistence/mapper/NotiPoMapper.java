package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.NotiPo;
import com.liang.bbs.user.persistence.entity.NotiPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotiPoMapper {
    long countByExample(NotiPoExample example);

    int deleteByExample(NotiPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(NotiPo record);

    int insertSelective(NotiPo record);

    List<NotiPo> selectByExampleWithBLOBs(NotiPoExample example);

    List<NotiPo> selectByExample(NotiPoExample example);

    NotiPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") NotiPo record, @Param("example") NotiPoExample example);

    int updateByExampleWithBLOBs(@Param("record") NotiPo record, @Param("example") NotiPoExample example);

    int updateByExample(@Param("record") NotiPo record, @Param("example") NotiPoExample example);

    int updateByPrimaryKeySelective(NotiPo record);

    int updateByPrimaryKeyWithBLOBs(NotiPo record);

    int updateByPrimaryKey(NotiPo record);
}